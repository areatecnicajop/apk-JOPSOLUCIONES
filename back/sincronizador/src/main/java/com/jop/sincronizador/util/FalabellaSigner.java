package com.jop.sincronizador.util;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Firma HMAC-SHA256 de requests a la API de Falabella Seller Center.
// Referencia: https://developers.falabella.com/v500/reference/signing-requests
public class FalabellaSigner {

    private static final String HMAC_ALGO = "HmacSHA256";

    public static String buildSignedQuery(String action, String userId, String apiKey, String version,
                                           Map<String, String> extraParams) {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("Action", action);
        params.put("Format", "JSON");
        params.put("Timestamp", OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        params.put("UserID", userId);
        params.put("Version", version);
        if (extraParams != null) {
            params.putAll(extraParams);
        }

        StringBuilder toSign = new StringBuilder();
        StringBuilder queryString = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String encodedKey = urlEncode(entry.getKey());
            String encodedValue = urlEncode(entry.getValue());
            if (toSign.length() > 0) {
                toSign.append('&');
                queryString.append('&');
            }
            toSign.append(encodedKey).append('=').append(encodedValue);
            queryString.append(encodedKey).append('=').append(encodedValue);
        }

        String signature = hmacSha256Hex(toSign.toString(), apiKey);
        queryString.append("&Signature=").append(signature);
        return queryString.toString();
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.US_ASCII));
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la request a Falabella: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
