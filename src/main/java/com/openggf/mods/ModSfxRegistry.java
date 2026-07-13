package com.openggf.mods;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Validated exact-key SFX declarations eligible for launch preparation. */
public final class ModSfxRegistry {
    public static final ModSfxRegistry EMPTY = new ModSfxRegistry(List.of());
    private final List<ModAudioSfx> sfx;
    private final Map<SfxKey, ModAudioSfx> byKey;

    public ModSfxRegistry(List<ModAudioSfx> sfx) {
        this.sfx = List.copyOf(Objects.requireNonNull(sfx, "sfx"));
        LinkedHashMap<SfxKey, ModAudioSfx> values = new LinkedHashMap<>();
        for (ModAudioSfx value : this.sfx) {
            if (values.putIfAbsent(value.key(), value) != null) {
                throw new IllegalArgumentException("Duplicate SFX key: " + value.key());
            }
        }
        this.byKey = Map.copyOf(values);
    }

    public Optional<ModAudioSfx> find(SfxKey key) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(key, "key")));
    }

    public List<ModAudioSfx> sfx() { return sfx; }
}
