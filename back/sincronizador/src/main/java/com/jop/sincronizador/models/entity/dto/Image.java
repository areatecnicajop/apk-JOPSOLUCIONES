package com.jop.sincronizador.models.entity.dto;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Image {
    @Expose
    private int id; // WooCommerce devuelve los IDs de las imágenes
}
