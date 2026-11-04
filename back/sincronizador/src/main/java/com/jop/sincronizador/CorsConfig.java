package com.jop.sincronizador;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// CorsConfig desactivado — CORS se maneja en MvcConfig.java
// (tener dos WebMvcConfigurer con addCorsMappings causa conflicto)
@Configuration
public class CorsConfig {
}
