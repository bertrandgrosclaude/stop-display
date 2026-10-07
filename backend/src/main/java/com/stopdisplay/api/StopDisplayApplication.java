package com.stopdisplay.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StopDisplayApplication {

    public static void main(String[] args) {
        SpringApplication.run(StopDisplayApplication.class, args);
    }
}
