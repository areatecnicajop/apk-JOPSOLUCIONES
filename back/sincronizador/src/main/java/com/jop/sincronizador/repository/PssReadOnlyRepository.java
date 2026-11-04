package com.jop.sincronizador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.jop.sincronizador.data.local.model.entity.LaudusProduct;
import java.util.List;

@Repository
public interface PssReadOnlyRepository extends JpaRepository<LaudusProduct, Long> {
    
    List<LaudusProduct> findAll(); 
     // 🔹 Spring Data JPA ya proporciona esto, no es necesario Query manual
    List<LaudusProduct> findBySkuIn(List<String> skus);
}
