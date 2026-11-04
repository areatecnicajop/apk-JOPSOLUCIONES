package com.jop.sincronizador.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.RegisterChange;
import com.jop.sincronizador.data.local.model.services.IProductBDService;
import com.jop.sincronizador.data.local.model.services.TransactionBDtoWPService;
import com.jop.sincronizador.repository.PssWriteRepositoryStock;

// Vista de administración: productos que van a la web (webStatus=true) con su
// código y stock, y el override manual para forzar stock 0 sin depender de
// lo que reporte Laudus. Afecta directamente lo que se envía a WooCommerce
// (ver TransactionBDtoWPService.convertLaudusToLocal).
@RestController
@RequestMapping("/api/productos-web")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201", "http://localhost:8081", "https://jopsoluciones.com", "https://jopsoluciones.cl"})
public class ProductosWebController {

    @Autowired
    private IProductBDService productService;

    @Autowired
    private PssWriteRepositoryStock stockRepository;

    @Autowired
    private TransactionBDtoWPService transactionService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<LaudusProduct> productos = productService.findAll().stream()
            .filter(p -> Boolean.TRUE.equals(p.getWebStatus()))
            .collect(Collectors.toList());

        Map<String, Integer> stockMap = stockRepository.findAll().stream()
            .collect(Collectors.toMap(s -> s.getSku(), s -> s.getStock() != null ? s.getStock() : 0, (a, b) -> a));

        List<Map<String, Object>> result = productos.stream().map(p -> {
            int stockReal = stockMap.getOrDefault(p.getSku(), 0);
            boolean forzado = Boolean.TRUE.equals(p.getForceZeroStock());
            Map<String, Object> item = new HashMap<>();
            item.put("sku", p.getSku());
            item.put("description", p.getDescription());
            item.put("stockReal", stockReal);
            item.put("forceZeroStock", forzado);
            item.put("stockEfectivo", forzado ? 0 : stockReal);
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{sku}/force-zero-stock")
    public ResponseEntity<Map<String, Object>> setForceZeroStock(
            @PathVariable String sku,
            @RequestBody Map<String, Boolean> body) {
        boolean value = Boolean.TRUE.equals(body.get("value"));
        boolean encontrado = productService.setForceZeroStock(sku, value);
        if (!encontrado) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("sku", sku, "forceZeroStock", value));
    }

    // Preview de exactamente lo que se subiría a WooCommerce para este SKU —
    // reusa el mismo método que arma el paquete real (convertLaudusToLocal +
    // transformProductToJson), no lo reconstruye aparte. No escribe nada,
    // no llama a WooCommerce.
    @GetMapping("/{sku}/preview-woocommerce")
    public ResponseEntity<String> previewWooCommerce(@PathVariable String sku) {
        List<LaudusProduct> found = productService.findBySkuIn(List.of(sku));
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        RegisterChange registerChange = transactionService.convertLaudusToLocal(found.get(0));
        String json = TransactionBDtoWPService.transformProductToJson(registerChange);
        return ResponseEntity.ok().header("Content-Type", "application/json").body(json);
    }
}
