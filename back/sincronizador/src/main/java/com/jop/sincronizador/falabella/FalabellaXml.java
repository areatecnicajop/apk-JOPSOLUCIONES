package com.jop.sincronizador.falabella;

// Escapa texto para insertarlo en nodos XML que no van envueltos en CDATA.
public class FalabellaXml {
    public static String escape(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
