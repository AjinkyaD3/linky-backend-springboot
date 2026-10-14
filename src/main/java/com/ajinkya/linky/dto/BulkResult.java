package com.ajinkya.linky.dto;

/** Outcome for one row of a bulk create: exactly one of {@code link} / {@code error} is set. */
public record BulkResult(int index, boolean success, UrlResponse link, String error) {}
