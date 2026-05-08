package com.hris.auth.dto;

import java.util.List;
import java.util.UUID;

public record AccountProvisioningRequest(
    String username,
    String email,
    String firstName,
    String lastName,
    String password,
    boolean temporaryPassword,
    List<UUID> profileIds
) {
}
