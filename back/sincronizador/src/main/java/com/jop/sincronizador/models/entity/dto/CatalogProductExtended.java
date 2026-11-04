package com.jop.sincronizador.models.entity.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CatalogProductExtended {
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
    
    // CustomFields extendidos para el catálogo
    private String updateImage;
    private String largo;
    private String material;
    private String diametro;
    private String diametroRosc;
    private String diametroExterno;
    private String medidas;
    private String medidasBase;
    private String medidaEje;
    private String medidaVias;
    private String diametroCentro;
    private String medidaAccesorios;
    private String nVias;
    private String nPistones;
    private String tipo;
    private String corriente;
    private String hp;
    private String medidaCondensadores;
    private String cabezal;
    private String motor;
    private String etapas;
    private String presion;
    private String caudal;
    private String aspiracion;
    private String elementoFiltrante;
    private String disipiadorCalor;
    private String amperes;
    private String voltaje;
    private String ubicacionRosc;
    private String grosor;
    private String diametroPiston;
    private String cantidadTuercas;
    private String diametroEje;
    private String tipoRosca;
    private String estanque;
    private String rpm;
    private String accesorios;
    private String roscaEntrada;
    private String profundidad;
}


