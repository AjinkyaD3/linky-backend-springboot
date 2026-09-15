package com.ajinkya.linky.repository;

import com.ajinkya.linky.entity.Tag;
import com.ajinkya.linky.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    List<Tag> findByUser(User user);
    Optional<Tag> findByIdAndUser(Long id, User user);
    boolean existsByNameAndUser(String name, User user);

    @Modifying
    void deleteByUser(User user);
}
