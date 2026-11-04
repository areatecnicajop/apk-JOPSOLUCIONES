package com.jop.sincronizador.data.local.model.services;


import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.CategorieRegisterChange;
import com.jop.sincronizador.data.local.model.entity.ConfigWordPress;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.RegisterChange;
import com.jop.sincronizador.data.local.model.repository.ILaudusProduct;
import com.jop.sincronizador.models.entity.dto.ImageDTO;
import com.jop.sincronizador.models.entity.dto.Product;
import com.jop.sincronizador.models.entity.dto.ProductImageDetail;
import com.jop.sincronizador.models.entity.dto.ProductListImages;
import com.jop.sincronizador.models.entity.dto.ProductUpdateImage;
import com.jop.sincronizador.services.IRestLaudusWordP;
import com.jop.sincronizador.services.ImageProcessor;
import com.jop.sincronizador.util.ImageFileUtil;
import com.jop.sincronizador.webSockets.WebSocket;

import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;


@Service
public class TransactionBDtoWPService implements ITransactionBDtoWPService {

    @Autowired
    private ImageFileUtil imageFileUtil;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private IProductBDService bd;
    @Autowired
    private ILaudusProduct bdPRoduct;

    
    @Autowired
    private ImageProcessor imageProcessor; 
    
    @Autowired
    private IRestLaudusWordP iRest;

    @Autowired
    private IProductTsService dao;
    @Autowired
    private ICategorieTsService daoCategorie;

    private List<LaudusProduct> cwpList;
    private List<Categorie> cwpListcategories;

    private Boolean includeImages;

    private Boolean forceUpdate;



    private final String NO_IMAGE = "NO_IMAGE";
    private final String empty = "";


    // Se ejecuta después de que todas las dependencias hayan sido inyectadas
    @PostConstruct
    public void init() {
        // Obtener todos los productos de la base de datos
        // SynchroIdWP();
        // Llamar a la sincronización después de actualizar los IDs
        // Synchro();
        // SynchroIdWPCategorie();
        String tokenUpdate = iRest.updateToken();
        System.out.println(tokenUpdate);
    }

    public void initCategorie() {
        // Obtener todos los productos de la base de datos

        cwpListcategories = bd.findAllCategorie();

        SynchroCategories(cwpListcategories);


        // Llamar a la sincronización después de actualizar los IDs
        // Synchro();
    }

    public void SynchroIdWP(RegisterChange pro){
        
        cwpList = bd.findAll();


        // Recorrer la lista y actualizar idWP si está vacío
        for (LaudusProduct product : cwpList) {
            String idWP = String.valueOf(iRest.ProductGetId(product.getSku())); // Obtener ID de WordPress
            product.setIdWP(idWP); // Asignar ID
            bd.updateProduct(product); // Guardar el cambio en la BD
        }


    }

    public void SynchroIdWPCategorie(){


        cwpListcategories = bd.findAllCategorie();

        // Recorrer la lista y actualizar idWP si está vacío
        for (Categorie categorie : cwpListcategories) {
            Integer idWP = iRest.ProductGetIdCategorie(categorie.getIdWpCategorie()); // Obtener ID de WordPress
            categorie.setIdWpCategorie(idWP); // Asignar ID
            bd.updateCategorie(categorie); // Guardar el cambio en la BD
        }


    }


    public void SynchroCategories(List<Categorie> categories) {
        // Ordenar la lista de categorías por positionPath de menor a mayor
        categories.sort(Comparator.comparingInt(Categorie::getPositionPath));

        for (Categorie categorie : categories) {
            // Convertir el producto de Laudus a formato local
            CategorieRegisterChange localCategorie = convertLaudusToLocalCategorie(categorie);

            // Asegurar que se agregan en el orden correcto
            addOrUpdateSiteCategorie(localCategorie,categorie);
        }
    }


    // Público para reusarlo desde el preview de "qué categoría se va a subir
    // a WooCommerce" (CategoriasWebController), igual que se hizo con
    // convertLaudusToLocal para productos.
    public CategorieRegisterChange convertLaudusToLocalCategorie(Categorie categorie) {
        CategorieRegisterChange categorieRegisterChange = new CategorieRegisterChange();

        Integer idParent = bd.findIdByparentID(categorie.getParentId());
        int idParentValue = (idParent != null) ? idParent : 0;


        // Asignar valores a los nuevos campos
        categorieRegisterChange.setName(categorie.getLaudusName());
        categorieRegisterChange.setSlug(generateSlugFromPath(categorie.getLaudusPath()));
        categorieRegisterChange.setParent(idParentValue); // Por defecto, se puede cambiar si se necesita jerarquía
        categorieRegisterChange.setIdWpCategorie(categorie.getIdWpCategorie()); // Por defecto, se puede cambiar si se necesita jerarquía

        // Agregar metadata con el custom_id



        return categorieRegisterChange;
    }

    private String generateSlugFromPath(String laudusPath) {
        return Arrays.stream(laudusPath.split("\\\\")) // Divide por "\"
                .map(String::trim) // Elimina espacios alrededor de cada parte
                .filter(part -> !part.isEmpty()) // Descarta partes vacías
                .map(part -> part.toLowerCase()
                        .replaceAll("[^a-z0-9 ]", "") // Elimina caracteres especiales excepto espacios
                        .replace(" ", "-")) // Reemplaza espacios por "-"
                .collect(Collectors.joining("-")); // Une con "-"
    }



    // Método de sincronización que se ejecuta desde init()
    public void Synchro(List<String> skus, Boolean includeImages,Boolean forceUpdate) {

        this.includeImages = includeImages;
        this.forceUpdate = forceUpdate;
        System.err.println("Forzar actualizacion ? :"+forceUpdate);
        System.err.println("Incluir imagenes ? : " +includeImages);
        try {
            // SynchroIdWP();
            // Si la lista de SKUs es null o está vacía, usa cwpList como está
            if (skus == null || skus.isEmpty()) {
                cwpList = iRest.ProductGetAllLaudus();
                String logMessage = "Cantidad de productos a procesar (todos): " + cwpList.size();
                System.out.println(logMessage);
            } else {
                // Si la lista de SKUs tiene valores, obtener los productos desde la BD
                cwpList = bd.findBySkuIn(skus);
                String logMessage = "Cantidad de productos filtrados por SKUs: " + cwpList.size();
                System.out.println(logMessage);
            }


            // Procesar productos de cwpList (ya sea filtrada o completa)

            for (LaudusProduct productLaudus : cwpList) {
                // Obtener el ID del producto en la base de dato

                // Convertir el producto de Laudus a formato local
                RegisterChange localProduct = convertLaudusToLocal(productLaudus);

                
                addOrUpdateSite(localProduct , productLaudus );


            }

          
        } catch (Exception e) {
            String errorMessage = "❌ Error durante la sincronización: " + e.getMessage();
            System.err.println(errorMessage);
            WebSocket.sendMessageToAll(errorMessage);
        }
    }



    // Público para poder reusarlo desde el preview de "qué se va a subir a
    // WooCommerce" (ProductosWebController) — así el preview usa exactamente
    // la misma lógica que el push real, sin duplicarla.
    public RegisterChange convertLaudusToLocal(LaudusProduct producLaudus) {

        Integer idwpCategorie = bd.findIdByparentID(producLaudus.getProductCategoryId());

        RegisterChange registerChange = new RegisterChange();

        // Si el SKU no tiene entrada en laudus_stock, no tiene stock conocido:
        // se deja null para que addOrUpdateSite pueda detectarlo y omitir la actualización.
        Integer stockQuantity = bd.findStockBySku(producLaudus.getSku());
        // Override manual: si está activo, se manda 0 a WooCommerce sin importar
        // lo que reporte Laudus (pero solo si había stock conocido; un SKU sin
        // fila en laudus_stock sigue omitiéndose como antes).
        if (Boolean.TRUE.equals(producLaudus.getForceZeroStock()) && stockQuantity != null) {
            stockQuantity = 0;
        }
        String publish = producLaudus.getWebStatus() == false ? "draft" : "publish";
        String idGenerado = producLaudus.getSku()+producLaudus.getModifiedAt();
        // Asignar valores a los nuevos campos
        registerChange.setSku(producLaudus.getSku());
        registerChange.setName(producLaudus.getDescription());
        registerChange.setPrice(producLaudus.getUnitPriceWithTaxes());
        registerChange.setRegular_price(producLaudus.getUnitPriceWithTaxes() != null
            ? producLaudus.getUnitPriceWithTaxes().toString() : "0");
        registerChange.setStock_quantity(stockQuantity);
        registerChange.setStatus(publish);
        registerChange.setShortDescription(producLaudus.getWebDescription());
        registerChange.setIdWP(producLaudus.getIdWP());
        registerChange.setManage_stock(true);
        registerChange.setIdWpCategorie(idwpCategorie);

        registerChange.setIdTransaction(idGenerado);

        return registerChange;
    }
    private void addOrUpdateSite(RegisterChange pro, LaudusProduct laudusProduct ) {
        // GUARD: si el stock es null, el producto no tiene entrada en laudus_stock.
        // No enviar a WordPress para evitar pisar stock con 0 por falta de dato.
        if (pro.getStock_quantity() == null) {
            String msg = "⚠️ SIN STOCK | " + pro.getSku() + " | no existe en Laudus, omitido.";
            System.out.println(msg);
            WebSocket.sendMessageToAll(msg);
            return;
        }

        int productId = iRest.ProductGetId(pro.getSku());

        if (productId == 0) {
            String msg = "🚫 NO EN WEB | " + pro.getSku() + " | no existe en WooCommerce, no se puede actualizar.";
            System.out.println(msg);
            WebSocket.sendMessageToAll(msg);
            addSite(pro, laudusProduct);
        } else {
            // ⚠️ Si NO es 0, actualiza si no existe transacción o si forceUpdate está activado
            boolean transactionExists = bdPRoduct.existsById(pro.getIdTransaction());
            if (!transactionExists || forceUpdate) {
                updateSite(pro);
            }
        }
    }
    
        private void addOrUpdateSiteCategorie(CategorieRegisterChange regCategorie, Categorie categorie ) {
            int productId = iRest.ProductGetIdCategorie(regCategorie.getIdWpCategorie());
            if (productId == 0) {
                addSiteCategorie(regCategorie,categorie);
            } else {
                updateSiteCategorie(regCategorie);
            }

        }


        // @Transactional
        // public void addSiteCategorie(CategorieRegisterChange regCategorie) {
        //     ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
        //     GsonBuilder b = new GsonBuilder().disableHtmlEscaping();
        //     Gson gson = b.excludeFieldsWithoutExposeAnnotation().create();
        //     daoCategorie.add(regCategorie);

        //     // 🔥 Crear el producto en WordPress y obtener su ID
        //     String idwpCategorie = iRest.CategoriePost(gson.toJson(regCategorie));

        //     regCategorie.setIdWpCategorie(Integer.parseInt(idwpCategorie));
        //     // registrar movimiento
        //     // dao.add(regCategorie, forceUpdate);

        //     String logMessage = "🟢 Categoria creado en WordPress con ID: " + idwpCategorie;
        //     System.out.println(logMessage);
        //     WebSocket.sendMessageToAll(logMessage);

        //     // 🔥 Sincronizar ID de WooCommerce
        // }


        @Transactional
        public void addSiteCategorie(CategorieRegisterChange regCategorie , Categorie categorie) {
            daoCategorie.add(regCategorie);
            // 🔥 Crear la categoría en WordPress y obtener su ID
            String idwpCategorie = iRest.CategoriePost(transformCategorieToJson(regCategorie)).trim();  // Quitar espacios extra

            // Verificar que la respuesta sea un número antes de convertir
            if (idwpCategorie.matches("\\d+")) {  // Solo números
                categorie.setIdWpCategorie(Integer.parseInt(idwpCategorie));

                String logMessage = "🟢 Categoría creada en WordPress con ID: " + idwpCategorie;
                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage);
                bd.updateCategorie(categorie);


            } else {
                String logMessage = "❌ Error al crear la categoría en WordPress. Respuesta: " + idwpCategorie;
                System.out.println(logMessage);
                WebSocket.sendMessageToAll(logMessage);

                regCategorie.setIdWpCategorie(0); // Evita valores inválidos en la BD
            }
            // SynchroIdWPCategorie();
            // 🔥 Sincronizar ID de WooCommerce si es necesario
        }



    @Transactional
public void addSite(RegisterChange pro, LaudusProduct laudusProduct) {
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    GsonBuilder b = new GsonBuilder().disableHtmlEscaping();
    Gson gson = b.excludeFieldsWithoutExposeAnnotation().create();

    String jsonBody = transformProductToJson(pro);
    // iRest.ProductPost(jsonBody);
    // 🔥 Crear el producto en WordPress y obtener su ID
    String idwp = iRest.ProductPost(jsonBody);
    pro.setTransactionDescription("add");
    pro.setIdTransaction(pro.getIdTransaction());
    laudusProduct.setIdWP(idwp);
    dao.add(pro, forceUpdate);
    // dao.add(pro,forceUpdate);
    bd.updateProduct(laudusProduct);
     String logMessage = "🟢 Producto creado en WordPress con ID: " + idwp;
    System.out.println(logMessage);
    WebSocket.sendMessageToAll(logMessage);

    // 🔥 Sincronizar ID de WooCommerce
    // SynchroIdWP();

    // 🔥 Solo ejecutar la lógica de imágenes si includeImages es true
    if (includeImages) {
        List<ImageDTO> imagenes = imageFileUtil.getImageDTOs(cwp.getImagesPathLocal(), pro.getSku());
        List<ProductImageDetail> idImagesProduct = new ArrayList<>();

        if (imagenes == null || imagenes.isEmpty()) {
            logMessage = "⚠️ No hay imágenes disponibles para el SKU: " + pro.getSku();
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage);
        } else {
            for (int i = 0; i < imagenes.size(); i++) {
                try {
                    logMessage = "📸 Subiendo imagen - Posición: " + (i + 1) + " | SKU: " + pro.getSku();
                    System.out.println(logMessage);
                    WebSocket.sendMessageToAll(logMessage);
            
                    // 1. Tomo el DTO
                    ImageDTO dto = imagenes.get(i);
            
                    // 2. Decodifico Base64
                    byte[] original = Base64.getDecoder().decode(dto.getBase64Content());
            
                    // 3. Redimensiono a 1000×10000
                    byte[] resized  = imageProcessor.resize(original);
            
                    // 4. Re-codifico a Base64 y actualizo el DTO
                    dto.setBase64Content(Base64.getEncoder().encodeToString(resized));
            
                    // 5. Subo la imagen redimensionada y obtengo su ID
                    String imageId = iRest.productMediaPost(dto, pro.getSku(), String.valueOf(i + 1));
            
                    if (imageId != null && !imageId.isEmpty()) {
                        logMessage = "✅ Imagen subida con ID: " + imageId;
                        System.out.println(logMessage);
                        WebSocket.sendMessageToAll(logMessage);
            
                        ProductImageDetail detail = new ProductImageDetail();
                        detail.setId(imageId);
                        detail.setPosition(i + 1);
                        detail.setAlt(pro.getSku());
                        idImagesProduct.add(detail);
                    } else {
                        logMessage = "❌ Error: La imagen no se subió correctamente en la posición " + (i + 1);
                        System.out.println(logMessage);
                        WebSocket.sendMessageToAll(logMessage);
                    }
            
                } catch (Exception e) {
                    logMessage = "🚨 Error al procesar/subir la imagen en la posición " + (i + 1) + " | SKU: " + pro.getSku();
                    System.err.println(logMessage);
                    WebSocket.sendMessageToAll(logMessage);
                    e.printStackTrace();
                }
            }
            
            // 🔥 Actualizar imágenes en WordPress
            ProductUpdateImage update = new ProductUpdateImage(idImagesProduct);
            String jsonOutput = gson.toJson(update);
            iRest.ProductPut(idwp, jsonOutput, "Asignación de imágenes");

            logMessage = "🔄 Imágenes actualizadas en WordPress para el SKU: " + pro.getSku();
            System.out.println(logMessage);
            WebSocket.sendMessageToAll(logMessage);
        }
    }
}



@Override
public void deleteGalery(RegisterChange pro) {
    // 🔥 Obtener la lista de IDs de imágenes del producto
    ProductListImages idImagenes = iRest.ProductGetImages(pro.getSku());

    if (idImagenes.getProductImagesIds().isEmpty()) {
        String logMessage = "⚠️ No hay imágenes para eliminar en SKU: " + pro.getSku();

        System.out.println(logMessage);
        WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket

        return;
    }

    for (String id : idImagenes.getProductImagesIds()) {
        iRest.productMediaDelete(id);

        String logMessage = "🗑️ Imagen eliminada con éxito - ID: " + id + " | SKU: " + pro.getSku();

        System.out.println(logMessage);
        WebSocket.sendMessageToAll(logMessage); // 🔥 Enviar a WebSocket
    }
}

@Override
public void updateSiteCategorie(CategorieRegisterChange regCate) {
    // 🔥 Actualiza la información del producto en WordPress
    iRest.CategoriePut(regCate.getIdWpCategorie(), transformCategorieToJson(regCate));

    // regCate.setTransactionDescription("update");

    // dao.add(regCate, forceUpdate);

    String logMessage = "🔄 Producto actualizado en WordPress : " + regCate.getName();
    System.out.println(logMessage);
    WebSocket.sendMessageToAll(logMessage);

}


@Override
public void updateSite(RegisterChange pro) {
    ConfigWordPress cwp = context.getBean("getConfigWordPress", ConfigWordPress.class);
    GsonBuilder b = new GsonBuilder().disableHtmlEscaping();
    Gson gson = b.excludeFieldsWithoutExposeAnnotation().create();

    String jsonBody = transformProductToJson(pro);

    // 🔥 Actualiza la información del producto en WordPress
    int responseCode = iRest.ProductPut(pro.getIdWP(), jsonBody, "actualizado");

    pro.setTransactionDescription("update");
    dao.add(pro, forceUpdate);

    String stockStr = pro.getStock_quantity() == 0 ? "⚠️ stock=0" : "stock=" + pro.getStock_quantity();
    String logMessage = responseCode == 200
        ? "✅ OK | " + pro.getSku() + " | " + stockStr
        : "❌ FALLO | " + pro.getSku() + " | HTTP " + responseCode;
    System.out.println(logMessage);
    WebSocket.sendMessageToAll(logMessage);

    // 🔥 Solo ejecuta la lógica de imágenes si includeImages es true y ProductPut fue exitoso (200)
    if (includeImages && responseCode == 200) {
        List<ImageDTO> imagenes = imageFileUtil.getImageDTOs(cwp.getImagesPathLocal(), pro.getSku());
        List<ProductImageDetail> idImagesProduct = new ArrayList<>();

        if (imagenes == null || imagenes.isEmpty()) {
            logMessage = "⚠️ No hay imágenes disponibles para el SKU: " + pro.getSku();
            System.out.println(logMessage);
            sendWebSocketMessage(logMessage);
        } else {
            deleteGalery(pro); // 🔥 Eliminamos la galería antes de actualizar
            logMessage = "🗑️ Galería de imágenes eliminada para el SKU: " + pro.getSku();
            System.out.println(logMessage);
            sendWebSocketMessage(logMessage);

            for (int i = 0; i < imagenes.size(); i++) {
                try {
                    logMessage = "📸 Subiendo imagen - Posición: " + (i + 1) + " | SKU: " + pro.getSku();
                    System.out.println(logMessage);
                    WebSocket.sendMessageToAll(logMessage);
            
                    ImageDTO dto = imagenes.get(i);
                    // 1. Decodificar Base64
                    byte[] original = Base64.getDecoder().decode(dto.getBase64Content());
                    // 2. Redimensionar a 1000×10000
                    byte[] resized  = imageProcessor.resize(original);
                    // 3. Volver a codificar y actualizar DTO
                    dto.setBase64Content(Base64.getEncoder().encodeToString(resized));
            
                    // 🔥 Subir la imagen redimensionada y obtener su ID
                    String imageId = iRest.productMediaPost(dto, pro.getSku(), String.valueOf(i + 1));
            
                    if (imageId != null && !imageId.isEmpty()) {
                        logMessage = "✅ Imagen subida con ID: " + imageId;
                        System.out.println(logMessage);
                        WebSocket.sendMessageToAll(logMessage);
            
                        ProductImageDetail detail = new ProductImageDetail();
                        detail.setId(imageId);
                        detail.setPosition(i + 1);
                        detail.setAlt(pro.getSku());
                        idImagesProduct.add(detail);
                    } else {
                        logMessage = "❌ Error: La imagen no se subió correctamente en la posición " + (i + 1);
                        System.out.println(logMessage);
                        WebSocket.sendMessageToAll(logMessage);
                    }
            
                } catch (Exception e) {
                    logMessage = "🚨 Error al procesar/subir la imagen en la posición " + (i + 1) + " | SKU: " + pro.getSku();
                    System.err.println(logMessage);
                    WebSocket.sendMessageToAll(logMessage);
                    e.printStackTrace();
                }
            }
            

            // 🔥 Actualizar imágenes en WordPress
            ProductUpdateImage update = new ProductUpdateImage(idImagesProduct);
            String jsonOutput = gson.toJson(update);
            iRest.ProductPut(pro.getIdWP(), jsonOutput, "Asignación de imágenes");

            logMessage = "🔄 Imágenes actualizadas en WordPress para el SKU: " + pro.getSku();
            System.out.println(logMessage);
        }
    }
}


// Método auxiliar para enviar mensajes a través del WebSocket de forma sincronizada
private void sendWebSocketMessage(String message) {
    synchronized (WebSocket.class) {
        WebSocket.sendMessageToAll(message);
    }
}

 public static String transformProductToJson(RegisterChange product) {
        Map<String, Object> productData = new HashMap<>();

        productData.put("sku", product.getSku());
        productData.put("name", product.getName());
        productData.put("stock_quantity", product.getStock_quantity());
        productData.put("price", product.getPrice());
        productData.put("regular_price", product.getRegular_price());
        productData.put("short_description", product.getShortDescription());
        productData.put("status", product.getStatus());
        productData.put("manage_stock", product.isManage_stock());

        // 🔥 Transformar idWpCategorie en el formato correcto para categories
        List<Map<String, Integer>> categories = new ArrayList<>();
        if (product.getIdWpCategorie() != null) {
            categories.add(Collections.singletonMap("id", product.getIdWpCategorie()));
        }
        productData.put("categories", categories);

        // 🔥 Convertir a JSON
        return new Gson().toJson(productData);
    }

    // Mismo Gson usado en addSiteCategorie/updateSiteCategorie — extraído acá
    // para no duplicarlo, y para poder reusarlo desde el preview.
    public static String transformCategorieToJson(CategorieRegisterChange categorie) {
        Gson gson = new GsonBuilder().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().create();
        return gson.toJson(categorie);
    }

}


