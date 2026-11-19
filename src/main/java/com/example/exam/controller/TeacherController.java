package com.example.exam.controller;

import com.example.exam.model.*;
import com.example.exam.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class TeacherController {

    @Autowired
    private TeacherRepository teacherRepo;

    @Autowired
    private ExamRepository examRepo;

    @Autowired
    private StudentRepository studentRepo;

    @Autowired
    private QuestionRepository questionRepo;

    @Autowired
    private AnswerRepository answerRepo;

    @Autowired
    private ResultRepository resultRepo;

    // teacher login page
    @GetMapping("/teacher")
    public String loginPage() {
        return "teacher-login";
    }

    // check teacher login
    @PostMapping("/teacher/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
                        HttpSession session, Model model) {
        Teacher t = teacherRepo.findByEmail(email);
        if (t != null && t.getPassword().equals(password)) {
            session.setAttribute("teacher", t);
            return "redirect:/teacher/dashboard";
        }
        model.addAttribute("error", "Invalid email or password");
        return "teacher-login";
    }

    // dashboard with student list
    @GetMapping("/teacher/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Teacher t = (Teacher) session.getAttribute("teacher");
        if (t == null) return "redirect:/teacher";

        Exam exam = examRepo.findFirstByOrderByStartTimeDesc();
        List<Map<String, Object>> rows = new ArrayList<>();
        int submitted = 0;
        int evaluated = 0;

        if (exam != null) {
            for (Student s : studentRepo.findAll()) {
                Result r = resultRepo.findByStudentIdAndExamId(s.getId(), exam.getId());
                Map<String, Object> row = new HashMap<>();
                row.put("id", s.getId());
                row.put("enrollmentNo", s.getEnrollmentNo());
                row.put("name", s.getName());
                if (r == null) {
                    row.put("status", "NOT ATTEMPTED");
                    row.put("marks", "-");
                    row.put("canEvaluate", false);
                } else {
                    row.put("status", r.getStatus());
                    row.put("marks", r.getTotalMarks());
                    row.put("canEvaluate", true);
                    if (r.getStatus().equals("EVALUATED")) evaluated++;
                    else submitted++;
                }
                rows.add(row);
            }
        }

        model.addAttribute("teacher", t);
        model.addAttribute("exam", exam);
        model.addAttribute("rows", rows);
        model.addAttribute("totalStudents", rows.size());
        model.addAttribute("pending", submitted);
        model.addAttribute("evaluated", evaluated);
        return "teacher-dashboard";
    }

    // answer sheet of one student
    @GetMapping("/teacher/evaluate/{studentId}")
    public String evaluate(@PathVariable Long studentId, HttpSession session, Model model) {
        Teacher t = (Teacher) session.getAttribute("teacher");
        if (t == null) return "redirect:/teacher";

        Exam exam = examRepo.findFirstByOrderByStartTimeDesc();
        Student st = studentRepo.findById(studentId).orElse(null);
        if (exam == null || st == null) return "redirect:/teacher/dashboard";

        Result r = resultRepo.findByStudentIdAndExamId(studentId, exam.getId());
        if (r == null) return "redirect:/teacher/dashboard";

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Answer a : answerRepo.findByStudentIdAndExamId(studentId, exam.getId())) {
            Question q = questionRepo.findById(a.getQuestionId()).orElse(null);
            if (q == null) continue;

            Map<String, Object> row = new HashMap<>();
            row.put("answerId", a.getId());
            row.put("question", q.getQuestionText());
            row.put("selected", a.getSelectedOption() == null ? "-" : a.getSelectedOption());
            row.put("selectedText", optionText(q, a.getSelectedOption()));
            row.put("correct", q.getCorrectOption());
            row.put("correctText", optionText(q, q.getCorrectOption()));
            row.put("maxMarks", q.getMaxMarks());
            row.put("marks", a.getMarksAwarded());
            rows.add(row);
        }

        model.addAttribute("student", st);
        model.addAttribute("exam", exam);
        model.addAttribute("result", r);
        model.addAttribute("rows", rows);
        return "teacher-evaluate";
    }

    // save marks given by teacher
    @PostMapping("/teacher/evaluate/{studentId}")
    public String saveMarks(@PathVariable Long studentId,
                            @RequestParam Map<String, String> params,
                            HttpSession session) {
        Teacher t = (Teacher) session.getAttribute("teacher");
        if (t == null) return "redirect:/teacher";

        Exam exam = examRepo.findFirstByOrderByStartTimeDesc();
        if (exam == null) return "redirect:/teacher/dashboard";

        int total = 0;
        for (Answer a : answerRepo.findByStudentIdAndExamId(studentId, exam.getId())) {
            Question q = questionRepo.findById(a.getQuestionId()).orElse(null);
            if (q == null) continue;

            int marks = a.getMarksAwarded();
            String value = params.get("marks_" + a.getId());
            if (value != null) {
                try {
                    marks = Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    // keep old marks
                }
            }
            // marks must stay between 0 and max
            if (marks < 0) marks = 0;
            if (marks > q.getMaxMarks()) marks = q.getMaxMarks();

            a.setMarksAwarded(marks);
            answerRepo.save(a);
            total += marks;
        }

        Result r = resultRepo.findByStudentIdAndExamId(studentId, exam.getId());
        if (r != null) {
            r.setTotalMarks(total);
            r.setStatus("EVALUATED");
            resultRepo.save(r);
        }
        return "redirect:/teacher/dashboard";
    }

    // logout
    @GetMapping("/teacher/logout")
    public String logout(HttpSession session) {
        session.removeAttribute("teacher");
        return "redirect:/teacher";
    }

    // get option text from letter
    private String optionText(Question q, String letter) {
        if (letter == null) return "";
        switch (letter) {
            case "A": return q.getOptionA();
            case "B": return q.getOptionB();
            case "C": return q.getOptionC();
            case "D": return q.getOptionD();
            default: return "";
        }
    }
}