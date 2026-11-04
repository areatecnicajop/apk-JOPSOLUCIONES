package com.jop.sincronizador.models.entity.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogResponseExtended {
    private List<CatalogProductExtended> products;
    
    public CatalogResponseExtended(List<CatalogProductExtended> products) {
        this.products = products;
    }
}






