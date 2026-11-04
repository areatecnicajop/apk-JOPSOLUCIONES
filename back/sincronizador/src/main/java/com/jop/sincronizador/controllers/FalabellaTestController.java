package com.jop.sincronizador.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jop.sincronizador.falabella.FalabellaClient;

// Endpoints de solo-lectura para ir probando la API de Falabella Seller Center.
// No modifican nada en Falabella ni en la BD local — solo firman la request y devuelven el JSON crudo.
@RestController
public class FalabellaTestController {

    @Autowired
    private FalabellaClient falabella;

    private ResponseEntity<String> call(String action, Map<String, String> extraParams) {
        if (!falabella.credentialsConfigured()) {
            return ResponseEntity.status(400).body(
                "{\"error\":\"Falta configurar falabella.user-id / falabella.api-key en application.yaml\"}");
        }
        FalabellaClient.FalabellaResponse res = falabella.get(action, extraParams);
        return ResponseEntity.status(res.statusCode())
            .header("Content-Type", "application/json")
            .body(res.body());
    }

    @GetMapping("/test/falabella/categorias")
    public ResponseEntity<String> testCategoryTree() {
        return call("GetCategoryTree", null);
    }

    @GetMapping("/test/falabella/marcas")
    public ResponseEntity<String> testBrands() {
        return call("GetBrands", null);
    }

    @GetMapping("/test/falabella/productos")
    public ResponseEntity<String> testProducts(@RequestParam(defaultValue = "10") String limit) {
        return call("GetProducts", Map.of("Limit", limit));
    }

    @GetMapping("/test/falabella/ordenes")
    public ResponseEntity<String> testOrders(@RequestParam(defaultValue = "10") String limit) {
        return call("GetOrders", Map.of("Limit", limit, "SortBy", "created_at", "SortDirection", "DESC"));
    }
}
