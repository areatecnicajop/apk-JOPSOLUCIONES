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

    // Stock que tenía este SKU justo antes de la última actualización que lo cambió.
    // Solo tiene sentido mientras cambioUltima sea true.
    @Column(name = "stock_anterior")
    private Integer stockAnterior;

    // true = este SKU cambió de stock en la ÚLTIMA actualización desde Laudus.
    // Se reinicia en cada actualización (Boolean y no boolean: las filas viejas quedan en null).
    @Column(name = "cambio_ultima")
    private Boolean cambioUltima;

}
