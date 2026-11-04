package com.jop.sincronizador.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jop.sincronizador.data.local.model.entity.LaudusStock;


@Repository
public interface PssWriteRepositoryStock extends JpaRepository<LaudusStock, String> {

    @Query("SELECT p.stock FROM LaudusStock p WHERE p.sku = :sku")
    Integer findStockBySku(@Param("sku") String sku);
  
}