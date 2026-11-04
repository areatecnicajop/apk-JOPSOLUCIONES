package com.jop.sincronizador.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jop.sincronizador.data.local.model.entity.AppCatalogProduct;
import com.jop.sincronizador.repository.AppCatalogProductRepository;
import com.jop.sincronizador.services.AppCatalogService;

// Endpoint dedicado y aislado para la APK: botón "Actualizar catálogo" y
// lectura del catálogo local. No reutiliza /sync/products, /sync/stock ni
// /api/products (esos los dispara otro proceso y leen/escriben en
// laudus_products / laudus_stock). Todo acá usa su propia tabla
// (app_catalog_products) de punta a punta.
@RestController
@RequestMapping("/api/app-catalog")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201", "http://localhost:8081", "https://jopsoluciones.com", "https://jopsoluciones.cl"})
public class AppCatalogController {

    @Autowired
    private AppCatalogService appCatalogService;

    @Autowired
    private AppCatalogProductRepository repository;

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        try {
            Map<String, Object> result = appCatalogService.refreshCatalog();
            if ("error".equals(result.get("status"))) {
                return ResponseEntity.status(502).body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/products")
    public ResponseEntity<Map<String, Object>> getProducts() {
        List<AppCatalogProduct> productos = repository.findAll();
        List<Map<String, Object>> result = productos.stream().map(p -> {
            Map<String, Object> item = new HashMap<>();
            item.put("sku", p.getSku());
            item.put("name", p.getName());
            item.put("unitPriceWithTaxes", p.getUnitPriceWithTaxes() != null ? p.getUnitPriceWithTaxes() : 0);
            item.put("stock", p.getStock() != null ? p.getStock() : 0);
            item.put("categoryName", p.getCategoryName());
            item.put("productCategoryId", p.getProductCategoryId());
            item.put("webDescription", p.getWebDescription());
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("products", result, "total", result.size()));
    }
}
