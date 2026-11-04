package com.jop.sincronizador.data.laudus.model.entity;



import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;

@Entity
@Getter

public class ProductShopSynchro {


    @Id
    private String id;
    private String sku;
    private String name;
    private int stock;
    private int price;
	@Column(name = "short_description")
	private String shortDescription;
    private String img1;
    private String img2;
    private String img3;
    private String img4;
   


    
    
}

   

