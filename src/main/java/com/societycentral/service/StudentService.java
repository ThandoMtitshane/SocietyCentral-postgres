package com.societycentral.service;

import com.societycentral.model.School;
import com.societycentral.model.Student;
import com.societycentral.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    @Autowired
    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public List<Student> findAll() {
        return studentRepository.findAll();
    }

    public Optional<Student> findByStudentNumber(String studentNumber) {
        return studentRepository.findById(studentNumber);
    }

    public Optional<Student> findByEmail(String email) {
        return studentRepository.findByEmail(email);
    }

    public List<Student> findBySchool(School school) {
        // Faculty is derived from school - to filter by faculty, call
        // this method for each school in that faculty, or use a dedicated
        // @Query in StudentRepository. Used by the recommendation system.
        return studentRepository.findBySchool(school);
    }

    public List<Student> findByCourse(String course) {
        return studentRepository.findByCourse(course);
    }

    public Student save(Student student) {
        return studentRepository.save(student);
    }

    public void deleteByStudentNumber(String studentNumber) {
        studentRepository.deleteById(studentNumber);
    }

}