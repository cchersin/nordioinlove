/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.http.MediaType;


import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.util.MultiValueMap;


@Controller
// @Scope("session")
@SpringBootApplication
public class Main {

  @Value("${spring.datasource.url}")
  private String dbUrl;

  @Autowired
  private DataSource dataSource;

  private boolean migrated = false;

  public static void main(String[] args) throws Exception {
    SpringApplication.run(Main.class, args);
  }
 
  @RequestMapping("/")
  String index() {
    return "index";
  }

  void migrate() throws Exception{
   if (!migrated) {
      migrated = true;
      System.out.println("-------- MIGRATE --------- ");
      try (Connection connection = dataSource.getConnection()) {
        Statement stmt = connection.createStatement();
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS student (id uuid, name varchar, gender varchar, school_class varchar, gender_preference varchar, address varchar, created_on timestamp DEFAULT NOW());");
        stmt.executeUpdate("CREATE TABLE IF NOT EXISTS answer (student_id uuid, question varchar, answer varchar, created_on timestamp DEFAULT NOW());");
        System.out.println("-------- MIGRATE END --------- ");
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }
 
  @RequestMapping(value="/student",
                method=RequestMethod.POST,
                consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
  String createStudent(@RequestBody MultiValueMap<String, String> formData, Map<String, Object> model) throws Exception {
    migrate();

    try (Connection connection = dataSource.getConnection()) {    
       PreparedStatement insert = connection.prepareStatement("insert into student (id, name, gender, school_class, gender_preference, address) values (?, ?, ?, ?, ?, ?)");
      UUID id = java.util.UUID.randomUUID();
      
      insert.setObject(1, id);
      insert.setString(2, formData.get("name").toString());
      insert.setString(3, formData.get("gender").toString());
      insert.setString(4, formData.get("school_class").toString());
      insert.setString(5, formData.get("gender_preference").toString());
      insert.setString(6, formData.get("address") != null ? formData.get("address").toString() : null);

      // request.getSession().setAttribute("name", formData.get("name").toString());
      
      insert.executeUpdate();

      model.put("student_id", id);
      model.put("name", formData.get("name").toString());

      return "questions";
    }
  }

  @RequestMapping(value="/answers",
                method=RequestMethod.POST,
                consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  String saveAnswers(@RequestBody MultiValueMap<String, String> formData) throws Exception {
    migrate();

    try (Connection connection = dataSource.getConnection()) {
      PreparedStatement insert = connection.prepareStatement("insert into answer (student_id, question, answer) values (?, ?, ?, ?)");
    
      formData.keySet().stream().filter(k -> k.startsWith("q")).forEach(question -> {
        try {
          insert.setObject(1, UUID.fromString(formData.get("student_id").toString()));
          insert.setString(2, question);
          insert.setString(3, formData.get(question).toString());

          insert.executeUpdate();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      });

      return "bye";
    }
  }

  Integer calcScore(UUID id1, UUID id2) {
    return 0;
  }

  void buildPreferences(Student student) throws Exception {
    class SortByScore implements Comparator<Score> {
      public int compare(Score a, Score b) {
          return b.score - a.score;
      }
    }

    List<Score> scores = new ArrayList<>();
    String requiredGender = null;
    if("[ragazzi]".equals(student.genderPreference)) {
      requiredGender = "[ragazzo]";
    } else if("[ragazze]".equals(student.genderPreference)) {
      requiredGender = "[ragazza]";
    }

    try (Connection connection = dataSource.getConnection()) {

        String sql = "SELECT id, name from student where id != ? ";
        if (requiredGender != null) {
          sql += " and (gender = ? or gender = '[nonbinary]')";
        }
        PreparedStatement stmt = connection.prepareStatement(sql);
        
        stmt.setObject(1, student.id);
        if (requiredGender != null) {
           stmt.setString(2, requiredGender);
        }

        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
          System.out.println("next....");
          UUID candidateId = (UUID)rs.getObject("id");
          String candidateName = rs.getString("name");

          Score score = new Score();

          score.studentId = candidateId;
          score.studentName = candidateName;
          score.score = calcScore(student.id, candidateId);
          
          scores.add(score);
        }

        System.out.println("Found " + scores.size() + " preferences for " + requiredGender + " " + student.id);

        student.preferences = scores.stream().sorted(new SortByScore()).limit(3).map(s -> s.studentName).collect(Collectors.toList());
    }
  }

  @RequestMapping(value="/admin/students",
                method=RequestMethod.GET)
  String getStudents(Map<String, Object> model) throws Exception {
    migrate();

    List<Student> students = new ArrayList<Student>();

    try (Connection connection = dataSource.getConnection()) {
      String query = "SELECT id, name, gender, school_class, gender_preference from student";
      try (Statement stmt = connection.createStatement()) {
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
          Student student = new Student();
          student.id = (UUID)rs.getObject("id");
          student.name = rs.getString("name");
          student.schoolClass = rs.getString("school_class");
          student.gender = rs.getString("gender");
          student.genderPreference = rs.getString("gender_preference");

          buildPreferences(student);

          students.add(student);
        }
      }
    } catch(Exception e) {
      e.printStackTrace();
    }

    model.put("students", students);

    return "students";
  }

  @RequestMapping(value="/admin/reset",
                method=RequestMethod.POST)
  String reset() throws Exception {  
    System.out.println("RESET!!!!");
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
      stmt.executeUpdate("DROP TABLE IF EXISTS student;");
      stmt.executeUpdate("DROP TABLE IF EXISTS answer;");
    } catch(Exception e) {
      e.printStackTrace();
    }
    return "index";
  }

  @Bean
  public DataSource dataSource() throws SQLException {
    if (dbUrl == null || dbUrl.isEmpty()) {
      return new HikariDataSource();
    } else {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl(dbUrl);
      return new HikariDataSource(config);
    }
  }

}
