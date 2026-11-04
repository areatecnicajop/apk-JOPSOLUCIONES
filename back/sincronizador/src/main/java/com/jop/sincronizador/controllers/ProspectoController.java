package com.jop.sincronizador.controllers;

import com.jop.sincronizador.data.local.model.entity.Prospecto;
import com.jop.sincronizador.data.local.model.repository.IProspecto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/prospectos")
public class ProspectoController {

    @Autowired
    private IProspecto prospectoRepository;

    @GetMapping
    public ResponseEntity<List<Prospecto>> getAll() {
        return ResponseEntity.ok(prospectoRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Prospecto> getById(@PathVariable Long id) {
        Optional<Prospecto> prospecto = prospectoRepository.findById(id);
        return prospecto.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Prospecto> create(@RequestBody Prospecto prospecto) {
        Prospecto saved = prospectoRepository.save(prospecto);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<Prospecto>> createBulk(@RequestBody List<Prospecto> prospectos) {
        List<Prospecto> saved = prospectoRepository.saveAll(prospectos);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Prospecto> update(@PathVariable Long id, @RequestBody Prospecto prospecto) {
        if (!prospectoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        prospecto.setId(id);
        return ResponseEntity.ok(prospectoRepository.save(prospecto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!prospectoRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        prospectoRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
