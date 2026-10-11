package com.service.stagebet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StageBetApplication {

    public static void main(String[] args) {
        SpringApplication.run(StageBetApplication.class, args);
    }

}
