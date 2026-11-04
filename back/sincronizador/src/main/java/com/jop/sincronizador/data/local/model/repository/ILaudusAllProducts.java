package com.jop.sincronizador.data.local.model.repository;

import org.springframework.data.repository.CrudRepository;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import java.util.List;

public interface ILaudusAllProducts extends CrudRepository<LaudusProduct, Long> {

    List<LaudusProduct> findAll(); // Método para obtener todos los productos

}
