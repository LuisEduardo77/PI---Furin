package br.com.furin.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FurinAccessApplication {

	public static void main(String[] args) {
		SpringApplication.run(FurinAccessApplication.class, args);
	}

}
