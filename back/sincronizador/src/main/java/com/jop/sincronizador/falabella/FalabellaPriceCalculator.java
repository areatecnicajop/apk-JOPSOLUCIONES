package com.jop.sincronizador.falabella;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

// Precio de venta en Falabella calculado con las reglas REALES del marketplace (Chile):
//
//   Neto que recibes = Precio − comisión (% de la categoría sobre el precio) − despacho
//
//   • Comisión: tabla oficial por categoría (falabella-comisiones.csv, "Comisiones falabella.com CL",
//     versión 20260720). Se cobra sobre el precio de venta (IVA incluido).
//   • Despacho: cofinanciamiento logístico por unidad vendida, según la talla logística y el rango de
//     precio del producto. Talla = MAX(peso físico, largo×ancho×alto ÷ 4.000), en kg. Las tarifas incluyen
//     IVA y corresponden al segmento f.plus+ de 4 estrellas (el que aplica mientras no se tiene segmento).
//     Fuente: ayudaseller.falabella.com → "Tarifa de cofinanciamiento logístico (despacho)".
//
// El precio sugerido es el MENOR (redondeado al centenar) con el que, después de comisión y despacho,
// recibes al menos lo mismo que vendiendo ese producto en tu web (precio de Laudus) más el margen extra
// configurado (falabella.pricing.margen-extra, 0 por defecto).
@Component
public class FalabellaPriceCalculator {

    // Categoría que se asume cuando el formulario aún no eligió una (la que usan todos los productos hoy).
    public static final String CATEGORIA_POR_DEFECTO =
        "Construcción y ferretería > Herramientas/equipos eléctricos > Accesorios/repuestos para herramientas eléctricas > Accesorios de maquinarias compresoras";

    // Comisión que se asume si la categoría no está en la tabla (la más frecuente de la tabla).
    private static final double COMISION_SIN_DATOS = 15.0;

    // Tramos de talla logística (kg, límite superior inclusivo: si pesa justo 3,0 kg se aplica el tramo inferior).
    private static final double[] TRAMOS_KG = {1, 2, 3, 6, 10, 15, 20, 30, 50, 80, 100, 125, 150, 175, 200,
        225, 250, 275, 300, 325, 350, 375, 400};

    // Tarifas de despacho (CLP con IVA, segmento 4 estrellas), un valor por tramo de TRAMOS_KG.
    // Rangos de precio de venta: [0] menor a 9.990 · [1] entre 9.990 y 19.990 · [2] más de 19.990.
    private static final long[][] DESPACHO_4_ESTRELLAS = {
        {1290, 1290, 1290, 1690, 3590, 5990, 7190, 8390, 9590, 10790, 11990, 13190, 13190, 15590, 19190,
            22790, 22790, 27590, 27590, 29990, 33590, 33590, 33590},
        {1490, 1490, 1490, 2990, 4190, 5990, 7190, 8390, 9590, 10790, 11990, 13190, 13190, 15590, 19190,
            22790, 22790, 27590, 27590, 29990, 33590, 33590, 33590},
        {3890, 4190, 4790, 5190, 5390, 5990, 7190, 8390, 9590, 10790, 11990, 13190, 13190, 15590, 19190,
            22790, 22790, 27590, 27590, 29990, 33590, 33590, 33590}
    };

    @Value("${falabella.pricing.margen-extra:0.0}")
    private double margenExtra;

    private final Map<String, Double> comisionPorRuta = new HashMap<>();
    private final Map<String, List<Double>> comisionPorHoja = new HashMap<>();

    public record Resultado(long precioSugerido, double comisionPct, long comision, long despacho,
                            double tallaKg, String tallaTramo, long neto, long objetivoNeto,
                            String categoriaUsada, List<String> avisos) {}

    @PostConstruct
    void cargarComisiones() {
        try (InputStream in = getClass().getResourceAsStream("/falabella-comisiones.csv")) {
            if (in == null) return;
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                int i = line.lastIndexOf(';');
                if (i <= 0) continue;
                try {
                    double pct = Double.parseDouble(line.substring(i + 1).trim());
                    String ruta = normalizar(line.substring(0, i));
                    comisionPorRuta.put(ruta, pct);
                    String hoja = ruta.substring(ruta.lastIndexOf(" > ") + 3);
                    comisionPorHoja.computeIfAbsent(hoja, k -> new ArrayList<>()).add(pct);
                } catch (NumberFormatException ignored) { }
            }
        } catch (Exception e) {
            System.out.println("⚠️ No se pudo cargar falabella-comisiones.csv: " + e.getMessage());
        }
    }

    // El árbol de categorías del Seller Center usa «|» donde la tabla de comisiones usa «/».
    private static String normalizar(String s) {
        return s == null ? "" : s.replace('|', '/').replaceAll("\\s+", " ").trim().toLowerCase();
    }

    // Comisión (%) de una categoría por su ruta completa; si no calza exacto, por su última parte
    // (solo si es inequívoca). Devuelve null si no se encuentra.
    public Double comisionDe(String categoriaPath) {
        String ruta = normalizar(categoriaPath);
        Double exacta = comisionPorRuta.get(ruta);
        if (exacta != null) return exacta;
        String hoja = ruta.contains(" > ") ? ruta.substring(ruta.lastIndexOf(" > ") + 3) : ruta;
        List<Double> l = comisionPorHoja.get(hoja);
        if (l != null && l.stream().distinct().count() == 1) return l.get(0);
        return null;
    }

    private static int rangoDePrecio(long precio) {
        if (precio < 9990) return 0;
        if (precio <= 19990) return 1;
        return 2;
    }

    private static int tramoDe(double kg) {
        for (int i = 0; i < TRAMOS_KG.length; i++) if (kg <= TRAMOS_KG[i]) return i;
        return TRAMOS_KG.length - 1;
    }

    private static String nombreTramo(int i) {
        double lo = i == 0 ? 0 : TRAMOS_KG[i - 1];
        return String.format("%s–%s kg", fmt(lo), fmt(TRAMOS_KG[i]));
    }

    private static String fmt(double d) { return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d); }

    // alto/ancho/largo en cm y peso en kg del paquete; cualquiera puede venir null.
    public Resultado calcular(long precioBase, String categoriaPath, Double alto, Double ancho, Double largo, Double peso) {
        List<String> avisos = new ArrayList<>();

        String categoria = categoriaPath != null && !categoriaPath.isBlank() ? categoriaPath : CATEGORIA_POR_DEFECTO;
        if (categoriaPath == null || categoriaPath.isBlank()) {
            avisos.add("Sin categoría elegida: se calculó con «Accesorios de maquinarias compresoras».");
        }
        Double pct = comisionDe(categoria);
        if (pct == null) {
            pct = COMISION_SIN_DATOS;
            avisos.add("No se encontró la comisión de esta categoría en la tabla: se asumió " + fmt(COMISION_SIN_DATOS) + "%.");
        }

        // Talla logística = el mayor entre peso físico y peso volumétrico.
        double fisico = peso != null && peso > 0 ? peso : 0;
        double volumetrico = (alto != null && ancho != null && largo != null && alto > 0 && ancho > 0 && largo > 0)
            ? alto * ancho * largo / 4000.0 : 0;
        double talla = Math.max(fisico, volumetrico);
        if (talla <= 0) {
            talla = 1;
            avisos.add("Sin peso ni medidas del paquete: se asumió talla 0–1 kg. Completa el paquete para un precio exacto.");
        } else if (fisico == 0 || volumetrico == 0) {
            avisos.add("Faltan " + (fisico == 0 ? "el peso" : "las medidas") + " del paquete: el despacho puede estar subestimado.");
        }
        if (talla > TRAMOS_KG[TRAMOS_KG.length - 1]) {
            avisos.add("Talla sobre 400 kg: la tarifa real se calcula por kilo adicional y no está incluida aquí.");
        }
        int tramo = tramoDe(talla);

        double comision = pct / 100.0;
        long objetivo = Math.round(precioBase * (1.0 + margenExtra));
        if (precioBase <= 0) {
            avisos.add("El producto no tiene precio en Laudus.");
            return new Resultado(0, pct, 0, 0, talla, nombreTramo(tramo), 0, 0, categoria, avisos);
        }

        // Menor precio (múltiplo de 100) cuyo neto cubre el objetivo. El despacho depende del rango de precio
        // y baja/sube al cruzar 9.990 y 19.990, por eso se busca hacia arriba comprobando el neto real.
        long minEnvio = Math.min(DESPACHO_4_ESTRELLAS[0][tramo], Math.min(DESPACHO_4_ESTRELLAS[1][tramo], DESPACHO_4_ESTRELLAS[2][tramo]));
        long precio = (long) (Math.ceil((objetivo + minEnvio) / (1.0 - comision) / 100.0) * 100);
        for (int i = 0; i < 100000; i++) {
            long envio = DESPACHO_4_ESTRELLAS[rangoDePrecio(precio)][tramo];
            if (precio * (1.0 - comision) - envio >= objetivo) break;
            precio += 100;
        }

        long despacho = DESPACHO_4_ESTRELLAS[rangoDePrecio(precio)][tramo];
        long com = Math.round(precio * comision);
        return new Resultado(precio, pct, com, despacho, talla, nombreTramo(tramo), precio - com - despacho, objetivo, categoria, avisos);
    }
}
