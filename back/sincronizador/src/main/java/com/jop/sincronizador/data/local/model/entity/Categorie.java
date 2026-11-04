package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "categories")
public class Categorie {

    @Id
    @Column(name = "laudus_id")
    private String laudusId; // Lo tomas del ERP directamente, sin autogenerar

    @Column(name = "laudus_name")
    private String laudusName;

    @Column(name = "laudus_path")
    private String laudusPath;

    @Column(name = "parent_id")
    private String parentId;
    
    @Column(name = "id_wp_categorie")
    private Integer idWpCategorie;

    @Column(name = "position_path")
    private Integer positionPath;
    
}
