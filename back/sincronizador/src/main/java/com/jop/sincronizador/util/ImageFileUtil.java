package com.jop.sincronizador.util;

import com.jop.sincronizador.models.entity.dto.ImageDTO;
import com.jop.sincronizador.webSockets.WebSocket;

import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Base64;

@Component
public class ImageFileUtil {

    /**
     * Obtiene una lista de ImageDTO a partir de los archivos .jpg (o .jpeg)
     * que se encuentren en la carpeta formada por basePath y el SKU.
     *
     * @param basePath la ruta base donde se encuentran las imágenes (por ejemplo, "/ruta/a/imagenes")
     * @param sku el SKU del producto, que corresponde al nombre de la subcarpeta
     * @return Lista de ImageDTO con el contenido en base64 de cada imagen encontrada
     */
    public List<ImageDTO> getImageDTOs(String basePath, String sku) {
        List<ImageDTO> imageDTOs = new ArrayList<>();
        File folder = new File(basePath + File.separator + sku);

        if (!folder.exists() || !folder.isDirectory()) {
            String logMessage = "❌ Carpeta no encontrada: " + folder.getAbsolutePath();
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            
            return imageDTOs;
        }

        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".jpg") || name.toLowerCase().endsWith(".jpeg")||  name.toLowerCase().endsWith(".png"));

        if (files == null || files.length == 0) {
            String logMessage = "⚠️ No se encontraron imágenes en la carpeta: " + folder.getAbsolutePath();
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            
            return imageDTOs;
        }

        // 🔥 Ordena los archivos por nombre
        Arrays.sort(files, Comparator.comparing(File::getName));

        String logMessage = "📸 Se encontraron " + files.length + " imágenes en la carpeta: " + folder.getAbsolutePath();
        System.out.println(logMessage);
        WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket

        for (File file : files) {
            try {
                byte[] content = Files.readAllBytes(file.toPath());
                String base64Content = Base64.getEncoder().encodeToString(content);
                imageDTOs.add(new ImageDTO(base64Content));

                logMessage = "✅ Imagen leída correctamente: " + file.getName();
                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket

            } catch (IOException e) {
                logMessage = "🚨 Error al leer el archivo " + file.getName() + ": " + e.getMessage();
                
                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            }
        }

        return imageDTOs;
    }
}
