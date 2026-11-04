package com.jop.sincronizador.data.local.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "rutas")
public class Ruta {

    @Id
    @Column(length = 36)
    private String uuid;

    @Column(length = 150)
    private String nombre;

    @Column(length = 20)
    private String tipo; // auto | manual

    private Double inicioLat;
    private Double inicioLng;
    @Column(length = 300)
    private String inicioLabel;

    private Double finLat;
    private Double finLng;
    @Column(length = 300)
    private String finLabel;

    @Column(columnDefinition = "TEXT")
    private String paradas; // JSON array: [{prospectoUuid, nombre, rubro, lat, lng, orden}]

    private Double distanciaKm;
    private Integer duracionMin;

    @Column(length = 30)
    private String createdAt;

    public Ruta() {}

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Double getInicioLat() { return inicioLat; }
    public void setInicioLat(Double inicioLat) { this.inicioLat = inicioLat; }

    public Double getInicioLng() { return inicioLng; }
    public void setInicioLng(Double inicioLng) { this.inicioLng = inicioLng; }

    public String getInicioLabel() { return inicioLabel; }
    public void setInicioLabel(String inicioLabel) { this.inicioLabel = inicioLabel; }

    public Double getFinLat() { return finLat; }
    public void setFinLat(Double finLat) { this.finLat = finLat; }

    public Double getFinLng() { return finLng; }
    public void setFinLng(Double finLng) { this.finLng = finLng; }

    public String getFinLabel() { return finLabel; }
    public void setFinLabel(String finLabel) { this.finLabel = finLabel; }

    public String getParadas() { return paradas; }
    public void setParadas(String paradas) { this.paradas = paradas; }

    public Double getDistanciaKm() { return distanciaKm; }
    public void setDistanciaKm(Double distanciaKm) { this.distanciaKm = distanciaKm; }

    public Integer getDuracionMin() { return duracionMin; }
    public void setDuracionMin(Integer duracionMin) { this.duracionMin = duracionMin; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
