package com.openggf.game.presentation;

import com.openggf.graphics.SpritePresentation.PatternVersion;
import com.openggf.level.Level;
import com.openggf.level.Pattern;

import java.util.HashMap;
import java.util.Map;

/** Local immutable art residency; no content from an incoming scene is inserted into this catalog. */
public final class RomSceneArtCatalog {
    private final Map<ScenePresentationFrame.ArtReference, PatternVersion> patterns = new HashMap<>();
    private final Map<PatternVersion, ScenePresentationFrame.ArtReference> references = new HashMap<>();

    public void addRecipe(String recipe, Pattern[] art) {
        if (art == null) return;
        for (int index = 0; index < art.length; index++) {
            if (art[index] == null) continue;
            var reference = new ScenePresentationFrame.ArtReference(recipe, index);
            var version = version(art[index]);
            var old = patterns.putIfAbsent(reference, version);
            if (old != null && !old.equals(version)) throw new IllegalStateException("ROM recipe changed: " + reference);
            references.putIfAbsent(version, reference);
        }
    }

    public void addLevel(String recipe, Level level) {
        Pattern[] art = new Pattern[level.getPatternCount()];
        for (int i = 0; i < art.length; i++) art[i] = level.getPattern(i);
        addRecipe(recipe, art);
    }

    public ScenePresentationFrame.ArtReference reference(PatternVersion version) {
        var reference = references.get(version);
        if (reference == null) throw new IllegalStateException("Displayed art has no local ROM decoding recipe");
        return reference;
    }

    PatternVersion resolve(ScenePresentationFrame.ArtReference reference) {
        var pattern = patterns.get(reference);
        if (pattern == null) throw new IllegalArgumentException("Unknown local ROM recipe: " + reference);
        return pattern;
    }

    public static PatternVersion version(Pattern pattern) {
        return new PatternVersion(pack(pattern, 0), pack(pattern, 16), pack(pattern, 32), pack(pattern, 48));
    }

    private static long pack(Pattern pattern, int first) {
        long value = 0;
        for (int i = 0; i < 16; i++) value |= (long) (pattern.getPixel((first + i) % 8, (first + i) / 8) & 15) << (4 * i);
        return value;
    }

    static int pixel(PatternVersion version, int x, int y) {
        int index = y * 8 + x;
        long word = switch (index / 16) { case 0 -> version.a(); case 1 -> version.b(); case 2 -> version.c(); default -> version.d(); };
        return (int) ((word >>> (4 * (index % 16))) & 15);
    }
}
