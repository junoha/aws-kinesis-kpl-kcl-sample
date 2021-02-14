package com.junoha.sample.kinesis.springbootdemo.producer;

import com.junoha.sample.kinesis.springbootdemo.producer.service.ProducerService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class KdsProducer {

    public static void main(String[] args) {
        SpringApplication.run(KdsProducer.class, args);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            ProducerService service = ctx.getBean(ProducerService.class);
            service.execute();
        };
    }
}
