package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.UrlClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UrlClickRepository extends JpaRepository<UrlClick, Long> {
    long countByUrl(Url url);

    List<UrlClick> findByUrlAndClickedAtBetween(Url url, LocalDateTime start, LocalDateTime end);

    /** Daily click counts computed in the database, so only one row per day comes back. */
    @Query("SELECT CAST(uc.clickedAt AS LocalDate), COUNT(uc) FROM UrlClick uc "
            + "WHERE uc.url = :url AND uc.clickedAt >= :start AND uc.clickedAt <= :end "
            + "GROUP BY CAST(uc.clickedAt AS LocalDate)")
    List<Object[]> countClicksPerDay(@Param("url") Url url, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT uc.browser, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url GROUP BY uc.browser ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopBrowsersByUrl(@Param("url") Url url);

    @Query("SELECT uc.deviceType, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url GROUP BY uc.deviceType ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopDevicesByUrl(@Param("url") Url url);

    @Query("SELECT uc.country, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url GROUP BY uc.country ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopCountriesByUrl(@Param("url") Url url);

    @Query("SELECT uc.city, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url GROUP BY uc.city ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopCitiesByUrl(@Param("url") Url url);

    @Query("SELECT uc.referrer, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url GROUP BY uc.referrer ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopReferrersByUrl(@Param("url") Url url);
}
