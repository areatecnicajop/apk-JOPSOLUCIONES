package com.jop.sincronizador.controllers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.services.IProductBDService;
import com.jop.sincronizador.models.entity.dto.ImageDTO;
import com.jop.sincronizador.services.IRestLaudusWordP;
import com.jop.sincronizador.webSockets.WebSocket;

// Editor de fotos de la pestaña "Productos".
//
// La fuente de verdad es WooCommerce: se lee lo que el producto tiene AHORA en la web,
// y al guardar se deja la web exactamente como quedó el editor (orden incluido).
// Solo toca las imágenes: nunca nombre, precio, stock, descripciones ni categorías.
//
// Orden de operaciones al guardar (para que un fallo nunca deje el producto sin fotos):
//   1) subir las fotos nuevas a la biblioteca de medios
//   2) asignar la lista final de fotos al producto
//   3) copiar a la papelera las fotos quitadas y SOLO DESPUÉS borrarlas de la biblioteca
//   4) releer el producto en la web para verificar
//   5) dejar la carpeta <SKU>/ del servidor igual que la web (para que el flujo antiguo
//      de imágenes no deshaga estos cambios)
@RestController
@RequestMapping("/sync/productos")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201", "http://localhost:8081", "https://jopsoluciones.com", "https://jopsoluciones.cl"})
public class ProductoFotosController {

    static final int MAX_FOTOS = 10;
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int LADO = 1000;
    static final float CALIDAD_JPEG = 0.88f;

    @Autowired
    private IProductBDService productService;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private IRestLaudusWordP restLaudusWordP;

    private final Gson gson = new Gson();
    private final HttpClient http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    // SKUs con un guardado en curso: evita guardados dobles sobre el mismo producto.
    private final Set<String> enCurso = ConcurrentHashMap.newKeySet();

    private record Foto(long id, String url, String nombre) {
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("url", url);
            m.put("nombre", nombre);
            return m;
        }
    }

    // ── Resumen: cuántas fotos tiene cada producto en la web ─────────

    @GetMapping("/fotos-resumen")
    public ResponseEntity<Map<String, Object>> resumen() {
        try {
            ConfigWordPress cwp = cfg();
            Map<String, Integer> conteo = new HashMap<>();
            for (int page = 1; page <= 30; page++) {
                HttpResponse<String> r = wcGet(cwp, "products?per_page=100&page=" + page + "&_fields=id,sku,images");
                if (r.statusCode() != 200) {
                    return ResponseEntity.status(502).body(Map.of("error", "WooCommerce respondió " + r.statusCode()));
                }
                JsonArray arr = gson.fromJson(r.body(), JsonArray.class);
                if (arr == null || arr.isEmpty()) break;
                for (JsonElement el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    String sku = str(o, "sku");
                    if (sku == null || sku.isBlank()) continue;
                    int n = o.has("images") && o.get("images").isJsonArray() ? o.getAsJsonArray("images").size() : 0;
                    conteo.put(sku, n);
                }
                if (arr.size() < 100) break;
            }
            return ResponseEntity.ok(Map.of("fotos", conteo));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "No se pudo consultar WooCommerce: " + e.getMessage()));
        }
    }

    // ── Fotos actuales de un producto (tal cual están en la web) ─────

    @GetMapping("/{sku}/fotos")
    public ResponseEntity<Map<String, Object>> fotos(@PathVariable String sku) {
        LaudusProduct p = producto(sku);
        if (p == null) return ResponseEntity.status(404).body(Map.of("error", "SKU no encontrado"));
        if (!enWeb(p)) return ResponseEntity.status(409).body(Map.of("error", "El producto no está en la web: sincronízalo primero."));
        try {
            List<Foto> actuales = leerFotos(cfg(), p.getIdWP());
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("fotos", actuales.stream().map(Foto::toMap).toList());
            res.put("max", MAX_FOTOS);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "No se pudo leer el producto en WooCommerce: " + e.getMessage()));
        }
    }

    // ── Foto original, para poder editarla (zoom, mover, girar) en el navegador ──
    // El navegador no puede leer pixeles de imágenes de otro dominio, por eso pasa por acá.
    // Solo sirve fotos que el producto tiene HOY en la web (no es un proxy abierto).

    @GetMapping("/{sku}/fotos/{id}/original")
    public ResponseEntity<byte[]> original(@PathVariable String sku, @PathVariable long id) {
        LaudusProduct p = producto(sku);
        if (p == null || !enWeb(p)) return ResponseEntity.status(404).build();
        try {
            Foto f = leerFotos(cfg(), p.getIdWP()).stream().filter(x -> x.id() == id).findFirst().orElse(null);
            if (f == null || f.url() == null) return ResponseEntity.status(404).build();
            byte[] bytes = descargar(f.url());
            if (bytes == null) return ResponseEntity.status(502).build();
            String ext = extensionDe(f.url());
            String tipo = ext.equals(".png") ? "image/png" : "image/jpeg";
            return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(tipo))
                .header("Cache-Control", "private, max-age=60")
                .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(502).build();
        }
    }

    // ── Guardar: deja la web como quedó el editor ────────────────────
    //
    // plan = {"orden": ["e:123", "n:0", "e:456", "n:1"]}
    //   e:<id>  → foto que ya está en la web (se conserva)
    //   n:<i>   → i-ésimo archivo de "nuevas" (se sube)
    // Toda foto de la web que no aparezca en el orden se elimina.

    @PostMapping(value = "/{sku}/fotos", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> guardar(
            @PathVariable String sku,
            @RequestParam("plan") String planJson,
            @RequestParam(value = "nuevas", required = false) List<MultipartFile> nuevas) {

        LaudusProduct p = producto(sku);
        if (p == null) return ResponseEntity.status(404).body(Map.of("error", "SKU no encontrado"));
        if (!enWeb(p)) return ResponseEntity.status(409).body(Map.of("error", "El producto no está en la web: sincronízalo primero."));
        if (!enCurso.add(sku)) return ResponseEntity.status(409).body(Map.of("error", "Ya hay un guardado en curso para este producto."));

        List<Long> subidos = new ArrayList<>(); // medios nuevos creados (para deshacer si algo falla)
        try {
            if (nuevas == null) nuevas = List.of();
            ConfigWordPress cwp = cfg();
            String idWP = p.getIdWP();

            // 1) Interpretar y validar el plan
            List<String> orden = new ArrayList<>();
            try {
                JsonArray arr = gson.fromJson(planJson, JsonObject.class).getAsJsonArray("orden");
                for (JsonElement el : arr) orden.add(el.getAsString());
            } catch (Exception e) {
                return error(400, "Plan inválido.");
            }
            if (orden.size() > MAX_FOTOS) return error(400, "Máximo " + MAX_FOTOS + " fotos por producto.");
            if (new HashSet<>(orden).size() != orden.size()) return error(400, "Plan inválido: fotos repetidas.");

            // 2) Estado real de la web: si alguien cambió las fotos mientras editabas, se avisa en vez de pisarlas
            List<Foto> actuales = leerFotos(cwp, idWP);
            Map<Long, Foto> actualesPorId = new LinkedHashMap<>();
            for (Foto f : actuales) actualesPorId.put(f.id(), f);

            Set<Long> conservadas = new HashSet<>();
            Set<Integer> nuevasUsadas = new HashSet<>();
            for (String it : orden) {
                if (it.startsWith("e:")) {
                    long id;
                    try { id = Long.parseLong(it.substring(2)); } catch (NumberFormatException e) { return error(400, "Plan inválido."); }
                    if (!actualesPorId.containsKey(id)) {
                        return error(409, "Las fotos de este producto cambiaron en la web mientras editabas. Cierra el editor y vuelve a abrirlo.");
                    }
                    conservadas.add(id);
                } else if (it.startsWith("n:")) {
                    int i;
                    try { i = Integer.parseInt(it.substring(2)); } catch (NumberFormatException e) { return error(400, "Plan inválido."); }
                    if (i < 0 || i >= nuevas.size()) return error(400, "Plan inválido: archivo nuevo inexistente.");
                    nuevasUsadas.add(i);
                } else {
                    return error(400, "Plan inválido.");
                }
            }

            // 3) Validar y procesar las fotos nuevas (antes de tocar nada en la web)
            Map<Integer, byte[]> procesadas = new HashMap<>();
            for (int i : nuevasUsadas) {
                MultipartFile mf = nuevas.get(i);
                String nombre = mf.getOriginalFilename() != null ? mf.getOriginalFilename() : ("foto " + (i + 1));
                if (mf.getSize() > MAX_BYTES) return error(400, "«" + nombre + "» pesa más de 10 MB.");
                try {
                    procesadas.put(i, aJpeg1000(mf.getBytes()));
                } catch (IllegalArgumentException e) {
                    return error(400, "«" + nombre + "»: " + e.getMessage());
                }
            }

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String alt = p.getDescription() != null && !p.getDescription().isBlank() ? p.getDescription() : sku;

            // 4) Subir las nuevas a la biblioteca de medios (si una falla, se deshace todo lo subido)
            Map<Integer, Long> idNueva = new HashMap<>();
            int k = 0;
            for (int i : orden.stream().filter(s -> s.startsWith("n:")).map(s -> Integer.parseInt(s.substring(2))).toList()) {
                String resp = restLaudusWordP.productMediaPost(
                    new ImageDTO(Base64.getEncoder().encodeToString(procesadas.get(i))), sku, ts + "-" + (++k));
                long id;
                try { id = Long.parseLong(resp); } catch (Exception e) {
                    deshacer(subidos);
                    return error(502, "No se pudo subir una foto a WordPress (" + resp + "). No se cambió nada en la web.");
                }
                subidos.add(id);
                idNueva.put(i, id);
            }

            // 5) Asignar la lista final al producto (solo imágenes)
            List<Long> esperado = new ArrayList<>();
            JsonArray imagenes = new JsonArray();
            for (String it : orden) {
                boolean nueva = it.startsWith("n:");
                long id = nueva ? idNueva.get(Integer.parseInt(it.substring(2))) : Long.parseLong(it.substring(2));
                esperado.add(id);
                JsonObject o = new JsonObject();
                o.addProperty("id", id);
                if (nueva) o.addProperty("alt", alt);
                imagenes.add(o);
            }
            JsonObject body = new JsonObject();
            body.add("images", imagenes);
            Integer code = restLaudusWordP.ProductPut(idWP, gson.toJson(body), "Fotos del producto " + sku);
            if (code == null || code != 200) {
                deshacer(subidos);
                return error(502, "WooCommerce rechazó el cambio de fotos (código " + code + "). No se cambió nada en la web.");
            }

            List<String> avisos = new ArrayList<>();

            // 6) Quitadas: copia a la papelera y recién después se borran de la biblioteca
            int borradas = 0;
            for (Foto f : actuales) {
                if (conservadas.contains(f.id())) continue;
                boolean respaldada = respaldarEnPapelera(cwp, sku, ts, f);
                if (!respaldada) {
                    avisos.add("La foto " + f.id() + " se quitó del producto pero NO se borró de la biblioteca (no se pudo guardar su copia en la papelera).");
                    continue;
                }
                String r = restLaudusWordP.productMediaDelete(String.valueOf(f.id()));
                if ("IMAGE_DELETED".equals(r)) borradas++;
                else avisos.add("La foto " + f.id() + " se quitó del producto pero no se pudo borrar de la biblioteca de medios.");
            }

            // 7) Verificar releyendo la web
            List<Foto> finales = leerFotos(cwp, idWP);
            List<Long> ids = finales.stream().map(Foto::id).toList();
            if (!ids.equals(esperado)) {
                avisos.add("La web devolvió las fotos en un orden o cantidad distintos a lo esperado. Revisa el producto.");
            }

            // 8) Carpeta del servidor = web
            String avisoCarpeta = espejarCarpeta(cwp, sku, ts, esperado, idNueva, procesadas, orden, finales);
            if (avisoCarpeta != null) avisos.add(avisoCarpeta);

            WebSocket.sendMessageToAll("📷 Fotos de " + sku + " actualizadas en la web: " + finales.size()
                + " foto(s), " + subidos.size() + " nueva(s), " + borradas + " borrada(s).");

            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ok", true);
            res.put("fotos", finales.stream().map(Foto::toMap).toList());
            res.put("subidas", subidos.size());
            res.put("borradas", borradas);
            res.put("advertencias", avisos);
            return ResponseEntity.ok(res);

        } catch (Exception e) {
            deshacer(subidos);
            return error(500, "Error al guardar las fotos: " + e.getMessage());
        } finally {
            enCurso.remove(sku);
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> error(int status, String msg) {
        return ResponseEntity.status(status).body(Map.of("error", msg));
    }

    private ConfigWordPress cfg() {
        return context.getBean("getConfigWordPress", ConfigWordPress.class);
    }

    private LaudusProduct producto(String sku) {
        if (sku == null || sku.isBlank() || sku.contains("/") || sku.contains("\\") || sku.contains("..")) return null;
        List<LaudusProduct> m = productService.findBySkuIn(List.of(sku));
        return m.isEmpty() ? null : m.get(0);
    }

    private boolean enWeb(LaudusProduct p) {
        return p.getIdWP() != null && !p.getIdWP().isBlank();
    }

    private String authHeader(ConfigWordPress cwp) {
        String auth = cwp.getUsernameToken() + ":" + cwp.getPasswordToken();
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> wcGet(ConfigWordPress cwp, String path) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(cwp.getUrlBase() + path))
            .header("Authorization", authHeader(cwp))
            .timeout(Duration.ofSeconds(60))
            .GET().build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private List<Foto> leerFotos(ConfigWordPress cwp, String idWP) throws IOException, InterruptedException {
        HttpResponse<String> r = wcGet(cwp, "products/" + idWP + "?_fields=id,images");
        if (r.statusCode() != 200) throw new IOException("WooCommerce respondió " + r.statusCode());
        JsonObject o = gson.fromJson(r.body(), JsonObject.class);
        List<Foto> out = new ArrayList<>();
        if (o.has("images") && o.get("images").isJsonArray()) {
            for (JsonElement el : o.getAsJsonArray("images")) {
                JsonObject im = el.getAsJsonObject();
                out.add(new Foto(im.get("id").getAsLong(), str(im, "src"), str(im, "name")));
            }
        }
        return out;
    }

    private String str(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && !el.isJsonNull() ? el.getAsString() : null;
    }

    // Deshace los medios recién subidos si el guardado no llegó a buen término.
    private void deshacer(List<Long> subidos) {
        for (Long id : subidos) {
            try { restLaudusWordP.productMediaDelete(String.valueOf(id)); } catch (Exception ignored) { }
        }
        subidos.clear();
    }

    private byte[] descargar(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build();
            HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Path base(ConfigWordPress cwp) {
        return Paths.get(cwp.getImagesPathLocal()).toAbsolutePath().normalize();
    }

    // Carpeta hermana de la de imágenes: <carpeta>-papelera/<SKU>/...
    private Path papelera(ConfigWordPress cwp, String sku) {
        Path b = base(cwp);
        return b.resolveSibling(b.getFileName() + "-papelera").resolve(sku);
    }

    // Extensión real del archivo según su URL (.jpg, .jpeg o .png; cualquier otra cosa → .jpg).
    private String extensionDe(String url) {
        try {
            String path = url == null ? null : URI.create(url).getPath();
            if (path != null && path.contains(".")) {
                String ext = path.substring(path.lastIndexOf('.')).toLowerCase();
                if (ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".png")) return ext;
            }
        } catch (Exception ignored) { }
        return ".jpg";
    }

    private boolean respaldarEnPapelera(ConfigWordPress cwp, String sku, String ts, Foto f) {
        if (f.url() == null) return false;
        byte[] bytes = descargar(f.url());
        if (bytes == null) return false;
        try {
            String path = URI.create(f.url()).getPath();
            String ext = path != null && path.contains(".") ? path.substring(path.lastIndexOf('.')) : ".jpg";
            Path dir = papelera(cwp, sku);
            Files.createDirectories(dir);
            Files.write(dir.resolve(ts + "_" + f.id() + ext), bytes);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Deja <imágenes>/<SKU>/ idéntica a la web: SKU_01.jpg, SKU_02.jpg... en el orden final.
    // Lo que había antes en esa carpeta se mueve a la papelera. Devuelve un aviso si no se pudo.
    private String espejarCarpeta(ConfigWordPress cwp, String sku, String ts, List<Long> esperado,
                                  Map<Integer, Long> idNueva, Map<Integer, byte[]> procesadas,
                                  List<String> orden, List<Foto> finales) {
        try {
            Map<Long, byte[]> nuevasPorId = new HashMap<>();
            for (Map.Entry<Integer, Long> e : idNueva.entrySet()) nuevasPorId.put(e.getValue(), procesadas.get(e.getKey()));
            Map<Long, String> urls = new HashMap<>();
            for (Foto f : finales) urls.put(f.id(), f.url());

            List<byte[]> contenido = new ArrayList<>();
            List<String> extensiones = new ArrayList<>();
            for (Long id : esperado) {
                byte[] b = nuevasPorId.get(id);
                String ext = ".jpg"; // las nuevas siempre quedan en JPEG
                if (b == null) {
                    String url = urls.get(id);
                    b = url != null ? descargar(url) : null;
                    ext = extensionDe(url); // las que ya estaban conservan su formato real (jpg/png)
                }
                if (b == null) return "La web quedó bien, pero no se pudo actualizar la carpeta del servidor (falló la descarga de una foto).";
                contenido.add(b);
                extensiones.add(ext);
            }

            Path b = base(cwp);
            Path carpeta = b.resolve(sku).normalize();
            if (!carpeta.startsWith(b)) return "La web quedó bien, pero la ruta de la carpeta del servidor es inválida.";
            Files.createDirectories(carpeta);

            // mover lo anterior a la papelera
            Path viejo = papelera(cwp, sku).resolve(ts + "-carpeta");
            try (Stream<Path> s = Files.list(carpeta)) {
                for (Path f : s.filter(Files::isRegularFile).toList()) {
                    String n = f.getFileName().toString().toLowerCase();
                    if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) {
                        Files.createDirectories(viejo);
                        Files.move(f, viejo.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            for (int i = 0; i < contenido.size(); i++) {
                Files.write(carpeta.resolve(String.format("%s_%02d%s", sku, i + 1, extensiones.get(i))), contenido.get(i));
            }
            return null;
        } catch (Exception e) {
            return "La web quedó bien, pero no se pudo actualizar la carpeta del servidor: " + e.getMessage();
        }
    }

    // Deja la foto en JPEG 1000×1000 con fondo blanco (sin recortar, centrada).
    // Si ya viene así de 1000×1000 en JPEG, se respeta tal cual para no recomprimirla.
    static byte[] aJpeg1000(byte[] original) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<javax.imageio.ImageReader> readers = iis == null ? null : ImageIO.getImageReaders(iis);
            if (readers == null || !readers.hasNext()) throw new IllegalArgumentException("formato de imagen no soportado (usa JPG o PNG)");
            javax.imageio.ImageReader reader = readers.next();
            String formato = reader.getFormatName().toLowerCase();
            reader.setInput(iis);
            BufferedImage src = reader.read(0);
            reader.dispose();
            if (src == null) throw new IllegalArgumentException("no se pudo leer la imagen");

            int w = src.getWidth(), h = src.getHeight();
            if (w == LADO && h == LADO && (formato.equals("jpeg") || formato.equals("jpg"))) return original;

            double ratio = Math.min((double) LADO / w, (double) LADO / h);
            int nw = Math.max(1, (int) Math.round(w * ratio));
            int nh = Math.max(1, (int) Math.round(h * ratio));
            Image esc = src.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);

            BufferedImage out = new BufferedImage(LADO, LADO, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, LADO, LADO);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(esc, (LADO - nw) / 2, (LADO - nh) / 2, null);
            g.dispose();

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(CALIDAD_JPEG);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(out, null, null), param);
                writer.dispose();
            }
            return bos.toByteArray();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("no se pudo procesar la imagen (" + e.getMessage() + ")");
        }
    }
}
