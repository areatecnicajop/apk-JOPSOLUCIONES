package com.jop.sincronizador.controllers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.LaudusStock;
import com.jop.sincronizador.data.local.model.services.IProductBDService;
import com.jop.sincronizador.repository.PssWriteRepositoryStock;
import com.jop.sincronizador.services.IRestLaudusWordP;

// Pestaña "Productos" del Sincronizador — igual espíritu que la de Falabella
// (saber de un vistazo si el producto ya existe del otro lado y precargar sus
// datos reales), pero minimalista: WooCommerce ya tiene toda su lógica de
// sync hecha (TransactionBDtoWPService), acá solo se muestra estado + preview,
// no se reinventa el push — eso lo sigue haciendo /start.
@RestController
@RequestMapping("/sync/productos")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201", "http://localhost:8081", "https://jopsoluciones.com", "https://jopsoluciones.cl"})
public class SyncProductosController {

    @Autowired
    private IProductBDService productService;

    @Autowired
    private PssWriteRepositoryStock stockRepository;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private IRestLaudusWordP restLaudusWordP;

    private final Gson gson = new Gson();

    // idWP ya es el indicador real de "está en WooCommerce" — se guarda en
    // cada sync real (TransactionBDtoWPService.addSite/updateSite). No hace
    // falta golpear ninguna API externa para esta lista.
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar() {
        List<LaudusProduct> productos = productService.findAll();
        List<Categorie> categorias = productService.findAllCategorie();
        Map<String, Categorie> categoryMap = categorias.stream()
            .collect(Collectors.toMap(Categorie::getLaudusId, c -> c, (a, b) -> a));
        Map<String, Integer> stockMap = stockMap();
        // Filas completas de stock: aportan "cambió en la última actualización" y el valor anterior.
        Map<String, LaudusStock> stockRows = new HashMap<>();
        for (LaudusStock s : stockRepository.findAll()) {
            if (s.getSku() != null) stockRows.putIfAbsent(s.getSku(), s);
        }

        List<Map<String, Object>> result = productos.stream()
            .filter(p -> p.getSku() != null && !p.getSku().isBlank())
            .map(p -> {
                Map<String, Object> item = new HashMap<>();
                item.put("sku", p.getSku());
                item.put("name", p.getDescription());
                item.put("price", p.getUnitPriceWithTaxes());
                item.put("stock", stockMap.getOrDefault(p.getSku(), 0));
                // Sin fila en laudus_stock no hay stock conocido: no se puede enviar a la web.
                boolean stockConocido = stockMap.containsKey(p.getSku());
                boolean forzado = Boolean.TRUE.equals(p.getForceZeroStock());
                item.put("stockConocido", stockConocido);
                item.put("forceZeroStock", forzado);
                item.put("stockEfectivo", forzado ? 0 : stockMap.getOrDefault(p.getSku(), 0));
                item.put("stockWebEnviado", p.getStockWebEnviado());
                LaudusStock row = stockRows.get(p.getSku());
                boolean cambio = row != null && Boolean.TRUE.equals(row.getCambioUltima());
                item.put("stockCambio", cambio);
                item.put("stockAnterior", cambio ? row.getStockAnterior() : null);
                Categorie cat = p.getProductCategoryId() != null ? categoryMap.get(p.getProductCategoryId()) : null;
                item.put("categoryName", cat != null ? cat.getLaudusName() : null);
                item.put("enWooCommerce", p.getIdWP() != null && !p.getIdWP().isBlank());
                return item;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // Datos reales tal cual están AHORA en WooCommerce (no lo que Laudus
    // enviaría) — solo si el producto ya tiene idWP asignado.
    @GetMapping("/{sku}/datos-woocommerce")
    public ResponseEntity<Map<String, Object>> datosWooCommerce(@PathVariable String sku) {
        List<LaudusProduct> matches = productService.findBySkuIn(List.of(sku));
        if (matches.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "SKU no encontrado en laudus_products"));
        }
        LaudusProduct product = matches.get(0);
        if (product.getIdWP() == null || product.getIdWP().isBlank()) {
            return ResponseEntity.ok(Map.of("existe", false));
        }

        ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
        try {
            String url = cwp.getUrlBase() + "products?sku=" + sku;
            String auth = cwp.getUsernameToken() + ":" + cwp.getPasswordToken();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + encodedAuth)
                .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ResponseEntity.status(response.statusCode()).body(Map.of("error", response.body()));
            }

            JsonArray arr = gson.fromJson(response.body(), JsonArray.class);
            if (arr == null || arr.isEmpty()) {
                return ResponseEntity.ok(Map.of("existe", false));
            }
            JsonObject wp = arr.get(0).getAsJsonObject();

            Map<String, Object> result = new HashMap<>();
            result.put("existe", true);
            result.put("id", getOrNull(wp, "id"));
            result.put("name", getOrNull(wp, "name"));
            result.put("price", getOrNull(wp, "price"));
            result.put("regularPrice", getOrNull(wp, "regular_price"));
            result.put("stockQuantity", getOrNull(wp, "stock_quantity"));
            result.put("status", getOrNull(wp, "status"));
            result.put("shortDescription", getOrNull(wp, "short_description"));
            result.put("description", getOrNull(wp, "description"));

            List<String> categoryNames = new ArrayList<>();
            if (wp.has("categories") && wp.get("categories").isJsonArray()) {
                for (JsonElement c : wp.getAsJsonArray("categories")) {
                    JsonObject co = c.getAsJsonObject();
                    if (co.has("name")) categoryNames.add(co.get("name").getAsString());
                }
            }
            result.put("categoryNames", categoryNames);

            List<String> images = new ArrayList<>();
            if (wp.has("images") && wp.get("images").isJsonArray()) {
                for (JsonElement img : wp.getAsJsonArray("images")) {
                    JsonObject io = img.getAsJsonObject();
                    if (io.has("src")) images.add(io.get("src").getAsString());
                }
            }
            result.put("images", images);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Actualiza título y/o descripción corta/larga directo en WooCommerce
    // (ProductPut), sin pasar por el sync completo de Laudus — esto es lo
    // único de este panel que de verdad se puede editar y guardar.
    @PutMapping("/{sku}/editar")
    public ResponseEntity<Map<String, Object>> editarEnWooCommerce(@PathVariable String sku,
                                                                     @RequestBody Map<String, String> body) {
        List<LaudusProduct> matches = productService.findBySkuIn(List.of(sku));
        if (matches.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "SKU no encontrado en laudus_products"));
        }
        LaudusProduct product = matches.get(0);
        if (product.getIdWP() == null || product.getIdWP().isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error",
                "Este producto todavía no está en WooCommerce — sincronízalo primero antes de editarlo."));
        }

        Map<String, String> payload = new HashMap<>();
        if (body.containsKey("name")) payload.put("name", body.get("name"));
        if (body.containsKey("description")) payload.put("description", body.get("description"));
        if (body.containsKey("short_description")) payload.put("short_description", body.get("short_description"));
        if (payload.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "Nada que actualizar"));
        }

        String json = gson.toJson(payload);
        Integer code = restLaudusWordP.ProductPut(product.getIdWP(), json, "descripción actualizada manualmente");
        if (code != null && code == 200) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(502).body(Map.of("error", "WooCommerce respondió con código " + code));
    }

    // Envía a WooCommerce SOLO el stock de los SKUs indicados (no toca nombre, precio,
    // descripciones, estado ni categorías). Manda el stock efectivo: 0 si el producto
    // tiene "forzar stock 0", si no el de laudus_stock. Al confirmar WooCommerce
    // (200) guarda ese valor en stockWebEnviado, que es la referencia de la
    // diferencia en la pestaña Productos. No crea productos: sin idWP se omite.
    @PostMapping("/enviar-stock")
    public ResponseEntity<Map<String, Object>> enviarStock(@RequestBody Map<String, List<String>> body) {
        List<String> skus = body.get("skus");
        if (skus == null || skus.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("error", "Sin SKUs para enviar"));
        }

        Map<String, LaudusProduct> porSku = new HashMap<>();
        for (LaudusProduct p : productService.findBySkuIn(skus)) {
            if (p.getSku() != null) porSku.put(p.getSku(), p);
        }
        Map<String, Integer> stockMap = stockMap();

        List<Map<String, Object>> resultados = new ArrayList<>();
        int enviados = 0, errores = 0, omitidos = 0;

        for (String sku : skus) {
            Map<String, Object> r = new HashMap<>();
            r.put("sku", sku);
            LaudusProduct p = porSku.get(sku);

            if (p == null) {
                r.put("estado", "no_encontrado");
                omitidos++;
            } else if (p.getIdWP() == null || p.getIdWP().isBlank()) {
                r.put("estado", "no_en_woocommerce");
                omitidos++;
            } else if (!stockMap.containsKey(sku)) {
                r.put("estado", "sin_stock_conocido");
                omitidos++;
            } else {
                int efectivo = Boolean.TRUE.equals(p.getForceZeroStock()) ? 0 : stockMap.get(sku);
                Map<String, Object> payload = new HashMap<>();
                payload.put("manage_stock", true);
                payload.put("stock_quantity", efectivo);
                Integer code = restLaudusWordP.ProductPut(p.getIdWP(), gson.toJson(payload),
                    "stock actualizado (" + sku + " → " + efectivo + ")");
                if (code != null && code == 200) {
                    p.setStockWebEnviado(efectivo);
                    productService.updateProduct(p);
                    // Ya está en la web: deja de marcarse como "cambió en la última actualización"
                    // (lo que había antes pasa a ser lo que se subió).
                    stockRepository.findById(sku).ifPresent(s -> {
                        if (Boolean.TRUE.equals(s.getCambioUltima()) || s.getStockAnterior() != null) {
                            s.setCambioUltima(false);
                            s.setStockAnterior(null);
                            stockRepository.save(s);
                        }
                    });
                    r.put("estado", "ok");
                    r.put("stockEnviado", efectivo);
                    enviados++;
                } else {
                    r.put("estado", "error");
                    r.put("detalle", "WooCommerce respondió con código " + code);
                    errores++;
                }
            }
            resultados.add(r);
        }

        Map<String, Object> res = new HashMap<>();
        res.put("enviados", enviados);
        res.put("errores", errores);
        res.put("omitidos", omitidos);
        res.put("resultados", resultados);
        return ResponseEntity.ok(res);
    }

    private Map<String, Integer> stockMap() {
        Map<String, Integer> map = new HashMap<>();
        for (LaudusStock s : stockRepository.findAll()) {
            if (s.getSku() != null && s.getStock() != null) map.putIfAbsent(s.getSku(), s.getStock());
        }
        return map;
    }

    private String getOrNull(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && !el.isJsonNull() ? el.getAsString() : null;
    }
}
