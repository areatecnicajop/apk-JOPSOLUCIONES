package com.jop.sincronizador.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.CategorieRegisterChange;
import com.jop.sincronizador.data.local.model.services.IProductBDService;
import com.jop.sincronizador.data.local.model.services.TransactionBDtoWPService;

// Vista de administración: categorías locales (tabla "categories") y preview
// de exactamente lo que se sube a WooCommerce por cada una. A diferencia de
// los productos, las categorías no tienen un flag tipo webStatus — todas las
// que están en la tabla se sincronizan (el filtro real ocurre antes, al
// traerlas desde Laudus — ver RestLaudusWordP.CategoriesGetAllLaudus).
@RestController
@RequestMapping("/api/categorias-web")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201", "http://localhost:8081", "https://jopsoluciones.com", "https://jopsoluciones.cl"})
public class CategoriasWebController {

    @Autowired
    private IProductBDService productService;

    @Autowired
    private TransactionBDtoWPService transactionService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Categorie> categorias = productService.findAllCategorie();

        List<Map<String, Object>> result = categorias.stream().map(c -> {
            Map<String, Object> item = new HashMap<>();
            item.put("laudusId", c.getLaudusId());
            item.put("laudusName", c.getLaudusName());
            item.put("laudusPath", c.getLaudusPath());
            item.put("parentId", c.getParentId());
            item.put("idWpCategorie", c.getIdWpCategorie());
            item.put("yaSincronizada", c.getIdWpCategorie() != null);
            return item;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // Preview de exactamente lo que se subiría a WooCommerce para esta
    // categoría — reusa convertLaudusToLocalCategorie + transformCategorieToJson,
    // el mismo código que usa el push real. No escribe nada, no llama a WooCommerce.
    @GetMapping("/{laudusId}/preview-woocommerce")
    public ResponseEntity<String> previewWooCommerce(@PathVariable String laudusId) {
        List<Categorie> categorias = productService.findAllCategorie();
        Categorie categoria = categorias.stream()
            .filter(c -> laudusId.equals(c.getLaudusId()))
            .findFirst()
            .orElse(null);
        if (categoria == null) {
            return ResponseEntity.notFound().build();
        }
        CategorieRegisterChange registerChange = transactionService.convertLaudusToLocalCategorie(categoria);
        String json = TransactionBDtoWPService.transformCategorieToJson(registerChange);
        return ResponseEntity.ok().header("Content-Type", "application/json").body(json);
    }
}
