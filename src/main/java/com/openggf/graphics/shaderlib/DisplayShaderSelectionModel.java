package com.openggf.graphics.shaderlib;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class DisplayShaderSelectionModel {
    private static final String OFF_CATEGORY = "System";

    private final List<SelectionItem> items;
    private List<SelectionItem> visibleItems;

    public DisplayShaderSelectionModel(DisplayShaderLibrary library) {
        this(Objects.requireNonNull(library, "library").entries());
    }

    public DisplayShaderSelectionModel(List<DisplayShaderPresetRef> entries) {
        Objects.requireNonNull(entries, "entries");
        this.items = entries.stream()
                .map(DisplayShaderSelectionModel::toSelectionItem)
                .toList();
        this.visibleItems = items;
    }

    public List<SelectionItem> filter(String query) {
        String normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.isEmpty()) {
            visibleItems = items;
            return visibleItems;
        }

        List<SelectionItem> filtered = new ArrayList<>();
        for (SelectionItem item : items) {
            if (item.ref().kind() == DisplayShaderPresetRef.Kind.OFF
                    || matches(item.displayPath(), normalizedQuery)
                    || matches(item.category(), normalizedQuery)) {
                filtered.add(item);
            }
        }
        visibleItems = List.copyOf(filtered);
        return visibleItems;
    }

    public List<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        for (SelectionItem item : items) {
            String category = item.category();
            if (!category.isBlank() && item.ref().kind() != DisplayShaderPresetRef.Kind.OFF) {
                categories.add(category);
            }
        }
        return List.copyOf(categories);
    }

    public DisplayShaderPresetRef select(int visibleIndex) {
        Objects.checkIndex(visibleIndex, visibleItems.size());
        return visibleItems.get(visibleIndex).ref();
    }

    private static SelectionItem toSelectionItem(DisplayShaderPresetRef ref) {
        if (ref.kind() == DisplayShaderPresetRef.Kind.OFF) {
            return new SelectionItem(ref, OFF_CATEGORY, ref.label());
        }
        String displayPath = ref.relativePath().replace('\\', '/');
        return new SelectionItem(ref, categoryFor(displayPath), displayPath);
    }

    private static String categoryFor(String displayPath) {
        String[] segments = displayPath.split("/");
        int start = startAfterKnownInstallRoot(segments);
        int directoryLimit = Math.max(0, segments.length - 1);
        for (int i = start; i < directoryLimit; i++) {
            String segment = segments[i];
            if (!segment.isBlank() && !isImplementationSegment(segment)) {
                return segment;
            }
        }
        return "";
    }

    private static int startAfterKnownInstallRoot(String[] segments) {
        if (segments.length == 0) {
            return 0;
        }
        if ("libretro-glsl".equalsIgnoreCase(segments[0])) {
            return 1;
        }
        if (segments.length >= 2
                && "RetroArch".equalsIgnoreCase(segments[0])
                && "shaders_glsl".equalsIgnoreCase(segments[1])) {
            return 2;
        }
        return 0;
    }

    private static boolean isImplementationSegment(String segment) {
        return "shaders".equalsIgnoreCase(segment) || "resources".equalsIgnoreCase(segment);
    }

    private static boolean matches(String value, String normalizedQuery) {
        return value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        return query.trim().toLowerCase(Locale.ROOT);
    }

    public record SelectionItem(DisplayShaderPresetRef ref, String category, String displayPath) {}
}
