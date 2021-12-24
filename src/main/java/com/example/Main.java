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
import java.util.Map;
import java.util.UUID;

import org.springframework.util.MultiValueMap;



@Controller
@SpringBootApplication
public class Main {

  @Value("${spring.datasource.url}")
  private String dbUrl;

  @Autowired
  private DataSource dataSource;

  public static void main(String[] args) throws Exception {
    SpringApplication.run(Main.class, args);
  }

  @RequestMapping("/")
  String index() {
    return "index";
  }

  @RequestMapping(value="/student",
                method=RequestMethod.POST,
                consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  String createStudent(@RequestBody MultiValueMap<String, String> formData) throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();

      // stmt.executeUpdate("DROP TABLE student");
     
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS student (id uuid, name varchar, gender varchar, school_class varchar, gender_preference varchar)");
      
      PreparedStatement insert = connection.prepareStatement("insert into student (id, name, gender, school_class, gender_preference) values (?, ?, ?, ?, ? )");
      UUID id = java.util.UUID.randomUUID();
      
      insert.setObject(1, id);
      insert.setString(2, formData.get("name").toString());
      insert.setString(3, formData.get("gender").toString());
      insert.setString(4, formData.get("school_class").toString());
      insert.setString(5, formData.get("gender_preference").toString());

      // TODO created on
      
      insert.executeUpdate();

      return "questions";
    }
  }

  @RequestMapping(value="/answers",
                method=RequestMethod.POST,
                consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  String saveAnswers(@RequestBody MultiValueMap<String, String> formData) throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      Statement stmt = connection.createStatement();
     
      stmt.executeUpdate("CREATE TABLE IF NOT EXISTS answer (student_id uuid, question varchar, answer varchar)");
      
      PreparedStatement insert = connection.prepareStatement("insert into answer (student_id, question, answer) values (?, ?, ?, ?)");
      UUID studentId = java.util.UUID.randomUUID();
      
      insert.setObject(1, studentId);
      // insert.setString(3, formData.get("gender").toString());
      // insert.setString(4, formData.get("school_class").toString());
      // insert.setString(5, formData.get("gender_preference").toString());
      
      insert.executeUpdate();

      return "questions";
    }
  }

  @RequestMapping(value="/students",
                method=RequestMethod.GET)
  String getStudents(Map<String, Object> model) throws Exception {
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
          students.add(student);
        }
      }
    }

    model.put("students", students);

    return "students";
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
