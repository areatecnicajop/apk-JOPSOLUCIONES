package com.jop.sincronizador.laudus;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;

// Cliente para el recurso System/CustomFields de la API de Laudus.
// A diferencia de /production/products/... (usado por el sync a WooCommerce),
// este recurso vive en la raíz: https://api.laudus.cl/system/customFields —
// confirmado a mano, con el prefijo "production/" tira 404.
@Service
public class LaudusCustomFieldsClient {

    private static final String BASE_URL = "https://api.laudus.cl/system/customFields";

    @Autowired
    private ApplicationContext context;

    private String token() {
        ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
        return cwp.getTokenLaudusApi();
    }

    public LaudusResponse list() {
        return send("POST", BASE_URL + "/list", "{}");
    }

    public LaudusResponse get(int customFieldId) {
        return send("GET", BASE_URL + "/" + customFieldId, null);
    }

    public LaudusResponse create(String jsonBody) {
        return send("POST", BASE_URL, jsonBody);
    }

    public LaudusResponse update(int customFieldId, String jsonBody) {
        return send("PUT", BASE_URL + "/" + customFieldId, jsonBody);
    }

    public LaudusResponse delete(int customFieldId) {
        return send("DELETE", BASE_URL + "/" + customFieldId, null);
    }

    private LaudusResponse send(String method, String url, String jsonBody) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");

            HttpRequest request = switch (method) {
                case "GET" -> builder.GET().build();
                case "DELETE" -> builder.DELETE().build();
                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build();
                default -> builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody != null ? jsonBody : "{}", StandardCharsets.UTF_8)).build();
            };

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new LaudusResponse(response.statusCode(), response.body());
        } catch (Exception e) {
            return new LaudusResponse(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public record LaudusResponse(int statusCode, String body) {}
}
