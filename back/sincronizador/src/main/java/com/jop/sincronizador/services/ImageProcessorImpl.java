
// src/main/java/com/jop/sincronizador/services/ImageProcessorImpl.java
package com.jop.sincronizador.services;

import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Implementación de ImageProcessor que redimensiona imágenes usando Java2D.
 * Mantiene la relación de aspecto, centra la imagen en un lienzo 1000×1000 y
 * conserva transparencia para PNG.
 */
@Component
public class ImageProcessorImpl implements ImageProcessor {

    // Tamaño explícito (hardcodeado) para 1000×1000
    private static final int targetWidth = 1000;
    private static final int targetHeight = 1000;

    @Override
    public byte[] resize(byte[] originalImage) {
        try {
            // Leer la imagen desde bytes (detecta canal alpha si existe)
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(originalImage));

            // Calcular ratio para encajar en 1000×1000 sin recorte
            double ratio = Math.min(
                (double) targetWidth / src.getWidth(),
                (double) targetHeight / src.getHeight()
            );
            int newW = (int) (src.getWidth() * ratio);
            int newH = (int) (src.getHeight() * ratio);

            // Escalar la imagen
            Image tmp = src.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);

            // Crear lienzo ARGB de fondo transparente
            BufferedImage output = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = output.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            // Centrar imagen escalada
            int x = (targetWidth - newW) / 2;
            int y = (targetHeight - newH) / 2;
            g2.drawImage(tmp, x, y, null);
            g2.dispose();

            // Escribir a bytes PNG para mantener transparencia
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(output, "png", os);
            return os.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Error redimensionando imagen", e);
        }
    }
}
