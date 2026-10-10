package com.kodika.kodikalab.architecture;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compara el esquema que Hibernate genera desde las entidades con las tablas visibles de
 * {@code docs/sdd/assets/oficial.erd}: columnas, tipos, longitudes, nulos, defaults, FKs y unicidades.
 * Usa un schema aleatorio en una base exclusiva de pruebas y lo elimina al terminar.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "ERD_TEST_DB_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ErdSchemaIntegrationTests {
    private static final String SCHEMA = "erd_test_" + UUID.randomUUID().toString().replace("-", "");
    private static boolean schemaCreated;

    private static final String INT = "integer";
    private static final String VARCHAR = "character varying";
    private static final String TIMESTAMPTZ = "timestamp with time zone";
    private static final boolean NULL = true;
    private static final boolean NOT_NULL = false;

    /** tabla -> columnas en el orden del ERD: nombre, tipo, longitud, nullable, default esperado (fragmento). */
    private static final Map<String, List<Col>> ERD = new LinkedHashMap<>();

    static {
        ERD.put("usuario", List.of(
                col("id", INT, null, NOT_NULL), col("nombre_completo", VARCHAR, 150, NOT_NULL),
                col("correo", VARCHAR, 100, NOT_NULL), col("password_hash", VARCHAR, 255, NOT_NULL),
                col("rol", VARCHAR, 20, NOT_NULL), col("estado_cuenta", VARCHAR, 20, NOT_NULL, "ACTIVO"),
                col("fecha_registro", TIMESTAMPTZ, null, NOT_NULL, "CURRENT_TIMESTAMP")));
        ERD.put("coach", List.of(
                col("usuario_id", INT, null, NOT_NULL), col("especialidad_principal", VARCHAR, 120, NOT_NULL),
                col("organizacion_club", VARCHAR, 150, NULL), col("anios_experiencia", INT, null, NOT_NULL, "0"),
                col("presentacion", VARCHAR, 500, NULL)));
        ERD.put("practicante", List.of(
                col("usuario_id", INT, null, NOT_NULL), col("codigo_estudiante", VARCHAR, 20, NOT_NULL),
                col("carrera", VARCHAR, 100, NOT_NULL), col("ciclo_academico", INT, null, NOT_NULL, "1"),
                col("nivel_competitivo", VARCHAR, 30, NOT_NULL, "PRINCIPIANTE"),
                col("codeforces_handle", VARCHAR, 50, NULL), col("codeforces_rating", INT, null, NULL, "0"),
                col("atcoder_handle", VARCHAR, 50, NULL), col("vjudge_handle", VARCHAR, 50, NULL)));
        ERD.put("grupo_estudio", List.of(
                col("id", INT, null, NOT_NULL), col("coach_id", INT, null, NOT_NULL),
                col("nombre", VARCHAR, 120, NOT_NULL), col("descripcion", VARCHAR, 500, NULL),
                col("nivel_esperado", VARCHAR, 50, NOT_NULL), col("cupo_maximo", INT, null, NOT_NULL, "15"),
                col("horario_sesiones", VARCHAR, 150, NULL), col("codigo_invitacion", VARCHAR, 20, NOT_NULL),
                col("estado", VARCHAR, 20, NOT_NULL, "ACTIVO"),
                col("fecha_creacion", TIMESTAMPTZ, null, NOT_NULL, "CURRENT_TIMESTAMP"),
                col("visibilidad", VARCHAR, 50, NULL, "PUBLICO")));
        ERD.put("practicante_grupo", List.of(
                col("id", INT, null, NOT_NULL), col("grupo_id", INT, null, NOT_NULL),
                col("practicante_id", INT, null, NOT_NULL), col("estado", VARCHAR, 20, NOT_NULL, "ACTIVO"),
                col("rol_equipo", VARCHAR, 30, NOT_NULL, "MIEMBRO"),
                col("fecha_ingreso", TIMESTAMPTZ, null, NOT_NULL, "CURRENT_TIMESTAMP"),
                col("fecha_salida", TIMESTAMPTZ, null, NULL)));
        ERD.put("competencia", List.of(
                col("id", INT, null, NOT_NULL), col("grupo_id", INT, null, NOT_NULL),
                col("nombre_evento", VARCHAR, 150, NOT_NULL), col("descripcion", VARCHAR, 500, NULL),
                col("tipo_acceso", VARCHAR, 30, NOT_NULL, "PUBLICO_GRUPO"), col("clave_acceso", VARCHAR, 100, NULL),
                col("regla_penalizacion", VARCHAR, 30, NOT_NULL, "ICPC_20_MIN"),
                col("duracion_minutos", INT, null, NOT_NULL, "300"),
                col("congelar_scoreboard_min", INT, null, NOT_NULL, "60"),
                col("estado", VARCHAR, 20, NOT_NULL, "PROGRAMADA"),
                col("fecha_inicio", TIMESTAMPTZ, null, NOT_NULL), col("fecha_fin", TIMESTAMPTZ, null, NOT_NULL)));
        ERD.put("competencia_problema", List.of(
                col("id", INT, null, NOT_NULL), col("competencia_id", INT, null, NOT_NULL),
                col("problema_id", INT, null, NOT_NULL), col("orden_letra", VARCHAR, 5, NOT_NULL),
                col("puntaje", INT, null, NOT_NULL, "1"), col("color_globo", VARCHAR, 20, NULL, "#FF0000"),
                col("fecha_asignacion", TIMESTAMPTZ, null, NOT_NULL, "CURRENT_TIMESTAMP")));
        ERD.put("problema", List.of(
                col("id", INT, null, NOT_NULL), col("codigo_origen", VARCHAR, 50, NULL),
                col("titulo", VARCHAR, 150, NOT_NULL), col("plataforma_origen", VARCHAR, 50, NOT_NULL, "CODEFORCES"),
                col("url_problema", VARCHAR, 300, NOT_NULL), col("dificultad_rating", VARCHAR, 30, NULL),
                col("limite_tiempo_ms", INT, null, NOT_NULL, "1000"),
                col("limite_memoria_mb", INT, null, NOT_NULL, "256")));
        ERD.put("resolucion_problema", List.of(
                col("id", INT, null, NOT_NULL), col("veredicto", VARCHAR, 30, NOT_NULL, "PENDIENTE"),
                col("lenguaje", VARCHAR, 30, NOT_NULL, "C++20"), col("tiempo_ejecucion_ms", INT, null, NULL, "0"),
                col("memoria_usada_kb", INT, null, NULL, "0"),
                col("fecha_envio", TIMESTAMPTZ, null, NOT_NULL, "CURRENT_TIMESTAMP"),
                col("competencia_problema_id", INT, null, NOT_NULL), col("practicante_grupo_id", INT, null, NOT_NULL),
                col("url_evidencia", VARCHAR, 500, NULL)));
        ERD.put("material", List.of(
                col("id", INT, null, NOT_NULL), col("problema_id", INT, null, NULL),
                col("titulo", VARCHAR, 200, NOT_NULL), col("autor", VARCHAR, 150, NULL),
                col("tipo_recurso", VARCHAR, 30, NOT_NULL, "LIBRO"), col("tema", VARCHAR, 80, NOT_NULL),
                col("enlace_url", VARCHAR, 400, NOT_NULL), col("descripcion", VARCHAR, 300, NULL)));
        ERD.put("tema", List.of(
                col("id", INT, null, NOT_NULL), col("nombre", VARCHAR, 80, NOT_NULL),
                col("descripcion", "text", null, NULL)));
        ERD.put("problema_tema", List.of(
                col("problema_id", INT, null, NOT_NULL), col("tema_id", INT, null, NOT_NULL)));
        ERD.put("categoria", List.of(
                col("idcategoria", INT, null, NOT_NULL), col("descripcioncategoria", VARCHAR, 100, NULL),
                col("nombrecategoria", VARCHAR, 10, NULL), col("idgrupo", INT, null, NOT_NULL)));
        ERD.put("ranking_equipo_actual", List.of(
                col("grupo_id", INT, null, NOT_NULL),
                col("calculado_en", TIMESTAMPTZ, null, NOT_NULL),
                col("resultado", "jsonb", null, NOT_NULL)));
        ERD.put("resultado_oficial_competencia", List.of(
                col("id", INT, null, NOT_NULL), col("competencia_id", INT, null, NOT_NULL),
                col("posicion_final", INT, null, NULL), col("problemas_resueltos", INT, null, NULL),
                col("estado", VARCHAR, 20, NOT_NULL), col("registrado_en", TIMESTAMPTZ, null, NOT_NULL),
                col("confirmado_en", TIMESTAMPTZ, null, NULL)));
    }

    /** Relaciones visibles del ERD: tabla.columna -> tabla_referenciada.columna. */
    private static final List<String> FOREIGN_KEYS = List.of(
            "coach.usuario_id -> usuario.id",
            "practicante.usuario_id -> usuario.id",
            "grupo_estudio.coach_id -> coach.usuario_id",
            "practicante_grupo.grupo_id -> grupo_estudio.id",
            "practicante_grupo.practicante_id -> practicante.usuario_id",
            "competencia.grupo_id -> grupo_estudio.id",
            "competencia_problema.competencia_id -> competencia.id",
            "competencia_problema.problema_id -> problema.id",
            "resolucion_problema.competencia_problema_id -> competencia_problema.id",
            "resolucion_problema.practicante_grupo_id -> practicante_grupo.id",
            "material.problema_id -> problema.id",
            "problema_tema.problema_id -> problema.id",
            "problema_tema.tema_id -> tema.id",
            "categoria.idgrupo -> grupo_estudio.id",
            "ranking_equipo_actual.grupo_id -> grupo_estudio.id",
            "resultado_oficial_competencia.competencia_id -> competencia.id");

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getenv("ERD_TEST_DB_URL"),
                System.getenv().getOrDefault("ERD_TEST_DB_USER", "postgres"),
                System.getenv().getOrDefault("ERD_TEST_DB_PASSWORD", ""));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws SQLException {
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
            schemaCreated = true;
        }
        registry.add("spring.datasource.url", () -> System.getenv("ERD_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ERD_TEST_DB_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ERD_TEST_DB_PASSWORD", ""));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + SCHEMA);
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        if (schemaCreated) {
            try (Connection connection = connect(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }

    @Test
    void generatesExactlyTheVisibleErdTables() {
        assertThat(query("SELECT table_name FROM information_schema.tables WHERE table_schema = ?", SCHEMA)
                .stream().map(row -> row.get(0)).toList())
                .containsExactlyInAnyOrderElementsOf(ERD.keySet());
    }

    @Test
    void columnsMatchErdTypesLengthsNullabilityAndDefaults() {
        ERD.forEach((table, columns) -> {
            List<List<Object>> actual = query("""
                    SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
                    FROM information_schema.columns WHERE table_schema = ? AND table_name = ?""", SCHEMA, table);
            assertThat(actual.stream().map(row -> row.get(0)).toList()).as("columnas de %s", table)
                    .containsExactlyInAnyOrderElementsOf(columns.stream().map(Col::name).toList());
            for (Col expected : columns) {
                List<Object> row = actual.stream().filter(r -> expected.name().equals(r.get(0))).findFirst().orElseThrow();
                String where = table + "." + expected.name();
                assertThat(row.get(1)).as("tipo de %s", where).isEqualTo(expected.type());
                assertThat(row.get(2)).as("longitud de %s", where).isEqualTo(expected.length());
                assertThat(row.get(3)).as("nulos de %s", where).isEqualTo(expected.nullable() ? "YES" : "NO");
                if (expected.defaultFragment() != null) {
                    assertThat(String.valueOf(row.get(4))).as("default de %s", where)
                            .containsIgnoringCase(expected.defaultFragment());
                }
            }
        });
    }

    @Test
    void foreignKeysMatchVisibleErdRelationships() {
        List<String> actual = query("""
                SELECT kcu.table_name || '.' || kcu.column_name || ' -> ' || ccu.table_name || '.' || ccu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON tc.constraint_name = ccu.constraint_name AND tc.table_schema = ccu.table_schema
                WHERE tc.table_schema = ? AND tc.constraint_type = 'FOREIGN KEY'""", SCHEMA)
                .stream().map(row -> (String) row.get(0)).toList();
        assertThat(actual).containsExactlyInAnyOrderElementsOf(FOREIGN_KEYS);
    }

    @Test
    void primaryKeysMatchErd() {
        assertThat(primaryKey("usuario")).containsExactly("id");
        assertThat(primaryKey("coach")).containsExactly("usuario_id");
        assertThat(primaryKey("practicante")).containsExactly("usuario_id");
        assertThat(primaryKey("problema_tema")).containsExactlyInAnyOrder("problema_id", "tema_id");
        assertThat(primaryKey("categoria")).containsExactly("idcategoria");
        assertThat(primaryKey("ranking_equipo_actual")).containsExactly("grupo_id");
        for (String table : List.of("grupo_estudio", "practicante_grupo", "competencia", "competencia_problema",
                "problema", "resolucion_problema", "material", "tema", "resultado_oficial_competencia")) {
            assertThat(primaryKey(table)).as("PK de %s", table).containsExactly("id");
        }
    }

    @Test
    void uniqueConstraintsMatchErd() {
        List<String> actual = query("""
                SELECT tc.table_name || '(' || string_agg(kcu.column_name, ',' ORDER BY kcu.column_name) || ')'
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = ? AND tc.constraint_type = 'UNIQUE'
                GROUP BY tc.table_name, tc.constraint_name""", SCHEMA)
                .stream().map(row -> (String) row.get(0)).toList();
        assertThat(actual).contains(
                "usuario(correo)",
                "practicante(codigo_estudiante)",
                "grupo_estudio(codigo_invitacion)",
                "practicante_grupo(grupo_id,practicante_id)",
                "competencia_problema(competencia_id,problema_id)",
                "competencia_problema(competencia_id,orden_letra)",
                "tema(nombre)",
                "categoria(idgrupo)",
                "resultado_oficial_competencia(competencia_id)");
    }

    private List<String> primaryKey(String table) {
        return query("""
                SELECT kcu.column_name FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = ? AND tc.table_name = ? AND tc.constraint_type = 'PRIMARY KEY'""",
                SCHEMA, table).stream().map(row -> (String) row.get(0)).toList();
    }

    private static List<List<Object>> query(String sql, Object... params) {
        try (Connection connection = connect(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            List<List<Object>> rows = new ArrayList<>();
            try (var result = statement.executeQuery()) {
                int columns = result.getMetaData().getColumnCount();
                while (result.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columns; i++) {
                        row.add(result.getObject(i));
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new AssertionError("No se pudo consultar la base de pruebas", exception);
        }
    }

    private static Col col(String name, String type, Integer length, boolean nullable) {
        return new Col(name, type, length, nullable, null);
    }

    private static Col col(String name, String type, Integer length, boolean nullable, String defaultFragment) {
        return new Col(name, type, length, nullable, defaultFragment);
    }

    private record Col(String name, String type, Integer length, boolean nullable, String defaultFragment) {
    }
}
