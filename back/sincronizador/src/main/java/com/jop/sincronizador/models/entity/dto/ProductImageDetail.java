package com.jop.sincronizador.models.entity.dto;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductImageDetail {
    @Expose
    private String id;
    @Expose
    private int position;
    @Expose
    private String alt;
}
