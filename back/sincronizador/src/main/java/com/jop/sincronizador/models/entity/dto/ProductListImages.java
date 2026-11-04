package com.jop.sincronizador.models.entity.dto;

import java.util.List;
import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductListImages {
    @Expose
    private List<String> productImagesIds;
}
