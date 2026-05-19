package com.chatrah.school.resource;

import com.chatrah.school.entity.*;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.security.SecurityRoles;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.*;
import java.util.stream.Collectors;

@Path("/api/quizzes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class QuizResource {

    @Inject QuizRepo quizRepo;
    @Inject QuestionRepo questionRepo;
    @Inject AttemptRepo attemptRepo;
    @Inject ClassRoomRepository classRoomRepo;
    @Inject StudentRepository studentRepo;
    @Inject JsonWebToken jwt;

    // Teacher: Create quiz
    @POST
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @Transactional
    public Map<String, Object> createQuiz(Map<String, Object> body) {
        ClassRoom cr = classRoomRepo.findById(((Number) body.get("classId")).longValue());
        if (cr == null) throw new NotFoundException("Class not found");

        Quiz quiz = new Quiz();
        quiz.setClassRoom(cr);
        quiz.setSubject((String) body.get("subject"));
        quiz.setTitle((String) body.get("title"));
        quiz.setDurationMinutes(body.get("durationMinutes") != null ? ((Number) body.get("durationMinutes")).intValue() : 30);
        quiz.setPublished(false);
        quiz.setIitNeetOnly(Boolean.TRUE.equals(body.get("iitNeetOnly")));

        Object tc = jwt.getClaim("teacherId");
        if (tc instanceof Number) quiz.setCreatedByTeacherId(((Number) tc).longValue());
        else if (tc instanceof jakarta.json.JsonNumber) quiz.setCreatedByTeacherId(((jakarta.json.JsonNumber) tc).longValue());

        quizRepo.persist(quiz);

        // Add questions
        List<Map<String, Object>> questions = (List<Map<String, Object>>) body.get("questions");
        if (questions != null) {
            for (Map<String, Object> q : questions) {
                QuizQuestion qq = new QuizQuestion();
                qq.setQuiz(quiz);
                qq.setQuestion((String) q.get("question"));
                if (q.get("imageData") != null) qq.setImageData((String) q.get("imageData"));
                qq.setOptionA((String) q.get("optionA"));
                qq.setOptionB((String) q.get("optionB"));
                qq.setOptionC((String) q.get("optionC"));
                qq.setOptionD((String) q.get("optionD"));
                qq.setCorrectAnswer((String) q.get("correctAnswer"));
                questionRepo.persist(qq);
            }
        }

        return Map.of("id", quiz.getId(), "message", "Quiz created");
    }

    // Teacher: Publish quiz
    @PUT
    @Path("/{id}/publish")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @Transactional
    public Map<String, Object> publish(@PathParam("id") Long id) {
        Quiz q = quizRepo.findById(id);
        if (q == null) throw new NotFoundException("Quiz not found");
        q.setPublished(true);
        return Map.of("message", "Quiz published");
    }

    // List quizzes for a class
    @GET
    @Path("/class/{classId}")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.STUDENT, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public List<Map<String, Object>> listByClass(@PathParam("classId") Long classId) {
        return quizRepo.list("classRoom.id", classId).stream().map(q -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", q.getId()); m.put("title", q.getTitle()); m.put("subject", q.getSubject());
            m.put("durationMinutes", q.getDurationMinutes()); m.put("published", q.getPublished());
            m.put("iitNeetOnly", q.getIitNeetOnly());
            m.put("questionCount", questionRepo.count("quiz.id", q.getId()));
            m.put("createdAt", q.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());
    }

    // Student: Get quiz questions (no correct answers)
    @GET
    @Path("/{id}/take")
    @RolesAllowed({SecurityRoles.STUDENT})
    public Map<String, Object> takeQuiz(@PathParam("id") Long id) {
        Quiz q = quizRepo.findById(id);
        if (q == null || !Boolean.TRUE.equals(q.getPublished())) throw new NotFoundException("Quiz not available");

        // Check if already attempted
        Long studentId = extractStudentId();
        if (studentId != null && attemptRepo.count("quiz.id = ?1 and student.id = ?2", id, studentId) > 0) {
            throw new BadRequestException("Already attempted");
        }

        List<Map<String, Object>> questions = questionRepo.list("quiz.id", id).stream().map(qq -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", qq.getId()); m.put("question", qq.getQuestion());
            m.put("imageData", qq.getImageData());
            m.put("optionA", qq.getOptionA()); m.put("optionB", qq.getOptionB());
            m.put("optionC", qq.getOptionC()); m.put("optionD", qq.getOptionD());
            return m;
        }).collect(Collectors.toList());

        return Map.of("id", q.getId(), "title", q.getTitle(), "subject", q.getSubject(),
                "durationMinutes", q.getDurationMinutes(), "questions", questions);
    }

    // Student: Submit quiz
    @POST
    @Path("/{id}/submit")
    @RolesAllowed({SecurityRoles.STUDENT})
    @Transactional
    public Map<String, Object> submitQuiz(@PathParam("id") Long id, Map<String, Object> body) {
        Quiz q = quizRepo.findById(id);
        if (q == null) throw new NotFoundException("Quiz not found");

        Long studentId = extractStudentId();
        if (studentId == null) throw new ForbiddenException("No student");
        if (attemptRepo.count("quiz.id = ?1 and student.id = ?2", id, studentId) > 0) throw new BadRequestException("Already attempted");

        Student student = studentRepo.findById(studentId);
        Map<String, String> answers = (Map<String, String>) body.get("answers"); // questionId -> answer (A/B/C/D)
        List<QuizQuestion> questions = questionRepo.list("quiz.id", id);

        int correct = 0;
        for (QuizQuestion qq : questions) {
            String ans = answers != null ? answers.get(String.valueOf(qq.getId())) : null;
            if (ans != null && ans.equalsIgnoreCase(qq.getCorrectAnswer())) correct++;
        }

        int score = questions.size() > 0 ? (correct * 100) / questions.size() : 0;

        QuizAttempt attempt = new QuizAttempt();
        attempt.setQuiz(q); attempt.setStudent(student);
        attempt.setTotalQuestions(questions.size()); attempt.setCorrectAnswers(correct); attempt.setScore(score);
        attemptRepo.persist(attempt);

        return Map.of("correct", correct, "total", questions.size(), "score", score);
    }

    // Teacher/Admin: Get quiz results
    @GET
    @Path("/{id}/results")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public List<Map<String, Object>> getResults(@PathParam("id") Long id) {
        return attemptRepo.list("quiz.id", id).stream().map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("studentName", a.getStudent().getName()); m.put("rollNo", a.getStudent().getRollNo());
            m.put("correct", a.getCorrectAnswers()); m.put("total", a.getTotalQuestions());
            m.put("score", a.getScore()); m.put("submittedAt", a.getSubmittedAt().toString());
            return m;
        }).collect(Collectors.toList());
    }

    private Long extractStudentId() {
        Object c = jwt.getClaim("studentId");
        if (c instanceof Number) return ((Number) c).longValue();
        if (c instanceof jakarta.json.JsonNumber) return ((jakarta.json.JsonNumber) c).longValue();
        return null;
    }

    @ApplicationScoped
    public static class QuizRepo implements PanacheRepository<Quiz> {}
    @ApplicationScoped
    public static class QuestionRepo implements PanacheRepository<QuizQuestion> {}
    @ApplicationScoped
    public static class AttemptRepo implements PanacheRepository<QuizAttempt> {}
}
