package com.openggf.mods.code;

import com.openggf.game.ObjectArtProvider;
import com.openggf.level.objects.BakedSheetReader;
import com.openggf.level.objects.ObjectSpriteSheet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Mod-side adapter that converts validated baked sheets into an engine art overlay. */
public final class ModArtOverlayProvider {
    private ModArtOverlayProvider() {}

    public static ObjectArtProvider decorate(ObjectArtProvider base,
                                             Map<String, BakedSheetReader.BakedSheet> overlays) {
        return decorate(base, overlays, Map.of());
    }

    /** Overload that also merges in already-materialized sheets (e.g. from ROM art intake). */
    static ObjectArtProvider decorate(ObjectArtProvider base,
                                      Map<String, BakedSheetReader.BakedSheet> prepared,
                                      Map<String, ObjectSpriteSheet> extraSheets) {
        Map<String, ObjectSpriteSheet> converted = convert(prepared);
        converted.putAll(Objects.requireNonNull(extraSheets, "extraSheets"));
        return new com.openggf.game.ObjectArtOverlayProvider(base, converted);
    }

    private static Map<String, ObjectSpriteSheet> convert(
            Map<String, BakedSheetReader.BakedSheet> overlays) {
        Objects.requireNonNull(overlays, "overlays");
        LinkedHashMap<String, ObjectSpriteSheet> converted = new LinkedHashMap<>();
        overlays.forEach((key, baked) -> converted.put(
                Objects.requireNonNull(key, "overlay key"),
                Objects.requireNonNull(baked, "overlay sheet").toObjectSpriteSheet()));
        return converted;
    }
}
