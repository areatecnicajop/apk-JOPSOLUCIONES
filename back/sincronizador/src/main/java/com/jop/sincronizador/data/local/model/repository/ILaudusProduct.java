package com.jop.sincronizador.data.local.model.repository;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

import com.jop.sincronizador.data.local.model.entity.RegisterChange;


public interface ILaudusProduct extends CrudRepository<RegisterChange, String> {


    // public List<RegisterChange> findAllBystatusTS(String statusTS);

	// public List<RegisterChange> findAllByIdAndStatusTS(String id, String statusTS);

	// public List<RegisterChange> findAllBySkuAndStatusTS(String sku, String statusTS);





}
