package com.czl.teamupbackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.czl.teamupbackend.mapper")
public class TeamUpBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamUpBackEndApplication.class, args);
    }

}
