package com.example.hitmodel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class HitModelApplication {

    public static void main(String[] args) {
        SpringApplication.run(HitModelApplication.class, args);

    }

}
