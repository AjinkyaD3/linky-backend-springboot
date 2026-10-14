package com.ajinkya.linky.dto;

/**
 * One conditional-redirect rule: when the visitor matches (type, value), send them to {@code url}
 * instead of the link's default destination. Rules are evaluated in order; first match wins.
 *
 * @param type  "device" (mobile|tablet|desktop), "os" (ios|android|windows|macos|linux) or "country" (ISO-3166 alpha-2)
 * @param value the value to match for that type
 * @param url   absolute http(s) destination
 */
public record RoutingRule(String type, String value, String url) {}
