package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.UrlClick;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UrlClickRepository extends JpaRepository<UrlClick, Long> {
    long countByUrl(Url url);

    /** Daily click counts computed in the database, so only one row per day comes back. */
    @Query("SELECT CAST(uc.clickedAt AS LocalDate), COUNT(uc) FROM UrlClick uc "
            + "WHERE uc.url = :url AND uc.clickedAt >= :start AND uc.clickedAt <= :end "
            + "GROUP BY CAST(uc.clickedAt AS LocalDate)")
    List<Object[]> countClicksPerDay(@Param("url") Url url, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // Top-N breakdowns cover the same window as the chart and are limited in SQL (pass TOP_N).

    @Query("SELECT uc.browser, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url AND uc.clickedAt >= :start "
            + "GROUP BY uc.browser ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopBrowsersByUrl(@Param("url") Url url, @Param("start") LocalDateTime start, Pageable limit);

    @Query("SELECT uc.deviceType, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url AND uc.clickedAt >= :start "
            + "GROUP BY uc.deviceType ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopDevicesByUrl(@Param("url") Url url, @Param("start") LocalDateTime start, Pageable limit);

    @Query("SELECT uc.country, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url AND uc.clickedAt >= :start "
            + "GROUP BY uc.country ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopCountriesByUrl(@Param("url") Url url, @Param("start") LocalDateTime start, Pageable limit);

    @Query("SELECT uc.city, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url AND uc.clickedAt >= :start "
            + "GROUP BY uc.city ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopCitiesByUrl(@Param("url") Url url, @Param("start") LocalDateTime start, Pageable limit);

    @Query("SELECT uc.referrer, COUNT(uc) FROM UrlClick uc WHERE uc.url = :url AND uc.clickedAt >= :start "
            + "GROUP BY uc.referrer ORDER BY COUNT(uc) DESC")
    List<Object[]> findTopReferrersByUrl(@Param("url") Url url, @Param("start") LocalDateTime start, Pageable limit);
}
