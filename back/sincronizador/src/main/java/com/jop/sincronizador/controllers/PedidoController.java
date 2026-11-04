package com.jop.sincronizador.controllers;

import com.jop.sincronizador.data.local.model.entity.Pedido;
import com.jop.sincronizador.data.local.model.repository.IPedido;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private IPedido dao;

    @GetMapping
    public List<Pedido> getAll() {
        return dao.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/{uuid}")
    public ResponseEntity<Pedido> getById(@PathVariable String uuid) {
        return dao.findById(uuid)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/cliente/{clienteUuid}")
    public List<Pedido> getByCliente(@PathVariable String clienteUuid) {
        return dao.findByClienteUuidOrderByCreatedAtDesc(clienteUuid);
    }

    @GetMapping("/status/{status}")
    public List<Pedido> getByStatus(@PathVariable String status) {
        return dao.findByStatusOrderByCreatedAtDesc(status);
    }

    @PostMapping
    public ResponseEntity<Pedido> create(@RequestBody Pedido pedido) {
        String now = Instant.now().toString();
        pedido.setCreatedAt(now);
        pedido.setUpdatedAt(now);
        if (pedido.getStatus() == null) pedido.setStatus("pending");
        return ResponseEntity.ok(dao.save(pedido));
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<Pedido> update(@PathVariable String uuid, @RequestBody Pedido pedido) {
        if (!dao.existsById(uuid)) return ResponseEntity.notFound().build();
        pedido.setUuid(uuid);
        pedido.setUpdatedAt(Instant.now().toString());
        return ResponseEntity.ok(dao.save(pedido));
    }

    @PatchMapping("/{uuid}/status")
    public ResponseEntity<Pedido> updateStatus(@PathVariable String uuid, @RequestBody Map<String, String> body) {
        return dao.findById(uuid).map(p -> {
            p.setStatus(body.get("status"));
            p.setUpdatedAt(Instant.now().toString());
            return ResponseEntity.ok(dao.save(p));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        if (!dao.existsById(uuid)) return ResponseEntity.notFound().build();
        dao.deleteById(uuid);
        return ResponseEntity.noContent().build();
    }
}
