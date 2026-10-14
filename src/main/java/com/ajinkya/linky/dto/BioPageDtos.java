package com.ajinkya.linky.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request and response shapes for link-in-bio pages. */
public final class BioPageDtos {

    private BioPageDtos() {}

    public record LinkInput(
            @NotBlank(message = "Each link needs a label") @Size(max = 100, message = "Label is too long (max 100)") String label,
            @NotBlank(message = "Each link needs a URL")
            @Pattern(regexp = "(?i)^https?://\\S+$", message = "Each link must be a valid http or https URL")
            @Size(max = 2048, message = "URL is too long") String url) {}

    public record PageRequest(
            @NotBlank(message = "Slug is required")
            @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,38}[a-z0-9])$",
                    message = "Slug must be 3-40 characters: lowercase letters, digits and dashes, not starting or ending with a dash")
            String slug,
            @NotBlank(message = "Title is required") @Size(max = 100, message = "Title is too long (max 100)") String title,
            @Size(max = 300, message = "Bio is too long (max 300)") String bio,
            @NotNull(message = "links is required") @Size(max = 20, message = "At most 20 links per page") List<@Valid LinkInput> links) {}

    /** A link on a page as shown to its owner (includes the original URL for editing). */
    public record OwnerLink(String label, String url, String shortCode) {}

    public record OwnerPage(Long id, String slug, String title, String bio, List<OwnerLink> links) {}

    /** What the public sees: only labels and short codes, never owner details. */
    public record PublicLink(String label, String shortCode) {}

    public record PublicPage(String slug, String title, String bio, List<PublicLink> links) {}
}
