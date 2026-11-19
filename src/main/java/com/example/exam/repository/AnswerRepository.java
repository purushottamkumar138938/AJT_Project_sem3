package com.example.exam.repository;

import com.example.exam.model.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByStudentIdAndExamId(Long studentId, Long examId);
}