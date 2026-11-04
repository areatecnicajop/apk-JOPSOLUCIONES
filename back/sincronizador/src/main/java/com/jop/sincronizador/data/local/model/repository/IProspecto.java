package com.jop.sincronizador.data.local.model.repository;

import com.jop.sincronizador.data.local.model.entity.Prospecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IProspecto extends JpaRepository<Prospecto, Long> {
    List<Prospecto> findByRubroIgnoreCase(String rubro);
    List<Prospecto> findByNombreContainingIgnoreCase(String nombre);
}
