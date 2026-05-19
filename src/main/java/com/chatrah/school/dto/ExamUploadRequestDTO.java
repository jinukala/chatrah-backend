package com.chatrah.school.dto;

import java.util.List;

/**
 * Request DTO used when a teacher or principal uploads marks for a particular exam,
 * class, and subject. Typically populated after parsing an Excel sheet.
 */
public class ExamUploadRequestDTO {

    /** ID of the exam for which marks are being uploaded. */
    private Long examId;

    /** ID of the class-section combination to which these marks belong. */
    private Long classId;

    /** Subject name for which marks are being uploaded. */
    private String subject;

    /** Parsed list of student rows with marks from the Excel sheet or form. */
    private List<ExamMarkRowDTO> rows;

    public Long getExamId() {
        return examId;
    }

    public void setExamId(Long examId) {
        this.examId = examId;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public List<ExamMarkRowDTO> getRows() {
        return rows;
    }

    public void setRows(List<ExamMarkRowDTO> rows) {
        this.rows = rows;
    }
}
