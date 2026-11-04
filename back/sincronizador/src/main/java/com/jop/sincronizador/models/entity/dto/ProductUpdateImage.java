package com.jop.sincronizador.models.entity.dto;

import com.google.gson.annotations.Expose;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ProductUpdateImage {
        @Expose
        private List<ProductImageDetail> images;
    
        // Constructor, getters y setters
        public ProductUpdateImage(List<ProductImageDetail> images) {
            this.images = images;
        }
    
       
    }
    