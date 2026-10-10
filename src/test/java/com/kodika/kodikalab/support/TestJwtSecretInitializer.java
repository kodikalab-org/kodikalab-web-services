package com.kodika.kodikalab.support;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Fija {@code jwt.secret} con la clave de pruebas en todos los contextos de Spring de los tests, con mayor
 * prioridad que las variables de entorno: un {@code JWT_SECRET} vacio o ajeno en el entorno no rompe la suite.
 */
public class TestJwtSecretInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of("jwt.secret=" + TestJwt.SECRET).applyTo(context);
    }
}
