package com.junoha.sample.kinesis.springbootdemo.consumer;

import com.junoha.sample.kinesis.springbootdemo.consumer.service.ConsumerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class KdsConsumer {

	public static void main(String[] args) {
		SpringApplication.run(KdsConsumer.class, args);
	}

	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			ConsumerService service = ctx.getBean(ConsumerService.class);
			service.execute();
		};
	}
}
