package com.hris.identity.security;

import com.hris.auth.entity.User;
import com.hris.identity.account.entity.UserCredential;
import com.hris.identity.account.repository.UserCredentialRepository;
import com.hris.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Form-login user lookup for the authorization server. Username is the email.
 * Authorities are a placeholder ROLE_USER — real authorization remains the
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
        String email = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));

        UserCredential credential = userCredentialRepository.findById(user.getId())
            .orElseThrow(() -> new UsernameNotFoundException("Account not activated"));

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())
            .password(credential.getPasswordHash())
            .disabled(!user.isActive())
            .accountLocked(credential.isLocked())
            .authorities("ROLE_USER")
            .build();
    }
}
