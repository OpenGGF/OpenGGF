package com.openggf.game.sonic3k.events;

import com.openggf.game.mutation.LayoutMutationContext;
import com.openggf.game.mutation.LevelMutationSurface;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.SeamlessTransitionResourceHandoff;
import com.openggf.level.resources.DeferredLevelResourceManifest;

import java.util.BitSet;

/** Retained background VRAM from MHZ1_BackgroundEvent's Load_Level call. */
record MhzActTransitionHandoff(Level previous, Sonic3kMHZEvents access)
        implements SeamlessTransitionResourceHandoff {
    @Override
    public DeferredLevelResourceManifest deferredResources() {
        return DeferredLevelResourceManifest.EMPTY;
    }

    @Override
    public void transferAfterTargetInit() {
        var manager = access.levelManager();
        var target = manager.getCurrentLevel();
        // AnimateTiles_MHZ writes $80 words at tile $1B8 and $200 at $1D5.
        // MHZ1_BackgroundEvent calls Load_Level/LoadSolids without replacing
        // these VRAM ranges. Our reload reconstructs base art, whose slots
        // contain unrelated glyphs until the next Animate_Tiles pass. Retain
        // the previous level's final DMA pixels instead of ticking animation
        // again (which would also advance the mushroom-cap counter).
        // Keep the outgoing level until transfer so a final animation pass
        // after the event requests its reload is included in this handoff.
        var context = new LayoutMutationContext(LevelMutationSurface.forLevel(target), manager::applyMutationEffects);
        access.zoneLayoutMutationPipeline().applyImmediately(mutation -> {
            BitSet changed = new BitSet();
            copyRange(mutation.surface(), 0x1B8, 8, changed);
            copyRange(mutation.surface(), 0x1D5, 32, changed);
            return new MutationEffects(changed, true, false, false, false, false, false);
        }, context);
    }

    private void copyRange(LevelMutationSurface target, int first, int count, BitSet changed) {
        for (int tile = first; tile < first + count; tile++) {
            Pattern retained = new Pattern();
            retained.copyFrom(previous.getPattern(tile));
            target.setPattern(tile, retained);
            changed.set(tile);
        }
    }
}
