package com.hris.identity.authserver;

import com.hris.identity.authserver.entity.SigningKey;
import com.hris.identity.authserver.repository.SigningKeyRepository;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Loads (or creates on first boot) the persisted RSA key pairs that back JWT
 * signing and the JWKS endpoint. RETIRED keys contribute only their public
 * half so previously issued tokens stay verifiable; rotation is: insert a new
 * ACTIVE row, flip the old one to RETIRED, restart.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwkKeyService {

    private final SigningKeyRepository signingKeyRepository;

    @Transactional
    public JWKSet loadOrCreateJwkSet() {
        List<SigningKey> activeKeys = signingKeyRepository.findByStatus(SigningKey.Status.ACTIVE);

        if (activeKeys.isEmpty()) {
            SigningKey created = generateAndPersist();
            activeKeys = List.of(created);
            log.info("Generated new RSA signing key with kid={}", created.getKid());
        }

        List<JWK> jwks = new ArrayList<>();
        for (SigningKey key : activeKeys) {
            jwks.add(toRsaKey(key, true));
        }
        for (SigningKey retired : signingKeyRepository.findByStatus(SigningKey.Status.RETIRED)) {
            jwks.add(toRsaKey(retired, false));
        }
        return new JWKSet(jwks);
    }

    private SigningKey generateAndPersist() {
        KeyPair keyPair = generateRsaKeyPair();
        return signingKeyRepository.save(SigningKey.builder()
            .kid(UUID.randomUUID().toString())
            .publicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()))
            .privateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()))
            .status(SigningKey.Status.ACTIVE)
            .build());
    }

    private KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation unavailable", e);
        }
    }

    private RSAKey toRsaKey(SigningKey key, boolean includePrivate) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(key.getPublicKey())));

            RSAKey.Builder builder = new RSAKey.Builder(publicKey).keyID(key.getKid());
            if (includePrivate) {
                RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.getPrivateKey())));
                builder.privateKey(privateKey);
            }
            return builder.build();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Stored signing key " + key.getKid() + " is unreadable", e);
        }
    }
}
