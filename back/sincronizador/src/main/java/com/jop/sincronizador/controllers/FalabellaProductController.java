package com.jop.sincronizador.controllers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.LaudusStock;
import com.jop.sincronizador.data.local.model.services.IProductBDService;
import com.jop.sincronizador.falabella.FalabellaClient;
import com.jop.sincronizador.falabella.FalabellaProductUploadRequest;
import com.jop.sincronizador.falabella.FalabellaXml;
import com.jop.sincronizador.repository.PssWriteRepositoryStock;

// Flujo de subida de productos al marketplace de Falabella — pestaña
// "Falabella Marketplace > Productos". Reutiliza los mismos datos de
// laudus_products que ya usa el sync a WooCommerce (sku, nombre, precio),
// pero pide a mano lo que Laudus no tiene: categoría/marca de Falabella y
// dimensiones del paquete (Falabella los exige, WooCommerce no).
@RestController
public class FalabellaProductController {

    private static final String OPERATOR_CODE = "facl"; // Falabella Chile

    @Autowired
    private FalabellaClient falabella;

    @Autowired
    private IProductBDService bd;

    @Autowired
    private PssWriteRepositoryStock stockRepository;

    @Autowired
    private ApplicationContext context;

    private final Gson gson = new Gson();

    private static final int SKU_BATCH_SIZE = 100;

    // ── Listado con estado (verde/rojo) ───────────────────────────
    // El verde/rojo se decide consultando GetProducts contra Falabella —
    // no basta con nuestro propio flag local, porque un producto puede haber
    // sido subido por otra vía y nosotros no enterarnos. La columna
    // id_falabella queda solo como dato secundario, no como fuente de verdad.

    @GetMapping("/falabella/productos")
    public ResponseEntity<List<Map<String, Object>>> listProducts() {
        List<LaudusProduct> products = bd.findAll();
        List<Categorie> categories = bd.findAllCategorie();
        Map<String, Categorie> categoryMap = categories.stream()
            .collect(Collectors.toMap(Categorie::getLaudusId, c -> c, (a, b) -> a));
        Map<String, Integer> stockMap = stockRepository.findAll().stream()
            .collect(Collectors.toMap(LaudusStock::getSku, LaudusStock::getStock, (a, b) -> a));

        List<String> skus = products.stream()
            .map(LaudusProduct::getSku)
            .filter(sku -> sku != null && !sku.isBlank())
            .collect(Collectors.toList());
        Set<String> existingInFalabella = falabella.credentialsConfigured()
            ? fetchExistingFalabellaSkus(skus) : Set.of();

        List<Map<String, Object>> result = products.stream()
            .filter(p -> p.getSku() != null && !p.getSku().isBlank())
            .map(p -> {
                Map<String, Object> item = new HashMap<>();
                item.put("sku", p.getSku());
                item.put("name", p.getDescription());
                item.put("price", p.getUnitPriceWithTaxes());
                item.put("stock", stockMap.getOrDefault(p.getSku(), 0));
                Categorie cat = p.getProductCategoryId() != null ? categoryMap.get(p.getProductCategoryId()) : null;
                item.put("categoryName", cat != null ? cat.getLaudusName() : null);
                item.put("idWP", p.getIdWP());
                item.put("enFalabella", existingInFalabella.contains(p.getSku().trim().toUpperCase()));
                return item;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // Consulta GetProducts filtrado por SkuSellerList, en tandas, y devuelve
    // el set de SellerSku que Falabella realmente tiene (normalizado a mayúsculas
    // para no fallar por diferencias de casing entre Laudus y Falabella).
    private Set<String> fetchExistingFalabellaSkus(List<String> skus) {
        Set<String> found = new HashSet<>();
        for (int i = 0; i < skus.size(); i += SKU_BATCH_SIZE) {
            List<String> batch = skus.subList(i, Math.min(i + SKU_BATCH_SIZE, skus.size()));
            FalabellaClient.FalabellaResponse res = falabella.get("GetProducts",
                Map.of("SkuSellerList", gson.toJson(batch), "Limit", "1000"));
            if (res.statusCode() != 200) continue;
            try {
                JsonObject root = gson.fromJson(res.body(), JsonObject.class);
                JsonObject success = root.getAsJsonObject("SuccessResponse");
                if (success == null) continue;
                JsonObject body = success.getAsJsonObject("Body");
                JsonElement productsEl = body != null ? body.get("Products") : null;
                if (productsEl == null || !productsEl.isJsonObject()) continue;
                JsonElement productEl = productsEl.getAsJsonObject().get("Product");
                if (productEl == null || productEl.isJsonNull()) continue;

                List<JsonObject> productList = new ArrayList<>();
                if (productEl.isJsonArray()) {
                    for (JsonElement el : productEl.getAsJsonArray()) productList.add(el.getAsJsonObject());
                } else if (productEl.isJsonObject()) {
                    productList.add(productEl.getAsJsonObject());
                }

                for (JsonObject p : productList) {
                    JsonElement sellerSku = p.get("SellerSku");
                    if (sellerSku != null && sellerSku.isJsonPrimitive()) {
                        found.add(sellerSku.getAsString().trim().toUpperCase());
                    }
                }
            } catch (Exception ignored) {
                // Si la respuesta no viene con la forma esperada, se omite esa tanda.
            }
        }
        return found;
    }

    // ── Proxies de solo lectura (mismo cliente firmado que /test/falabella) ──

    @GetMapping("/falabella/categorias")
    public ResponseEntity<String> categoryTree() {
        FalabellaClient.FalabellaResponse res = falabella.get("GetCategoryTree", null);
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }

    @GetMapping("/falabella/categorias/atributos")
    public ResponseEntity<String> categoryAttributes(@RequestParam String primaryCategory) {
        FalabellaClient.FalabellaResponse res = falabella.get("GetCategoryAttributes",
            Map.of("PrimaryCategory", primaryCategory));
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }

    @GetMapping("/falabella/marcas")
    public ResponseEntity<String> brands() {
        FalabellaClient.FalabellaResponse res = falabella.get("GetBrands", null);
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }

    // ── Consultar el estado de un feed (ProductCreate/Image son async) ───

    @GetMapping("/falabella/feeds/{feedId}")
    public ResponseEntity<String> feedStatus(@PathVariable String feedId) {
        FalabellaClient.FalabellaResponse res = falabella.get("FeedStatus", Map.of("FeedID", feedId));
        return ResponseEntity.status(res.statusCode()).body(res.body());
    }

    // Estado real del producto ya publicado (o no) en Falabella — QCStatus,
    // ContentScore e imágenes efectivamente asociadas. El feed puede terminar
    // "Finished" sin errores y aun así el producto quedar rechazado en la
    // revisión de contenido, así que esto es lo único que confirma de verdad
    // si quedó arriba.
    @GetMapping("/falabella/productos/{sku}/estado")
    public ResponseEntity<Map<String, Object>> productStatus(@PathVariable String sku) {
        FalabellaClient.FalabellaResponse res = falabella.get("GetProducts",
            Map.of("SkuSellerList", gson.toJson(List.of(sku))));
        if (res.statusCode() != 200) {
            return ResponseEntity.status(res.statusCode()).body(Map.of("error", res.body()));
        }
        try {
            JsonObject root = gson.fromJson(res.body(), JsonObject.class);
            JsonObject body = root.getAsJsonObject("SuccessResponse").getAsJsonObject("Body");
            JsonElement productsEl = body.get("Products");
            JsonElement productEl = productsEl != null && productsEl.isJsonObject() ? productsEl.getAsJsonObject().get("Product") : null;
            if (productEl == null || productEl.isJsonNull()) {
                return ResponseEntity.ok(Map.of("existe", false));
            }
            JsonObject p = productEl.isJsonArray() ? productEl.getAsJsonArray().get(0).getAsJsonObject() : productEl.getAsJsonObject();

            Map<String, Object> result = new HashMap<>();
            result.put("existe", true);
            result.put("qcStatus", getOrNull(p, "QCStatus"));
            result.put("contentScore", getOrNull(p, "ContentScore"));
            result.put("mainImage", getOrNull(p, "MainImage"));
            JsonElement imagesEl = p.get("Images");
            List<String> images = new ArrayList<>();
            if (imagesEl != null && imagesEl.isJsonObject()) {
                JsonElement imgArr = imagesEl.getAsJsonObject().get("Image");
                if (imgArr != null) {
                    if (imgArr.isJsonArray()) for (JsonElement i : imgArr.getAsJsonArray()) images.add(i.getAsString());
                    else images.add(imgArr.getAsString());
                }
            }
            result.put("images", images);
            JsonObject bu = p.getAsJsonObject("BusinessUnits") != null ? p.getAsJsonObject("BusinessUnits").getAsJsonObject("BusinessUnit") : null;
            result.put("isPublished", bu != null && getOrNull(bu, "IsPublished") != null && bu.get("IsPublished").getAsString().equals("1"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private String getOrNull(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && !el.isJsonNull() ? el.getAsString() : null;
    }

    // Datos reales tal cual están en Falabella ahora mismo — para precargar el
    // formulario de edición con lo que de verdad hay allá (no con lo sugerido
    // desde Laudus) cuando el producto ya existe.
    @GetMapping("/falabella/productos/{sku}/datos-falabella")
    public ResponseEntity<Map<String, Object>> datosFalabella(@PathVariable String sku) {
        FalabellaClient.FalabellaResponse res = falabella.get("GetProducts",
            Map.of("SkuSellerList", gson.toJson(List.of(sku))));
        if (res.statusCode() != 200) {
            return ResponseEntity.status(res.statusCode()).body(Map.of("error", res.body()));
        }
        try {
            JsonObject root = gson.fromJson(res.body(), JsonObject.class);
            JsonObject body = root.getAsJsonObject("SuccessResponse").getAsJsonObject("Body");
            JsonElement productsEl = body.get("Products");
            JsonElement productEl = productsEl != null && productsEl.isJsonObject() ? productsEl.getAsJsonObject().get("Product") : null;
            if (productEl == null || productEl.isJsonNull()) {
                return ResponseEntity.ok(Map.of("existe", false));
            }
            JsonObject p = productEl.isJsonArray() ? productEl.getAsJsonArray().get(0).getAsJsonObject() : productEl.getAsJsonObject();

            Map<String, Object> result = new HashMap<>();
            result.put("existe", true);
            result.put("name", getOrNull(p, "Name"));
            result.put("primaryCategoryId", getOrNull(p, "PrimaryCategoryId"));
            result.put("brand", getOrNull(p, "Brand"));
            result.put("description", getOrNull(p, "Description"));

            JsonObject bu = p.getAsJsonObject("BusinessUnits") != null ? p.getAsJsonObject("BusinessUnits").getAsJsonObject("BusinessUnit") : null;
            result.put("price", bu != null ? getOrNull(bu, "Price") : null);
            result.put("specialPrice", bu != null ? getOrNull(bu, "SpecialPrice") : null);

            // ProductData trae de todo (ConditionType, PackageHeight/Width/Length/Weight,
            // Ancho, Largo, Diametro, Plazo..., Duracion..., etc.) — se devuelve tal cual
            // como mapa genérico, el front saca lo que necesita por FeedName.
            Map<String, String> productData = new HashMap<>();
            JsonObject pd = p.getAsJsonObject("ProductData");
            if (pd != null) {
                for (String key : pd.keySet()) {
                    JsonElement v = pd.get(key);
                    if (v != null && v.isJsonPrimitive()) productData.put(key, v.getAsString());
                }
            }
            result.put("productData", productData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // Precio sugerido para cubrir la comisión de Falabella: precio de Laudus
    // + 30% mínimo, redondeado hacia arriba al centenar más cercano.
    @GetMapping("/falabella/productos/{sku}/precio-sugerido")
    public ResponseEntity<Map<String, Object>> precioSugerido(@PathVariable String sku) {
        List<LaudusProduct> matches = bd.findBySkuIn(List.of(sku));
        if (matches.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "SKU no encontrado en laudus_products"));
        }
        Integer precioLaudus = matches.get(0).getUnitPriceWithTaxes();
        if (precioLaudus == null) precioLaudus = 0;
        long sugerido = (long) Math.ceil(precioLaudus * 1.30 / 100.0) * 100;
        Map<String, Object> result = new HashMap<>();
        result.put("precioLaudus", precioLaudus);
        result.put("precioSugerido", sugerido);
        return ResponseEntity.ok(result);
    }

    // Descripción actual (la que se manda como <Description>) para precargarla
    // editable en el formulario, en vez de mandarla a ciegas desde Laudus.
    @GetMapping("/falabella/productos/{sku}/descripcion")
    public ResponseEntity<Map<String, Object>> descripcionActual(@PathVariable String sku) {
        List<LaudusProduct> matches = bd.findBySkuIn(List.of(sku));
        if (matches.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "SKU no encontrado en laudus_products"));
        }
        LaudusProduct p = matches.get(0);
        String descripcion = p.getWebDescription() != null && !p.getWebDescription().isBlank()
            ? p.getWebDescription() : p.getDescription();
        return ResponseEntity.ok(Map.of("descripcion", descripcion != null ? descripcion : ""));
    }

    // ── Subir el producto (ProductCreate) ─────────────────────────

    @PostMapping("/falabella/productos/{sku}/subir")
    public ResponseEntity<String> uploadProduct(@PathVariable String sku,
                                                 @RequestBody FalabellaProductUploadRequest req) {
        CreateOrUpdateResult result = sendProductCreateOrUpdate(sku, req);
        if (result.response() == null) {
            return ResponseEntity.status(404).body("{\"error\":\"SKU no encontrado en laudus_products\"}");
        }
        return ResponseEntity.status(result.response().statusCode())
            .header("Content-Type", "application/json")
            .body(result.response().body());
    }

    // Encadena ProductCreate + Image en una sola llamada bloqueante: crea el
    // producto, espera a que ese feed termine, y recién ahí intenta las
    // imágenes (con su propio reintento/verificación). Puede tardar hasta ~1
    // minuto — el front lo cubre con un solo loading.
    @PostMapping("/falabella/productos/{sku}/subir-completo")
    public ResponseEntity<Map<String, Object>> uploadProductComplete(@PathVariable String sku,
                                                                      @RequestBody FalabellaProductUploadRequest req) {
        CreateOrUpdateResult createResult = sendProductCreateOrUpdate(sku, req);
        FalabellaClient.FalabellaResponse productRes = createResult.response();
        if (productRes == null) {
            return ResponseEntity.status(404).body(Map.of("error", "SKU no encontrado en laudus_products"));
        }

        String productFeedId = extractRequestId(productRes.body());
        String productFeedStatus = productFeedId != null ? waitForFeedFinish(productFeedId, 15000) : null;

        Map<String, Object> producto = new HashMap<>();
        producto.put("statusCode", productRes.statusCode());
        producto.put("body", productRes.body());
        producto.put("feedId", productFeedId);
        producto.put("feedStatus", productFeedStatus);
        producto.put("action", createResult.action());

        Map<String, Object> result = new HashMap<>();
        result.put("producto", producto);

        // Si el feed de creación terminó en error, no tiene sentido intentar
        // las imágenes — el producto no llegó a existir.
        if ("Error".equals(productFeedStatus) || "Canceled".equals(productFeedStatus)) {
            result.put("imagenes", Map.of("asociadas", false, "error", "No se intentaron: el producto no se creó correctamente."));
            return ResponseEntity.ok(result);
        }

        Map<String, Object> imagenes = tryUploadImages(sku);
        imagenes.remove("httpStatus");
        result.put("imagenes", imagenes);
        return ResponseEntity.ok(result);
    }

    // Campos comunes a ProductCreate y ProductUpdate — se calculan una sola
    // vez y cada builder arma el XML con la forma que le corresponde.
    private record ProductFields(LaudusProduct product, Integer stock, String price, String name, String description,
                                  String extraTags, String specialPriceTags) {}

    private ProductFields computeProductFields(String sku, FalabellaProductUploadRequest req) {
        List<LaudusProduct> matches = bd.findBySkuIn(List.of(sku));
        if (matches.isEmpty()) return null;

        LaudusProduct product = matches.get(0);
        Integer stock = stockRepository.findAll().stream()
            .filter(s -> sku.equals(s.getSku()))
            .map(LaudusStock::getStock)
            .findFirst().orElse(0);

        // Precio: si el front no manda uno editado, se usa el de Laudus tal
        // cual (fallback de seguridad — en la práctica el front siempre manda
        // el sugerido +30% redondeado, editable por el usuario antes de subir).
        String price = req.getPrice() != null
            ? String.valueOf(req.getPrice().longValue())
            : (product.getUnitPriceWithTaxes() != null ? String.valueOf(product.getUnitPriceWithTaxes()) : "0");

        String description = req.getDescription() != null && !req.getDescription().isBlank()
            ? req.getDescription()
            : (product.getWebDescription() != null && !product.getWebDescription().isBlank()
                ? product.getWebDescription() : product.getDescription());

        String name = req.getName() != null && !req.getName().isBlank()
            ? req.getName() : product.getDescription();

        StringBuilder extraTags = new StringBuilder();
        if (req.getExtraAttributes() != null) {
            for (Map.Entry<String, String> attr : req.getExtraAttributes().entrySet()) {
                if (attr.getValue() == null || attr.getValue().isBlank()) continue;
                extraTags.append('<').append(attr.getKey()).append('>')
                    .append(FalabellaXml.escape(attr.getValue()))
                    .append("</").append(attr.getKey()).append('>');
            }
        }
        if (req.getAncho() != null && !req.getAncho().isBlank()) {
            extraTags.append("<Ancho>").append(FalabellaXml.escape(req.getAncho())).append("</Ancho>");
        }
        if (req.getLargo() != null && !req.getLargo().isBlank()) {
            extraTags.append("<Largo>").append(FalabellaXml.escape(req.getLargo())).append("</Largo>");
        }

        // Precio de oferta: si viene, las fechas se calculan solas — hoy hasta
        // hoy + 3 meses — el usuario nunca elige fechas a mano.
        StringBuilder specialPriceTags = new StringBuilder();
        if (req.getSpecialPrice() != null) {
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime desde = LocalDateTime.now();
            LocalDateTime hasta = desde.plusMonths(3);
            specialPriceTags.append("<SpecialPrice>").append(req.getSpecialPrice().longValue()).append("</SpecialPrice>")
                .append("<SpecialFromDate>").append(desde.format(fmt)).append("</SpecialFromDate>")
                .append("<SpecialToDate>").append(hasta.format(fmt)).append("</SpecialToDate>");
        }

        return new ProductFields(product, stock, price, name, description, extraTags.toString(), specialPriceTags.toString());
    }

    // Decide Create vs Update según si el SKU ya existe en Falabella (GetProducts),
    // y devuelve además cuál se usó para poder mostrarlo en el front.
    private record CreateOrUpdateResult(FalabellaClient.FalabellaResponse response, String action) {}

    private CreateOrUpdateResult sendProductCreateOrUpdate(String sku, FalabellaProductUploadRequest req) {
        boolean exists = fetchExistingFalabellaSkus(List.of(sku)).contains(sku.trim().toUpperCase());
        if (exists) {
            return new CreateOrUpdateResult(sendProductUpdate(sku, req), "ProductUpdate");
        }
        return new CreateOrUpdateResult(sendProductCreate(sku, req), "ProductCreate");
    }

    private FalabellaClient.FalabellaResponse sendProductCreate(String sku, FalabellaProductUploadRequest req) {
        ProductFields f = computeProductFields(sku, req);
        if (f == null) return null;

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Request><Product>"
            + "<SellerSku>" + FalabellaXml.escape(sku) + "</SellerSku>"
            // ParentSku = mismo SellerSku y Variation="..." para productos sin
            // variaciones — visto en productos reales ya publicados en esta cuenta.
            + "<ParentSku>" + FalabellaXml.escape(sku) + "</ParentSku>"
            + "<Variation>...</Variation>"
            + "<Name>" + FalabellaXml.escape(f.name()) + "</Name>"
            + "<PrimaryCategory>" + FalabellaXml.escape(req.getPrimaryCategory()) + "</PrimaryCategory>"
            + "<Description><![CDATA[" + (f.description() != null ? f.description() : "") + "]]></Description>"
            + "<Brand>" + FalabellaXml.escape(req.getBrand()) + "</Brand>"
            + "<BusinessUnits><BusinessUnit>"
            + "<OperatorCode>" + OPERATOR_CODE + "</OperatorCode>"
            + "<Price>" + f.price() + "</Price>"
            + f.specialPriceTags()
            + "<Stock>" + f.stock() + "</Stock>"
            + "<Status>active</Status>"
            + "</BusinessUnit></BusinessUnits>"
            + "<ProductData>"
            + "<ConditionType>" + FalabellaXml.escape(req.getConditionType()) + "</ConditionType>"
            + "<PackageHeight>" + req.getPackageHeight() + "</PackageHeight>"
            + "<PackageWidth>" + req.getPackageWidth() + "</PackageWidth>"
            + "<PackageLength>" + req.getPackageLength() + "</PackageLength>"
            + "<PackageWeight>" + req.getPackageWeight() + "</PackageWeight>"
            + f.extraTags()
            + "</ProductData>"
            + "</Product></Request>";

        return falabella.postXml("ProductCreate", null, xml);
    }

    // ProductUpdate es más simple que Create: sin ParentSku/Variation (según
    // doc oficial, esos solo aplican a Create) — solo SellerSku + lo que cambie.
    private FalabellaClient.FalabellaResponse sendProductUpdate(String sku, FalabellaProductUploadRequest req) {
        ProductFields f = computeProductFields(sku, req);
        if (f == null) return null;

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Request><Product>"
            + "<SellerSku>" + FalabellaXml.escape(sku) + "</SellerSku>"
            + "<Name>" + FalabellaXml.escape(f.name()) + "</Name>"
            + "<Description><![CDATA[" + (f.description() != null ? f.description() : "") + "]]></Description>"
            + "<Brand>" + FalabellaXml.escape(req.getBrand()) + "</Brand>"
            + "<BusinessUnits><BusinessUnit>"
            + "<OperatorCode>" + OPERATOR_CODE + "</OperatorCode>"
            + "<Price>" + f.price() + "</Price>"
            + f.specialPriceTags()
            + "<Stock>" + f.stock() + "</Stock>"
            + "<Status>active</Status>"
            + "</BusinessUnit></BusinessUnits>"
            + "<ProductData>"
            + "<ConditionType>" + FalabellaXml.escape(req.getConditionType()) + "</ConditionType>"
            + "<PackageHeight>" + req.getPackageHeight() + "</PackageHeight>"
            + "<PackageWidth>" + req.getPackageWidth() + "</PackageWidth>"
            + "<PackageLength>" + req.getPackageLength() + "</PackageLength>"
            + "<PackageWeight>" + req.getPackageWeight() + "</PackageWeight>"
            + f.extraTags()
            + "</ProductData>"
            + "</Product></Request>";

        return falabella.postXml("ProductUpdate", null, xml);
    }

    // Llamar una vez que FeedStatus confirmó que el producto se creó bien —
    // marca id_falabella localmente para que la tabla lo pinte en verde.
    @PostMapping("/falabella/productos/{sku}/confirmar")
    public ResponseEntity<Map<String, Object>> confirmUploaded(@PathVariable String sku) {
        List<LaudusProduct> matches = bd.findBySkuIn(List.of(sku));
        if (matches.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "SKU no encontrado"));
        }
        LaudusProduct product = matches.get(0);
        product.setIdFalabella(sku);
        bd.updateProduct(product);
        return ResponseEntity.ok(Map.of("sku", sku, "idFalabella", sku));
    }

    // ── Imágenes: se reutilizan las URLs ya públicas de WooCommerce ──────

    @GetMapping("/falabella/productos/{sku}/imagenes-woocommerce")
    public ResponseEntity<List<String>> wooCommerceImageUrls(@PathVariable String sku) {
        return ResponseEntity.ok(fetchWooCommerceImageUrls(sku));
    }

    // El feed de Image puede reportar "Finished, 0 errores" y aun así no dejar
    // las imágenes asociadas — confirmado empíricamente (AF05, AF09, AF16, AF08):
    // no basta con que el producto ya exista, hay un margen extra de propagación
    // interna en Falabella. Por eso acá no solo se manda el feed: se espera a que
    // termine, se verifica con GetProducts que MainImage quedó poblado, y si no,
    // se reintenta un par de veces antes de darse por vencido.
    private static final int IMAGE_MAX_ATTEMPTS = 3;

    @PostMapping("/falabella/productos/{sku}/imagenes")
    public ResponseEntity<Map<String, Object>> uploadImages(@PathVariable String sku) {
        Map<String, Object> result = tryUploadImages(sku);
        int status = (int) result.remove("httpStatus");
        return ResponseEntity.status(status).body(result);
    }

    private Map<String, Object> tryUploadImages(String sku) {
        if (!fetchExistingFalabellaSkus(List.of(sku)).contains(sku.trim().toUpperCase())) {
            return withStatus(409, "error",
                "El producto " + sku + " todavía no existe en Falabella (ProductCreate no ha terminado de procesarse). "
                + "Espera unos segundos y vuelve a intentar.");
        }
        List<String> urls = fetchWooCommerceImageUrls(sku);
        if (urls.isEmpty()) {
            return withStatus(400, "error", "No se encontraron imágenes públicas en WooCommerce para el SKU " + sku);
        }

        StringBuilder images = new StringBuilder();
        for (String url : urls) {
            images.append("<Image>").append(FalabellaXml.escape(url)).append("</Image>");
        }
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Request><ProductImage>"
            + "<SellerSku>" + FalabellaXml.escape(sku) + "</SellerSku>"
            + "<Images>" + images + "</Images>"
            + "</ProductImage></Request>";

        // Confirmado empíricamente: esperar unos segundos NO alcanza — hicimos la
        // prueba de esperar 3 minutos sin reintentar y seguía vacío, pero el
        // siguiente intento nuevo after eso funcionó al primer tiro. O sea:
        // Falabella necesita varios minutos reales desde ProductCreate antes de
        // que un feed de Image prenda, no un problema de "cuántos intentos".
        // Por eso los reintentos (2° y 3°) esperan minutos, no segundos.
        long[] waitBeforeRetryMs = {0, 90_000, 120_000}; // sin espera extra en el 1er intento

        String lastFeedBody = null;
        for (int attempt = 1; attempt <= IMAGE_MAX_ATTEMPTS; attempt++) {
            sleepQuietly(waitBeforeRetryMs[attempt - 1]);

            FalabellaClient.FalabellaResponse res = falabella.postXml("Image", null, xml);
            lastFeedBody = res.body();
            String feedId = extractRequestId(res.body());

            if (feedId != null) {
                waitForFeedFinish(feedId, 15000);
            }
            sleepQuietly(3000); // margen de propagación observado empíricamente

            if (fetchMainImage(sku) != null) {
                Map<String, Object> ok = new HashMap<>();
                ok.put("httpStatus", 200);
                ok.put("asociadas", true);
                ok.put("intentos", attempt);
                ok.put("feed", lastFeedBody);
                return ok;
            }
        }

        Map<String, Object> fail = new HashMap<>();
        fail.put("httpStatus", 502);
        fail.put("asociadas", false);
        fail.put("intentos", IMAGE_MAX_ATTEMPTS);
        fail.put("feed", lastFeedBody);
        fail.put("error", "El feed se procesó sin errores reportados pero las imágenes no quedaron asociadas tras "
            + IMAGE_MAX_ATTEMPTS + " intentos. Puede ser un problema con el contenido de las imágenes (revisar formato/fondo).");
        return fail;
    }

    private Map<String, Object> withStatus(int status, String key, String value) {
        Map<String, Object> m = new HashMap<>();
        m.put("httpStatus", status);
        m.put(key, value);
        return m;
    }

    private String extractRequestId(String jsonBody) {
        try {
            JsonObject root = gson.fromJson(jsonBody, JsonObject.class);
            JsonObject success = root.getAsJsonObject("SuccessResponse");
            if (success == null) return null;
            JsonElement id = success.getAsJsonObject("Head").get("RequestId");
            return id != null && !id.isJsonNull() && !id.getAsString().isBlank() ? id.getAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String waitForFeedFinish(String feedId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        String lastStatus = null;
        while (System.currentTimeMillis() < deadline) {
            FalabellaClient.FalabellaResponse res = falabella.get("FeedStatus", Map.of("FeedID", feedId));
            try {
                JsonObject root = gson.fromJson(res.body(), JsonObject.class);
                String status = root.getAsJsonObject("SuccessResponse").getAsJsonObject("Body")
                    .getAsJsonObject("FeedDetail").get("Status").getAsString();
                lastStatus = status;
                if (!status.equals("Queued") && !status.equals("Processing")) return status;
            } catch (Exception ignored) {
                return lastStatus;
            }
            sleepQuietly(1500);
        }
        return lastStatus;
    }

    private String fetchMainImage(String sku) {
        FalabellaClient.FalabellaResponse res = falabella.get("GetProducts", Map.of("SkuSellerList", gson.toJson(List.of(sku))));
        try {
            JsonObject root = gson.fromJson(res.body(), JsonObject.class);
            JsonObject body = root.getAsJsonObject("SuccessResponse").getAsJsonObject("Body");
            JsonElement productsEl = body.get("Products");
            if (productsEl == null || !productsEl.isJsonObject()) return null;
            JsonElement productEl = productsEl.getAsJsonObject().get("Product");
            if (productEl == null || productEl.isJsonNull()) return null;
            JsonObject p = productEl.isJsonArray() ? productEl.getAsJsonArray().get(0).getAsJsonObject() : productEl.getAsJsonObject();
            String mainImage = getOrNull(p, "MainImage");
            return mainImage != null && !mainImage.isBlank() ? mainImage : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private List<String> fetchWooCommerceImageUrls(String sku) {
        ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
        List<String> urls = new ArrayList<>();
        try {
            String url = cwp.getUrlBase() + "products?sku=" + sku;
            String auth = cwp.getUsernameToken() + ":" + cwp.getPasswordToken();
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + encodedAuth)
                .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return urls;

            JsonArray products = gson.fromJson(response.body(), JsonArray.class);
            if (products == null || products.isEmpty()) return urls;
            JsonObject firstProduct = products.get(0).getAsJsonObject();
            JsonArray images = firstProduct.getAsJsonArray("images");
            if (images == null) return urls;
            for (JsonElement img : images) {
                JsonElement src = img.getAsJsonObject().get("src");
                if (src != null && !src.isJsonNull()) urls.add(src.getAsString());
            }
        } catch (Exception ignored) {
            // Se devuelve la lista vacía; el llamador decide qué hacer.
        }
        return urls;
    }
}
