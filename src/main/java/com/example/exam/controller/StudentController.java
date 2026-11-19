package com.example.exam.controller;

import com.example.exam.model.Exam;
import com.example.exam.model.Student;
import com.example.exam.repository.ExamRepository;
import com.example.exam.repository.QuestionRepository;
import com.example.exam.repository.StudentRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.example.exam.model.Answer;
import com.example.exam.model.Question;
import com.example.exam.model.Result;
import com.example.exam.repository.AnswerRepository;
import com.example.exam.repository.ResultRepository;
import java.util.Map;

import java.time.Duration;
import java.time.LocalDateTime;

@Controller
public class StudentController {

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private ExamRepository examRepo;

    @Autowired
    private QuestionRepository questionRepo;

    @Autowired
    private AnswerRepository answerRepo;

    @Autowired
    private ResultRepository resultRepo;

    // show login page
    @GetMapping("/")
    public String loginPage() {
        return "login";
    }

    // check login details
    @PostMapping("/login")
    public String login(@RequestParam String enrollmentNo,
                        @RequestParam String password,
                        HttpSession session, Model model) {
        Student s = studentRepo.findByEnrollmentNo(enrollmentNo);
        if (s != null && s.getPassword().equals(password)) {
            session.setAttribute("student", s);
            return "redirect:/instructions";
        }
        model.addAttribute("error", "Invalid enrollment no. or password");
        return "login";
    }

    // waiting and instructions page
    @GetMapping("/instructions")
    public String instructions(HttpSession session, Model model) {
        Student s = (Student) session.getAttribute("student");
        if (s == null) return "redirect:/";

        Exam exam = examRepo.findFirstByOrderByStartTimeDesc();
        model.addAttribute("student", s);
        model.addAttribute("exam", exam);

        if (exam != null) {
            // time left in milliseconds
            long remainingMs = Duration.between(LocalDateTime.now(), exam.getStartTime()).toMillis();
            model.addAttribute("remainingMs", remainingMs);
        }
        return "instructions";
    }

    // exam page, only after start time
        @GetMapping("/exam")
    public String examPage(HttpSession session, Model model) {
        Student s = (Student) session.getAttribute("student");
        if (s == null) return "redirect:/";

        Exam exam = examRepo.findFirstByOrderByStartTimeDesc();
        if (exam == null || LocalDateTime.now().isBefore(exam.getStartTime())) {
            return "redirect:/instructions";
        }

        // already submitted, don't allow again
        if (resultRepo.findByStudentIdAndExamId(s.getId(), exam.getId()) != null) {
            return "redirect:/submitted";
        }

        // time left till exam ends
        LocalDateTime endTime = exam.getStartTime().plusMinutes(exam.getDurationMin());
        long examRemainingMs = Duration.between(LocalDateTime.now(), endTime).toMillis();

        model.addAttribute("exam", exam);
        model.addAttribute("questions", questionRepo.findByExamId(exam.getId()));
        model.addAttribute("examRemainingMs", examRemainingMs);
        return "exam";
    }

    // save answers when student submits
    @PostMapping("/submit")
    @ResponseBody
    public String submit(@RequestBody Map<String, String> answers, HttpSession session) {
        Student s = (Student) session.getAttribute("student");
        if (s == null) return "error";

        Exam exam = examRepo.findFirstByOrderByStartTimeDesc();
        if (exam == null) return "error";

        // ignore if already submitted
        if (resultRepo.findByStudentIdAndExamId(s.getId(), exam.getId()) != null) {
            return "ok";
        }

        int total = 0;
        for (Question q : questionRepo.findByExamId(exam.getId())) {
            String selected = answers.get(String.valueOf(q.getId()));
            int marks = 0;
            if (selected != null && selected.equals(q.getCorrectOption())) {
                marks = q.getMaxMarks();
            }
            total += marks;

            Answer a = new Answer();
            a.setStudentId(s.getId());
            a.setExamId(exam.getId());
            a.setQuestionId(q.getId());
            a.setSelectedOption(selected);
            a.setMarksAwarded(marks);
            answerRepo.save(a);
        }

        Result r = new Result();
        r.setStudentId(s.getId());
        r.setExamId(exam.getId());
        r.setTotalMarks(total);
        r.setStatus("SUBMITTED");
        resultRepo.save(r);
        return "ok";
    }

    // thank you page
    @GetMapping("/submitted")
    public String submitted(HttpSession session) {
        if (session.getAttribute("student") == null) return "redirect:/";
        return "submitted";
    }
}