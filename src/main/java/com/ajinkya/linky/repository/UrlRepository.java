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

    org.springframework.data.domain.Page<Url> findByUserAndIsArchived(com.ajinkya.linky.entity.User user, boolean isArchived, org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Url> findByUserAndIsFavorite(com.ajinkya.linky.entity.User user, boolean isFavorite, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(u), COALESCE(SUM(u.clickCount), 0), "
            + "COALESCE(SUM(CASE WHEN u.isFavorite = true THEN 1 ELSE 0 END), 0) FROM Url u WHERE u.user = :user")
    java.util.List<Object[]> summarizeByUser(@Param("user") com.ajinkya.linky.entity.User user);

    Optional<Url> findByShortCodeAndUser(String shortCode, com.ajinkya.linky.entity.User user);

    /**
     * Atomically consumes a one-time link. Returns 1 for the single caller that flips
     * isActive from true to false, 0 for everyone else, so concurrent clicks can't both win.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("UPDATE Url u SET u.isActive = false WHERE u.id = :id AND u.isActive = true")
    int deactivateIfActive(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + 1 WHERE u.id = :id")
    void incrementClickCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Url u SET u.clickCount = u.clickCount + :n WHERE u.id = :id")
    void incrementClickCountBy(@Param("id") Long id, @Param("n") Long n);

    @Modifying
    @Query(value = "DELETE FROM url_clicks WHERE url_id = :id", nativeQuery = true)
    void forceDeleteUrlClicks(@Param("id") Long id);

    @Modifying
    @Query(value = "DELETE FROM url_tags WHERE url_id = :id", nativeQuery = true)
    void forceDeleteUrlTags(@Param("id") Long id);

    @Modifying
    @Query(value = "DELETE FROM urls WHERE id = :id", nativeQuery = true)
    void forceDeleteUrl(@Param("id") Long id);

    @Modifying
    @Query(value = "DELETE FROM url_clicks WHERE url_id IN (SELECT id FROM urls WHERE user_id = :userId)", nativeQuery = true)
    void forceDeleteUrlClicksByUser(@Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM url_tags WHERE url_id IN (SELECT id FROM urls WHERE user_id = :userId)", nativeQuery = true)
    void forceDeleteUrlTagsByUser(@Param("userId") Long userId);

    @Modifying
    @Query(value = "DELETE FROM urls WHERE user_id = :userId", nativeQuery = true)
    void forceDeleteUrlsByUser(@Param("userId") Long userId);

}