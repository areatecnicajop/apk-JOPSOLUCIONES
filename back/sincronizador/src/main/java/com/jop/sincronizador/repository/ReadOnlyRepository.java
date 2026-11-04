package com.jop.sincronizador.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface ReadOnlyRepository<T, ID> extends JpaRepository<T, ID> {
    // Aquí podrías definir métodos comunes de solo lectura, si lo requieres
}
