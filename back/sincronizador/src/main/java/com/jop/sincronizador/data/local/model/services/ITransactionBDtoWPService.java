package com.jop.sincronizador.data.local.model.services;



import java.util.List;

import com.jop.sincronizador.data.local.model.entity.Categorie;
import com.jop.sincronizador.data.local.model.entity.CategorieRegisterChange;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.RegisterChange;

public interface ITransactionBDtoWPService {

    public void Synchro( List<String>skus,Boolean includeImages,Boolean forceUpdate );
    public void initCategorie();
    public void SynchroCategories( List<Categorie> categories );

	public void addSite(RegisterChange pro,LaudusProduct laudusProduct)  ;
	public void addSiteCategorie(CategorieRegisterChange json , Categorie categorie )  ;
	public void deleteGalery(RegisterChange registerChange)  ;
    public void updateSite(RegisterChange json);
    public void updateSiteCategorie(CategorieRegisterChange json );


}
