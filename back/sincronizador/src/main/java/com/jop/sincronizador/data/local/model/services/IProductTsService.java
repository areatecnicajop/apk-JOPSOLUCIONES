package com.jop.sincronizador.data.local.model.services;

import java.util.List;

import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.RegisterChange;

public interface IProductTsService {

	public void add(RegisterChange ts, boolean forceUpdate );

	public void update(RegisterChange ts);




}
