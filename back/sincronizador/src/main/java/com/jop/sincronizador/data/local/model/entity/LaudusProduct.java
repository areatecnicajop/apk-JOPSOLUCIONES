package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Data
@Entity
@Table(name = "laudus_products")
public class LaudusProduct {

    @Id
    @Column(name = "id")
    private Long id; // Lo tomas del ERP directamente, sin autogenerar

    @Column(name = "sku")
    private String sku;

    @Column(name = "description")
    private String description;

    @Column(name = "unit_price_with_taxes")
    private Integer unitPriceWithTaxes;

    @Column(name = "modified_at")
    private String modifiedAt;

    @Column(name = "product_category_id")
    private String productCategoryId;

    @Column(name = "web_status_")
    private Boolean webStatus;
    
    @Lob
    @Column(name = "web_description_",columnDefinition = "TEXT")
    private String webDescription;

    @Column(name = "idWP")
    private String idWP;

    // Id/SellerSku confirmado en Falabella Seller Center tras un ProductCreate exitoso.
    // Null = nunca se ha subido (o subió y falló). Mismo rol que idWP pero para Falabella.
    @Column(name = "id_falabella")
    private String idFalabella;

    // Override manual: si está en true, el stock que se envía a WooCommerce
    // se trunca a 0 sin importar lo que reporte Laudus. Se preserva en cada
    // re-sync (ver ProductBDImplService.addProductAll) para no perderse.
    @Column(name = "force_zero_stock")
    private Boolean forceZeroStock = false;
}
