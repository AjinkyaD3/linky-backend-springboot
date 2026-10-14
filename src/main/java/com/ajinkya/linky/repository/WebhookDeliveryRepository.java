package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.Webhook;
import com.ajinkya.linky.entity.WebhookDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findByWebhookOrderByIdDesc(Webhook webhook, Pageable pageable);

    @Query("SELECT d FROM WebhookDelivery d JOIN FETCH d.webhook WHERE d.id = :id")
    Optional<WebhookDelivery> findWithWebhook(@Param("id") Long id);
}
