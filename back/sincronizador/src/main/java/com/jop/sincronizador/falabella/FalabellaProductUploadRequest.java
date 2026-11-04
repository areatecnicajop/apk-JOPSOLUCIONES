package com.jop.sincronizador.falabella;

import java.util.Map;

import lombok.Getter;
import lombok.Setter;

// Datos que Laudus no tiene y que hay que completar a mano para el primer
// producto de prueba: categoría/marca de Falabella y las dimensiones del paquete.
@Getter
@Setter
public class FalabellaProductUploadRequest {
    private String primaryCategory;
    private String brand;
    private String conditionType = "Nuevo";
    private Integer packageHeight;
    private Integer packageWidth;
    private Integer packageLength;
    private Double packageWeight;

    // Ancho/Largo del producto en sí (distinto de las medidas del paquete de
    // envío de arriba) — opcionales, Falabella los pide como texto libre con
    // unidad incluida (ej. "34 cm").
    private String ancho;
    private String largo;

    // Precio final a mandar a Falabella — el front lo precarga con el precio
    // de Laudus + 30% redondeado (para cubrir la comisión), pero es editable.
    private Double price;

    // Precio de oferta (opcional). Si viene, las fechas se calculan solas acá
    // en el backend: hoy → hoy + 3 meses, el usuario no las toca.
    private Double specialPrice;

    // Descripción editable — si viene, pisa la de Laudus (webDescription/description).
    private String description;

    // Título/Name editable — si viene, pisa el de Laudus (product.description).
    private String name;

    // Resto de atributos obligatorios que exige la categoría elegida (varían
    // por categoría) — clave = FeedName de GetCategoryAttributes (ej. "Diametro",
    // "PlazoDeDisponibilidadDeRepuestos"), tal cual se insertan en <ProductData>.
    private Map<String, String> extraAttributes;
}
