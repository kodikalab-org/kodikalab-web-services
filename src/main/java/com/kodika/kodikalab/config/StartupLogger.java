package com.kodika.kodikalab.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupLogger implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    private final Environment environment;

    public StartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        String applicationName = environment.getProperty("spring.application.name", "kodikalab");
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String host = getHostAddress();
        String activeProfiles = String.join(", ", environment.getActiveProfiles());

        if (activeProfiles.isBlank()) {
            activeProfiles = "default";
        }

        log.info("""

                ============================================================
                  {} iniciado correctamente
                ------------------------------------------------------------
                  Perfil activo      : {}
                  Puerto             : {}
                  Context path       : {}
                  API local          : http://localhost:{}{}
                  API red local      : http://{}:{}{}
                  Swagger UI         : http://localhost:{}{}/swagger-ui.html
                  Seguridad          : JWT (Authorization: Bearer)
                ============================================================
                """,
                applicationName,
                activeProfiles,
                port,
                contextPath.isBlank() ? "/" : contextPath,
                port,
                contextPath,
                host,
                port,
                contextPath,
                port,
                contextPath
        );
    }

    private String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException exception) {
            return "localhost";
        }
    }
}
