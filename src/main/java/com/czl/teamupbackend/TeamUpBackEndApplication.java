package com.czl.teamupbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.czl.teamupbackend.mapper")
@EnableScheduling
@EnableAsync
public class TeamUpBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamUpBackEndApplication.class, args);
    }

}
