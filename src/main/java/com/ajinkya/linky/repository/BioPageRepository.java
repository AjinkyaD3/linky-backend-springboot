package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.BioPage;
import com.ajinkya.linky.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BioPageRepository extends JpaRepository<BioPage, Long> {

    Optional<BioPage> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<BioPage> findByUserOrderByCreatedAtDesc(User user);

    long countByUser(User user);

    void deleteByUser(User user);
}
