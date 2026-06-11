package com.hris.identity.security;

import com.hris.auth.entity.User;
import com.hris.identity.account.entity.UserCredential;
import com.hris.identity.account.repository.UserCredentialRepository;
import com.hris.auth.repository.UserRepository;
import com.hris.tenancy.TenantContext;
import com.hris.tenancy.TenantPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Form-login user lookup for the authorization server. The visitor types
 * their email; {@code LoginTenantFilter} has already established which tenant
 * they are signing in to. The returned UserDetails' username is the canonical
 * composite principal ({@link TenantPrincipal}), which is what the session and
 * authorization stores persist.
 *
 * <p>Authorities are a placeholder ROLE_USER — real authorization remains the
 * DB-driven permission model resolved per request, never the token.
 */
@Service
@RequiredArgsConstructor
public class HrisUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = TenantPrincipal.normalizeEmail(username);
        UUID tenantId = TenantContext.get() != null
            ? TenantContext.get()
            : TenantContext.DEFAULT_TENANT_ID;

        User user = userRepository.findByTenantIdAndEmail(tenantId, email)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));

        UserCredential credential = userCredentialRepository.findById(user.getId())
            .orElseThrow(() -> new UsernameNotFoundException("Account not activated"));

        return org.springframework.security.core.userdetails.User.builder()
            .username(new TenantPrincipal(tenantId, user.getEmail()).format())
            .password(credential.getPasswordHash())
            .disabled(!user.isActive())
            .accountLocked(credential.isLocked())
            .authorities("ROLE_USER")
            .build();
    }
}
