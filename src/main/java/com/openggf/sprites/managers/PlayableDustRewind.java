package com.openggf.sprites.managers;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Internal fixed-Dust owner omitted by the legacy public animation-only record.
 * Values carry scalars and the producer-supplied surface art's borrowed resource
 * binding, never a player/controller instance or mutable presentation-cache copy.
 * Rebinding waits for every restore adapter, including roster/art recreation.
 */
final class PlayableDustRewind implements RewindSnapshottable<Map<String, SpindashDustController.EffectsRewindState>> {
    private final SpriteManager sprites;
    private Map<String, SpindashDustController.EffectsRewindState> pending;

    PlayableDustRewind(SpriteManager sprites) {
        this.sprites = Objects.requireNonNull(sprites, "sprites");
    }

    @Override public String key() { return "playable-dust-effects"; }

    @Override public Map<String, SpindashDustController.EffectsRewindState> capture() {
        var values = new LinkedHashMap<String, SpindashDustController.EffectsRewindState>();
        for (var sprite : sprites.getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite player) {
                var dust = player.getSpindashDustController();
                if (dust != null) values.put(player.getCode(), dust.captureEffectsRewindState());
            }
        }
        return Map.copyOf(values);
    }

    @Override public void restore(Map<String, SpindashDustController.EffectsRewindState> state) {
        pending = Objects.requireNonNull(state, "dust effects snapshot");
    }

    @Override public void resetForMissingSnapshot() { pending = Map.of(); }

    void afterRestore() {
        if (pending == null) return;
        var values = pending;
        pending = null;
        // Use the actual restored roster and current controllers, not captured
        // controller references that an ObjectManager recreation may replace.
        // Validate all captured participants before applying any supplementary state.
        for (String code : values.keySet()) {
            var sprite = sprites.getSprite(code);
            if (!(sprite instanceof AbstractPlayableSprite player)
                    || player.getSpindashDustController() == null) {
                throw new IllegalStateException("Missing restored fixed-Dust participant: " + code);
            }
        }
        for (var sprite : sprites.getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite player) {
                var dust = player.getSpindashDustController();
                if (dust != null) dust.restoreEffectsRewindState(values.get(player.getCode()));
            }
        }
    }
}
