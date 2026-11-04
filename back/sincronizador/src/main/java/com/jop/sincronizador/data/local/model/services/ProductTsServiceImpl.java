package com.jop.sincronizador.data.local.model.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import com.jop.sincronizador.data.local.model.entity.RegisterChange;
import com.jop.sincronizador.data.local.model.repository.ILaudusProduct;
import com.jop.sincronizador.repository.PssReadOnlyRepository;


@Service
public class ProductTsServiceImpl implements IProductTsService{

	@Autowired(required = true)
	PssReadOnlyRepository onlyRepository;

    @Autowired

    private  ILaudusProduct iLaudusProduct;
    

    @Override
    public void update(RegisterChange rc) {

        if (!iLaudusProduct.existsById(rc.getIdTransaction())) {
			iLaudusProduct.save(rc);

		}
    
    }


    @Override
    public void add(RegisterChange ts, boolean forceUpdate) {
        // Verifica si ya existe un registro con el mismo ID
        if (!iLaudusProduct.existsById(ts.getIdTransaction())) {
            iLaudusProduct.save(ts);
        } else {
            if (forceUpdate) {
                // Obtener la fecha y hora actual del sistema en formato YYYY-MM-DD_HH:mm:ss
                String timestamp = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"));
    
                // Concatenar la fecha y hora al ID actual
                ts.setIdTransaction(ts.getIdTransaction() + "_STOCK_" + timestamp);
    
                // Guardar el registro con el nuevo ID
                iLaudusProduct.save(ts);
            } else {
                System.out.println("El registro con ID ya existe. No se guardará.");
            }
        }
    }
    

	
    
    
}
