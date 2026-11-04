package com.jop.sincronizador.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jop.sincronizador.data.local.model.entity.AppCatalogProduct;
import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;
import com.jop.sincronizador.repository.AppCatalogProductRepository;

import jakarta.transaction.Transactional;

// Servicio exclusivo del catálogo local de la APK. No comparte código ni
// tablas con RestLaudusWordP / TransactionBDtoWPService — esos alimentan el
// pipeline ERP -> WooCommerce vía laudus_products / laudus_stock, que lo
// dispara otro proceso aparte. Este servicio consulta Laudus directo y
// escribe únicamente en app_catalog_products.
@Service
public class AppCatalogService {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private AppCatalogProductRepository repository;

    @Transactional
    public Map<String, Object> refreshCatalog() {
        ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
        String apiUrl = cwp.getApiLaudusUrl();
        String token = cwp.getTokenLaudusApi();

        List<AppCatalogProduct> productos = fetchProductos(apiUrl, token);
        if (productos.isEmpty()) {
            return Map.of("status", "error", "error", "Laudus no devolvió productos, no se modificó el catálogo local");
        }

        Map<String, Integer> stockPorSku = fetchStock(apiUrl, token);

        LocalDateTime now = LocalDateTime.now();
        long conStock = 0;
        for (AppCatalogProduct p : productos) {
            Integer stock = stockPorSku.get(p.getSku());
            p.setStock(stock != null ? stock : 0);
            p.setUpdatedAt(now);
            if (stock != null) conStock++;
        }

        // saveAll hace upsert por SKU (clave primaria): agrega los nuevos,
        // actualiza los que ya existían.
        repository.saveAll(productos);

        // Sacar del catálogo local los que Laudus ya no devolvió como activos.
        List<String> skusVigentes = productos.stream().map(AppCatalogProduct::getSku).toList();
        List<AppCatalogProduct> obsoletos = repository.findAll().stream()
            .filter(p -> !skusVigentes.contains(p.getSku()))
            .toList();
        if (!obsoletos.isEmpty()) {
            repository.deleteAll(obsoletos);
        }

        return Map.of(
            "status", "ok",
            "productos", productos.size(),
            "stockActualizado", conStock
        );
    }

    private List<AppCatalogProduct> fetchProductos(String apiUrl, String token) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String requestBody = """
            {
              "fields": [
                "sku",
                "description",
                "unitPriceWithTaxes",
                "productCategory.productCategoryId",
                "productCategory.name",
                "customFields.web_status_",
                "customFields.web_description_"
              ],
              "options": { "offset": 0, "limit": 2500 },
              "filterBy": [
                { "field": "customFields.web_status_", "operator": "=", "value": true }
              ],
              "orderBy": [ { "field": "sku", "direction": "ASC" } ]
            }
            """;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "list"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.out.println("❌ [AppCatalog] Error al obtener productos. HTTP " + response.statusCode());
                return List.of();
            }

            Gson gson = new Gson();
            JsonArray array = gson.fromJson(response.body(), JsonArray.class);
            List<AppCatalogProduct> result = new ArrayList<>();

            for (JsonElement el : array) {
                JsonObject o = el.getAsJsonObject();
                AppCatalogProduct p = new AppCatalogProduct();
                String sku = getString(o, "sku", "").replace(" ", "-");
                if (sku.isEmpty()) continue;
                p.setSku(sku);
                p.setName(getString(o, "description", ""));
                p.setUnitPriceWithTaxes(o.has("unitPriceWithTaxes") && !o.get("unitPriceWithTaxes").isJsonNull()
                    ? o.get("unitPriceWithTaxes").getAsInt() : null);
                p.setCategoryName(getString(o, "productCategory_name", null));
                p.setProductCategoryId(getString(o, "productCategory_productCategoryId", null));
                p.setWebDescription(getString(o, "customFields_web_description_", null));
                result.add(p);
            }
            return result;
        } catch (Exception e) {
            System.out.println("⚠️ [AppCatalog] Error obteniendo productos: " + e.getMessage());
            return List.of();
        }
    }

    // Bodega real de JOP en Laudus. Verificado contra la API: "005" y "001"
    // están casi vacías, "8" (sin ceros) ni siquiera existe ("No existe la
    // bodega ID: 8"). "008" es la bodega con el inventario real: 733 de 837
    // productos con stock.
    private static final String BODEGA = "008";

    private Map<String, Integer> fetchStock(String apiUrl, String token) {
        Map<String, Integer> stockMap = new HashMap<>();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "stock?warehouseId=" + BODEGA))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.out.println("❌ [AppCatalog] Error al obtener stock bodega " + BODEGA + ". HTTP " + response.statusCode());
                return stockMap;
            }

            Gson gson = new Gson();
            JsonObject json = gson.fromJson(response.body(), JsonObject.class);
            if (!json.has("products") || json.get("products").isJsonNull()) return stockMap;

            for (JsonElement el : json.getAsJsonArray("products")) {
                JsonObject o = el.getAsJsonObject();
                String sku = o.get("sku").getAsString();
                int stock = o.get("stock").getAsInt();
                stockMap.put(sku, stock);
            }
        } catch (Exception e) {
            System.out.println("⚠️ [AppCatalog] Error obteniendo stock: " + e.getMessage());
        }
        return stockMap;
    }

    private String getString(JsonObject o, String field, String defaultValue) {
        return o.has(field) && !o.get(field).isJsonNull() ? o.get(field).getAsString() : defaultValue;
    }
}
