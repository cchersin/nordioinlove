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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.MediaType;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Cookie;


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
  @ResponseBody
  String createStudent(@RequestBody MultiValueMap<String, String> formData) throws Exception {
    migrate();

    try (Connection connection = dataSource.getConnection()) {    
      PreparedStatement insert = connection.prepareStatement("insert into student (id, name, gender, school_class, gender_preference, address) values (?, ?, ?, ?, ?, ?)");
      
      UUID id = java.util.UUID.randomUUID();
      

      List<String> genderPreferences = new ArrayList<String>();
      
      if (formData.get("ragazzo") != null) {
        genderPreferences.add("ragazzo");
      }
      if (formData.get("ragazza") != null) {
        genderPreferences.add("ragazza");
      }
      if (formData.get("nonbinary") != null) {
        genderPreferences.add("nonbinary");
      }

      insert.setObject(1, id);
      insert.setString(2, String.valueOf(formData.get("name").get(0)));
      insert.setString(3, formData.get("gender").get(0));
      insert.setString(4, formData.get("school_class").get(0));
      insert.setString(5, genderPreferences.stream().map(String::valueOf).collect(Collectors.joining(",")));
      insert.setString(6, formData.get("address").get(0));

      // request.getSession().setAttribute("name", formData.get("name").toString());
      
      insert.executeUpdate();

      return "{ \"redirectUrl\": \"/questions\", \"token\": \"" + id + "\"}";
    }
  }

  @RequestMapping("/questions")
  String getQuestions(HttpServletRequest request, Map<String, Object> model) {
    Cookie cookie[]=request.getCookies();
    Cookie cook;
    String token="";
    if (cookie != null) {
      for (int i = 0; i < cookie.length; i++) {
        cook = cookie[i];
        if(cook.getName().equalsIgnoreCase("token"))
            token=cook.getValue();                  
      }    
    }
    System.out.println("token: " + token);

    model.put("student_id", token);
     
    return "questions";
  }

  @RequestMapping(value="/answers",
                method=RequestMethod.POST,
                consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  String saveAnswers(HttpServletRequest request, @RequestBody MultiValueMap<String, String> formData) throws Exception {
    migrate();

      Cookie cookie[]=request.getCookies();
    Cookie cook;
    String token="";
    if (cookie != null) {
      for (int i = 0; i < cookie.length; i++) {
        cook = cookie[i];
        if(cook.getName().equalsIgnoreCase("token"))
            token=cook.getValue();                  
      }    
    }
    System.out.println("token: " + token);


    System.out.println("token: " + token);
    UUID studentId = UUID.fromString(token);

    try (Connection connection = dataSource.getConnection()) {
      PreparedStatement insert = connection.prepareStatement("insert into answer (student_id, question, answer) values (?, ?, ?)");
    
      formData.keySet().stream().filter(k -> k.startsWith("q")).forEach(question -> {
        try {
          insert.setObject(1, studentId);
          insert.setString(2, question);
          insert.setString(3, formData.get(question).toString());

          insert.executeUpdate();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      });

      return "{ \"redirectUrl\": \"/bye\", \"token\": \"" + studentId.toString() + "\"}";
    }
  }

  @RequestMapping("/bye")
  String bye() {
    return "bye";
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

    // TODO mark already linked
    // TODO read in memory all students, answer to avoid multiple selects...
    // TODO multiple select gender preferences

    List<Score> scores = new ArrayList<>();
    String requiredGender = null;
    if("ragazzi".equals(student.genderPreference)) {
      requiredGender = "ragazzo";
    } else if("ragazze".equals(student.genderPreference)) {
      requiredGender = "ragazza";
    }

    try (Connection connection = dataSource.getConnection()) {

        String sql = "SELECT id, name from student where id != ? ";
        if (requiredGender != null) {
          sql += " and (gender = ? or gender = 'nonbinary')";
        }
        PreparedStatement stmt = connection.prepareStatement(sql);
        
        stmt.setObject(1, student.id);
        if (requiredGender != null) {
           stmt.setString(2, requiredGender);
        }

        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
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
      String query = "SELECT id, name, gender, school_class, gender_preference, address from student order by school_class, name";
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
