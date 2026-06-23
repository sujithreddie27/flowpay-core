package com.flowpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlowPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowPayApplication.class, args);
    }
}
