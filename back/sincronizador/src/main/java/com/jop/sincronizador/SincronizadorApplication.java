package com.jop.sincronizador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SincronizadorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SincronizadorApplication.class, args);
    }
}
