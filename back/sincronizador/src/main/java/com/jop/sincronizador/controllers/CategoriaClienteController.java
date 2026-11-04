package com.jop.sincronizador.controllers;

import com.jop.sincronizador.data.local.model.entity.CategoriaCliente;
import com.jop.sincronizador.data.local.model.repository.ICategoriaCliente;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/categorias-cliente")
public class CategoriaClienteController {

    @Autowired
    private ICategoriaCliente repo;

    @GetMapping
    public ResponseEntity<List<CategoriaCliente>> getAll() {
        return ResponseEntity.ok(repo.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<CategoriaCliente> getById(@PathVariable String uuid) {
        Optional<CategoriaCliente> cat = repo.findById(uuid);
        return cat.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CategoriaCliente> create(@RequestBody CategoriaCliente cat) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(cat));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<CategoriaCliente> update(@PathVariable String uuid, @RequestBody CategoriaCliente cat) {
        if (!repo.existsById(uuid)) return ResponseEntity.notFound().build();
        cat.setUuid(uuid);
        return ResponseEntity.ok(repo.save(cat));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        if (!repo.existsById(uuid)) return ResponseEntity.notFound().build();
        repo.deleteById(uuid);
        return ResponseEntity.noContent().build();
    }
}
