package com.loan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LoanApplication {
    public static void main(String[] args) {
        // Java 21 虚拟线程特性已经在 application.yml 中开启
        // Spring Boot 3.4 会自动根据配置接管 Tomcat 的线程池
        SpringApplication.run(LoanApplication.class, args);
    }
}
