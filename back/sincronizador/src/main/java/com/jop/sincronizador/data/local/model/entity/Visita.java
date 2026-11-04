package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "visitas")
public class Visita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 36, nullable = false)
    private String rutaUuid;

    private Long prospectoId;

    @Column(length = 500)
    private String prospectoNombre;

    @Column(length = 30)
    private String fecha;

    @Column(length = 1000)
    private String nota;

    public Visita() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRutaUuid() { return rutaUuid; }
    public void setRutaUuid(String rutaUuid) { this.rutaUuid = rutaUuid; }

    public Long getProspectoId() { return prospectoId; }
    public void setProspectoId(Long prospectoId) { this.prospectoId = prospectoId; }

    public String getProspectoNombre() { return prospectoNombre; }
    public void setProspectoNombre(String prospectoNombre) { this.prospectoNombre = prospectoNombre; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
}
