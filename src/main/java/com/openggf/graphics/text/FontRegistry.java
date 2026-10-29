package com.openggf.graphics.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.Character.UnicodeScript;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host font fallback chains, read from {@code fonts/host-fonts.json}. A language needs no
 * code: its script's faces apply automatically, and a manifest entry can prefer a variant
 * (for example Han glyph forms) or reserve a taller line for it.
 */
final class FontRegistry {
    static final String MANIFEST = "fonts/host-fonts.json";

    private record Entry(Map<TextFace, HostFace> faces, List<String> preferredFor, boolean fallback) { }

    private final Map<TextFace, NativeFace> natives = new EnumMap<>(TextFace.class);
    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, List<HostFace>> chains = new ConcurrentHashMap<>();

    FontRegistry(JsonNode manifest) {
        for (TextFace role : TextFace.values()) natives.put(role, new NativeFace(role));
        for (JsonNode node : manifest.path("faces")) {
            String id = node.path("id").asText();
            String format = node.path("format").asText();
            List<String> preferred = new ArrayList<>();
            node.path("preferredFor").forEach(tag -> preferred.add(tag.asText()));
            boolean fallback = node.path("fallback").asBoolean(true);
            Map<TextFace, HostFace> faces = new EnumMap<>(TextFace.class);
            switch (format) {
                case "native" -> faces.putAll(natives);
                case "bdf", "hex" -> {
                    String resource = node.path("resource").asText();
                    BitmapFace face = new BitmapFace(id,
                            () -> format.equals("bdf") ? BitmapFontTables.bdf(resource) : BitmapFontTables.hex(resource),
                            node.path("ascent").asInt(), node.path("descent").asInt(),
                            node.path("overlayMarks").asBoolean(false));
                    for (TextFace role : TextFace.values()) faces.put(role, face);
                }
                case "opentype" -> {
                    Set<UnicodeScript> scripts = new HashSet<>();
                    node.path("scripts").forEach(script -> scripts.add(UnicodeScript.valueOf(script.asText())));
                    for (TextFace role : TextFace.values()) {
                        JsonNode size = node.path("roles").path((role == TextFace.FIXED ? TextFace.COMPACT : role).name());
                        if (size.isMissingNode()) continue;
                        faces.put(role, new OpenTypeFace(id + "/" + role.name().toLowerCase(java.util.Locale.ROOT),
                                size.path("resource").asText(), size.path("pixels").asInt(), scripts));
                    }
                }
                default -> throw new IllegalArgumentException("Unknown host font format " + format + " for " + id);
            }
            entries.add(new Entry(faces, List.copyOf(preferred), fallback));
        }
    }

    static FontRegistry bundled() {
        try (InputStream in = FontRegistry.class.getClassLoader().getResourceAsStream(MANIFEST)) {
            if (in == null) throw new IOException("missing resource " + MANIFEST);
            return new FontRegistry(new ObjectMapper().readTree(in));
        } catch (IOException e) {
            throw new UncheckedIOException("host font manifest is unreadable", e);
        }
    }

    NativeFace nativeFace(TextFace role) {
        return natives.get(role);
    }

    /** Native lettering first, then language-preferred faces, then the general fallbacks. */
    List<HostFace> chain(TextFace role, TextLanguage language) {
        return chains.computeIfAbsent(role + "|" + language.locale().toLanguageTag(), key -> {
            LinkedHashMap<HostFace, Boolean> ordered = new LinkedHashMap<>();
            ordered.put(natives.get(role), true);
            preferred(role, language).forEach(face -> ordered.put(face, true));
            for (Entry entry : entries) {
                HostFace face = entry.faces().get(role);
                if (face != null && entry.fallback()) ordered.putIfAbsent(face, true);
            }
            return List.copyOf(ordered.keySet());
        });
    }

    /** Faces whose manifest entry names this language, most specific match first. */
    List<HostFace> preferred(TextFace role, TextLanguage language) {
        record Scored(HostFace face, int score, int order) { }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            HostFace face = entry.faces().get(role);
            if (face == null) continue;
            int best = entry.preferredFor().stream().mapToInt(language::match).max().orElse(0);
            if (best > 0) scored.add(new Scored(face, best, i));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparingInt(Scored::order));
        return scored.stream().map(Scored::face).toList();
    }
}
