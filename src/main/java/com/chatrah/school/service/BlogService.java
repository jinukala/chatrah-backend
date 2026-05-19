package com.chatrah.school.service;

import com.chatrah.school.dto.BlogCreateRequestDTO;
import com.chatrah.school.dto.BlogDTO;
import com.chatrah.school.entity.Blog;
import com.chatrah.school.entity.Student;
import com.chatrah.school.entity.Teacher;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.BlogRepository;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.repository.TeacherRepository;
import com.chatrah.school.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class BlogService {

    @Inject BlogRepository blogRepository;
    @Inject UserRepository userRepository;
    @Inject StudentRepository studentRepository;
    @Inject TeacherRepository teacherRepository;

    @Transactional
    public BlogDTO createBlog(Long userId, String authorName, BlogCreateRequestDTO request) {
        Blog blog = new Blog();
        blog.setUserId(userId);
        blog.setTitle(request.getTitle());
        blog.setContent(request.getContent());
        blog.setStatus("PENDING");
        blogRepository.persist(blog);
        BlogDTO dto = toDTO(blog);
        dto.setAuthorName(authorName);
        return dto;
    }

    @Transactional
    public void approveBlog(Long id) {
        Blog b = blogRepository.findById(id);
        if (b == null) throw new NotFoundException("Blog not found");
        b.setStatus("APPROVED");
    }

    @Transactional
    public void rejectBlog(Long id) {
        Blog b = blogRepository.findById(id);
        if (b == null) throw new NotFoundException("Blog not found");
        b.setStatus("REJECTED");
    }

    public List<BlogDTO> listApproved() {
        return blogRepository.findApproved().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<BlogDTO> listApprovedPaginated(int page, int size) {
        return blogRepository.find("status", io.quarkus.panache.common.Sort.descending("createdAt"), "APPROVED")
                .page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<BlogDTO> listPending() {
        return blogRepository.findPending().stream().map(this::toDTO).collect(Collectors.toList());
    }

    private BlogDTO toDTO(Blog b) {
        BlogDTO dto = new BlogDTO();
        dto.setId(b.getId());
        dto.setUserId(b.getUserId());
        dto.setTitle(b.getTitle());
        dto.setContent(b.getContent());
        dto.setStatus(b.getStatus());
        dto.setCreatedAt(b.getCreatedAt());

        // Populate author info
        if (b.getUserId() != null) {
            User user = userRepository.findById(b.getUserId());
            if (user != null) {
                dto.setAuthorRole(user.getRole());
                if ("STUDENT".equals(user.getRole()) && user.getStudentId() != null) {
                    Student s = studentRepository.findById(user.getStudentId());
                    if (s != null) {
                        dto.setAuthorName(s.getName());
                        if (s.getClassRoom() != null) {
                            dto.setAuthorClass("Class " + s.getClassRoom().getClassName() + " - " + s.getClassRoom().getSection());
                        }
                    }
                } else if ("TEACHER".equals(user.getRole()) && user.getTeacherId() != null) {
                    Teacher t = teacherRepository.findById(user.getTeacherId());
                    if (t != null) dto.setAuthorName(t.getName());
                    dto.setAuthorClass("Teacher");
                } else {
                    dto.setAuthorName(user.getUsername());
                    dto.setAuthorClass(user.getRole());
                }
            }
        }
        return dto;
    }
}
