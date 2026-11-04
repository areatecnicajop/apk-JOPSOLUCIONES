package com.jop.sincronizador.data.local.model.entity;


import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
@Entity(name = "config_wordpress")
public class ConfigWordPress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url_base")
    private String urlBase;

    @Column(name = "username_token")
    private String usernameToken;

    @Column(name = "password_token")
    private String passwordToken;

    @Column(name = "images_path_local")
    private String imagesPathLocal;

    @Column(name = "wp_api_url")
    private String wpApiUrl;

    @Column(name = "wp_application_password")
    private String wpApplicationPassword;

    @Column(name = "api_laudus_url")
    private String apiLaudusUrl;

    @Column(name = "date_expirate_token")
    private String dateExpirateToken;

    @Column(name = "token_laudus_api", columnDefinition = "TEXT")
    private String tokenLaudusApi;
    
    @Column(name = "user_name_auth")
    private String userNameAuth;

    @Column(name = "password_auth")
    private String passwordAuth;

    @Column(name = "companyVATId_auth")
    private String companyVATIdAuth;
    
    @Column(name = "url_token_laudus")
    private String urlTokenLaudus;
    
}
