package com.lsearch.logsearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogSearchApplication.class, args);
    }
}
