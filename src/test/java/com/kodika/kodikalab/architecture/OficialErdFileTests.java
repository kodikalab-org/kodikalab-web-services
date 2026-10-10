package com.kodika.kodikalab.architecture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que {@code docs/sdd/assets/oficial.erd} (el archivo del erd-editor) contiene exactamente las tablas,
 * columnas, tipos, longitudes, nulos, defaults y relaciones que {@link ErdSchemaIntegrationTests} exige a las
 * entidades JPA. No requiere base de datos: si alguien cambia el ERD gráfico o el modelo y no el otro, falla.
 *
 * <p>Opciones de columna del erd-editor: 1 autoincremento, 2 PK, 4 único, 8 NOT NULL.
 */
class OficialErdFileTests {
    private static final Path FILE = Path.of("docs", "sdd", "assets", "oficial.erd");
    private static final Pattern VARCHAR = Pattern.compile("(?i)VARCHAR\\((\\d+)\\)");
    private static JsonNode erd;

    @BeforeAll
    static void load() throws IOException {
        erd = new ObjectMapper().readTree(Files.readString(FILE));
    }

    private static JsonNode collection(String name) {
        return erd.get("collections").get(name);
    }

    private static List<JsonNode> visible(String idList, String entities) {
        List<JsonNode> result = new ArrayList<>();
        erd.get("doc").get(idList).forEach(id -> result.add(collection(entities).get(id.asText())));
        return result;
    }

    private static String jdbcType(String dataType) {
        String type = dataType.trim().toUpperCase(Locale.ROOT);
        if (type.startsWith("VARCHAR")) {
            return "character varying";
        }
        return switch (type) {
            case "SERIAL", "INT", "INTEGER" -> "integer";
            case "TIMESTAMP WITH TIME ZONE" -> "timestamp with time zone";
            case "TEXT" -> "text";
            case "JSONB" -> "jsonb";
            default -> type;
        };
    }

    private static Integer length(String dataType) {
        Matcher matcher = VARCHAR.matcher(dataType.trim());
        return matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
    }

    @Test
    void visibleTablesAreExactlyTheModelTables() {
        List<String> tables = visible("tableIds", "tableEntities").stream()
                .map(table -> table.get("name").asText().toLowerCase(Locale.ROOT)).toList();
        assertThat(tables).containsExactlyInAnyOrderElementsOf(ErdSchemaIntegrationTests.ERD.keySet());
    }

    @Test
    void columnsMatchNamesTypesLengthsNullabilityAndDefaults() {
        for (JsonNode table : visible("tableIds", "tableEntities")) {
            String name = table.get("name").asText().toLowerCase(Locale.ROOT);
            List<ErdSchemaIntegrationTests.Col> expected = ErdSchemaIntegrationTests.ERD.get(name);
            List<JsonNode> columns = new ArrayList<>();
            table.get("columnIds").forEach(id -> columns.add(collection("tableColumnEntities").get(id.asText())));

            assertThat(columns.stream().map(column -> column.get("name").asText().toLowerCase(Locale.ROOT)).toList())
                    .as("columnas de %s", name)
                    .containsExactlyElementsOf(expected.stream().map(ErdSchemaIntegrationTests.Col::name).toList());
            for (int i = 0; i < expected.size(); i++) {
                var model = expected.get(i);
                JsonNode column = columns.get(i);
                String label = name + "." + model.name();
                String dataType = column.get("dataType").asText();
                int options = column.get("options").asInt();
                assertThat(jdbcType(dataType)).as("tipo de %s", label).isEqualTo(model.type());
                assertThat(length(dataType)).as("longitud de %s", label).isEqualTo(model.length());
                assertThat((options & 8) == 0 && (options & 2) == 0).as("nullable de %s", label).isEqualTo(model.nullable());
                if (model.defaultFragment() != null) {
                    assertThat(column.get("default").asText().replace("'", "")).as("default de %s", label)
                            .contains(model.defaultFragment());
                }
            }
        }
    }

    @Test
    void relationshipsMatchTheForeignKeys() {
        List<String> foreignKeys = new ArrayList<>();
        for (JsonNode relationship : visible("relationshipIds", "relationshipEntities")) {
            foreignKeys.add(endpoint(relationship.get("end")) + " -> " + endpoint(relationship.get("start")));
        }
        assertThat(foreignKeys).containsExactlyInAnyOrderElementsOf(ErdSchemaIntegrationTests.FOREIGN_KEYS);
    }

    private static String endpoint(JsonNode side) {
        String table = collection("tableEntities").get(side.get("tableId").asText()).get("name").asText();
        String column = collection("tableColumnEntities").get(side.get("columnIds").get(0).asText()).get("name").asText();
        return (table + "." + column).toLowerCase(Locale.ROOT);
    }

    @Test
    void extensionTablesAreDocumentedAsApprovedExtensions() {
        for (JsonNode table : visible("tableIds", "tableEntities")) {
            String name = table.get("name").asText();
            if (name.equals("ranking_equipo_actual") || name.equals("resultado_oficial_competencia")) {
                assertThat(table.get("comment").asText()).as("comentario de %s", name).startsWith("Extensión aprobada");
            }
        }
    }
}
