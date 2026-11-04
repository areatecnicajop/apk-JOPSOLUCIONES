package com.jop.sincronizador.controllers;

import com.jop.sincronizador.data.local.model.entity.Visita;
import com.jop.sincronizador.data.local.model.repository.IVisita;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/rutas/{rutaUuid}/visitas")
public class VisitaController {

    @Autowired
    private IVisita dao;

    @GetMapping
    public List<Visita> getAll(@PathVariable String rutaUuid) {
        return dao.findAllByRutaUuid(rutaUuid);
    }

    @PostMapping
    public Visita create(@PathVariable String rutaUuid, @RequestBody Visita visita) {
        visita.setId(null);
        visita.setRutaUuid(rutaUuid);
        visita.setFecha(Instant.now().toString());
        return dao.save(visita);
    }
}
