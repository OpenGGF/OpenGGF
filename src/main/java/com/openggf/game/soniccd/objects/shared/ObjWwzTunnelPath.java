package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.soniccd.zones.wwz.WwzPlayerProfile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Wacky Workbench's tunnel path, object $07 ({@code TunnelPathObject}, objects/r6/tunnel_path.asm:3-130; the directory
 * {@code TunnelPaths}, :310 on). The box is $40 wide and $60 high, centred on the object; the records are six bytes
 * (x, y, branch word) and relative to the object; the ride starts at ground speed $800 and a nonzero {@code subtype_2} also hides the player ({@code oPlayerCtrl} bit 6).
 * The player's drawn flip ({@code bclr/bset #6,obj.sprite_flags(a6)} by subtype bit 7) is not modelled: nothing the trace compares reads it.
 * A record whose branch word is negative lets a held jump button jump to the alternative record
 * {@code 6 * (word & $7FFF) + var_3b} (:207-221).
 */
public final class ObjWwzTunnelPath extends TunnelPathBase {
    public ObjWwzTunnelPath(ObjectSpawn spawn) {
        super(spawn);
    }

    @Override protected String pathsColumn() { return "wwzTunnelPaths"; }

    /** {@code move.w #$800,obj.ground_speed(a6)} (objects/r6/tunnel_path.asm:112). */
    @Override protected int captureGroundSpeed() { return 0x800; }

    /** {@code addi.w #$20,d0; cmpi.w #$40,d0} (:88-89). */
    @Override protected int boxX() { return 0x20; }

    /** {@code addi.w #$30,d1; cmpi.w #$60,d1} (:93-94). */
    @Override protected int boxY() { return 0x30; }

    @Override protected int recordSize() { return 6; }

    /** The records are offsets from the object ({@code add.w obj.x(a0),d0}, :151-170). */
    @Override protected int recordOriginX() { return x; }

    @Override protected int recordOriginY() { return y; }

    @Override protected boolean rollsEachFrame() { return true; }

    /** {@code addq.l #4,sp} in Routine6 (:153): the carry returns past {@code CheckObjectDespawn}. */
    @Override protected boolean carryingSkipsDespawn() { return true; }

    @Override
    protected int playerControl(AbstractPlayableSprite player) {
        return WwzPlayerProfile.playerControl(player);
    }

    /** {@code tst.b obj.subtype_2(a0); beq.s; bset #6,obj.var_2c(a6)} (:106-108). */
    @Override
    protected void onCapture(AbstractPlayableSprite player) {
        if (subtype2() != 0) WwzPlayerProfile.setHidden(player, true);
    }

    @Override
    protected void releaseControl(AbstractPlayableSprite player) {
        WwzPlayerProfile.clearControl(player);
    }

    /**
     * TunnelPathMovePlayerAlongPath's tail (:183-221): a negative branch word on the current record with a jump button held
     * ({@code p1_joy_hold & $70}) loads the alternative record {@code 6 * (word & $7FFF) + var_3b}.
     */
    @Override
    protected void afterMove(AbstractPlayableSprite player) {
        int word = artProvider().image().u16(pathData + pathIndex + 4);
        if ((short) word >= 0) return;
        if (!player.isJumpPressed()) return;
        int index = ((word & 0x7FFF) * 6 + pathLimit) & 0xFF;     // add.b d1,d1 / move.b d1,d0 / add.b d0,d0 / add.b d0,d1 / add.b var_3b
        loadNext(player, index);
    }
}
