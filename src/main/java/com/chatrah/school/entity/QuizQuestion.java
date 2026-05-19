package com.chatrah.school.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(nullable = false, length = 1000)
    private String question;

    /** Base64 image snippet for the question (optional) */
    @Column(columnDefinition = "TEXT")
    private String imageData;

    @Column(nullable = false)
    private String optionA;
    @Column(nullable = false)
    private String optionB;
    @Column(nullable = false)
    private String optionC;
    @Column(nullable = false)
    private String optionD;

    /** Correct answer: A, B, C, or D */
    @Column(nullable = false)
    private String correctAnswer;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Quiz getQuiz() { return quiz; }
    public void setQuiz(Quiz quiz) { this.quiz = quiz; }
    public String getQuestion() { return question; }
    public void setQuestion(String q) { this.question = q; }
    public String getImageData() { return imageData; }
    public void setImageData(String imageData) { this.imageData = imageData; }
    public String getOptionA() { return optionA; }
    public void setOptionA(String o) { this.optionA = o; }
    public String getOptionB() { return optionB; }
    public void setOptionB(String o) { this.optionB = o; }
    public String getOptionC() { return optionC; }
    public void setOptionC(String o) { this.optionC = o; }
    public String getOptionD() { return optionD; }
    public void setOptionD(String o) { this.optionD = o; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String a) { this.correctAnswer = a; }
}
