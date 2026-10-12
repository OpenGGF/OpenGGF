package com.openggf.mods.run;

import com.openggf.game.ModApi;

import java.util.Objects;

/**
 * One translucent ghost the engine draws in the level this frame, using the stock art of
 * {@code characterCode} ({@code sonic}, {@code tails} or {@code knuckles}). The engine owns art
 * allocation and layer placement; the host only supplies poses.
 *
 * @param slotId        stable identity of the ghost across frames (art is cached per slot)
 * @param characterCode stock character whose art is drawn
 * @param pose          where and how to draw it
 * @param nameplate     optional label drawn above the ghost, or null
 * @param opacity       0 (invisible) to 1 (the engine's normal ghost translucency)
 */
@ModApi
public record GhostPose(String slotId, String characterCode, PlayerPose pose, String nameplate,
                        float opacity) {
    public GhostPose {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(characterCode, "characterCode");
        Objects.requireNonNull(pose, "pose");
        opacity = Math.clamp(opacity, 0f, 1f);
    }
}
