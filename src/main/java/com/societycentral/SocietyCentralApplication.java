package com.societycentral;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SocietyCentralApplication {

    public static void main(String[] args) {
        SpringApplication.run(SocietyCentralApplication.class, args);

    }
}
