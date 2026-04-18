package com.leo.politicas_de_negocio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableMongoRepositories(basePackages = "com.leo.politicas_de_negocio")
@EnableScheduling
public class PoliticasDeNegocioApplication {

	public static void main(String[] args) {
		SpringApplication.run(PoliticasDeNegocioApplication.class, args);
	}

}
