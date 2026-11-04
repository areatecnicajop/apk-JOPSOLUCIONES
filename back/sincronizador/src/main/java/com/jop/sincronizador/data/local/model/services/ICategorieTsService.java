
package com.jop.sincronizador.data.local.model.services;


import java.util.Optional;

import com.jop.sincronizador.data.local.model.entity.CategorieRegisterChange;

public interface ICategorieTsService {

	public void add(CategorieRegisterChange ts );

	public void update(CategorieRegisterChange ts);

	// public Integer findIdByparentID(String id);


}
	
 
