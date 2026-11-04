package com.jop.sincronizador.falabella;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.jop.sincronizador.util.FalabellaSigner;

// Cliente HTTP compartido para hablar con Falabella Seller Center.
// GET para acciones de solo lectura (Action + params en query, firmados).
// POST-con-XML para acciones de escritura (ProductCreate, Image, etc.) — los
// parámetros firmados van en la query igual que en el GET, el XML del feed
// va aparte como cuerpo crudo del POST (no se firma).
@Service
public class FalabellaClient {

    @Value("${falabella.base-url}")
    private String baseUrl;

    @Value("${falabella.user-id}")
    private String userId;

    @Value("${falabella.api-key}")
    private String apiKey;

    @Value("${falabella.version}")
    private String version;

    public boolean credentialsConfigured() {
        return userId != null && !userId.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    public FalabellaResponse get(String action, Map<String, String> extraParams) {
        try {
            String query = FalabellaSigner.buildSignedQuery(action, userId, apiKey, version, extraParams);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "?" + query))
                .header("Accept", "application/json")
                .GET()
                .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new FalabellaResponse(res.statusCode(), res.body());
        } catch (Exception e) {
            return new FalabellaResponse(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public FalabellaResponse postXml(String action, Map<String, String> extraParams, String xmlBody) {
        try {
            String query = FalabellaSigner.buildSignedQuery(action, userId, apiKey, version, extraParams);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "?" + query))
                .header("Accept", "application/json")
                .header("Content-Type", "text/xml; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(xmlBody, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new FalabellaResponse(res.statusCode(), res.body());
        } catch (Exception e) {
            return new FalabellaResponse(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public record FalabellaResponse(int statusCode, String body) {}
}
