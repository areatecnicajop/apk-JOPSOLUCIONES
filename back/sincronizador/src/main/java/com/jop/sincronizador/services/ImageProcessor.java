// src/main/java/com/jop/sincronizador/services/ImageProcessor.java
package com.jop.sincronizador.services;

/**
 * Interfaz para procesar (redimensionar) imágenes.
 */
public interface ImageProcessor {
    /**
     * Redimensiona la imagen al tamaño configurado (ancho×alto).
     * @param originalImage bytes de la imagen original
     * @return bytes de la imagen redimensionada
     */
    byte[] resize(byte[] originalImage);
}
