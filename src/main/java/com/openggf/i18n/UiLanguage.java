package com.openggf.i18n;

/** Catalog-derived language option. Coverage describes current entries, before fallback. */
public record UiLanguage(String tag, String name, String quality, int translated, int total, int stale) { }
