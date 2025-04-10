package dev.java4now.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// IMPORTANT SQLLite

@Repository
public interface CyclingActivityRepository extends JpaRepository<CyclingActivityEntity, Long> {
    List<CyclingActivityEntity> findByUserName(String username);
}