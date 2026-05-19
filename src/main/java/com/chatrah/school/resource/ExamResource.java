// src/main/java/com/chatrah/school/resource/ExamResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.ExamDTO;
import com.chatrah.school.dto.ExamUploadRequestDTO;
import com.chatrah.school.dto.StudentExamResultDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Exam;
import com.chatrah.school.entity.ExamMark;
import com.chatrah.school.repository.ExamMarkRepository;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.ClassRoomService;
import com.chatrah.school.service.ExamService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Inherited;
import java.util.List;

/**
 * REST resource for configuring exams, uploading marks, and fetching student results.
 */
@Path("/api/exams")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ExamResource {

    @Inject
    ExamService examService;

    @Inject
    ClassRoomService classRoomService;

    @Inject
    ExamMarkRepository examMarkRepository;

    @Inject
    JsonWebToken jwt;

    /**
     * List all exams.
     */
    @GET
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.TEACHER, SecurityRoles.STUDENT, SecurityRoles.SYS_ADMIN})
    public List<ExamDTO> listExams() {
        return examService.listAll();
    }

    /**
     * Create a new exam configuration (principal/clerk).
     */
    @POST
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public ExamDTO createExam(ExamDTO dto) {
        Long creatorUserId = resolveUserIdFromJwt();
        return examService.createExam(dto, creatorUserId);
    }

    /**
     * Upload marks for a given exam, class, and subject (from parsed Excel).
     */
    @POST
    @Path("/upload")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public Response uploadMarks(ExamUploadRequestDTO request) {

        examService.uploadExamMarks(request);
        return Response.ok().entity("{\"message\":\"Marks uploaded successfully\"}").build();
    }

    /**
     * Get a student's result for a specific exam.
     */
    @GET
    @Path("/{examId}/student/{studentId}")
    @RolesAllowed({SecurityRoles.STUDENT, SecurityRoles.TEACHER,
            SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public StudentExamResultDTO getStudentResult(@PathParam("examId") Long examId,
                                                 @PathParam("studentId") Long studentId) {
        return examService.getStudentResult(examId, studentId);
    }

    @GET
    @Path("/my-results")
    @RolesAllowed(SecurityRoles.STUDENT)
    public List<StudentExamResultDTO> getMyResults() {
        Object claim = jwt.getClaim("studentId");
        final Long studentId;
        if (claim instanceof Number) studentId = ((Number) claim).longValue();
        else if (claim instanceof jakarta.json.JsonNumber) studentId = ((jakarta.json.JsonNumber) claim).longValue();
        else { try { studentId = Long.parseLong(claim.toString()); } catch (Exception e) { throw new ForbiddenException("No studentId"); } }
        // Only show results from published exams
        List<Exam> exams = examService.listAllEntities().stream().filter(e -> Boolean.TRUE.equals(e.getPublished())).collect(java.util.stream.Collectors.toList());
        return exams.stream()
                .map(e -> examService.getStudentResult(e.getId(), studentId))
                .filter(r -> r.getSubjects() != null && !r.getSubjects().isEmpty())
                .collect(java.util.stream.Collectors.toList());
    }

    @PUT
    @Path("/{id}/publish")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @jakarta.transaction.Transactional
    public java.util.Map<String, Object> publishExam(@PathParam("id") Long id) {
        Exam exam = examService.findById(id);
        if (exam == null) throw new NotFoundException("Exam not found");
        exam.setPublished(true);
        return java.util.Map.of("message", "Results published");
    }

    @GET
    @Path("/{id}/upload-status")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public List<java.util.Map<String, Object>> getUploadStatus(@PathParam("id") Long id) {
        Exam exam = examService.findById(id);
        if (exam == null) throw new NotFoundException("Exam not found");
        List<com.chatrah.school.entity.ClassRoom> classes = classRoomService.findAll();
        List<java.util.Map<String, Object>> status = new java.util.ArrayList<>();
        for (var cr : classes) {
            String subjects = cr.getSubjects() != null ? cr.getSubjects() : "";
            for (String sub : subjects.split(",")) {
                sub = sub.trim();
                if (sub.isEmpty()) continue;
                long count = examMarkRepository.count("exam.id = ?1 and classRoom.id = ?2 and subject = ?3", id, cr.getId(), sub);
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("className", cr.getClassName());
                m.put("section", cr.getSection());
                m.put("subject", sub);
                m.put("uploaded", count > 0);
                m.put("count", count);
                status.add(m);
            }
        }
        return status;
    }

    private Long resolveUserIdFromJwt() {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1L;
    }

    /**
     * Export marks for a given exam, class, and subject as an Excel file.
     */
    @GET
    @Path("/{examId}/class/{classId}/subject/{subject}/export")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    @Produces("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public Response exportMarksExcel(@PathParam("examId") Long examId,
                                     @PathParam("classId") Long classId,
                                     @PathParam("subject") String subject) {
        Exam exam = examService.findById(examId);
        if (exam == null) {
            throw new NotFoundException("Exam not found");
        }
        ClassRoom classRoom = classRoomService.findById(classId);
        if (classRoom == null) {
            throw new NotFoundException("Class not found");
        }

        List<ExamMark> marks = examMarkRepository.findByExamAndClassRoom(exam, classRoom);

        // Filter by subject in memory (or add a repo method with subject filter if you prefer)
        marks = marks.stream()
                .filter(m -> subject.equalsIgnoreCase(m.getSubject()))
                .toList();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Marks");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Roll No");
            header.createCell(1).setCellValue("Student Name");
            header.createCell(2).setCellValue("Subject");
            header.createCell(3).setCellValue("Marks");
            header.createCell(4).setCellValue("Max Marks");

            int rowIdx = 1;
            for (ExamMark m : marks) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(
                        m.getStudent() != null && m.getStudent().getRollNo() != null
                                ? m.getStudent().getRollNo()
                                : 0
                );
                r.createCell(1).setCellValue(
                        m.getStudent() != null && m.getStudent().getName() != null
                                ? m.getStudent().getName()
                                : ""
                );
                r.createCell(2).setCellValue(m.getSubject() != null ? m.getSubject() : subject);
                r.createCell(3).setCellValue(m.getMarks() != null ? m.getMarks() : 0);
                r.createCell(4).setCellValue(m.getMaxMarks() != null ? m.getMaxMarks() : 0);
            }

            // Autosize columns
            for (int i = 0; i <= 4; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            byte[] bytes = baos.toByteArray();

            String safeSubject = subject.replaceAll("[^a-zA-Z0-9_-]", "_");
            String fileName = "marks_exam_" + examId + "_class_" + classId + "_" + safeSubject + ".xlsx";

            return Response.ok(bytes)
                    .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Excel", e);
        }
    }
}
