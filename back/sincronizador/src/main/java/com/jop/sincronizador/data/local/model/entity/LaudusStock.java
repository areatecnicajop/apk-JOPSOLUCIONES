package com.jop.sincronizador.data.local.model.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity


@Table(name = "laudus_stock")
public class LaudusStock {
    
    @Id
    private String sku ;

    private Integer stock;
    
}
