package com.jop.sincronizador.controllers;

import com.jop.sincronizador.data.local.model.entity.Cliente;
import com.jop.sincronizador.data.local.model.repository.ICliente;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

    @Autowired
    private ICliente clienteRepository;

    @GetMapping
    public ResponseEntity<List<Cliente>> getAll() {
        return ResponseEntity.ok(clienteRepository.findAll());
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Cliente> getById(@PathVariable String uuid) {
        Optional<Cliente> cliente = clienteRepository.findById(uuid);
        return cliente.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<Cliente>> getByStatus(@PathVariable String status) {
        return ResponseEntity.ok(clienteRepository.findByStatus(status));
    }

    @PostMapping
    public ResponseEntity<Cliente> create(@RequestBody Cliente cliente) {
        Cliente saved = clienteRepository.save(cliente);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<Cliente>> createBulk(@RequestBody List<Cliente> clientes) {
        List<Cliente> saved = clienteRepository.saveAll(clientes);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<Cliente> update(@PathVariable String uuid, @RequestBody Cliente cliente) {
        if (!clienteRepository.existsById(uuid)) {
            return ResponseEntity.notFound().build();
        }
        cliente.setUuid(uuid);
        return ResponseEntity.ok(clienteRepository.save(cliente));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        if (!clienteRepository.existsById(uuid)) {
            return ResponseEntity.notFound().build();
        }
        clienteRepository.deleteById(uuid);
        return ResponseEntity.noContent().build();
    }
}
