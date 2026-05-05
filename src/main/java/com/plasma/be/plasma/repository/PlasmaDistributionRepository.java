package com.plasma.be.plasma.repository;

import com.plasma.be.plasma.entity.PlasmaDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlasmaDistributionRepository extends JpaRepository<PlasmaDistribution, Long> {

    @Query("SELECT p FROM PlasmaDistribution p WHERE p.iedValues IS NOT NULL")
    List<PlasmaDistribution> findAllWithOutput();
}
