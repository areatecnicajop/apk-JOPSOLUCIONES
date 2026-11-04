package com.jop.sincronizador.data.local.model.entity;

import java.math.BigDecimal;

import com.google.gson.annotations.Expose;
import com.jop.sincronizador.data.laudus.model.entity.expose;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "register_change")
public class RegisterChange {


    @Expose
    @Column(name = "sku", nullable = false)
    private String sku;

    @Expose
    @Column(name = "name", nullable = false)
    private String name;

    @Expose
    @Column(name = "stock_quantity", nullable = true)
    private Integer stock_quantity;

    @Expose
    @Column(name = "price")
    private Integer price;
    
    @Expose
    @Column(name = "regular_price")
    private String regular_price;
    
    @Lob
    @Expose
    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;

    @Expose
    @Column(name = "status")
    private String status;

    @Expose
    private boolean manage_stock;

    @Column(name = "idWP", nullable = true, length = 255)
    private String idWP;

    @Id
    @Column(name = "id_generated")
    private String idTransaction;
    
    @Column(name = "transaction_description")
    private String transactionDescription;
    
    @expose
    @Column(name = "id_wp_categorie")
    private Integer idWpCategorie;

    
}
