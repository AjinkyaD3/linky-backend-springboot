package com.ajinkya.linky.controller;

import com.ajinkya.linky.dto.TagResponse;
import com.ajinkya.linky.entity.Tag;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.repository.TagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagRepository tagRepository;

    public TagController(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @PostMapping
    public ResponseEntity<TagResponse> createTag(@AuthenticationPrincipal User user, @RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        if (tagRepository.existsByNameAndUser(name, user)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        Tag tag = Tag.builder()
                .name(name)
                .user(user)
                .build();

        Tag saved = tagRepository.save(tag);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(saved));
    }

    @GetMapping
    public ResponseEntity<List<TagResponse>> getTags(@AuthenticationPrincipal User user) {
        List<TagResponse> tags = tagRepository.findByUser(user).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tags);
    }

    private TagResponse mapToResponse(Tag tag) {
        return TagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTag(@AuthenticationPrincipal User user, @PathVariable Long id) {
        Tag tag = tagRepository.findByIdAndUser(id, user).orElse(null);
        if (tag == null) {
            return ResponseEntity.notFound().build();
        }
        tagRepository.delete(tag);
        return ResponseEntity.noContent().build();
    }
}
