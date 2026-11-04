package com.jop.sincronizador.services;

import java.util.List;

import com.jop.sincronizador.data.laudus.model.entity.OrderWP;
import com.jop.sincronizador.data.laudus.model.entity.ProductWP;
import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.LaudusStock;
import com.jop.sincronizador.data.local.model.entity.RegisterChange;
import com.jop.sincronizador.data.local.model.entity.TokenLaudus;
import com.jop.sincronizador.models.entity.dto.ImageDTO;
import com.jop.sincronizador.models.entity.dto.ProductListImages;

public interface IRestLaudusWordP {

  public Integer ProductPut(String id,String json,String message); 

  public String CategoriePut(Integer id,String json); 
 
  public String CategoriePost(String json); 
 

  public int ProductGetId(String sku ); 

  public int ProductGetIdCategorie(Integer  idwpCategorie ); 

  public ProductListImages ProductGetImages(String sku ); 
 

  public String productMediaDelete(String imageId ); 

  public List<ProductWP> ProductGetAll();

  public List<OrderWP> ProductGetAllOrders();

  public String ProductPost(String json);

  public String productMediaPost(ImageDTO imageDto,String sku , String position);

  public List<LaudusProduct> ProductGetAllLaudus();

  public List<com.jop.sincronizador.models.entity.dto.CatalogProductExtended> ProductGetAllLaudusForCatalog();

  public List<Categorie> CategoriesGetAllLaudus();

  public List<String> ProductGetAllLaudusStock(String warehouseId);

  public String updateToken();

  public com.jop.sincronizador.data.local.model.entity.ConfigWordPress getConfig();


  
  
}
