package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import java.util.Objects;

/** Semantic projection of the actual repeated IPX.RunMMD clear, not a Frame or entry permit.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/CD Initial Program/IPX.asm:1311-1319: [FF2000,FFFB00).
 * MAINVARS, the debug probe and the cloud RAM below FF2000 survive this clear.
 * The caller must own the genuine programme transfer and all other native storage owners. */
final class CdOriginalProgrammeWorkClear {
    private CdOriginalProgrammeWorkClear() { }
    static CdPlayerBody.Environment environment(CdPlayerBody.Environment old) {
        Objects.requireNonNull(old);var g=old.globals();var move=old.movement();
        var gate=move.gates();var mg=move.globals();
        return new CdPlayerBody.Environment(new CdPlayerMovement.Environment(
                new CdPlayerMovement.Physics(0,0,0),new CdPlayerMovement.Bounds(0,0,0,0),
                new CdPlayerMovement.Gates(gate.zoneAct(),gate.timeZone(),gate.miniSonic(),0,0,gate.speedShoes(),0),
                new CdPlayerMovement.Globals(0,0,0,0,0,0,mg.debugAngle(),mg.debugAngleShift(),mg.debugQuadrant(),mg.debugFloorDistance())),
                new CdPlayerBody.Controls(0,0,0,0,0),
                new CdPlayerBody.Globals(g.levelFrames(),0,g.timeAttackMode(),g.debugMode(),g.debugCheat(),0,g.invincible(),g.bossMusic()),old.dynamicSlots());
    }
    static CdPlayerBody.State primary() {
        return com.openggf.game.soniccd.player.CdPlayerFrameRuntime.clearedObjectValues(
                new CdPlayerBody.History(CdAssetBytes.copyOf(new byte[256]),0));
    }
}
