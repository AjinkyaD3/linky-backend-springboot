package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.entity.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    List<Webhook> findByUserOrderByCreatedAtDesc(User user);

    List<Webhook> findByUserAndActiveTrue(User user);

    long countByUser(User user);

    void deleteByUser(User user);
}
