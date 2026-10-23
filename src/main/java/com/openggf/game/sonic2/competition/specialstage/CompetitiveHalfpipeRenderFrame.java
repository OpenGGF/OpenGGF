package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageObject;
import com.openggf.graphics.RenderPriority;

import java.util.List;
import java.util.Objects;

/** Native DisplaySprite queue after BuildSprites dereferences its live object slots. */
public record CompetitiveHalfpipeRenderFrame(List<CompetitiveHalfpipeRenderFrame.Draw> draws) {
    public enum Type {
        PLAYER, PLAYER_SHADOW, TAIL, HUD, COUNTERS, WIN_COUNTERS, ITEM, ITEM_SHADOW,
        SPILL, BANNER, MESSAGE, RAINBOW, HAND, WINGS
    }

    /**
     * Fixed actors retain their logical owner pose; other coordinates are absolute H32 screen
     * coordinates. COUNTERS and WIN_COUNTERS instead carry their frozen P1/P2 values in x/y.
     * Item/particle references supply type or ROM art identity, never synthetic display eligibility.
     */
    public record Draw(Type type, int slot, int bucket, int owner, int x, int y, int mappingFrame,
                       int nativeArt, int palette, boolean xFlip,
                       Sonic2SpecialStageObject item, CompetitiveHalfpipeSpill spill) {
        public Draw {
            Objects.requireNonNull(type, "draw type");
            Objects.checkIndex(slot, CompetitiveHalfpipeObjectRam.SLOT_COUNT);
            RenderPriority.bucket(bucket);
            if (palette < 0 || palette > 3) throw new IllegalArgumentException("Native palette line must be 0..3");
            if (type == Type.ITEM) Objects.requireNonNull(item, "item draw");
            if (type == Type.SPILL) Objects.requireNonNull(spill, "spill draw");
        }
    }

    public CompetitiveHalfpipeRenderFrame {
        draws = List.copyOf(draws);
        int previousBucket = RenderPriority.MIN;
        for (Draw draw : draws) {
            if (draw.bucket() < previousBucket) {
                throw new IllegalArgumentException("Native display queue must retain ascending bucket order");
            }
            previousBucket = draw.bucket();
        }
    }
}
