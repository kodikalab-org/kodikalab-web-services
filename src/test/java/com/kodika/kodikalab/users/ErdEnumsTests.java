package com.kodika.kodikalab.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Convert;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class ErdEnumsTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(Role.class)
    void rolesUseOfficialNamesInJavaAndJson(Role role) throws Exception {
        assertThat(role.name()).isIn("PRACTICANTE", "COACH");
        String json = mapper.writeValueAsString(role);
        assertThat(json).isEqualTo("\"" + role.name() + "\"");
        assertThat(mapper.readValue(json, Role.class)).isEqualTo(role);
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    void statusesUseOfficialNamesInJavaAndJson(UserStatus status) throws Exception {
        assertThat(status.name()).isIn("ACTIVO", "SUSPENDIDO");
        String json = mapper.writeValueAsString(status);
        assertThat(json).isEqualTo("\"" + status.name() + "\"");
        assertThat(mapper.readValue(json, UserStatus.class)).isEqualTo(status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "PRACTITIONER", "ROOT"})
    void unsupportedRolesAreNotReclassified(String value) {
        assertThatThrownBy(() -> Role.valueOf(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "SUSPENDED", "INACTIVE", "BLOCKED"})
    void unsupportedStatusesAreNotReactivated(String value) {
        assertThatThrownBy(() -> UserStatus.valueOf(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void jpaPersistsEnumNamesDirectlyWithoutConverters() throws Exception {
        for (String name : new String[]{"role", "status"}) {
            var field = User.class.getDeclaredField(name);
            assertThat(field.getAnnotation(Enumerated.class)).isNotNull();
            assertThat(field.getAnnotation(Enumerated.class).value()).isEqualTo(EnumType.STRING);
            assertThat(field.getAnnotation(Convert.class)).isNull();
        }
    }
}
