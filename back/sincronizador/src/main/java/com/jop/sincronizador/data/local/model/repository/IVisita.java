package com.jop.sincronizador.data.local.model.repository;

import com.jop.sincronizador.data.local.model.entity.Visita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IVisita extends JpaRepository<Visita, Long> {
    List<Visita> findAllByRutaUuid(String rutaUuid);
}
