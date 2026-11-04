package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @Column(name = "uuid", length = 36)
    private String uuid;

    @Column(name = "name", length = 300, nullable = false)
    private String name;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "address", length = 400)
    private String address;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "category_id", length = 36)
    private String categoryId;

    @Column(name = "category_name", length = 100)
    private String categoryName;

    // active | inactive | prospect | lead
    @Column(name = "status", length = 20)
    private String status = "active";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // JSON con campos extra
    @Column(name = "extra_fields", columnDefinition = "TEXT")
    private String extraFields;

    @Column(name = "created_at", length = 30)
    private String createdAt;

    @Column(name = "updated_at", length = 30)
    private String updatedAt;
}
