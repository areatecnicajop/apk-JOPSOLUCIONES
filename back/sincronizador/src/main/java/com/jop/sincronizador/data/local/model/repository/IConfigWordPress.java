package com.jop.sincronizador.data.local.model.repository;

import org.springframework.data.repository.CrudRepository;

import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;


public interface IConfigWordPress extends CrudRepository<ConfigWordPress, Long> {

}
