package com.jop.sincronizador.services;

import java.io.File;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jop.sincronizador.data.laudus.model.entity.OrderWP;
import com.jop.sincronizador.data.laudus.model.entity.ProductWP;
import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.LaudusStock;
import com.jop.sincronizador.data.local.model.entity.RegisterChange;
import com.jop.sincronizador.data.local.model.entity.TokenLaudus;
import com.jop.sincronizador.data.local.model.repository.IConfigWordPress;
import com.jop.sincronizador.data.local.model.services.IProductBDService;
import com.jop.sincronizador.data.local.model.services.ITransactionBDtoWPService;
import com.jop.sincronizador.data.local.model.services.TransactionBDtoWPService;
import com.jop.sincronizador.models.entity.dto.Image;
import com.jop.sincronizador.models.entity.dto.ImageDTO;
import com.jop.sincronizador.models.entity.dto.Product;
import com.jop.sincronizador.models.entity.dto.ProductId;
import com.jop.sincronizador.models.entity.dto.ProductListImages;
import com.jop.sincronizador.webSockets.WebSocket;

import ch.qos.logback.core.subst.Token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationContext;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


@Service
public class RestLaudusWordP implements IRestLaudusWordP{
    
String urlSearch = "products?sku=";
String urlSearchCategorie = "products/categories/";
    @Autowired
	private ApplicationContext context;
    
    @Autowired
    private IProductBDService bd;
    
    @Autowired
    private IConfigWordPress bdConfig;


 
    

    @Override
    public int ProductGetId(String sku) {
        Gson gson = new Gson();
        int productId = 0;  // Variable para almacenar el ID del producto
        int code = 0;
        
        // 🔥 Credenciales y URL de la API
        ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
        String baseUrl = cwp.getUrlBase() + urlSearch + sku; // Filtrar por SKU
        String username = cwp.getUsernameToken();
        String password = cwp.getPasswordToken();
    
        try {
            // 🔥 Crear HttpClient
            HttpClient client = HttpClient.newHttpClient();
    
            // 🔥 Codificar las credenciales en Base64
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    
            // 🔥 Crear la solicitud HTTP GET con Basic Auth
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();
    
            // 🔥 Enviar la solicitud y recibir la respuesta
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            code = response.statusCode();
    
            if (code == 200) {
                // 🔥 Convertir JSON en un array de productos
                ProductId[] products = gson.fromJson(response.body(), ProductId[].class);
                if (products.length > 0) {
                    productId = products[0].getId(); // Tomar el primer producto
                 
                    
                } else {
                    String logMessage = "⚠️ No se encontraron productos con SKU: " + sku;
                    
                    System.out.println(logMessage);
                    WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
                }
            } else if (code == 404) {
                String logMessage = "❌ Producto no encontrado - SKU: " + sku;
                
                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            } else if (code == 401) {
                String logMessage = "🚨 Acceso no autorizado.";
                
                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            } else {
                String logMessage = "🔴 Error inesperado - Código: " + code;
                
                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            }
    
        } catch (Exception e) {
            String logMessage = "🔥 Error en la solicitud: " + e.getMessage();
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            e.printStackTrace();
        }
    
        return productId; // Retorna el ID del producto o 0 si no se encontró
    }
    

    @Override
    public int ProductGetIdCategorie(Integer idWpCategorie) {
        
        int responseCode = 0;
    
        // 🔥 Credenciales y URL de la API
        ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
        String baseUrl = cwp.getUrlBase() + urlSearchCategorie + idWpCategorie ; 
        String username = cwp.getUsernameToken();
        String password = cwp.getPasswordToken();
    
        try {
            // 🔥 Crear HttpClient
            HttpClient client = HttpClient.newHttpClient();
    
            // 🔥 Codificar credenciales en Base64
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    
            HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl))
            .header("Content-Type", "application/json")
            .header("Authorization", "Basic " + encodedAuth)
            .GET() // 🔥 Cambiado de POST() a GET()
            .build();
    
            // 🔥 Enviar la solicitud y recibir respuesta
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            responseCode = response.statusCode();
    
            if (responseCode != 200) {
                return 0; // Si recibe 404, devuelve 0
            } else  {
                return idWpCategorie; // Si recibe 202, devuelve 1
            }
    
        } catch (Exception e) {
            e.printStackTrace();
        }
    
        return responseCode; // Devuelve el código en cualquier otro caso
    }
    

    
@Override
public ProductListImages ProductGetImages(String sku) {
    Gson gson = new Gson();
    ProductListImages productImages = new ProductListImages(); // Para almacenar los IDs de imágenes
    List<String> imageIds = new ArrayList<>();
    int code = 0;

    // Configuración de la API
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getUrlBase() + urlSearch + sku;
    String username = cwp.getUsernameToken();
    String password = cwp.getPasswordToken();

    try {
        HttpClient client = HttpClient.newHttpClient();

        // Autenticación en Base64
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + encodedAuth)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        code = response.statusCode();

        if (code == 200) {
            // Convertir JSON en un array de productos
            Product[] products = gson.fromJson(response.body(), Product[].class);
            if (products.length > 0) {
                // Extraer los IDs de las imágenes del primer producto
                for (Image img : products[0].getImages()) {
                    imageIds.add(String.valueOf(img.getId())); // Guardamos los IDs
                }
            }
        } else {
            System.out.println("Error: " + code + " para SKU: " + sku);
        }

    } catch (Exception e) {
        System.out.println("Error en la solicitud: " + e.getMessage());
    }

    productImages.setProductImagesIds(imageIds); // Guardamos la lista de IDs
    return productImages; // Retornamos el objeto con la lista de IDs de imágenes
}


    
@Override
public Integer ProductPut(String id, String json, String message) {
    int code = 0;

    // 🔥 Credenciales y URL de la API
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getUrlBase();
    String username = cwp.getUsernameToken();
    String password = cwp.getPasswordToken();

    try {
        // 🔥 Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // 🔥 Codificar las credenciales en Base64
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // 🔥 Endpoint para actualizar el producto
        String updateUrl = baseUrl + "products/" + id;  // Usar directamente el ID recibido
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(updateUrl))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))  // JSON recibido para actualizar
                .build();

        // 🔥 Enviar la solicitud
        HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());
        code = putResponse.statusCode();

        if (code == 200) {
            String logMessage = "✅ Producto " + message + " con éxito. ID: " + id;
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            
        } else {
            String logMessage = "❌ Error al actualizar producto. Código: " + code + " | ID: " + id;
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            
            WebSocket.sendMessageToAll("🔴 Respuesta API: " + putResponse.body()); // 🔥 Mostrar detalles de error
        }

    } catch (Exception e) {
        String logMessage = "🚨 Error durante la sincronización: " + e.getMessage();
        
        System.out.println(logMessage);
        WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
        
        return code;
    }

    return code;
}

    
    
@Override
public String CategoriePut(Integer id, String json ) {
    int code = 0;

    // 🔥 Credenciales y URL de la API
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getUrlBase();
    String username = cwp.getUsernameToken();
    String password = cwp.getPasswordToken();

    try {
        // 🔥 Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // 🔥 Codificar las credenciales en Base64
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // 🔥 Endpoint para actualizar el producto
        String updateUrl = baseUrl + "products/categories/" + id ;  // Usar directamente el ID recibido
        HttpRequest putRequest = HttpRequest.newBuilder()
                .uri(URI.create(updateUrl))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))  // JSON recibido para actualizar
                .build();

        // 🔥 Enviar la solicitud
        HttpResponse<String> putResponse = client.send(putRequest, HttpResponse.BodyHandlers.ofString());
        code = putResponse.statusCode();

        if (code == 200) {
            String logMessage = "✅ Categoria actualizado con éxito. ID: " + id;
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            
        } else {
            String logMessage = "❌ Error al actualizar categoria . Código: " + code + " | ID: " + id;
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            
            WebSocket.sendMessageToAll("🔴 Respuesta API: " + putResponse.body()); // 🔥 Mostrar detalles de error
        }

    } catch (Exception e) {
        String logMessage = "🚨 Error durante la sincronización: " + e.getMessage();
        
        System.out.println(logMessage);
        WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
        
        return "Error: " + e.getMessage();
    }

    return String.valueOf(code);
}

    



@Override
public List<ProductWP> ProductGetAll() {
    Gson gson = new Gson();
    List<ProductWP> productList = new ArrayList<>();
    int code;

      ConfigWordPress cwp = context.getBean("getConfigWordPress",  ConfigWordPress.class);
        String baseUrl = cwp.getUrlBase() + "products"; 
        String username = cwp.getUsernameToken();
        String password = cwp.getPasswordToken();// Reemplaza con tu contraseña

    try {
        // Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // Codificar las credenciales en Base64
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            // Crear URL con parámetros de paginación
            String urlWithPagination = baseUrl + "?per_page=100&page=" + page;

            // Crear la solicitud HTTP GET con Basic Auth
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlWithPagination))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();

            // Enviar la solicitud y recibir la respuesta
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            code = response.statusCode();

            if (code == 200) {
                // Parsear el JSON en una lista de Product
               
                ProductWP[] products = gson.fromJson(response.body(), ProductWP[].class);

                // Agregar los productos a la lista principal
                productList.addAll(Arrays.asList(products));

                // Si la cantidad de productos devueltos es menor que 100, no hay más páginas
                if (products.length < 100) {
                    hasMore = false;
                } else {
                    page++; // Ir a la siguiente página
                }
            } else if (code == 404) {
                System.out.println("No products found.");
                hasMore = false;
            } else if (code == 401) {
                System.out.println("Unauthorized access.");
                hasMore = false;
            } else {
                System.out.println("Error: Received status code " + code);
                hasMore = false;
            }
        }

    } catch (Exception e) {
        System.out.println("Error in request execution: " + e.getMessage());
    }

    return productList;
}

@Override
public List<OrderWP> ProductGetAllOrders() {
    Gson gson = new Gson();
    List<OrderWP> orderList = new ArrayList<>();

    // Obtener configuración
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getUrlBase() + "orders?per_page=100";
    String username = cwp.getUsernameToken();
    String password = cwp.getPasswordToken();

    try {
        // Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // Codificar credenciales en Base64
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // Crear la solicitud HTTP GET con autenticación
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + encodedAuth)
                .GET()
                .build();

        // Enviar la solicitud y recibir la respuesta
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();

        if (code == 200) {
            // Convertir la respuesta en una lista de pedidos
            OrderWP[] orders = gson.fromJson(response.body(), OrderWP[].class);
            orderList.addAll(Arrays.asList(orders));
        } else {
            System.out.println("❌ Error: Código HTTP " + code);
            System.out.println("📝 Respuesta del servidor: " + response.body());
        }

    } catch (Exception e) {
        System.out.println("⚠️ Error en la solicitud: " + e.getMessage());
    }

    return orderList;
}

@Override
public String CategoriePost(String json) {
    String categoryId = null;

    // 🔥 Credenciales y URL de la API
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getUrlBase();
    String username = cwp.getUsernameToken();
    String password = cwp.getPasswordToken();

    try {
        // 🔥 Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // 🔥 Codificar las credenciales en Base64
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // 🔥 Endpoint para crear categorías en WordPress
        String createUrl = baseUrl + "products/categories";
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(createUrl))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)) // JSON recibido para crear
                .build();

        // 🔥 Enviar la solicitud
        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        int code = postResponse.statusCode();

        // 🔥 Si la categoría se creó con éxito
        if (code == 201) {
            String responseBody = postResponse.body();

            // 🔥 Usamos Gson para convertir la respuesta en JSON
            Gson gson = new Gson();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            // 🔥 Verificamos si el campo "id" está presente en la respuesta JSON
            if (jsonResponse.has("id")) {
                categoryId = jsonResponse.get("id").getAsString();
                String logMessage = "✅ Categoría creada con ID: " + categoryId;

                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            } else {
                String logMessage = "❌ No se encontró el ID en la respuesta de la API.";

                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            }
        } else {
            String logMessage = "🚨 Error al crear la categoría. Código de respuesta: " + code;

            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            WebSocket.sendMessageToAll("🔴 Respuesta API: " + postResponse.body()); // 🔥 Mostrar detalles de error
        }

    } catch (Exception e) {
        String logMessage = "🔥 Error durante la ejecución de la solicitud: " + e.getMessage();

        System.out.println(logMessage);
        WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
        e.printStackTrace(); // Mostrar detalles del error
    }

    return categoryId != null ? categoryId : "Error: No se pudo obtener el ID";
}


@Override
public String ProductPost(String json) {
    String productId = null;

    // 🔥 Credenciales y URL de la API
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getUrlBase();
    String username = cwp.getUsernameToken();
    String password = cwp.getPasswordToken();

    try {
        // 🔥 Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // 🔥 Codificar las credenciales en Base64
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // 🔥 Endpoint para crear productos en WordPress
        String createUrl = baseUrl + "products";
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(URI.create(createUrl))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))  // JSON recibido para crear
                .build();

        // 🔥 Enviar la solicitud
        HttpResponse<String> postResponse = client.send(postRequest, HttpResponse.BodyHandlers.ofString());
        int code = postResponse.statusCode();

        // 🔥 Si el producto se creó con éxito
        if (code == 201) {
            String responseBody = postResponse.body();

            // 🔥 Usamos Gson para convertir la respuesta en JSON
            Gson gson = new Gson();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            // 🔥 Verificamos si el campo "id" está presente en la respuesta JSON
            if (jsonResponse.has("id")) {
                productId = jsonResponse.get("id").getAsString();
                
                
            } else {
                String logMessage = "❌ No se encontró el ID en la respuesta de la API.";
                
                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            }
        } else {
            String logMessage = "🚨 Error al crear el producto. Código de respuesta: " + code;
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            WebSocket.sendMessageToAll("🔴 Respuesta API: " + postResponse.body()); // 🔥 Mostrar detalles de error
        }

    } catch (Exception e) {
        String logMessage = "🔥 Error durante la ejecución de la solicitud: " + e.getMessage();
        
        System.out.println(logMessage);
        WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
        e.printStackTrace(); // Mostrar detalles del error
    }

    return productId != null ? productId : "Error: No se pudo obtener el ID"; 
}





@Override
public String productMediaPost(ImageDTO imageDto, String sku, String position) {
    String imageId = null;

    // 🔥 Obtener configuración de WordPress desde el bean
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getWpApiUrl();                     // URL base de la API de WP
    String authWp = cwp.getWpApplicationPassword();         // Clave de autenticación

    try {
        // 🔥 Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();
        String encodedAuth = Base64.getEncoder().encodeToString(authWp.getBytes(StandardCharsets.UTF_8));

        // 🔥 Construir el nombre del archivo usando SKU y posición
        String imageName = sku + "_" + position + ".jpg";

        // 🔥 Convertir el contenido del DTO de base64 a bytes
        byte[] imageBytes = Base64.getDecoder().decode(imageDto.getBase64Content());

        // 🔥 Endpoint de WordPress para subir imágenes
        String uploadUrl = baseUrl + "media";

        // 🔥 Crear la solicitud HTTP
        HttpRequest imageRequest = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Disposition", "attachment; filename=" + imageName)
                .header("Content-Type", "image/jpeg")
                .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
                .build();

        // 🔥 Enviar la solicitud
        HttpResponse<String> imageResponse = client.send(imageRequest, HttpResponse.BodyHandlers.ofString());

        if (imageResponse.statusCode() == 201) {
            // 🔥 Extraer el ID de la imagen desde la respuesta JSON
            String responseBody = imageResponse.body();
            Gson gson = new Gson();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            imageId = jsonResponse.get("id").getAsString();

            String logMessage = "✅ Imagen subida con éxito - SKU: " + sku + " | Posición: " + position + " | ID: " + imageId;
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            
        } else {
            String logMessage = "❌ Error al subir imagen - SKU: " + sku + " | Posición: " + position + " | Respuesta: " + imageResponse.body();
            
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
            
            imageId = "NO_IMAGE";
        }
    } catch (Exception e) {
        String logMessage = "🚨 Error en la subida de la imagen - SKU: " + sku + " | Posición: " + position + " | Error: " + e.getMessage();
        
        System.out.println(logMessage);
        WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
        
        return "Error: " + e.getMessage();
    }

    return imageId;
}


@Override
public String productMediaDelete(String imageId) {
    String responseMessage = "NO_IMAGE_DELETED";

    // Obtener configuración de WordPress desde el bean
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getWpApiUrl();                     // URL base de la API de WP
    String authWp = cwp.getWpApplicationPassword();         // Clave de autenticación

    try {
        // Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();
        String encodedAuth = Base64.getEncoder().encodeToString(authWp.getBytes(StandardCharsets.UTF_8));

        // Endpoint de WordPress para eliminar la imagen
        String deleteUrl = baseUrl + "media/" + imageId + "?force=true";

        // Crear la solicitud HTTP DELETE
        HttpRequest deleteRequest = HttpRequest.newBuilder()
                .uri(URI.create(deleteUrl))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .DELETE()
                .build();

        // Enviar la solicitud
        HttpResponse<String> deleteResponse = client.send(deleteRequest, HttpResponse.BodyHandlers.ofString());

        if (deleteResponse.statusCode() == 200) {
            // Confirmación de eliminación
            responseMessage = "IMAGE_DELETED";
            System.out.println("Imagen eliminada con ID: " + imageId);
        } else {
            System.out.println("Error al eliminar la imagen. Respuesta: " + deleteResponse.body());
            responseMessage = "DELETE_FAILED";
        }
    } catch (Exception e) {
        System.out.println("Error en la eliminación de la imagen: " + e.getMessage());
        return "Error: " + e.getMessage();
    }

    return responseMessage;
}



@Override
public List<LaudusProduct> ProductGetAllLaudus() {
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String apiUrl = cwp.getApiLaudusUrl();
    String authToken = cwp.getTokenLaudusApi();

    try {
        HttpClient client = HttpClient.newHttpClient();
        String productsUrl = apiUrl + "list";

        String requestBody = """
        {
          "fields": [
            "productId",
            "sku",
            "description",
            "unitPriceWithTaxes",
            "modifiedAt",
            "productCategory.productCategoryId",
            "productCategory.name",
            "productCategory.fullPath",
            "customFields.web_status_",
            "customFields.web_description_"
          ],
          "options": {
            "offset": 0,
            "limit": 2500
          },
          "filterBy": [
            {
              "field": "customFields.web_status_",
              "operator": "=",
              "value": true
            }
          ],
          "orderBy": [
            {
              "field": "sku",
              "direction": "ASC"
            }
          ]
        }
        """;

        HttpRequest productsRequest = HttpRequest.newBuilder()
                .uri(URI.create(productsUrl))
                .header("Authorization", "Bearer " + authToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> productsResponse = client.send(productsRequest, HttpResponse.BodyHandlers.ofString());

        if (productsResponse.statusCode() == 200) {
            String responseBody = productsResponse.body();
            Gson gson = new Gson();
            JsonArray productsArray = gson.fromJson(responseBody, JsonArray.class);
            List<LaudusProduct> productList = new ArrayList<>();

            for (JsonElement productElement : productsArray) {
                JsonObject product = productElement.getAsJsonObject();
                LaudusProduct productObj = new LaudusProduct();

                productObj.setId(product.has("productId") && !product.get("productId").isJsonNull() ? 
                    product.get("productId").getAsInt() : 0L);

                String rawSku = product.has("sku") && !product.get("sku").isJsonNull() ?
                    product.get("sku").getAsString() : "";
                String cleanSku = rawSku.replace(" ", "-");
                productObj.setSku(cleanSku);

                productObj.setDescription(product.has("description") && !product.get("description").isJsonNull() ? 
                    product.get("description").getAsString() : "");

                productObj.setUnitPriceWithTaxes(product.has("unitPriceWithTaxes") && !product.get("unitPriceWithTaxes").isJsonNull() ? 
                    product.get("unitPriceWithTaxes").getAsInt() : null); 

                productObj.setModifiedAt(product.has("modifiedAt") && !product.get("modifiedAt").isJsonNull() ? 
                    product.get("modifiedAt").getAsString() : "");

                productObj.setProductCategoryId(product.has("productCategory_productCategoryId") && !product.get("productCategory_productCategoryId").isJsonNull() ? 
                    product.get("productCategory_productCategoryId").getAsString() : "");

                productObj.setWebStatus(product.has("customFields_web_status_") && !product.get("customFields_web_status_").isJsonNull() ? 
                    product.get("customFields_web_status_").getAsBoolean() : false);

                productObj.setWebDescription(product.has("customFields_web_description_") && !product.get("customFields_web_description_").isJsonNull() ? 
                    product.get("customFields_web_description_").getAsString() : "");

                productList.add(productObj);
            }

            List<LaudusProduct> productosCambiados = bd.addProductAll(productList);
            return productosCambiados;

        } else {
            String errorMessage = "❌ Error al obtener productos. Código HTTP: " + productsResponse.statusCode();
            System.out.println(errorMessage);
            System.out.println("📝 Respuesta del servidor: " + productsResponse.body());
            return Collections.emptyList();
        }

    } catch (Exception e) {
        String errorMessage = "⚠️ Error en la sincronización de productos: " + e.getMessage();
        System.out.println(errorMessage);
        return Collections.emptyList();
    }
}

@Override
public List<com.jop.sincronizador.models.entity.dto.CatalogProductExtended> ProductGetAllLaudusForCatalog() {
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String apiUrl = cwp.getApiLaudusUrl();
    String authToken = cwp.getTokenLaudusApi();

    try {
        HttpClient client = HttpClient.newHttpClient();
        String productsUrl = apiUrl + "list";

        String requestBody = """
        {
          "fields": [
            "productId",
            "sku",
            "description",
            "unitPriceWithTaxes",
            "modifiedAt",
            "productCategory.productCategoryId",
            "productCategory.name",
            "productCategory.fullPath",
            "customFields.web_status_",
            "customFields.web_description_",
            "customFields.update_image_",
            "customFields.LARGO_",
            "customFields.MATERIAL_",
            "customFields.DIAMETRO_",
            "customFields.DIAMETRO_ROSCA_",
            "customFields.DIAMETRO_EXTERNO_",
            "customFields.Medidas_",
            "customFields.Medidas_base_",
            "customFields.MEDIDA_EJE_",
            "customFields.Medida_vias_",
            "customFields.DIAMETRO_CENTRO_",
            "customFields.Medida_accesorios_",
            "customFields.N_vias_",
            "customFields.N_pistones_",
            "customFields.TIPO_",
            "customFields.Corriente_",
            "customFields.HP_",
            "customFields.Medida_condensadores_",
            "customFields.Cabezal_",
            "customFields.Motor_",
            "customFields.Etapas_",
            "customFields.Presion_",
            "customFields.Caudal_",
            "customFields.Aspiracion_",
            "customFields.Elemento_filtrante_",
            "customFields.Disipiador_calor_",
            "customFields.Amperes_",
            "customFields.Voltaje_",
            "customFields.Ubicacion_rosca_",
            "customFields.GROSOR_",
            "customFields.DIAMETRO_PISTON_",
            "customFields.CANTIDAD_TUERCAS_",
            "customFields.DIAMETRO_EJE_",
            "customFields.tipo_rosca_",
            "customFields.ESTANQUE_",
            "customFields.RPM_",
            "customFields.Accesorios_",
            "customFields.ROSCA_ENTRADA_",
            "customFields.PROFUNDIDAD_"
          ],
          "options": {
            "offset": 0,
            "limit": 2500
          },
          "filterBy": [
            {
              "field": "customFields.web_status_",
              "operator": "=",
              "value": true
            }
          ],
          "orderBy": [
            {
              "field": "sku",
              "direction": "ASC"
            }
          ]
        }
        """;

        HttpRequest productsRequest = HttpRequest.newBuilder()
                .uri(URI.create(productsUrl))
                .header("Authorization", "Bearer " + authToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> productsResponse = client.send(productsRequest, HttpResponse.BodyHandlers.ofString());

        if (productsResponse.statusCode() == 200) {
            String responseBody = productsResponse.body();
            Gson gson = new Gson();
            JsonArray productsArray = gson.fromJson(responseBody, JsonArray.class);
            List<com.jop.sincronizador.models.entity.dto.CatalogProductExtended> productList = new ArrayList<>();

            for (JsonElement productElement : productsArray) {
                JsonObject product = productElement.getAsJsonObject();
                com.jop.sincronizador.models.entity.dto.CatalogProductExtended productObj = new com.jop.sincronizador.models.entity.dto.CatalogProductExtended();

                // Campos básicos
                productObj.setId(product.has("productId") && !product.get("productId").isJsonNull() ? 
                    product.get("productId").getAsLong() : 0L);

                String rawSku = product.has("sku") && !product.get("sku").isJsonNull() ?
                    product.get("sku").getAsString() : "";
                String cleanSku = rawSku.replace(" ", "-");
                productObj.setSku(cleanSku);

                productObj.setDescription(product.has("description") && !product.get("description").isJsonNull() ? 
                    product.get("description").getAsString() : "");

                productObj.setUnitPriceWithTaxes(product.has("unitPriceWithTaxes") && !product.get("unitPriceWithTaxes").isJsonNull() ? 
                    product.get("unitPriceWithTaxes").getAsInt() : null); 

                productObj.setModifiedAt(product.has("modifiedAt") && !product.get("modifiedAt").isJsonNull() ? 
                    product.get("modifiedAt").getAsString() : "");

                productObj.setProductCategoryId(product.has("productCategory_productCategoryId") && !product.get("productCategory_productCategoryId").isJsonNull() ? 
                    product.get("productCategory_productCategoryId").getAsString() : "");

                productObj.setWebStatus(product.has("customFields_web_status_") && !product.get("customFields_web_status_").isJsonNull() ? 
                    product.get("customFields_web_status_").getAsBoolean() : false);

                productObj.setWebDescription(product.has("customFields_web_description_") && !product.get("customFields_web_description_").isJsonNull() ? 
                    product.get("customFields_web_description_").getAsString() : "");

                // CustomFields extendidos
                productObj.setUpdateImage(getCustomFieldValue(product, "customFields_update_image_"));
                productObj.setLargo(getCustomFieldValue(product, "customFields_LARGO_"));
                productObj.setMaterial(getCustomFieldValue(product, "customFields_MATERIAL_"));
                productObj.setDiametro(getCustomFieldValue(product, "customFields_DIAMETRO_"));
                productObj.setDiametroRosc(getCustomFieldValue(product, "customFields_DIAMETRO_ROSCA_"));
                productObj.setDiametroExterno(getCustomFieldValue(product, "customFields_DIAMETRO_EXTERNO_"));
                productObj.setMedidas(getCustomFieldValue(product, "customFields_Medidas_"));
                productObj.setMedidasBase(getCustomFieldValue(product, "customFields_Medidas_base_"));
                productObj.setMedidaEje(getCustomFieldValue(product, "customFields_MEDIDA_EJE_"));
                productObj.setMedidaVias(getCustomFieldValue(product, "customFields_Medida_vias_"));
                productObj.setDiametroCentro(getCustomFieldValue(product, "customFields_DIAMETRO_CENTRO_"));
                productObj.setMedidaAccesorios(getCustomFieldValue(product, "customFields_Medida_accesorios_"));
                productObj.setNVias(getCustomFieldValue(product, "customFields_N_vias_"));
                productObj.setNPistones(getCustomFieldValue(product, "customFields_N_pistones_"));
                productObj.setTipo(getCustomFieldValue(product, "customFields_TIPO_"));
                productObj.setCorriente(getCustomFieldValue(product, "customFields_Corriente_"));
                productObj.setHp(getCustomFieldValue(product, "customFields_HP_"));
                productObj.setMedidaCondensadores(getCustomFieldValue(product, "customFields_Medida_condensadores_"));
                productObj.setCabezal(getCustomFieldValue(product, "customFields_Cabezal_"));
                productObj.setMotor(getCustomFieldValue(product, "customFields_Motor_"));
                productObj.setEtapas(getCustomFieldValue(product, "customFields_Etapas_"));
                productObj.setPresion(getCustomFieldValue(product, "customFields_Presion_"));
                productObj.setCaudal(getCustomFieldValue(product, "customFields_Caudal_"));
                productObj.setAspiracion(getCustomFieldValue(product, "customFields_Aspiracion_"));
                productObj.setElementoFiltrante(getCustomFieldValue(product, "customFields_Elemento_filtrante_"));
                productObj.setDisipiadorCalor(getCustomFieldValue(product, "customFields_Disipiador_calor_"));
                productObj.setAmperes(getCustomFieldValue(product, "customFields_Amperes_"));
                productObj.setVoltaje(getCustomFieldValue(product, "customFields_Voltaje_"));
                productObj.setUbicacionRosc(getCustomFieldValue(product, "customFields_Ubicacion_rosca_"));
                productObj.setGrosor(getCustomFieldValue(product, "customFields_GROSOR_"));
                productObj.setDiametroPiston(getCustomFieldValue(product, "customFields_DIAMETRO_PISTON_"));
                productObj.setCantidadTuercas(getCustomFieldValue(product, "customFields_CANTIDAD_TUERCAS_"));
                productObj.setDiametroEje(getCustomFieldValue(product, "customFields_DIAMETRO_EJE_"));
                productObj.setTipoRosca(getCustomFieldValue(product, "customFields_tipo_rosca_"));
                productObj.setEstanque(getCustomFieldValue(product, "customFields_ESTANQUE_"));
                productObj.setRpm(getCustomFieldValue(product, "customFields_RPM_"));
                productObj.setAccesorios(getCustomFieldValue(product, "customFields_Accesorios_"));
                productObj.setRoscaEntrada(getCustomFieldValue(product, "customFields_ROSCA_ENTRADA_"));
                productObj.setProfundidad(getCustomFieldValue(product, "customFields_PROFUNDIDAD_"));

                productList.add(productObj);
            }

            return productList;

        } else {
            String errorMessage = "❌ Error al obtener productos para catálogo. Código HTTP: " + productsResponse.statusCode();
            System.out.println(errorMessage);
            System.out.println("📝 Respuesta del servidor: " + productsResponse.body());
            return Collections.emptyList();
        }

    } catch (Exception e) {
        String errorMessage = "⚠️ Error en la obtención de productos para catálogo: " + e.getMessage();
        System.out.println(errorMessage);
        e.printStackTrace();
        return Collections.emptyList();
    }
}

private String getCustomFieldValue(JsonObject product, String fieldName) {
    if (product.has(fieldName) && !product.get(fieldName).isJsonNull()) {
        JsonElement element = product.get(fieldName);
        if (element.isJsonPrimitive()) {
            // Manejar diferentes tipos: números, booleanos, strings
            if (element.getAsJsonPrimitive().isNumber()) {
                return String.valueOf(element.getAsNumber());
            } else if (element.getAsJsonPrimitive().isBoolean()) {
                return String.valueOf(element.getAsBoolean());
            } else {
                String value = element.getAsString();
                // Retornar el valor incluso si es "0" o vacío, para que el frontend decida
                return value != null ? value : null;
            }
        }
    }
    return null;
}
      

@Override
public List<Categorie> CategoriesGetAllLaudus() {
    // Obtener configuración desde el bean
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String apiUrl = cwp.getApiLaudusUrl();  // URL base de la API de Laudus
    String authToken = cwp.getTokenLaudusApi(); // Token de autenticación

    try {
        // Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // URL del endpoint donde se obtienen los productos
        String categoriesUrls = apiUrl + "categories/list";

        // Crear el JSON del body
        String requestBody = """
       {
          "fields": [
            "productCategory.productCategoryId",
            "productCategory.name",
            "productCategory.fullPath"
          ]
        }
        """;

        // Crear la solicitud HTTP POST con headers y body
        HttpRequest categoriesRequest = HttpRequest.newBuilder()
                .uri(URI.create(categoriesUrls))
                .header("Authorization", "Bearer " + authToken)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // Enviar la solicitud y recibir la respuesta
        HttpResponse<String> categoriesResponse = client.send(categoriesRequest, HttpResponse.BodyHandlers.ofString());

        if (categoriesResponse.statusCode() == 200) {
            // Extraer la respuesta en JSON
            String responseBody = categoriesResponse.body();
            Gson gson = new Gson();

            // Convertir respuesta en un JsonArray directamente
            JsonArray categoriesArray = gson.fromJson(responseBody, JsonArray.class);
            List<Categorie> categoriesList = new ArrayList<>();

            for (JsonElement categorieElement : categoriesArray) {
                JsonObject categorie = categorieElement.getAsJsonObject();
                Categorie categorieObj = new Categorie();

                // Asignar valores al objeto `Categorie`
                categorieObj.setLaudusId(categorie.has("productCategory_productCategoryId") && !categorie.get("productCategory_productCategoryId").isJsonNull() ? 
                    categorie.get("productCategory_productCategoryId").getAsString() : "");
                
                categorieObj.setLaudusName(categorie.has("productCategory_name") && !categorie.get("productCategory_name").isJsonNull() ? 
                    categorie.get("productCategory_name").getAsString() : "");

                categorieObj.setLaudusPath(categorie.has("productCategory_fullPath") && !categorie.get("productCategory_fullPath").isJsonNull() ? 
                    categorie.get("productCategory_fullPath").getAsString() : "");

                // 🔹 FILTRAR SOLO CATEGORÍAS QUE CONTENGAN "MILWAUKEE" O "COMPRESOR" EXCLUYENDO OTRAS
                String pathUpper = categorieObj.getLaudusPath().toUpperCase(); // Convertir a mayúsculas para evitar problemas de case-sensitive
                if ((pathUpper.contains("MILWAUKEE") || pathUpper.contains("COMPRESOR")) 
                    && !pathUpper.equals("\\TOTAL")  // Excluir TOTAL
                    && !pathUpper.equals("\\TOYAKI") // Excluir TOYAKI
                    && !pathUpper.equals("\\OTRAS MARCAS") // Excluir OTRAS MARCAS
                    ) {
                    
                    categoriesList.add(categorieObj);

                } else {
                    // 🔹 LOG PARA VER LAS CATEGORÍAS QUE SE ESTÁN FILTRANDO
                }
            }

            // Guardar solo las categorías filtradas en la base de datos
            bd.addCategoriesAll(categoriesList);

            // Retornar la lista de categorías filtradas
            return categoriesList;

        } else {
            String errorMessage = "❌ Error al obtener Categorías. Código HTTP: " + categoriesResponse.statusCode();
            System.out.println(errorMessage);
            System.out.println("📝 Respuesta del servidor: " + categoriesResponse.body());
            WebSocket.sendMessageToAll(errorMessage);
            WebSocket.sendMessageToAll("📝 Respuesta del servidor: " + categoriesResponse.body());
            return Collections.emptyList();
        }

    } catch (Exception e) {
        String errorMessage = "⚠️ Error en la sincronización de categorías: " + e.getMessage();
        System.out.println(errorMessage);
        WebSocket.sendMessageToAll(errorMessage);
        return Collections.emptyList();
    }
}




@Override
public List<String> ProductGetAllLaudusStock(String warehouseId) {
    // Obtener configuración desde el bean
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String apiUrl = cwp.getApiLaudusUrl();  // URL de la API de Laudus
    String authToken = cwp.getTokenLaudusApi(); // Token de autenticación

    // Consulta exactamente la bodega pedida — antes sumaba "001" siempre por
    // detrás sin que el llamador lo supiera. La bodega real con stock es "008"
    // (verificado contra la API: "001" y "005" están casi vacías).
    List<String> warehouseIds = List.of(warehouseId);

    try {
        // Crear HttpClient
        HttpClient client = HttpClient.newHttpClient();

        // Mapa para almacenar el stock acumulado por SKU
        Map<String, Integer> stockMap = new HashMap<>();

        for (String wh : warehouseIds) {
            // URL del endpoint para la bodega actual
            String stockUrl = apiUrl + "stock?warehouseId=" + wh;

            // Crear la solicitud HTTP
            HttpRequest stockRequest = HttpRequest.newBuilder()
                    .uri(URI.create(stockUrl))
                    .header("Authorization", "Bearer " + authToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            // Enviar la solicitud y recibir la respuesta
            HttpResponse<String> stockResponse = client.send(stockRequest, HttpResponse.BodyHandlers.ofString());

            if (stockResponse.statusCode() == 200) {
                // Procesar la respuesta JSON
                String responseBody = stockResponse.body();
                Gson gson = new Gson();
                JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

                if (!jsonResponse.has("products") || jsonResponse.get("products").isJsonNull()) {
                    System.out.println("Respuesta no contiene 'products' para la bodega " + wh);
                    continue;
                }

                JsonArray productsArray = jsonResponse.getAsJsonArray("products");

                // Acumular stock por SKU
                for (JsonElement productElement : productsArray) {
                    JsonObject product = productElement.getAsJsonObject();
                    String sku = product.get("sku").getAsString();
                    int stockAmount = product.get("stock").getAsInt();

                    stockMap.put(sku, stockMap.getOrDefault(sku, 0) + stockAmount);
                }
            } else {
                System.out.println("❌ Error al obtener stock de bodega " + wh + ". Código HTTP: " + stockResponse.statusCode());
                System.out.println("📝 Respuesta del servidor: " + stockResponse.body());
            }
        }

        // Convertir el mapa de stock a una lista de objetos LaudusStock
        List<LaudusStock> stockList = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : stockMap.entrySet()) {
            LaudusStock stock = new LaudusStock();
            stock.setSku(entry.getKey());
            stock.setStock(entry.getValue());
            stockList.add(stock);
        }

        // GUARD: si Laudus no devolvió ningún producto, es señal de fallo en la API.
        if (stockList.isEmpty()) {
            String msg = "⚠️ CANCELADO: Laudus no devolvió ningún producto de stock. No se actualizará la base de datos ni WordPress.";
            System.out.println(msg);
            WebSocket.sendMessageToAll(msg);
            return Collections.emptyList();
        }

        // Filtrar: solo conservar SKUs que existen en laudus_product (productos de la web)
        Set<String> skusEnBD = bd.findAll().stream()
            .map(p -> p.getSku())
            .filter(s -> s != null && !s.isEmpty())
            .collect(java.util.stream.Collectors.toSet());

        List<LaudusStock> stockFiltrado = stockList.stream()
            .filter(s -> skusEnBD.contains(s.getSku()))
            .collect(java.util.stream.Collectors.toList());

        WebSocket.sendMessageToAll("🔍 Laudus: " + stockList.size() + " productos totales → " + stockFiltrado.size() + " en la web.");

        if (stockFiltrado.isEmpty()) {
            WebSocket.sendMessageToAll("📢 Sin productos de la web en el stock recibido.");
            return Collections.emptyList();
        }

        // Enviar solo los productos de la web para comparar y actualizar
        return bd.updateStockLaudus(stockFiltrado);

    } catch (Exception e) {
        System.out.println("⚠️ Error en la sincronización del stock: " + e.getMessage());
        return Collections.emptyList();
    }
}


@Override
public String updateToken() {
    // 🔥 Credenciales y URL de la API
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    String baseUrl = cwp.getUrlTokenLaudus();
    String companyID = cwp.getCompanyVATIdAuth();
    String userNameAuth = cwp.getUserNameAuth();
    String passwordToken = cwp.getPasswordAuth();
    String dateExpirateToken = cwp.getDateExpirateToken();

    try {
        // 🔥 Formato de la fecha en el String
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        LocalDateTime fechaExpiracion = LocalDateTime.parse(dateExpirateToken, formatter);
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.systemDefault());

        // 🔥 Calcular la diferencia en horas
        long horasRestantes = Duration.between(ahora, fechaExpiracion).toHours();

        // 🔥 Si el token aún es válido, retornar mensaje de éxito
        if (!fechaExpiracion.isBefore(ahora) && horasRestantes >= 1) {
            String mensaje = "✅ Token válido. No es necesario renovarlo.";
            WebSocket.sendMessageToAll(mensaje);
            return mensaje;
        }
        // 🔥 Si está expirado o falta menos de 1 hora, proceder con la renovación
        HttpClient client = HttpClient.newHttpClient();
        JsonObject jsonRequest = new JsonObject();
        jsonRequest.addProperty("userName", userNameAuth);
        jsonRequest.addProperty("password", passwordToken);
        jsonRequest.addProperty("companyVATId", companyID);

        String json = new Gson().toJson(jsonRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 🔥 Procesar respuesta de la API
        if (response.statusCode() == 200) {
            JsonObject jsonResponse = new Gson().fromJson(response.body(), JsonObject.class);
            String newToken = jsonResponse.get("token").getAsString();
            String newDateExpiration = jsonResponse.get("expiration").getAsString();

            // 🔥 Actualizar configuración con el nuevo token y fecha de expiración
            cwp.setDateExpirateToken(newDateExpiration);
            cwp.setTokenLaudusApi(newToken);
            bdConfig.save(cwp);
            String mensaje = "✅ Token renovado correctamente.";
            WebSocket.sendMessageToAll(mensaje);
            return mensaje;
        } else {
            String mensaje = "🚨 Error al actualizar el token. Código: " + response.statusCode();
            WebSocket.sendMessageToAll(mensaje);
            return mensaje;
        }
    } catch (Exception e) {
        String mensaje = "🔥 Error al renovar el token: " + e.getMessage();
        WebSocket.sendMessageToAll(mensaje);
        return mensaje;
    }
}

@Override
public ConfigWordPress getConfig() {
    return context.getBean("getConfigWordPress", ConfigWordPress.class);
}
}



