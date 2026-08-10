package com.ajinkya.linky.service;

import com.ajinkya.linky.dto.CreateUrlRequest;
import com.ajinkya.linky.dto.UrlResponse;
import com.ajinkya.linky.entity.Url;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.exception.DuplicateShortCodeException;
import com.ajinkya.linky.exception.ResourceNotFoundException;
import com.ajinkya.linky.repository.UrlRepository;
import com.ajinkya.linky.util.Base62Encoder;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.ajinkya.linky.repository.TagRepository;
import com.ajinkya.linky.dto.UpdateUrlRequest;
import com.ajinkya.linky.entity.Tag;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import jakarta.persistence.criteria.Join;

@Service
@Transactional
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    private final UrlRepository urlRepository;
    private final RedisService redisService;
    private final PasswordEncoder passwordEncoder;
    private final TagRepository tagRepository;

    public UrlService(UrlRepository urlRepository, RedisService redisService, PasswordEncoder passwordEncoder, TagRepository tagRepository) {
        this.urlRepository = urlRepository;
        this.redisService = redisService;
        this.passwordEncoder = passwordEncoder;
        this.tagRepository = tagRepository;
    }

    /**
     * Get all URLs
     */
    public List<UrlResponse> findAll() {
        return urlRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * Find URL by ID
     */
    public Optional<Url> findById(Long id) {
        return urlRepository.findById(id);
    }

    /**
     * Find URL response by ID
     */
    public Optional<UrlResponse> findResponseById(Long id) {
        return urlRepository.findById(id).map(this::mapToResponse);
    }

    /**
     * Find URL response by ID and User
     */
    public Optional<UrlResponse> findResponseByIdAndUser(Long id, User user) {
        return urlRepository.findById(id)
                .filter(url -> url.getUser() != null && url.getUser().getId().equals(user.getId()))
                .map(this::mapToResponse);
    }

    /**
     * Find URL by short code
     */
    public Optional<Url> findByShortCode(String shortCode) {
        return urlRepository.findByShortCode(shortCode);
    }

    /**
     * Create a new shortened URL
     */
    public UrlResponse createShortUrl(CreateUrlRequest request, User user) {
        
        Url url = Url.builder()
                .originalUrl(request.getOriginalUrl())
                .shortCode("pending") // temporary
                .user(user)
                .build();
                
        if (request.getVisibility() != null && request.getVisibility().equalsIgnoreCase("PRIVATE")) {
            url.setVisibility(com.ajinkya.linky.entity.Visibility.PRIVATE);
        }
        
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            url.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        
        if (request.getIsOneTime() != null) {
            url.setIsOneTime(request.getIsOneTime());
        }
        
        url = urlRepository.save(url); // Save to get the ID

        String customAlias = request.getCustomAlias();
        if (customAlias != null && !customAlias.isBlank()) {
            if (urlRepository.existsByShortCode(customAlias)) {
                throw new DuplicateShortCodeException("Short code already exists.");
            }
            url.setShortCode(customAlias);
            url.setCustomAlias(customAlias);
        } else {
            url.setShortCode(Base62Encoder.encode(url.getId()));
        }

        url = urlRepository.save(url);
        
        // Write-through cache
        redisService.save(url.getShortCode(), url.getOriginalUrl());

        return mapToResponse(url);
    }

    public UrlResponse mapToResponse(Url url) {
        return UrlResponse.builder()
                .id(url.getId())
                .shortCode(url.getShortCode())
                .originalUrl(url.getOriginalUrl())
                .createdAt(url.getCreatedAt())
                .clickCount(url.getClickCount())
                .title(url.getTitle())
                .description(url.getDescription())
                .isFavorite(url.getIsFavorite())
                .isArchived(url.getIsArchived())
                .visibility(url.getVisibility() != null ? url.getVisibility().name() : null)
                .tags(url.getTags() != null ? url.getTags().stream().map(com.ajinkya.linky.entity.Tag::getName).toList() : null)
                .build();
    }

    /**
     * Update an existing URL
     */
    public Url update(Long id, Url updatedUrl) {

        Url existing = urlRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("URL not found with id " + id));

        existing.setOriginalUrl(updatedUrl.getOriginalUrl());
        existing.setShortCode(updatedUrl.getShortCode());
        existing.setExpiresAt(updatedUrl.getExpiresAt());
        existing.setUser(updatedUrl.getUser());
        existing.setCustomAlias(updatedUrl.getCustomAlias());
        if (updatedUrl.getIsActive() != null) {
            existing.setIsActive(updatedUrl.getIsActive());
        }
        
        // Update cache
        redisService.save(existing.getShortCode(), existing.getOriginalUrl());

        return existing;
    }

    public List<UrlResponse> getUserUrls(User user) {
        return urlRepository.findByUser(user).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<UrlResponse> getUserUrlsFiltered(User user, String search, String sort, String tag) {
        Specification<Url> spec = Specification.where((root, query, cb) -> cb.equal(root.get("user"), user));

        if (search != null && !search.isBlank()) {
            String searchPattern = "%" + search.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), searchPattern),
                    cb.like(cb.lower(root.get("originalUrl")), searchPattern),
                    cb.like(cb.lower(root.get("description")), searchPattern)
            ));
        }

        if (tag != null && !tag.isBlank()) {
            spec = spec.and((root, query, cb) -> {
                Join<Url, Tag> tags = root.join("tags");
                return cb.equal(cb.lower(tags.get("name")), tag.toLowerCase());
            });
        }

        Sort sorting = Sort.unsorted();
        if (sort != null && !sort.isBlank()) {
            if (sort.equalsIgnoreCase("clicks")) sorting = Sort.by(Sort.Direction.DESC, "clickCount");
            else if (sort.equalsIgnoreCase("clicks_asc")) sorting = Sort.by(Sort.Direction.ASC, "clickCount");
            else if (sort.equalsIgnoreCase("oldest")) sorting = Sort.by(Sort.Direction.ASC, "createdAt");
            else if (sort.equalsIgnoreCase("newest")) sorting = Sort.by(Sort.Direction.DESC, "createdAt");
        } else {
            sorting = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return urlRepository.findAll(spec, sorting).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<UrlResponse> getUserFavoriteUrls(User user) {
        return urlRepository.findByUserAndIsFavorite(user, true).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public List<UrlResponse> getUserArchivedUrls(User user) {
        return urlRepository.findByUserAndIsArchived(user, true).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public UrlResponse toggleFavorite(String shortCode, User user) {
        Url url = urlRepository.findByShortCodeAndUser(shortCode, user).orElseThrow(() -> new ResourceNotFoundException("URL not found"));
        url.setIsFavorite(!url.getIsFavorite());
        return mapToResponse(urlRepository.save(url));
    }

    public UrlResponse toggleArchive(String shortCode, User user) {
        Url url = urlRepository.findByShortCodeAndUser(shortCode, user).orElseThrow(() -> new ResourceNotFoundException("URL not found"));
        url.setIsArchived(!url.getIsArchived());
        return mapToResponse(urlRepository.save(url));
    }

    public UrlResponse duplicateUrl(String shortCode, User user) {
        Url original = urlRepository.findByShortCodeAndUser(shortCode, user).orElseThrow(() -> new ResourceNotFoundException("URL not found"));
        
        Url duplicate = Url.builder()
                .originalUrl(original.getOriginalUrl())
                .shortCode("pending")
                .user(user)
                .title(original.getTitle() != null ? original.getTitle() + " (Copy)" : null)
                .description(original.getDescription())
                .visibility(original.getVisibility())
                .passwordHash(original.getPasswordHash())
                .isOneTime(original.getIsOneTime())
                .isFavorite(original.getIsFavorite())
                .isArchived(original.getIsArchived())
                .build();
                
        duplicate = urlRepository.save(duplicate);
        duplicate.setShortCode(Base62Encoder.encode(duplicate.getId()));
        duplicate = urlRepository.save(duplicate);
        
        redisService.save(duplicate.getShortCode(), duplicate.getOriginalUrl());
        return mapToResponse(duplicate);
    }

    public UrlResponse updateUrlDetails(String shortCode, UpdateUrlRequest request, User user) {
        Url url = urlRepository.findByShortCodeAndUser(shortCode, user).orElseThrow(() -> new ResourceNotFoundException("URL not found"));
        
        url.setTitle(request.getTitle());
        url.setDescription(request.getDescription());
        
        if (request.getTags() != null) {
            Set<Tag> tags = new HashSet<>();
            for (String tagName : request.getTags()) {
                if (tagName != null && !tagName.isBlank()) {
                    List<Tag> userTags = tagRepository.findByUser(user);
                    Tag tag = userTags.stream().filter(t -> t.getName().equalsIgnoreCase(tagName)).findFirst().orElse(null);
                    if (tag == null) {
                        tag = Tag.builder().name(tagName).user(user).build();
                        tag = tagRepository.save(tag);
                    }
                    tags.add(tag);
                }
            }
            url.setTags(tags);
        }
        
        return mapToResponse(urlRepository.save(url));
    }

    /**
     * Delete URL
     */
    public void delete(Long id) {
        Url existing = urlRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("URL not found with id " + id));

        existing.setIsDeleted(true);
        urlRepository.save(existing);
        redisService.delete(existing.getShortCode());
    }

    /**
     * Check if short code exists
     */
    public boolean existsByShortCode(String shortCode) {
        return urlRepository.existsByShortCode(shortCode);
    }

    /**
     * Increment click count
     */
    public void incrementClickCount(String shortCode) {

        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Short code not found"));

        url.setClickCount(url.getClickCount() + 1);
    }

    /**
     * Check if URL has expired
     */
    public boolean isExpired(Url url) {

        return url.getExpiresAt() != null
                && url.getExpiresAt().isBefore(LocalDateTime.now());
    }

    /**
     * Get original URL for redirection.
     */
    public String getOriginalUrl(String shortCode) {
        
        Optional<String> cachedUrl = redisService.get(shortCode);
        if (cachedUrl.isPresent()) {
            log.debug("Cache HIT for {}", shortCode);
            return cachedUrl.get();
        }
        
        log.debug("Cache MISS for {}", shortCode);
        
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Short code not found"));

        if (isExpired(url)) {
            throw new ResourceNotFoundException("This URL has expired.");
        }
        
        if (!url.getIsActive()) {
            throw new ResourceNotFoundException("This URL is not active.");
        }

        // Populate cache
        redisService.save(shortCode, url.getOriginalUrl());

        return url.getOriginalUrl();
    }

    /**
     * Total URLs
     */
    public long count() {
        return urlRepository.count();
    }
}