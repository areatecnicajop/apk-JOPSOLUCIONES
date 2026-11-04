package com.jop.sincronizador.models.entity.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogProduct {
    // Campos del formato del catálogo (JSON original)
    private String name;
    private String tagline;
    private String description;
    private String price;
    private String colorPalette;
    private String materials;
    private String category;
    private String subcategory;
    
    // Campos adicionales del producto completo de Laudus
    private Long id;
    private String sku;
    private Integer unitPriceWithTaxes;
    private String modifiedAt;
    private String productCategoryId;
    private Boolean webStatus;
    private String webDescription;
    private String idWP;
    private String categoryPath;
    private String categoryName;
}

