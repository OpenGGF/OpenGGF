package com.openggf.game.internal;

import com.openggf.game.GameModule;

/**
 * Where an object's left-wall probe enters {@code FindWall}. Sonic 1's {@code ObjHitWallLeft}
 * and Sonic 2's {@code ObjCheckLeftWallDist} flip the probe X within its block
 * ({@code eori.w #$F,d3}) before the call, and the engine's object left-wall probe models that
 * flip. A host whose object left-wall probe passes the X unflipped registers {@link #RAW}:
 * Sonic CD's {@code ObjGetLWallDist} / {@code CheckBlockLeft} has no {@code eori} (scddisasm
 * src/Level/Collision Check.asm:401-409; src/common/object_block_collide.asm:384-396), which
 * with {@code FixBugs = 0} is the shipped behaviour. Callers that apply the flip themselves
 * (Sonic CD's {@code PlayerCheckBlockLeft2}, object_block_collide.asm:371-378) pass the
 * flipped X.
 */
public enum ObjectLeftWallProbeEntry {
    /** The probe X is flipped within its block before the wall read. */
    FLIPPED,
    /** The probe X reaches the wall read as given. */
    RAW;

    public static ObjectLeftWallProbeEntry forHost(GameModule host) {
        ObjectLeftWallProbeEntry model = host == null ? null : host.getGameService(ObjectLeftWallProbeEntry.class);
        return model == null ? FLIPPED : model;
    }
}
