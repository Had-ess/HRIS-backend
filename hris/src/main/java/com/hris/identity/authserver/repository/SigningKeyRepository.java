package com.hris.identity.authserver.repository;

import com.hris.identity.authserver.entity.SigningKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SigningKeyRepository extends JpaRepository<SigningKey, String> {

    List<SigningKey> findByStatus(SigningKey.Status status);
}
