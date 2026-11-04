package com.jop.sincronizador.data.local.model.repository;

import com.jop.sincronizador.data.local.model.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IPedido extends JpaRepository<Pedido, String> {
    List<Pedido> findByClienteUuidOrderByCreatedAtDesc(String clienteUuid);
    List<Pedido> findByStatusOrderByCreatedAtDesc(String status);
    List<Pedido> findAllByOrderByCreatedAtDesc();
}
