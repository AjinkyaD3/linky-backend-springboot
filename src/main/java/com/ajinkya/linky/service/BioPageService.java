package com.ajinkya.linky.service;

import com.ajinkya.linky.dto.BioPageDtos.*;
import com.ajinkya.linky.dto.CreateUrlRequest;
import com.ajinkya.linky.entity.BioPage;
import com.ajinkya.linky.entity.BioPageLink;
import com.ajinkya.linky.entity.User;
import com.ajinkya.linky.exception.DuplicateShortCodeException;
import com.ajinkya.linky.exception.ResourceNotFoundException;
import com.ajinkya.linky.repository.BioPageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Link-in-bio pages. Every button on a page is backed by a normal short link owned by the page's
 * owner, so clicks flow through the same cache and analytics pipeline as any other link.
 * Removing a button leaves its short link in the owner's list (their analytics are not discarded).
 */
@Service
public class BioPageService {

    static final int MAX_PAGES_PER_USER = 5;

    private final BioPageRepository pages;
    private final UrlService urlService;

    public BioPageService(BioPageRepository pages, UrlService urlService) {
        this.pages = pages;
        this.urlService = urlService;
    }

    @Transactional(readOnly = true)
    public List<OwnerPage> mine(User user) {
        return pages.findByUserOrderByCreatedAtDesc(user).stream().map(BioPageService::toOwner).toList();
    }

    @Transactional
    public OwnerPage create(User user, PageRequest request) {
        if (pages.countByUser(user) >= MAX_PAGES_PER_USER) {
            throw new IllegalArgumentException("You can have at most " + MAX_PAGES_PER_USER + " pages");
        }
        if (pages.existsBySlug(request.slug())) {
            throw new DuplicateShortCodeException("That page address is already taken");
        }
        BioPage page = new BioPage();
        page.setUser(user);
        apply(page, request, user);
        return toOwner(pages.save(page));
    }

    @Transactional
    public OwnerPage update(User user, Long id, PageRequest request) {
        BioPage page = owned(user, id);
        if (!page.getSlug().equals(request.slug()) && pages.existsBySlug(request.slug())) {
            throw new DuplicateShortCodeException("That page address is already taken");
        }
        apply(page, request, user);
        return toOwner(pages.save(page));
    }

    @Transactional
    public void delete(User user, Long id) {
        pages.delete(owned(user, id));
    }

    @Transactional(readOnly = true)
    public PublicPage publicView(String slug) {
        BioPage page = pages.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Page not found"));
        return new PublicPage(page.getSlug(), page.getTitle(), page.getBio(),
                page.getLinks().stream().map(l -> new PublicLink(l.getLabel(), l.getShortCode())).toList());
    }

    private BioPage owned(User user, Long id) {
        return pages.findById(id)
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Page not found"));
    }

    /** Copies request fields onto the page, reusing the short link of any button whose URL is unchanged. */
    private void apply(BioPage page, PageRequest request, User user) {
        page.setSlug(request.slug());
        page.setTitle(request.title().trim());
        page.setBio(request.bio() == null || request.bio().isBlank() ? null : request.bio().trim());

        Map<String, Deque<String>> reusable = new HashMap<>();
        for (BioPageLink existing : page.getLinks()) {
            reusable.computeIfAbsent(existing.getOriginalUrl(), k -> new ArrayDeque<>()).add(existing.getShortCode());
        }

        List<BioPageLink> fresh = new ArrayList<>();
        int position = 0;
        for (LinkInput input : request.links()) {
            String url = input.url().trim();
            Deque<String> codes = reusable.get(url);
            String shortCode;
            if (codes != null && !codes.isEmpty()) {
                shortCode = codes.poll();
            } else {
                CreateUrlRequest create = new CreateUrlRequest();
                create.setOriginalUrl(url);
                shortCode = urlService.createShortUrl(create, user).getShortCode();
            }
            BioPageLink link = new BioPageLink();
            link.setPage(page);
            link.setLabel(input.label().trim());
            link.setShortCode(shortCode);
            link.setOriginalUrl(url);
            link.setPosition(position++);
            fresh.add(link);
        }
        page.getLinks().clear();
        page.getLinks().addAll(fresh);
    }

    private static OwnerPage toOwner(BioPage page) {
        return new OwnerPage(page.getId(), page.getSlug(), page.getTitle(), page.getBio(),
                page.getLinks().stream().map(l -> new OwnerLink(l.getLabel(), l.getOriginalUrl(), l.getShortCode())).toList());
    }
}
