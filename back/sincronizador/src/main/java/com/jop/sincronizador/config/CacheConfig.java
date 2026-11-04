package com.jop.sincronizador.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de caché para la aplicación.
 * Utiliza caché en memoria para mejorar el rendimiento de consultas frecuentes.
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    /**
     * Configura el gestor de caché.
     * Usa ConcurrentMapCacheManager para caché en memoria simple.
     * 
     * @return CacheManager configurado
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        // Definir los nombres de los cachés
        cacheManager.setCacheNames(java.util.Arrays.asList(
            "catalogProducts"  // Caché para productos del catálogo extendido
        ));
        return cacheManager;
    }
}



