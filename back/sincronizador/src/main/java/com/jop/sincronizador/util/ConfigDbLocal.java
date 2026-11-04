// package com.jop.sincronizador.util;

// import javax.sql.DataSource;
// import org.springframework.beans.factory.annotation.Qualifier;
// import org.springframework.boot.context.properties.ConfigurationProperties;
// import org.springframework.boot.jdbc.DataSourceBuilder;
// import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
// import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
// import org.springframework.orm.jpa.JpaTransactionManager;
// import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
// import org.springframework.transaction.PlatformTransactionManager;
// import org.springframework.transaction.annotation.EnableTransactionManagement;
// import com.zaxxer.hikari.HikariDataSource;
// import jakarta.persistence.EntityManagerFactory;

// @Configuration
// @EnableTransactionManagement
// @EnableJpaRepositories(
//     entityManagerFactoryRef = "localEntityManagerFactory",
//     transactionManagerRef = "localTransactionManager",
//     basePackages = {"com.jop.sincronizador.data.local.model.repository"}
// )
// public class ConfigDbLocal {

//     @Bean(name = "localDataSource")
//     @ConfigurationProperties(prefix = "spring.datasource")
//      HikariDataSource dataSource() {
//         return DataSourceBuilder.create().type(HikariDataSource.class).build();
//     }

//     @Bean(name = "localEntityManagerFactory")
//      LocalContainerEntityManagerFactoryBean localEntityManagerFactory(
//             EntityManagerFactoryBuilder builder,
//             @Qualifier("localDataSource") DataSource dataSource) {
//         return builder.dataSource(dataSource)
//                 .packages("com.jop.sincronizador.data.local.model.entity")
//                 .persistenceUnit("mysql")
//                 .build();
//     }

//     @Bean(name = "localTransactionManager")
//      PlatformTransactionManager localTransactionManager(
//             @Qualifier("localEntityManagerFactory") EntityManagerFactory localEntityManagerFactory) {
//         return new JpaTransactionManager(localEntityManagerFactory);
//     }
// }
