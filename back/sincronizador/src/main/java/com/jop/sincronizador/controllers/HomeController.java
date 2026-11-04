package com.jop.sincronizador.controllers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jop.sincronizador.data.laudus.model.entity.OrderWP;
import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;
import com.jop.sincronizador.data.laudus.model.entity.ProductWP;
import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.LaudusStock;
import com.jop.sincronizador.data.local.model.entity.RegisterChange;
import com.jop.sincronizador.data.local.model.services.IProductBDService;
import com.jop.sincronizador.data.local.model.services.ITransactionBDtoWPService;
import com.jop.sincronizador.data.local.model.services.ProductBDImplService;
import com.jop.sincronizador.data.local.model.services.TransactionBDtoWPService;
import com.jop.sincronizador.repository.PssWriteRepositoryStock;
import com.jop.sincronizador.services.IRestLaudusWordP;
import com.jop.sincronizador.util.StockSyncScheduler;
import com.jop.sincronizador.services.RestLaudusWordP;
import com.jop.sincronizador.models.entity.dto.CatalogProduct;
import com.jop.sincronizador.models.entity.dto.CatalogResponse;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;


@RestController
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:4201", "http://localhost:8081", "https://jopsoluciones.com", "https://jopsoluciones.cl"})
public class HomeController {

    @Autowired
    private IProductBDService productService;

    @Autowired
    IRestLaudusWordP restLaudusWordP;

    @Autowired
    ITransactionBDtoWPService restDBtoWP;

    @Autowired
    private StockSyncScheduler stockSyncScheduler;

    @Autowired
    private PssWriteRepositoryStock stockRepository;

    
    public HomeController(TransactionBDtoWPService restDBtoWP) {
        this.restDBtoWP = restDBtoWP;
    }
    


     
    // Trae todos los productos de la bd  tabla laudus product
    // @GetMapping("/Alllaudus")
    // public List<LaudusProduct> getAllProducts() {
    //     return productService.findAll();
    // }
    
    

    // @GetMapping("/")
    // public String home() {
    //     return "¡Bienvenido a la aplicación sincronizadora!";
    // }

    

    // // @GetMapping("/product/get")
    // public int getProduct(@RequestParam String sku) {
    //     return restLaudusWordP.ProductGetId(sku);
    // }


    // trae todos los productos desde la api de laudus
    // @PostMapping("/updateInfolaudus")
    // public String getProduct() {
    //     restLaudusWordP.CategoriesGetAllLaudus();
    //     restLaudusWordP.ProductGetAllLaudus();
    //     // restLaudusWordP.ProductGetAllLaudusStock();
    //     return "Informacion de laudus actualizada correctamente";
    // }

    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "sincronizador"));
    }

    @PostMapping("/syncroCategorias")
    public String getCategoriesLaudus() {
        restLaudusWordP.CategoriesGetAllLaudus();
        restDBtoWP.initCategorie();
        return "Categorias Actualizadas con exito" ;
     }

    
    @PostMapping("/start")
    public ResponseEntity<String> startSync(
        
        @RequestBody List<String> skus,  
        @RequestParam(value = "includeImages", defaultValue = "false") boolean includeImages,
        @RequestParam(value = "forceUpdate", defaultValue = "false") boolean forceUpdate) {
        restDBtoWP.Synchro(skus, includeImages, forceUpdate);

    return ResponseEntity.ok("Sincronización iniciada con imágenes: " + includeImages + " para SKUs: " + skus);
    }

    @GetMapping("/allOrders")
    public List<OrderWP> ProductGetAllOrder( ) {
        return restLaudusWordP.ProductGetAllOrders();
    }

    // Endpoint para Flutter - devuelve todos los productos sin filtro de webStatus
    @GetMapping("/api/products")
    public ResponseEntity<?> getProductsForApp() {
        List<LaudusProduct> products = productService.findAll();
        List<Categorie> categories = productService.findAllCategorie();
        Map<String, Categorie> categoryMap = categories.stream()
            .collect(Collectors.toMap(Categorie::getLaudusId, cat -> cat, (a, b) -> a));

        Map<String, Integer> stockMap = stockRepository.findAll().stream()
            .collect(Collectors.toMap(LaudusStock::getSku, LaudusStock::getStock, (a, b) -> a));

        List<Map<String, Object>> result = products.stream()
            .filter(p -> p.getSku() != null && !p.getSku().isEmpty())
            .map(p -> {
                Map<String, Object> item = new HashMap<>();
                item.put("sku", p.getSku());
                item.put("name", p.getDescription() != null ? p.getDescription() : "");
                item.put("unitPriceWithTaxes", p.getUnitPriceWithTaxes() != null ? p.getUnitPriceWithTaxes() : 0);
                item.put("webDescription", p.getWebDescription());
                item.put("stock", stockMap.getOrDefault(p.getSku(), 0));
                Categorie cat = p.getProductCategoryId() != null ? categoryMap.get(p.getProductCategoryId()) : null;
                item.put("categoryName", cat != null ? cat.getLaudusName() : null);
                item.put("productCategoryId", p.getProductCategoryId());
                return item;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("products", result, "total", result.size()));
    }
    
    @GetMapping("/productLaudusStock/get")
    public List<String> getProductStock(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "008") String warehouseId) {
        return restLaudusWordP.ProductGetAllLaudusStock(warehouseId);
    }

    // ── Endpoints de solo-lectura para testing ────────────────────
    // No modifican BD ni WooCommerce. Solo leen de Laudus y devuelven el JSON crudo.

    @GetMapping("/test/laudus/stock")
    public ResponseEntity<String> testLaudusStock(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "008") String warehouseId) {
        ConfigWordPress cwp = restLaudusWordP.getConfig();
        String apiUrl = cwp.getApiLaudusUrl();
        String token  = cwp.getTokenLaudusApi();
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            StringBuilder result = new StringBuilder("[");
            java.util.List<String> warehouses = java.util.List.of(warehouseId, "001");
            for (String wh : warehouses) {
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiUrl + "stock?warehouseId=" + wh))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET().build();
                java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (result.length() > 1) result.append(",");
                result.append("{\"bodega\":\"").append(wh).append("\",\"status\":").append(res.statusCode())
                      .append(",\"data\":").append(res.body()).append("}");
            }
            result.append("]");
            return ResponseEntity.ok().header("Content-Type","application/json").body(result.toString());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/test/laudus/categorias")
    public ResponseEntity<String> testLaudusCategorias() {
        ConfigWordPress cwp = restLaudusWordP.getConfig();
        String apiUrl = cwp.getApiLaudusUrl();
        String token  = cwp.getTokenLaudusApi();
        String body   = "{\"fields\":[\"productCategoryId\",\"name\",\"fullpath\",\"parentId\"]}";
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(apiUrl + "categories/list"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(res.statusCode()).header("Content-Type","application/json").body(res.body());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
    
    @GetMapping("/test/laudus/pedidos")
    public ResponseEntity<String> testLaudusPedidos(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size) {
        ConfigWordPress cwp = restLaudusWordP.getConfig();
        String apiUrl = cwp.getApiLaudusUrl();
        String token  = cwp.getTokenLaudusApi();
        String body = String.format(
            "{\"fields\":[\"salesOrderId\",\"docNumber\",\"date\",\"dueDate\",\"customer\",\"contact\"," +
            "\"netAmount\",\"taxAmount\",\"totalAmount\",\"notes\",\"status\",\"invoiceStatus\",\"paymentStatus\"]," +
            "\"pageIndex\":%d,\"pageSize\":%d,\"orderField\":\"date\",\"orderDirection\":\"DESC\"}",
            page, size);
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(apiUrl + "sales/orders/list"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build();
            java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(res.statusCode()).header("Content-Type","application/json").body(res.body());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    @GetMapping("/catalog/products")
    public CatalogResponse getCatalogProducts() {
        List<LaudusProduct> products = productService.findAll();
        List<Categorie> categories = productService.findAllCategorie();
        
        // Crear un mapa de categorías por ID para búsqueda rápida
        Map<String, Categorie> categoryMap = categories.stream()
            .collect(Collectors.toMap(Categorie::getLaudusId, cat -> cat));
        
        // Filtrar solo productos activos y mapear al formato del catálogo
        List<CatalogProduct> catalogProducts = products.stream()
            .filter(p -> p.getWebStatus() != null && p.getWebStatus())
            .map(product -> {
                CatalogProduct catalogProduct = new CatalogProduct();
                
                // ===== MAPEAR TODOS LOS CAMPOS DEL PRODUCTO COMPLETO =====
                // Campos originales de LaudusProduct
                catalogProduct.setId(product.getId());
                catalogProduct.setSku(product.getSku());
                catalogProduct.setUnitPriceWithTaxes(product.getUnitPriceWithTaxes());
                catalogProduct.setModifiedAt(product.getModifiedAt());
                catalogProduct.setProductCategoryId(product.getProductCategoryId());
                catalogProduct.setWebStatus(product.getWebStatus());
                catalogProduct.setWebDescription(product.getWebDescription());
                catalogProduct.setIdWP(product.getIdWP());
                
                // Mapear campos básicos para el formato del catálogo
                catalogProduct.setName(product.getDescription() != null ? product.getDescription() : "");
                catalogProduct.setDescription(product.getWebDescription() != null ? 
                    product.getWebDescription() : product.getDescription() != null ? 
                    product.getDescription() : "");
                
                // Tagline: usar una parte de la descripción o un valor por defecto
                String description = catalogProduct.getDescription();
                if (description != null && !description.isEmpty()) {
                    String[] sentences = description.split("\\.");
                    catalogProduct.setTagline(sentences.length > 0 ? sentences[0].trim() : 
                        description.substring(0, Math.min(50, description.length())));
                } else {
                    catalogProduct.setTagline("Producto de calidad");
                }
                
                // Price: convertir de centavos a formato de moneda
                if (product.getUnitPriceWithTaxes() != null) {
                    double priceInEuros = product.getUnitPriceWithTaxes() / 100.0;
                    // Formatear como "$2,450" (formato similar al JSON original)
                    if (priceInEuros == (int) priceInEuros) {
                        // Sin decimales
                        catalogProduct.setPrice(String.format("$%,d", (int) priceInEuros));
                    } else {
                        // Con decimales
                        catalogProduct.setPrice(String.format("$%,.2f", priceInEuros));
                    }
                } else {
                    catalogProduct.setPrice("$0");
                }
                
                // Category y Subcategory: obtener de la categoría
                if (product.getProductCategoryId() != null) {
                    Categorie category = categoryMap.get(product.getProductCategoryId());
                    if (category != null) {
                        // Guardar información completa de la categoría
                        catalogProduct.setCategoryName(category.getLaudusName());
                        catalogProduct.setCategoryPath(category.getLaudusPath());
                        
                        if (category.getLaudusPath() != null) {
                            String[] pathParts = category.getLaudusPath().split("\\\\");
                            if (pathParts.length >= 2) {
                                // La categoría principal es el segundo elemento del path
                                catalogProduct.setCategory(pathParts[1]);
                                // La subcategoría es el último elemento
                                catalogProduct.setSubcategory(pathParts[pathParts.length - 1]);
                            } else {
                                catalogProduct.setCategory(category.getLaudusName() != null ? category.getLaudusName() : "General");
                                catalogProduct.setSubcategory("General");
                            }
                        } else {
                            catalogProduct.setCategory(category.getLaudusName() != null ? category.getLaudusName() : "General");
                            catalogProduct.setSubcategory("General");
                        }
                    } else {
                        catalogProduct.setCategory("General");
                        catalogProduct.setSubcategory("General");
                        catalogProduct.setCategoryName("General");
                        catalogProduct.setCategoryPath("");
                    }
                } else {
                    catalogProduct.setCategory("General");
                    catalogProduct.setSubcategory("General");
                    catalogProduct.setCategoryName("General");
                    catalogProduct.setCategoryPath("");
                }
                
                // ColorPalette y Materials: valores por defecto ya que no existen en Laudus
                catalogProduct.setColorPalette("Variado");
                catalogProduct.setMaterials("Materiales de calidad");
                
                return catalogProduct;
            })
            .collect(Collectors.toList());
        
        return new CatalogResponse(catalogProducts);
    }
    
    @GetMapping("/catalog/products-extended")
    public com.jop.sincronizador.models.entity.dto.CatalogResponseExtended getCatalogProductsExtended() {
        List<com.jop.sincronizador.models.entity.dto.CatalogProductExtended> products = restLaudusWordP.ProductGetAllLaudusForCatalog();
        List<Categorie> categories = productService.findAllCategorie();
        
        // Crear un mapa de categorías por ID para búsqueda rápida
        Map<String, Categorie> categoryMap = categories.stream()
            .collect(Collectors.toMap(Categorie::getLaudusId, cat -> cat));
        
        // Mapear productos con información de categorías
        List<com.jop.sincronizador.models.entity.dto.CatalogProductExtended> catalogProducts = products.stream()
            .filter(p -> p.getWebStatus() != null && p.getWebStatus())
            .map(product -> {
                // Mapear campos básicos para el formato del catálogo
                product.setName(product.getDescription() != null ? product.getDescription() : "");
                product.setDescription(product.getWebDescription() != null ? 
                    product.getWebDescription() : product.getDescription() != null ? 
                    product.getDescription() : "");
                
                // Tagline: usar una parte de la descripción o un valor por defecto
                String description = product.getDescription();
                if (description != null && !description.isEmpty()) {
                    String[] sentences = description.split("\\.");
                    product.setTagline(sentences.length > 0 ? sentences[0].trim() : 
                        description.substring(0, Math.min(50, description.length())));
                } else {
                    product.setTagline("Producto de calidad");
                }
                
                // Price: convertir de centavos a formato de moneda
                if (product.getUnitPriceWithTaxes() != null) {
                    double priceInEuros = product.getUnitPriceWithTaxes() / 100.0;
                    if (priceInEuros == (int) priceInEuros) {
                        product.setPrice(String.format("$%,d", (int) priceInEuros));
                    } else {
                        product.setPrice(String.format("$%,.2f", priceInEuros));
                    }
                } else {
                    product.setPrice("$0");
                }
                
                // Category y Subcategory: obtener de la categoría
                if (product.getProductCategoryId() != null) {
                    Categorie category = categoryMap.get(product.getProductCategoryId());
                    if (category != null) {
                        product.setCategoryName(category.getLaudusName());
                        product.setCategoryPath(category.getLaudusPath());
                        
                        if (category.getLaudusPath() != null) {
                            String[] pathParts = category.getLaudusPath().split("\\\\");
                            if (pathParts.length >= 2) {
                                product.setCategory(pathParts[1]);
                                product.setSubcategory(pathParts[pathParts.length - 1]);
                            } else {
                                product.setCategory(category.getLaudusName() != null ? category.getLaudusName() : "General");
                                product.setSubcategory("General");
                            }
                        } else {
                            product.setCategory(category.getLaudusName() != null ? category.getLaudusName() : "General");
                            product.setSubcategory("General");
                        }
                    } else {
                        product.setCategory("General");
                        product.setSubcategory("General");
                        product.setCategoryName("General");
                        product.setCategoryPath("");
                    }
                } else {
                    product.setCategory("General");
                    product.setSubcategory("General");
                    product.setCategoryName("General");
                    product.setCategoryPath("");
                }
                
                // ColorPalette y Materials: usar valores de customFields si existen
                if (product.getMaterial() != null && !product.getMaterial().isEmpty()) {
                    product.setMaterials(product.getMaterial());
                } else {
                    product.setMaterials("Materiales de calidad");
                }
                product.setColorPalette("Variado");
                
                return product;
            })
            .collect(Collectors.toList());
        
        return new com.jop.sincronizador.models.entity.dto.CatalogResponseExtended(catalogProducts);
    }
    
    
    // // Trae todos los productos desde word press
    // @GetMapping("/all")
    // public List<ProductWP> ProductGetAll( ) {
    //     return restLaudusWordP.ProductGetAll();
    // }
  
   

//   @PostMapping("/create")
//   public ResponseEntity<String> createProduct(@RequestBody String json) {
//     try {
//         // Llamar al método ProductPost con el JSON recibido
//         String productId = restLaudusWordP.ProductPost(json);

//         // Manejo de respuestas
//         if (productId != null) {
//             return ResponseEntity.ok("Product created successfully with ID: " + productId);
//         } else {
//             return ResponseEntity.status(HttpStatus.BAD_REQUEST)
//                     .body("Failed to create product. No product ID returned.");
//         }

//     } catch (Exception e) {
//         return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                 .body("Error processing request: " + e.getMessage());
//     }
// }




// @PostMapping("/startCategorie")
// public String startSyncCategorie() {

//     return "Categorias Actualizadas";
// }





// @DeleteMapping("/product/image/delete")
// public String deleteProductImage(@RequestParam String imageId) {
//     return restLaudusWordP.productMediaDelete(imageId);
// }

    // ── Gestión de imágenes locales ───────────────────────────────

    private java.nio.file.Path imagesBasePath() {
        ConfigWordPress cwp = restLaudusWordP.getConfig();
        return java.nio.file.Paths.get(cwp.getImagesPathLocal());
    }

    @GetMapping("/images")
    public ResponseEntity<List<Map<String, Object>>> listImages() {
        try {
            java.nio.file.Path base = imagesBasePath();
            // Agrupar por carpeta
            java.util.LinkedHashMap<String, List<Map<String, Object>>> byFolder = new java.util.LinkedHashMap<>();
            try (Stream<java.nio.file.Path> files = Files.walk(base)) {
                files.filter(p -> !Files.isDirectory(p))
                     .filter(p -> {
                         String n = p.getFileName().toString().toLowerCase();
                         return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
                     })
                     .sorted()
                     .forEach(p -> {
                         String rel = base.relativize(p).toString().replace("\\", "/");
                         String folder = rel.contains("/") ? rel.substring(0, rel.lastIndexOf("/")) : "/";
                         String fileName = p.getFileName().toString();
                         Map<String, Object> f = new HashMap<>();
                         f.put("name", fileName);
                         f.put("size", p.toFile().length());
                         f.put("url", "/images/file/" + rel);
                         byFolder.computeIfAbsent(folder, k -> new ArrayList<>()).add(f);
                     });
            }
            List<Map<String, Object>> result = new ArrayList<>();
            byFolder.forEach((folder, imgs) -> {
                Map<String, Object> g = new HashMap<>();
                g.put("folder", folder);
                g.put("images", imgs);
                g.put("count", imgs.size());
                result.add(g);
            });
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/images/file/**")
    public ResponseEntity<org.springframework.core.io.Resource> serveImage(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String filename = request.getRequestURI().replaceFirst(".*/images/file/", "");
            java.nio.file.Path file = imagesBasePath().resolve(filename).normalize();
            org.springframework.core.io.Resource resource = new org.springframework.core.io.FileSystemResource(file);
            if (!resource.exists()) return ResponseEntity.notFound().build();
            String lower = file.getFileName().toString().toLowerCase();
            String ct = lower.endsWith(".png") ? "image/png"
                      : lower.endsWith(".webp") ? "image/webp"
                      : "image/jpeg";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(ct)).body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/images/upload")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sku") String sku) {
        try {
            java.nio.file.Path base = imagesBasePath();
            String ext = "";
            String orig = file.getOriginalFilename();
            if (orig != null && orig.contains(".")) ext = orig.substring(orig.lastIndexOf('.'));

            // Nombre: SKU-N.ext donde N es el siguiente disponible
            long count = Files.list(base)
                .filter(p -> p.getFileName().toString().startsWith(sku + "-") || p.getFileName().toString().startsWith(sku + "."))
                .count();
            String filename = sku + "-" + (count + 1) + ext;
            java.nio.file.Path dest = base.resolve(filename);
            Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

            Map<String, String> res = new HashMap<>();
            res.put("filename", filename);
            res.put("url", "/images/file/" + filename);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/images/file/{filename:.+}")
    public ResponseEntity<Map<String, String>> deleteImage(@PathVariable String filename) {
        try {
            java.nio.file.Path file = imagesBasePath().resolve(filename).normalize();
            if (!Files.exists(file)) return ResponseEntity.notFound().build();
            Files.delete(file);
            return ResponseEntity.ok(Map.of("deleted", filename));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/images/folder")
    public ResponseEntity<Map<String, String>> createFolder(@RequestBody Map<String, String> body) {
        try {
            String name = body.get("name");
            if (name == null || name.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Nombre requerido"));
            java.nio.file.Path folder = imagesBasePath().resolve(name).normalize();
            if (!folder.startsWith(imagesBasePath())) return ResponseEntity.badRequest().body(Map.of("error", "Ruta inválida"));
            Files.createDirectories(folder);
            return ResponseEntity.ok(Map.of("created", name));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/images/folder/{folderName:.+}")
    public ResponseEntity<Map<String, String>> deleteFolder(@PathVariable String folderName) {
        try {
            java.nio.file.Path folder = imagesBasePath().resolve(folderName).normalize();
            if (!folder.startsWith(imagesBasePath())) return ResponseEntity.badRequest().body(Map.of("error", "Ruta inválida"));
            if (!Files.exists(folder)) return ResponseEntity.notFound().build();
            // Eliminar recursivamente
            Files.walk(folder)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
            return ResponseEntity.ok(Map.of("deleted", folderName));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/images/suggested-folders")
    public ResponseEntity<List<String>> suggestedFolders() {
        try {
            java.nio.file.Path base = imagesBasePath();
            // Carpetas existentes
            java.util.Set<String> existing = new java.util.HashSet<>();
            if (Files.exists(base)) {
                try (Stream<java.nio.file.Path> dirs = Files.walk(base, 1)) {
                    dirs.filter(Files::isDirectory)
                        .filter(p -> !p.equals(base))
                        .forEach(p -> existing.add(p.getFileName().toString().toUpperCase()));
                }
            }
            // SKUs de productos sin carpeta
            List<String> suggested = productService.findAll().stream()
                .map(LaudusProduct::getSku)
                .filter(sku -> sku != null && !sku.isBlank())
                .map(String::toUpperCase)
                .filter(sku -> !existing.contains(sku))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
            return ResponseEntity.ok(suggested);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    // ── Endpoints Flutter: sync directo ──────────────────────────
    @PostMapping("/sync/products")
    public ResponseEntity<Map<String, Object>> syncProducts() {
        try {
            restLaudusWordP.ProductGetAllLaudus();
            List<LaudusProduct> products = productService.findAll();
            return ResponseEntity.ok(Map.of("status", "ok", "count", products.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/sync/stock")
    public ResponseEntity<Map<String, Object>> syncStock() {
        try {
            List<String> skus = restLaudusWordP.ProductGetAllLaudusStock("008"); // bodega real con stock
            int total = skus != null ? skus.size() : 0;
            return ResponseEntity.ok(Map.of("status", "ok", "updated", total));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // ── Auto-Sync Stock Scheduler ─────────────────────────────────
    @GetMapping("/sync/stock/auto")
    public ResponseEntity<Map<String, Object>> getSyncAutoStatus() {
        Map<String, Object> res = new HashMap<>();
        res.put("enabled", stockSyncScheduler.isEnabled());
        res.put("intervalMinutes", stockSyncScheduler.getIntervalMinutes());
        res.put("lastRun", stockSyncScheduler.getLastRun());
        res.put("nextRun", stockSyncScheduler.getNextRun());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/sync/stock/auto")
    public ResponseEntity<Map<String, Object>> setSyncAuto(
            @RequestParam(defaultValue = "true") boolean enabled,
            @RequestParam(defaultValue = "60") int intervalMinutes) {
        if (enabled) {
            stockSyncScheduler.start(intervalMinutes);
        } else {
            stockSyncScheduler.stop();
        }
        return getSyncAutoStatus();
    }

}