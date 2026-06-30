package com.openggf.game.render;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.SpecialRenderEffectSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-owned registry for staged special render effects.
 *
 * <p>Effects are grouped by {@link SpecialRenderEffectStage} and dispatched from
 * the standard scene pipeline at fixed points between background, foreground,
 * and sprite rendering.
 */
public final class SpecialRenderEffectRegistry
        implements RewindSnapshottable<SpecialRenderEffectSnapshot> {

    private final EnumMap<SpecialRenderEffectStage, List<SpecialRenderEffect>> effectsByStage =
            new EnumMap<>(SpecialRenderEffectStage.class);

    public SpecialRenderEffectRegistry() {
        for (SpecialRenderEffectStage stage : SpecialRenderEffectStage.values()) {
            effectsByStage.put(stage, new ArrayList<>());
        }
    }

    /** Registers one effect at its declared stage. */
    public void register(SpecialRenderEffect effect) {
        Objects.requireNonNull(effect, "effect");
        effectsByStage.get(effect.stage()).add(effect);
    }

    /** Removes all staged effects. */
    public void clear() {
        for (List<SpecialRenderEffect> effects : effectsByStage.values()) {
            effects.clear();
        }
    }

    /** Returns {@code true} when no stage contains any registered effects. */
    public boolean isEmpty() {
        for (List<SpecialRenderEffect> effects : effectsByStage.values()) {
            if (!effects.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Returns the number of registered effects for one stage. */
    public int size(SpecialRenderEffectStage stage) {
        Objects.requireNonNull(stage, "stage");
        List<SpecialRenderEffect> effects = effectsByStage.get(stage);
        return effects != null ? effects.size() : 0;
    }

    /** Returns the total number of registered effects across all stages. */
    public int activeEffectCount() {
        int count = 0;
        for (List<SpecialRenderEffect> effects : effectsByStage.values()) {
            count += effects.size();
        }
        return count;
    }

    /**
     * Executes all effects registered for the requested stage.
     * Registrations are a level/setup-time contract; callers must not mutate
     * this registry while a stage dispatch is in progress.
     */
    public void dispatch(SpecialRenderEffectStage stage, SpecialRenderEffectContext context) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(context, "context");
        List<SpecialRenderEffect> effects = effectsByStage.get(stage);
        if (effects == null || effects.isEmpty()) {
            return;
        }
        for (SpecialRenderEffect effect : effects) {
            effect.render(context);
        }
    }

    // ── RewindSnapshottable ───────────────────────────────────────────────

    @Override
    public String key() {
        return "special-render";
    }

    @Override
    public SpecialRenderEffectSnapshot capture() {
        return new SpecialRenderEffectSnapshot(effectsByStage, captureEffectStates());
    }

    @Override
    public void restore(SpecialRenderEffectSnapshot s) {
        for (Map.Entry<SpecialRenderEffectStage, List<SpecialRenderEffect>> e
                : effectsByStage.entrySet()) {
            e.getValue().clear();
            List<SpecialRenderEffect> saved = s.effectsByStage().get(e.getKey());
            if (saved != null) {
                e.getValue().addAll(saved);
            }
        }
        restoreEffectStates(s);
    }

    private Map<SpecialRenderEffectStage, List<SpecialRenderEffectSnapshot.EffectState>> captureEffectStates() {
        EnumMap<SpecialRenderEffectStage, List<SpecialRenderEffectSnapshot.EffectState>> states =
                new EnumMap<>(SpecialRenderEffectStage.class);
        for (Map.Entry<SpecialRenderEffectStage, List<SpecialRenderEffect>> entry : effectsByStage.entrySet()) {
            List<SpecialRenderEffectSnapshot.EffectState> stageStates = new ArrayList<>();
            List<SpecialRenderEffect> effects = entry.getValue();
            for (int i = 0; i < effects.size(); i++) {
                SpecialRenderEffect effect = effects.get(i);
                if (effect instanceof RewindSnapshottable<?> snapshottable) {
                    stageStates.add(new SpecialRenderEffectSnapshot.EffectState(
                            i,
                            snapshottable.key(),
                            Objects.requireNonNull(snapshottable.capture(),
                                    "Special render effect snapshot must not be null for key: "
                                            + snapshottable.key())));
                }
            }
            if (!stageStates.isEmpty()) {
                states.put(entry.getKey(), stageStates);
            }
        }
        return states;
    }

    private void restoreEffectStates(SpecialRenderEffectSnapshot snapshot) {
        for (Map.Entry<SpecialRenderEffectStage, List<SpecialRenderEffectSnapshot.EffectState>> entry
                : snapshot.effectStatesByStage().entrySet()) {
            List<SpecialRenderEffect> effects = effectsByStage.get(entry.getKey());
            if (effects == null) {
                continue;
            }
            for (SpecialRenderEffectSnapshot.EffectState state : entry.getValue()) {
                if (state.index() < 0 || state.index() >= effects.size()) {
                    throw new IllegalStateException("Missing special render effect for snapshot key: " + state.key());
                }
                SpecialRenderEffect effect = effects.get(state.index());
                if (!(effect instanceof RewindSnapshottable<?> snapshottable)
                        || !snapshottable.key().equals(state.key())) {
                    throw new IllegalStateException("Cannot restore special render effect snapshot for key: "
                            + state.key());
                }
                restoreRaw(snapshottable, state.snapshot());
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void restoreRaw(RewindSnapshottable snapshottable, Object snapshot) {
        snapshottable.restore(snapshot);
    }
}
