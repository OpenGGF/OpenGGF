package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.soniccd.zones.qqz.QqzScrollHandler;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Quartz Quadrant's tunnel path, object $07 ({@code TunnelPathObject}, objects/r5/tunnel_path.asm:3-266; the path directory
 * is {@code TunnelPaths}, :267 on). A player in the $20 by $20 box is captured and carried along absolute points at
 * ground speed $A00 ({@code TunnelPathObject_0_Routine2}, :97-130). A subtype with bit 7 set belongs to the conveyors: it runs
 * only when bit 0 of the subtype equals {@code r5_conveyor_reverse} (:35-49); otherwise the frame only tests
 * {@code CheckObjectDespawn}. Records are four bytes ({@code addq.b #4,d1}, :152).
 */
public final class ObjQqzTunnelPath extends TunnelPathBase {
    public ObjQqzTunnelPath(ObjectSpawn spawn) {
        super(spawn);
    }

    @Override protected String pathsColumn() { return "qqzTunnelPaths"; }

    /** {@code move.w #$A00,obj.ground_speed(a6)} (objects/r5/tunnel_path.asm:119). */
    @Override protected int captureGroundSpeed() { return 0xA00; }

    /** {@code addi.w #$10,d0; cmpi.w #$20,d0} on both axes (:99-106). */
    @Override protected int boxX() { return 0x10; }

    @Override protected int boxY() { return 0x10; }

    @Override protected int recordSize() { return 4; }

    /** {@code tst.b obj.var_2c(a6)}: Quartz Quadrant's player keeps only bit 0 (object control) in it. */
    @Override
    protected int playerControl(AbstractPlayableSprite player) {
        return player.isObjectControlled() ? 1 : 0;
    }

    @Override
    protected void releaseControl(AbstractPlayableSprite player) {
        // clr.b obj.var_2c(a6): bit 0 is the whole byte here.
    }

    /**
     * loc_208A44 (:35-49): {@code tst.b obj.subtype(a0); bpl.s run; beq.s run}; then with {@code r5_conveyor_reverse} clear the object
     * runs when subtype bit 0 is clear, with it set when bit 0 is set.
     */
    @Override
    protected boolean runsRoutine() {
        int subtype = getSpawn().subtype() & 0xFF;
        if ((subtype & 0x80) == 0) return true;
        return (subtype & 1) != 0 == conveyorReverse();
    }

    private boolean conveyorReverse() {
        var level = cdLevel();
        var parallax = services().parallaxManager();
        return level != null && parallax != null
                && parallax.getHandler(SonicCdZoneRegistry.zoneIndex(level.payload())) instanceof QqzScrollHandler handler
                && handler.conveyorReverse();
    }
}
