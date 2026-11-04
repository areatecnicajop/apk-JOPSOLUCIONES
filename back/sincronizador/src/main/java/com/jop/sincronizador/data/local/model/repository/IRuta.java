package com.jop.sincronizador.data.local.model.repository;

import com.jop.sincronizador.data.local.model.entity.Ruta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IRuta extends JpaRepository<Ruta, String> {
    List<Ruta> findAllByOrderByCreatedAtDesc();
}
