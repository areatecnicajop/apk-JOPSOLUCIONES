package com.jop.sincronizador.controllers;

import com.jop.sincronizador.data.local.model.entity.Ruta;
import com.jop.sincronizador.data.local.model.repository.IRuta;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/rutas")
public class RutaController {

    @Autowired
    private IRuta dao;

    @GetMapping
    public List<Ruta> getAll() {
        return dao.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Ruta> getById(@PathVariable String uuid) {
        return dao.findById(uuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Ruta> create(@RequestBody Ruta ruta) {
        ruta.setCreatedAt(Instant.now().toString());
        return ResponseEntity.ok(dao.save(ruta));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        if (!dao.existsById(uuid)) return ResponseEntity.notFound().build();
        dao.deleteById(uuid);
        return ResponseEntity.noContent().build();
    }
}
