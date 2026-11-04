package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "prospectos")
public class Prospecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 500)
    private String nombre;

    @Column(name = "rubro", length = 100)
    private String rubro;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "direccion", length = 300)
    private String direccion;

    @Column(name = "comuna", length = 100)
    private String comuna;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "latitud")
    private Double latitud;

    @Column(name = "longitud")
    private Double longitud;

    @Column(name = "url", length = 300)
    private String url;

    @Column(name = "fuente", length = 50)
    private String fuente;

    @Column(name = "fecha", length = 20)
    private String fecha;
}
