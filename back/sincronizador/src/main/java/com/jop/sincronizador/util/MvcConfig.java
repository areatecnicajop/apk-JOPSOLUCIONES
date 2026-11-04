package com.jop.sincronizador.util;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.repository.IConfigWordPress;
import com.jop.sincronizador.data.local.model.repository.ILaudusAllProducts;


@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private IConfigWordPress dao;
   
    // Bean para obtener la configuración de WordPress
    @Bean("getConfigWordPress")
    ConfigWordPress configWordPress() {
        ConfigWordPress aux = dao.findById(1l).get();
        return aux;
    }



    // Configuración de CORS
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Permitir solicitudes CORS desde http://localhost:4200
        registry.addMapping("/**")  // Permite CORS en todos los endpoints
                .allowedOrigins("https://jopsoluciones.com", "https://jopsoluciones.cl", "https://apkjop.jopsoluciones.cl", "http://localhost:4200", "http://localhost:4201", "http://localhost:8081", "http://localhost:61880")  // Permite solicitudes desde el frontend Angular
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // Permite métodos HTTP específicos
                .allowedHeaders("*")  // Permite cualquier encabezado
                .allowCredentials(true);  // Permite el uso de credenciales (cookies, cabeceras de autenticación, etc.)
    }
}
