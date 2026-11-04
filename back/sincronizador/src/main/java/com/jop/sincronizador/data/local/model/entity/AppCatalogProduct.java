package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

// Tabla exclusiva del catálogo local de la APK — separada de laudus_products
// y laudus_stock, que alimentan el pipeline de sincronización hacia WooCommerce.
// Esta tabla no participa en ese pipeline: solo sirve de respaldo local para
// que la APK liste productos y arme pedidos sin depender del ERP en vivo.
@Getter
@Setter
@Data
@Entity
@Table(name = "app_catalog_products")
public class AppCatalogProduct {

    @Id
    @Column(name = "sku")
    private String sku;

    @Column(name = "name")
    private String name;

    @Column(name = "unit_price_with_taxes")
    private Integer unitPriceWithTaxes;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "category_name")
    private String categoryName;

    @Column(name = "product_category_id")
    private String productCategoryId;

    @Lob
    @Column(name = "web_description", columnDefinition = "TEXT")
    private String webDescription;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
