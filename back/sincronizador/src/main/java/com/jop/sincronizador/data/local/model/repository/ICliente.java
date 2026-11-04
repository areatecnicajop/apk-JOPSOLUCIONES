package com.jop.sincronizador.data.local.model.repository;

import com.jop.sincronizador.data.local.model.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ICliente extends JpaRepository<Cliente, String> {
    List<Cliente> findByStatus(String status);
    List<Cliente> findByNameContainingIgnoreCase(String name);
    List<Cliente> findByCategoryId(String categoryId);
}
