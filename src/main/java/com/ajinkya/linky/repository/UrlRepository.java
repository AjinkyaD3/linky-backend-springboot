package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.Url;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UrlRepository extends JpaRepository<Url, Long>, JpaSpecificationExecutor<Url> {

    Optional<Url> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    java.util.List<Url> findByUser(com.ajinkya.linky.entity.User user);

    java.util.List<Url> findByUserAndIsArchived(com.ajinkya.linky.entity.User user, boolean isArchived);

    java.util.List<Url> findByUserAndIsFavorite(com.ajinkya.linky.entity.User user, boolean isFavorite);

    Optional<Url> findByShortCodeAndUser(String shortCode, com.ajinkya.linky.entity.User user);

    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.id = :id")
    void incrementClickCount(@Param("id") Long id);

    @Modifying
    @Query(value = "DELETE FROM url_clicks WHERE url_id = :id", nativeQuery = true)
    void forceDeleteUrlClicks(@Param("id") Long id);

    @Modifying
    @Query(value = "DELETE FROM url_tags WHERE url_id = :id", nativeQuery = true)
    void forceDeleteUrlTags(@Param("id") Long id);

    @Modifying
    @Query(value = "DELETE FROM urls WHERE id = :id", nativeQuery = true)
    void forceDeleteUrl(@Param("id") Long id);

}