package com.jop.sincronizador.data.local.model.entity;

import com.google.gson.annotations.Expose;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "categorie_register_change")
public class CategorieRegisterChange {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)  // Genera automáticamente un UUID como ID
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Expose
    @Column(name = "name", nullable = false)
    private String name;

    @Expose
    @Column(name = "slug", nullable = false)
    private String slug;

    @Expose
    @Column(name = "parent", nullable = false)
    private int parent;
    
    @Column(name = "id_wp_categorie")
    private Integer idWpCategorie;
    

}
