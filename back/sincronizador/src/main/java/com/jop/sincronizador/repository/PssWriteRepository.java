package com.jop.sincronizador.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.jop.sincronizador.data.local.model.entity.LaudusProduct;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import jakarta.transaction.Transactional;

@Repository
public interface PssWriteRepository extends JpaRepository<LaudusProduct, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE LaudusProduct p SET p.webStatus = CAST(0 AS boolean) WHERE p.sku NOT IN :skus")
    void updateMissingProducts(@Param("skus") List<String> skus);

    
}    