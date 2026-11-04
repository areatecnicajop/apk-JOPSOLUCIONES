package com.jop.sincronizador.data.local.model.repository;


import org.springframework.data.repository.CrudRepository;

import com.jop.sincronizador.data.local.model.entity.CategorieRegisterChange;

public interface ICategorieLaudus extends CrudRepository<CategorieRegisterChange, String> {


    // public List<RegisterChange> findAllBystatusTS(String statusTS);

	// public List<RegisterChange> findAllByIdAndStatusTS(String id, String statusTS);

	// public List<RegisterChange> findAllBySkuAndStatusTS(String sku, String statusTS);





}
