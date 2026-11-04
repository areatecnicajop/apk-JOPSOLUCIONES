package com.jop.sincronizador.config;

import jakarta.servlet.MultipartConfigElement;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

// Límites de subida de archivos (el valor por defecto de Spring es 1 MB por archivo):
// el editor de fotos de Productos envía hasta 10 fotos de hasta 10 MB cada una.
@Configuration
public class MultipartLimitsConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(15));
        factory.setMaxRequestSize(DataSize.ofMegabytes(60));
        return factory.createMultipartConfig();
    }
}
