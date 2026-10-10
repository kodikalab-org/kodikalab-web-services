package com.kodika.kodikalab.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadatos de OpenAPI/Swagger y esquema de seguridad Bearer (botón "Authorize" de Swagger UI). */
@Configuration
public class OpenApiConfig {
    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI kodikalabOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        return new OpenAPI()
                .info(new Info()
                        .title("KodikaLab API")
                        .version(build == null ? "desarrollo" : build.getVersion())
                        .description("""
                                API REST de KodikaLab: entrenamiento de programación competitiva por equipos.

                                **Autenticación:** inicie sesión en `POST /auth/login`, copie el valor de `token` y \
                                péguelo (sin la palabra Bearer) en el botón **Authorize**. Los endpoints sin candado \
                                (registro e inicio de sesión) no requieren token. Un token faltante o inválido responde \
                                401; un rol sin permiso, 403."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT obtenido en POST /auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
