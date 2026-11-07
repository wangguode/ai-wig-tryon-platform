package com.wig.tryon;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class WigTryonApplication {

    public static void main(String[] args) {
        SpringApplication.run(WigTryonApplication.class, args);
    }
}
