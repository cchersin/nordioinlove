package com.example;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfFilter;


@Configuration
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.pwd}")
    private String adminPassword; 

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {

        auth
          .inMemoryAuthentication()
          .withUser(adminUsername)
          .password(passwordEncoder().encode(adminPassword))
          .roles("ADMIN");
    }

    /* @Override
    public void configure(WebSecurity web) throws Exception {
      web
        .ignoring()
        .antMatchers("/resources/**");
    } */

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
          .addFilterAfter(new CsrfTokenResponseHeaderBindingFilter(), CsrfFilter.class)
          .httpBasic().and()
          .authorizeRequests()
          .antMatchers(HttpMethod.GET, "/admin/**").hasRole("ADMIN")
          .antMatchers(HttpMethod.POST, "/admin/**").hasRole("ADMIN")
          //.antMatchers(HttpMethod.GET, "/**").permitAll()
          //.antMatchers(HttpMethod.POST, "/**").permitAll()
          .antMatchers(HttpMethod.GET, "/**").hasRole("ADMIN")
          .antMatchers(HttpMethod.POST, "/**").hasRole("ADMIN")
          .anyRequest().authenticated();

          http.csrf().disable();  

    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

