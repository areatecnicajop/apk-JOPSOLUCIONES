package com.jop.sincronizador.models;

import lombok.Data;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
@Data // Lombok genera automáticamente getters, setters, toString, equals, etc.
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true)
    private String email;
}
