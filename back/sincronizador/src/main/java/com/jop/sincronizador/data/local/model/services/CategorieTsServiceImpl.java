package com.jop.sincronizador.data.local.model.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.jop.sincronizador.data.local.model.repository.ICategorieLaudus;
import com.jop.sincronizador.data.local.model.entity.CategorieRegisterChange;

@Service
public class CategorieTsServiceImpl implements ICategorieTsService {

    @Autowired
    private ICategorieLaudus iCategorieLaudus;

    @Override
    public void update(CategorieRegisterChange rc) {
        String idCategorie = (rc.getIdWpCategorie() != null) ? rc.getIdWpCategorie().toString() : "0";

        if (!iCategorieLaudus.existsById(idCategorie)) {
            iCategorieLaudus.save(rc);
        }
    }

    @Override
    public void add(CategorieRegisterChange ts) {
        String idCategorie = (ts.getIdWpCategorie() != null) ? ts.getIdWpCategorie().toString() : "0";

        if (!iCategorieLaudus.existsById(idCategorie)) {
            iCategorieLaudus.save(ts);
        } else {
            System.out.println("El registro con ID ya existe. No se guardará.");
        }
    }
}
