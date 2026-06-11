package com.hris.identity.account;

import com.hris.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * DEV ONLY (gated by {@code app.auth.demo-seed.enabled}): gives the demo users
 * local credentials matching the passwords the old Keycloak realm export used,
 * so the demo flow keeps working after the Keycloak removal. Real environments
 * onboard users exclusively through the activation-email flow.
 *
 * <p>Uses the policy-bypassing setter on purpose — these are fixed demo
 * passwords, not user input, and some predate the current policy.
 */
@Component
@ConditionalOnProperty(name = "app.auth.demo-seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoCredentialSeeder implements CommandLineRunner {

    private static final Map<String, String> DEMO_PASSWORDS = Map.ofEntries(
        Map.entry("admin@demo.hris.local", "Admin123!"),
        Map.entry("hr.admin@demo.hris.local", "HrAdmin123!"),
        Map.entry("director@demo.hris.local", "Director123!"),
        Map.entry("manager.engineering@demo.hris.local", "Manager123!"),
        Map.entry("supervisor.operations@demo.hris.local", "Supervisor123!"),
        Map.entry("developer@demo.hris.local", "Employee123!"),
        Map.entry("analyst@demo.hris.local", "Employee123!"),
        Map.entry("product@demo.hris.local", "Employee123!"),
        Map.entry("office@demo.hris.local", "Employee123!"),
        Map.entry("former.employee@demo.hris.local", "Employee123!")
    );

    private final UserRepository userRepository;
    private final CredentialService credentialService;

    @Override
    @Transactional
    public void run(String... args) {
        int seeded = 0;
        for (Map.Entry<String, String> entry : DEMO_PASSWORDS.entrySet()) {
            var user = userRepository.findByEmail(entry.getKey()).orElse(null);
            if (user == null || credentialService.hasCredentials(user.getId())) {
                continue;
            }
            credentialService.setPasswordUnchecked(user.getId(), entry.getValue());
            seeded++;
        }
        if (seeded > 0) {
            log.warn("Demo credential seeder created {} local credential(s) — dev use only", seeded);
        }
    }
}
