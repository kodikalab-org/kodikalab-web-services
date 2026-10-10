package com.kodika.kodikalab.auth.dto;

import com.kodika.kodikalab.users.Role;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre debe tener como máximo 80 caracteres") String firstName,
        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido debe tener como máximo 80 caracteres") String lastName,
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo debe tener un formato válido")
        @Size(max = 100, message = "El correo debe tener como máximo 100 caracteres") String email,
        @NotBlank(message = "La contraseña es obligatoria")
        @Pattern(regexp = "(?s)(?=.*\\p{Lu})(?=.*\\p{Nd}).{8,}",
                message = "La contraseña debe contener al menos 8 caracteres, una mayúscula y un número")
        String password,
        @NotNull(message = "El rol es obligatorio")
        @JsonDeserialize(using = RoleDeserializer.class) Role role) {

    public RegisterRequest {
        firstName = firstName == null ? null : firstName.strip();
        lastName = lastName == null ? null : lastName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @JsonIgnore
    @AssertTrue(message = "El nombre completo debe tener como máximo 150 caracteres")
    public boolean isFullNameWithinLimit() {
        // Missing names are handled by their own required-field constraints.
        return firstName == null || lastName == null || (firstName + " " + lastName).length() <= 150;
    }

    /** Only enum names are accepted, never numeric ordinals. */
    public static class RoleDeserializer extends JsonDeserializer<Role> {
        @Override
        public Role deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                try {
                    return Role.valueOf(parser.getText());
                } catch (IllegalArgumentException ignored) {
                    // Fall through to the same safe invalid-role response.
                }
            }
            throw InvalidFormatException.from(parser, "Rol inválido", parser.getText(), Role.class);
        }
    }

    @Override
    public String toString() {
        return "RegisterRequest[password=REDACTED]";
    }
}
