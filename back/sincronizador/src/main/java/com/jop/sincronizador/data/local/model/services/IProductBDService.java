package com.jop.sincronizador.data.local.model.services;

import java.util.List;

import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.LaudusStock;

public interface IProductBDService {


    public List<LaudusProduct> findAll();

    public void updateProduct(LaudusProduct laudusProduct);
    public void updateCategorie(Categorie categorie);
    public void addCategorie(Categorie categorie);
    public Integer findStockBySku(String sku);
    public List<LaudusProduct> addProductAll(List<LaudusProduct> products);
    public void addCategoriesAll(List<Categorie> categories);
    public List<String> updateStockLaudus(List<LaudusStock> stocks);
    public List<Categorie> findAllCategorie();
    public void deleteProductAll(List<LaudusProduct> products);
    public List<LaudusProduct> findBySkuIn(List<String> skus);
    public Integer findIdByparentID(String laudusId);
    public boolean setForceZeroStock(String sku, boolean value);
    


    
}
