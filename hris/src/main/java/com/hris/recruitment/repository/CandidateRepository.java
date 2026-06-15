package com.hris.recruitment.repository;

import com.hris.recruitment.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateRepository extends JpaRepository<Candidate, UUID> {

    Optional<Candidate> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<Candidate> findAllByOrderByCreatedAtDesc();

    List<Candidate> findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByCreatedAtDesc(
        String firstName, String lastName, String email);
}
