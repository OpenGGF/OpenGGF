package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.soniccd.level.CdLevel;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * {@code stage_layer} / {@code layer} (Main CPU Variables.inc:183, "Layer ID"): the byte the Stardust
 * Speedway path-swap object toggles (path_swap.asm:76 {@code eori.b #1,stage_layer}) and the mesh and swap
 * platform read (mesh.asm:27, swap_platform.asm:15). Its one non-object reader is the player's block fetch:
 * {@code Collision Floor.asm:565-583} (src/common/block_collide.asm:112-135 for the other dialect) adds one to
 * chunks $28, $3C, $37, $2F and $16 while it is set, and tests the player's path bit for chunks 4 and 6.
 * That fetch belongs to the player lane (the zone's {@code PlayerCheckBlock}, src/r7/player_block_check.asm,
 * is not ported); this is the typed input it reads, one per loaded stage like {@code CdLevelFlags}
 * (cleared at stage load; rewind-captured through {@code SszPlayerProfile}).
 */
public final class SszStageLayer {
    private static final Map<CdLevel, SszStageLayer> LAYERS = Collections.synchronizedMap(new WeakHashMap<>());

    private int layer;

    private SszStageLayer() { }

    /** The loaded stage's layer, or a detached cleared one outside a stage. */
    public static SszStageLayer of(CdLevel level) {
        return level == null ? new SszStageLayer() : LAYERS.computeIfAbsent(level, l -> new SszStageLayer());
    }

    /** {@code tst.b stage_layer}: nonzero. */
    public boolean isSet() { return layer != 0; }

    public int value() { return layer; }

    /** {@code eori.b #1,stage_layer}. */
    public void toggle() { layer ^= 1; }

    /** Rewind: puts back a captured {@link #value()}. */
    public void restore(int value) { layer = value & 0xFF; }
}
