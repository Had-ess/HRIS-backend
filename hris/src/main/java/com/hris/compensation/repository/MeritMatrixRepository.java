package com.hris.compensation.repository;

import com.hris.compensation.entity.MeritMatrixCell;
import com.hris.compensation.enums.CompaBand;
import com.hris.compensation.enums.RatingBand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeritMatrixRepository extends JpaRepository<MeritMatrixCell, UUID> {

    List<MeritMatrixCell> findAllByOrderByRatingBandAscCompaBandAsc();

    Optional<MeritMatrixCell> findByRatingBandAndCompaBand(RatingBand ratingBand, CompaBand compaBand);
}
