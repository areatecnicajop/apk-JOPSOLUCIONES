package com.jop.sincronizador.data.local.model.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.LaudusStock;
import com.jop.sincronizador.repository.PssReadOnlyRepository;
import com.jop.sincronizador.repository.PssWriteRepository;
import com.jop.sincronizador.repository.PssWriteRepositoryCategories;
import com.jop.sincronizador.repository.PssWriteRepositoryStock;
import com.jop.sincronizador.webSockets.WebSocket;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;


@Service
public class ProductBDImplService implements IProductBDService{
    
	@Autowired
	private PssReadOnlyRepository onlyRepository;
	@Autowired
	private PssWriteRepositoryCategories categoriesService;

	@Autowired
	private PssWriteRepository writeRepository;

	@Autowired
	private PssWriteRepositoryStock writeRepositoryStock;
    
    @Autowired
	private PssWriteRepositoryCategories writeRepositoryCategories;

    


    @Override
	public List<LaudusProduct> findAll() {

		return onlyRepository.findAll();
	}
    
	@Override
	public List<LaudusProduct> findBySkuIn(List<String> skus) {
		return onlyRepository.findBySkuIn(skus);
	}
	
	@Override
    public Integer findStockBySku(String sku) {
    return writeRepositoryStock.findStockBySku(sku);
}

	
@Transactional
public List<LaudusProduct> addProductAll(List<LaudusProduct> products) {
    Map<String, LaudusProduct> productMap = new HashMap<>();
    List<LaudusProduct> existingProducts = writeRepository.findAll();
    Map<String, LaudusProduct> existingProductMap = existingProducts.stream()
        .collect(Collectors.toMap(LaudusProduct::getSku, prod -> prod));

    List<LaudusProduct> sortedProducts = new ArrayList<>();

    for (LaudusProduct product : products) {
        LaudusProduct existingProduct = existingProductMap.get(product.getSku());

        if (existingProduct != null) {
            // Solo si hay cambios
            if (!Objects.equals(product.getModifiedAt(), existingProduct.getModifiedAt())) {
                product.setIdWP(existingProduct.getIdWP()); // conservar idWP
                product.setForceZeroStock(existingProduct.getForceZeroStock()); // conservar override manual
                sortedProducts.add(product);
            }
        } else {
            // Producto nuevo
            sortedProducts.add(product);
        }

        productMap.put(product.getSku(), product);
    }

    // Detectar y desactivar productos que ya no vinieron
    Set<String> incomingSkus = productMap.keySet();
    List<LaudusProduct> missingProducts = existingProducts.stream()
        .filter(p -> !incomingSkus.contains(p.getSku()) && p.getWebStatus()) // solo si está activo
        .peek(p -> {
            p.setWebStatus(false);

            // Agregar "DIS" a modifiedAt solo si no lo tiene ya
            String modified = p.getModifiedAt();
            if (modified != null && !modified.endsWith("DIS")) {
                p.setModifiedAt(modified + "DIS");
            }
        })
        .collect(Collectors.toList());

    sortedProducts.addAll(missingProducts);

    if (!sortedProducts.isEmpty()) {
        writeRepository.saveAll(sortedProducts);
    }

    return sortedProducts;
}




@Override
public List<Categorie> findAllCategorie() {
    return categoriesService.findAll();
}

@Transactional
	public void updateCategorie(Categorie categorie) {
    writeRepositoryCategories.save(categorie);  // 🔥 Guarda el producto pero no retorna nada
	}

@Override
public Integer findIdByparentID(String idlaudus) {
return writeRepositoryCategories.findIdByparentID(idlaudus);
}

@Override
@Transactional
public boolean setForceZeroStock(String sku, boolean value) {
    List<LaudusProduct> found = onlyRepository.findBySkuIn(List.of(sku));
    if (found.isEmpty()) return false;
    LaudusProduct product = found.get(0);
    product.setForceZeroStock(value);
    writeRepository.save(product);
    return true;
}
@Override
public void addCategorie(Categorie categorie) {
writeRepositoryCategories.save(categorie);
}


@Transactional
public void addCategoriesAll(List<Categorie> categories) {
    // Mapa para almacenar las categorías por su laudusId
    Map<String, Categorie> categoryMap = new HashMap<>();

    // Obtener todas las categorías existentes en la base de datos antes de hacer cambios
    List<Categorie> existingCategories = writeRepositoryCategories.findAll();
    Map<String, Categorie> existingCategoryMap = existingCategories.stream()
        .collect(Collectors.toMap(Categorie::getLaudusId, cat -> cat));

    // Primero, agregamos todas las categorías al mapa para referencia rápida
    for (Categorie category : categories) {
        // Verificar si la categoría ya existe en la base de datos
        if (existingCategoryMap.containsKey(category.getLaudusId())) {
            Categorie existingCategory = existingCategoryMap.get(category.getLaudusId());

            // 🔹 Mantener `id_wp_categorie` si ya existía en la BD
            category.setIdWpCategorie(existingCategory.getIdWpCategorie());
        }

        categoryMap.put(category.getLaudusId(), category);
    }

    // Ordenar las categorías en niveles jerárquicos
    List<Categorie> sortedCategories = new ArrayList<>();

    for (Categorie category : categories) {
        String[] pathParts = category.getLaudusPath().split("\\\\"); // Dividir el path por \\ (doble escape)
        String parentId = null; // Se permite null por defecto
        int positionPath = pathParts.length - 1; // Calcular la posición jerárquica

        if (pathParts.length == 2) { // Si es una categoría de nivel 1 (abuelo)
            parentId = "0";
            positionPath = 1; // Es el nivel más alto
        } else {
            // Construcción progresiva del path para encontrar el padre real
            StringBuilder pathBuilder = new StringBuilder();
            for (int i = 1; i < pathParts.length - 1; i++) {
                pathBuilder.append("\\").append(pathParts[i]);
            }
            String parentPath = pathBuilder.toString();

            // Buscar el padre en base a laudusPath
            Optional<Categorie> parentCategory = categories.stream()
                .filter(cat -> cat.getLaudusPath().equals(parentPath))
                .findFirst();

            if (parentCategory.isPresent()) {
                parentId = parentCategory.get().getLaudusId();
            }
        }

        // Asignar el parentId solo si no está previamente asignado
        if (category.getParentId() == null) {
            category.setParentId(parentId);
        }

        // Asignar la posición jerárquica
        category.setPositionPath(positionPath);

        // Agregar la categoría al mapa y la lista ordenada si aún no está
        categoryMap.put(category.getLaudusId(), category);
        if (!sortedCategories.contains(category)) {
            sortedCategories.add(category);
        }
    }

    // Guardar en la base de datos en el orden correcto
    writeRepositoryCategories.saveAll(sortedCategories);
}

    


@Transactional
public List<String> updateStockLaudus(List<LaudusStock> stocks) {
    // Obtener los SKUs de los nuevos datos
    List<String> skus = stocks.stream().map(LaudusStock::getSku).toList();

    // Obtener los datos actuales de la base de datos
    List<LaudusStock> existingStocks = writeRepositoryStock.findAllById(skus);

    // Convertir los datos actuales en un mapa para comparación rápida
    Map<String, Integer> stockMap = existingStocks.stream()
        .collect(Collectors.toMap(LaudusStock::getSku, LaudusStock::getStock));

    // Listas para cambios detectados
    List<LaudusStock> updatedStocks = new ArrayList<>();
    List<String> changedProducts = new ArrayList<>();
    List<String> skusChanged = new ArrayList<>(); 

    // Comparar y detectar cambios en el stock
    for (LaudusStock newStock : stocks) {
        Integer currentStock = stockMap.get(newStock.getSku());

        if (currentStock == null || !currentStock.equals(newStock.getStock())) {
            updatedStocks.add(newStock);
            String anterior = (currentStock == null) ? "—" : String.valueOf(currentStock);
            String nuevo    = String.valueOf(newStock.getStock());
            String prefix   = newStock.getStock() == 0 ? "⚠️ STOCK CERO" : "📦";
            changedProducts.add(prefix + " | " + newStock.getSku() + " | " + anterior + " → " + nuevo);
            skusChanged.add(newStock.getSku());
        }
    }

    // Guardar solo los productos cuyo stock cambió
    if (!updatedStocks.isEmpty()) {
        writeRepositoryStock.saveAll(updatedStocks);
    }

    // Mostrar los cambios detectados
    if (!changedProducts.isEmpty()) {
        WebSocket.sendMessageToAll("🔍 " + changedProducts.size() + " productos con cambio de stock:");
        changedProducts.forEach(product -> {
            System.out.println(product);
            WebSocket.sendMessageToAll(product);
        });
    } else {
        String msg = "📢 Sin cambios en el stock. Todo al día.";
        System.out.println(msg);
        WebSocket.sendMessageToAll(msg);
        return Collections.emptyList();
    }

    // Retornar la lista de SKUs que cambiaron
    // writeRepository.updateModifeAtStock(skusChanged);
    return skusChanged;
}


	@Transactional
	public void deleteProductAll(List<LaudusProduct> products) {
    writeRepository.deleteAll(products);  // 🔥 Guarda el producto pero no retorna nada
	}

	

	public void init() {
		List<LaudusProduct> products = findAll();
		// Ejemplo: registrar el tamaño de la lista
		System.out.println("Cantidad de productos cargados: " + products.size());
	}

    @Transactional
	public void updateProduct(LaudusProduct product) {
    writeRepository.save(product);  // 🔥 Guarda el producto pero no retorna nada
	}
	


    
}
