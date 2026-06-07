package com.openggf.game.rewind.snapshot;

import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectStage;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of {@link com.openggf.game.render.SpecialRenderEffectRegistry}
 * active-effect list per stage.
 *
 * <p>Effect object references are captured by identity. Effects are stateless
 * unless they implement {@link com.openggf.game.rewind.RewindSnapshottable};
 * stateful effect snapshots are captured alongside the identity list and
 * restored into the same registered effect instance.
 */
public record SpecialRenderEffectSnapshot(
        Map<SpecialRenderEffectStage, List<SpecialRenderEffect>> effectsByStage,
        Map<SpecialRenderEffectStage, List<EffectState>> effectStatesByStage
) {
    public record EffectState(int index, String key, Object snapshot) {
    }

    public SpecialRenderEffectSnapshot(Map<SpecialRenderEffectStage, List<SpecialRenderEffect>> effectsByStage) {
        this(effectsByStage, Map.of());
    }

    public SpecialRenderEffectSnapshot {
        EnumMap<SpecialRenderEffectStage, List<SpecialRenderEffect>> effectCopy =
                new EnumMap<>(SpecialRenderEffectStage.class);
        for (Map.Entry<SpecialRenderEffectStage, List<SpecialRenderEffect>> e
                : effectsByStage.entrySet()) {
            effectCopy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        effectsByStage = Collections.unmodifiableMap(effectCopy);

        EnumMap<SpecialRenderEffectStage, List<EffectState>> stateCopy =
                new EnumMap<>(SpecialRenderEffectStage.class);
        for (Map.Entry<SpecialRenderEffectStage, List<EffectState>> e
                : effectStatesByStage.entrySet()) {
            stateCopy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        effectStatesByStage = Collections.unmodifiableMap(stateCopy);
    }
}
