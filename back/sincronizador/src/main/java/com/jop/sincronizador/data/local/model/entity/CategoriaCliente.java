package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "categorias_cliente")
public class CategoriaCliente {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "color_hex", length = 10)
    private String colorHex;

    @Column(name = "icon_name", length = 50)
    private String iconName;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "created_at", length = 30)
    private String createdAt;
}
