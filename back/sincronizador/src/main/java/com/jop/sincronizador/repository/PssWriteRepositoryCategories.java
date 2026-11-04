package com.jop.sincronizador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.jop.sincronizador.data.local.model.entity.Categorie;
@Repository
public interface PssWriteRepositoryCategories extends JpaRepository<Categorie, String> {

    @Query("SELECT COALESCE(c.idWpCategorie, 0) FROM Categorie c WHERE c.laudusId = :parentId")
    Integer findIdByparentID(@Param("parentId") String parentId);
}
