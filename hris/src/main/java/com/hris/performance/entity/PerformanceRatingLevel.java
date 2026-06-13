package com.hris.performance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * One level within a rating scale (e.g. 3 -> "Meets Expectations"). numeric_value
 * drives the weighted score computation. tenant_id is unmapped — set by DB
 * default, enforced by RLS.
 */
@Entity
@Table(name = "performance_rating_levels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PerformanceRatingLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "scale_id", nullable = false)
    private UUID scaleId;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "numeric_value", nullable = false)
    private int numericValue;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
