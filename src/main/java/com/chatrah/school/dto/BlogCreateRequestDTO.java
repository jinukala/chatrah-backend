package com.chatrah.school.dto;

/**
 * DTO for blog creation or update requests.
 * Contains only fields that are editable from UI.
 */
public class BlogCreateRequestDTO {

    private String title;
    private String content;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
