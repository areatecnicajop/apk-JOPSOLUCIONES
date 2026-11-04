package com.jop.sincronizador.data.local.model.repository;

import com.jop.sincronizador.data.local.model.entity.CategoriaCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ICategoriaCliente extends JpaRepository<CategoriaCliente, String> {
}
