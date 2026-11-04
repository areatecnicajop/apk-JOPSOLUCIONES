package com.jop.sincronizador.util;

import com.jop.sincronizador.models.entity.dto.CatalogProductExtended;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Utilidad para mapear campos custom desde meta_data de WooCommerce
 * a CatalogProductExtended de forma configurable y mantenible.
 */
public class CustomFieldMapper {
    
    private static final Map<String, BiConsumer<CatalogProductExtended, String>> FIELD_MAPPERS = new HashMap<>();
    
    static {
        initializeMappers();
    }
    
    /**
     * Inicializa el mapa de mappers para todos los campos custom.
     * Permite múltiples variantes de nombres para el mismo campo.
     */
    private static void initializeMappers() {
        // Medidas
        registerField("largo", CatalogProductExtended::setLargo);
        registerField("diametro", CatalogProductExtended::setDiametro);
        registerField("diametrorosc", CatalogProductExtended::setDiametroRosc);
        registerField("diametro_rosc", CatalogProductExtended::setDiametroRosc);
        registerField("diametroexterno", CatalogProductExtended::setDiametroExterno);
        registerField("diametro_externo", CatalogProductExtended::setDiametroExterno);
        registerField("diametrocentro", CatalogProductExtended::setDiametroCentro);
        registerField("diametro_centro", CatalogProductExtended::setDiametroCentro);
        registerField("diametropiston", CatalogProductExtended::setDiametroPiston);
        registerField("diametro_piston", CatalogProductExtended::setDiametroPiston);
        registerField("diametroeje", CatalogProductExtended::setDiametroEje);
        registerField("diametro_eje", CatalogProductExtended::setDiametroEje);
        registerField("medidas", CatalogProductExtended::setMedidas);
        registerField("medidasbase", CatalogProductExtended::setMedidasBase);
        registerField("medidas_base", CatalogProductExtended::setMedidasBase);
        registerField("medidaeje", CatalogProductExtended::setMedidaEje);
        registerField("medida_eje", CatalogProductExtended::setMedidaEje);
        registerField("medidavias", CatalogProductExtended::setMedidaVias);
        registerField("medida_vias", CatalogProductExtended::setMedidaVias);
        registerField("medidaaccesorios", CatalogProductExtended::setMedidaAccesorios);
        registerField("medida_accesorios", CatalogProductExtended::setMedidaAccesorios);
        registerField("medidacondensadores", CatalogProductExtended::setMedidaCondensadores);
        registerField("medida_condensadores", CatalogProductExtended::setMedidaCondensadores);
        registerField("grosor", CatalogProductExtended::setGrosor);
        registerField("profundidad", CatalogProductExtended::setProfundidad);
        
        // Motor
        registerField("hp", CatalogProductExtended::setHp);
        registerField("rpm", CatalogProductExtended::setRpm);
        registerField("corriente", CatalogProductExtended::setCorriente);
        registerField("amperes", CatalogProductExtended::setAmperes);
        registerField("voltaje", CatalogProductExtended::setVoltaje);
        registerField("motor", CatalogProductExtended::setMotor);
        registerField("cabezal", CatalogProductExtended::setCabezal);
        registerField("etapas", CatalogProductExtended::setEtapas);
        
        // Presión y Caudal
        registerField("presion", CatalogProductExtended::setPresion);
        registerField("presión", CatalogProductExtended::setPresion);
        registerField("caudal", CatalogProductExtended::setCaudal);
        registerField("aspiracion", CatalogProductExtended::setAspiracion);
        registerField("aspiración", CatalogProductExtended::setAspiracion);
        
        // Componentes
        registerField("nvias", CatalogProductExtended::setNVias);
        registerField("n_vias", CatalogProductExtended::setNVias);
        registerField("n° vías", CatalogProductExtended::setNVias);
        registerField("npistones", CatalogProductExtended::setNPistones);
        registerField("n_pistones", CatalogProductExtended::setNPistones);
        registerField("n° pistones", CatalogProductExtended::setNPistones);
        registerField("cantidadtuercas", CatalogProductExtended::setCantidadTuercas);
        registerField("cantidad_tuercas", CatalogProductExtended::setCantidadTuercas);
        registerField("elementofiltrante", CatalogProductExtended::setElementoFiltrante);
        registerField("elemento_filtrante", CatalogProductExtended::setElementoFiltrante);
        registerField("disipiadordecalor", CatalogProductExtended::setDisipiadorCalor);
        registerField("disipador_calor", CatalogProductExtended::setDisipiadorCalor);
        registerField("accesorios", CatalogProductExtended::setAccesorios);
        
        // Roscas
        registerField("tiporosc", CatalogProductExtended::setTipoRosca);
        registerField("tipo_rosc", CatalogProductExtended::setTipoRosca);
        registerField("tipo rosca", CatalogProductExtended::setTipoRosca);
        registerField("roscaentrada", CatalogProductExtended::setRoscaEntrada);
        registerField("rosca_entrada", CatalogProductExtended::setRoscaEntrada);
        registerField("ubicacionrosc", CatalogProductExtended::setUbicacionRosc);
        registerField("ubicacion_rosc", CatalogProductExtended::setUbicacionRosc);
        registerField("ubicación rosc", CatalogProductExtended::setUbicacionRosc);
        
        // Otros
        registerField("tipo", CatalogProductExtended::setTipo);
        registerField("material", CatalogProductExtended::setMaterial);
        registerField("estanque", CatalogProductExtended::setEstanque);
        registerField("updateimage", CatalogProductExtended::setUpdateImage);
        registerField("update_image", CatalogProductExtended::setUpdateImage);
    }
    
    /**
     * Registra un campo y su setter correspondiente.
     * 
     * @param fieldName Nombre del campo (se normaliza a lowercase)
     * @param setter BiConsumer que aplica el valor al producto
     */
    private static void registerField(String fieldName, BiConsumer<CatalogProductExtended, String> setter) {
        FIELD_MAPPERS.put(fieldName.toLowerCase(), setter);
    }
    
    /**
     * Mapea un campo custom desde meta_data a CatalogProductExtended.
     * 
     * @param product Producto al que se aplicará el campo
     * @param key Clave del campo en meta_data
     * @param value Valor del campo
     * @return true si el campo fue mapeado exitosamente, false en caso contrario
     */
    public static boolean mapCustomField(CatalogProductExtended product, String key, String value) {
        if (key == null || value == null || product == null) {
            return false;
        }
        
        String normalizedKey = key.toLowerCase();
        BiConsumer<CatalogProductExtended, String> mapper = FIELD_MAPPERS.get(normalizedKey);
        
        if (mapper != null) {
            mapper.accept(product, value);
            return true;
        }
        
        return false;
    }
    
    /**
     * Valida si un valor es válido para ser asignado a un campo custom.
     * Filtra valores vacíos, "0", "null", "undefined", etc.
     * 
     * @param value Valor a validar
     * @return true si el valor es válido, false en caso contrario
     */
    public static boolean isValidValue(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        String trimmed = value.trim();
        return !trimmed.equals("0") 
            && !trimmed.equalsIgnoreCase("null") 
            && !trimmed.equalsIgnoreCase("undefined")
            && !trimmed.equalsIgnoreCase("false");
    }
}



