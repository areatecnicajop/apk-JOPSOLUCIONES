package com.jop.sincronizador.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jop.sincronizador.laudus.LaudusCustomFieldsClient;

// Pestaña "Config Laudus" — administrar los custom fields del ERP
// (System/CustomFields de la API de Laudus) directamente desde acá, sin tener
// que entrar a Laudus. Ojo: el DELETE es real e irreversible del lado de Laudus.
@RestController
public class LaudusConfigController {

    @Autowired
    private LaudusCustomFieldsClient laudus;

    @GetMapping("/laudus/custom-fields")
    public ResponseEntity<String> list() {
        LaudusCustomFieldsClient.LaudusResponse res = laudus.list();
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }

    @GetMapping("/laudus/custom-fields/{id}")
    public ResponseEntity<String> get(@PathVariable int id) {
        LaudusCustomFieldsClient.LaudusResponse res = laudus.get(id);
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }

    @PostMapping("/laudus/custom-fields")
    public ResponseEntity<String> create(@RequestBody String body) {
        LaudusCustomFieldsClient.LaudusResponse res = laudus.create(body);
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }

    @PutMapping("/laudus/custom-fields/{id}")
    public ResponseEntity<String> update(@PathVariable int id, @RequestBody String body) {
        LaudusCustomFieldsClient.LaudusResponse res = laudus.update(id, body);
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }

    @DeleteMapping("/laudus/custom-fields/{id}")
    public ResponseEntity<String> delete(@PathVariable int id) {
        LaudusCustomFieldsClient.LaudusResponse res = laudus.delete(id);
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }
}
