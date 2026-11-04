package com.jop.sincronizador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jop.sincronizador.data.local.model.entity.AppCatalogProduct;

@Repository
public interface AppCatalogProductRepository extends JpaRepository<AppCatalogProduct, String> {
}
