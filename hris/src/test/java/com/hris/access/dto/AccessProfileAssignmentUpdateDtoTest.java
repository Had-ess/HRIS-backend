package com.hris.access.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AccessProfileAssignmentUpdateDtoTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("permission assignment payload may intentionally be empty")
    void permissionAssignmentMayBeEmpty() {
        var violations = validator.validate(new PermissionAssignmentUpdateDto(List.of()));

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("permission assignment payload still requires the field")
    void permissionAssignmentRequiresField() {
        var violations = validator.validate(new PermissionAssignmentUpdateDto(null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("menu assignment payload may intentionally be empty")
    void menuAssignmentMayBeEmpty() {
        var violations = validator.validate(new MenuAssignmentUpdateDto(List.of()));

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("menu assignment payload still requires the field")
    void menuAssignmentRequiresField() {
        var violations = validator.validate(new MenuAssignmentUpdateDto(null));

        assertThat(violations).isNotEmpty();
    }
}
