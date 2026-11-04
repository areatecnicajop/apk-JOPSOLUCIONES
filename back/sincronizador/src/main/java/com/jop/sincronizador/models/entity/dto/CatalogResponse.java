package com.jop.sincronizador.models.entity.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogResponse {
    private List<CatalogProduct> products;
    
    public CatalogResponse(List<CatalogProduct> products) {
        this.products = products;
    }
}















