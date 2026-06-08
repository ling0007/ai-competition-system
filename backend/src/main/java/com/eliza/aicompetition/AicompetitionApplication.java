package com.eliza.aicompetition;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.eliza.aicompetition.mapper")
public class AicompetitionApplication {

	public static void main(String[] args) {
		SpringApplication.run(AicompetitionApplication.class, args);
	}

}
