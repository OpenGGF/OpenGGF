package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The Sonic hole, object $31 ({@code ObjSonicHole}, Palmtree Panic/Objects/Tunnel Path/Main.asm:11-99; the file
 * is the spin tunnel's but this routine is the Palmtree Panic act 1 hole the player leaves in a wall). It is
 * invisible until the player, with any {@code oPlayerCtrl} bit set (a spin tunnel or another object has it),
 * is inside the 64 by 64 box around it; then it marks itself ({@code FM_A3}) and shows its sprite from then
 * on. The mark is bit 6 of the layout entry's saved flags for the current time zone, so a hole already marked
 * in this time zone shows from the start (ObjSonicHole_Init, Tunnel Path/Main.asm:24-46) and an unmarked one clears the bit it set
 * in the init to test it (ObjSonicHole_Init .ClearDisplay, Tunnel Path/Main.asm:44-46). Priority 6, $10 by $10, tile $3BB in the past and $3A0 otherwise.
 *
 * <p>{@code CheckObjDespawn} runs every frame (ObjSonicHole, Tunnel Path/Main.asm:11-17). The saved bit is modelled with the spawn's
 * remembered state, which belongs to this stage load; carrying bit 6 across a time warp into another period of
 * the same act is the time travel owner's (see the lane report).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSonicHole extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** The box test: {@code addi.w #$20,d0; bmi.s .End; cmpi.w #$40,d0; bcc.s .End} (ObjSonicHole_Main, Tunnel Path/Main.asm:50-77). */
    private static final int HALF_BOX = 0x20;
    private static final int PAST_TILE = 0x3BB, TILE = 0x3A0;
    private static final int INIT = 0, MAIN = 2, DISPLAY = 4;

    private int routine = INIT;

    public ObjSonicHole(ObjectSpawn spawn) {
        super(spawn, "Sonic hole");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override public boolean shouldStayActiveWhenRemembered() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (PpzStage.standDown(services(), this)) return;
        switch (routine) {
            case INIT -> init();
            case MAIN -> main(player);
            default -> { }
        }
    }

    /** ObjSonicHole_Init (Tunnel Path/Main.asm:24-46): it falls into the main routine when the bit was clear. */
    private void init() {
        routine = MAIN;
        tileWord = timeZone() == 0 ? PAST_TILE : TILE;           // tst.b timeZone; bne.s .NotPast
        width = 0x10;
        yRadius = 0x10;
        priority = 6;
        var objects = services().objectManager();
        if (objects != null && objects.isRemembered(spawn)) {    // bset #6: the previous state was set
            routine = DISPLAY;
            return;
        }
        main(services().camera().getFocusedSprite());            // .ClearDisplay: bclr #6; falls into _Main
    }

    /** ObjSonicHole_Main (Tunnel Path/Main.asm:50-77). */
    private void main(PlayableEntity entity) {
        if (!(entity instanceof AbstractPlayableSprite player) || !playerControlled(player)) return;
        int dx = (player.getCentreX() - x + HALF_BOX) & 0xFFFF;
        if ((short) dx < 0 || dx >= 2 * HALF_BOX) return;
        int dy = (player.getCentreY() - y + HALF_BOX) & 0xFFFF;
        if ((short) dy < 0 || dy >= 2 * HALF_BOX) return;
        ObjectLifetimeOps.markSpawnRemembered(services().objectManager(), spawn);   // ObjSonicHole_SetDisplay
        services().playSfx(PpzSounds.FM_A3);
        routine = DISPLAY;
    }

    /** {@code tst.b oPlayerCtrl(a6)}: any control bit, bit 0 (an object has the player) or bit 1 (a 3D ramp). */
    private static boolean playerControlled(AbstractPlayableSprite player) {
        return player.isObjectControlled() || com.openggf.game.soniccd.player.CdPlayerStage.onPseudo3dRamp(player);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != DISPLAY) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_HOLE, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, 0, false, false);
    }

    public boolean isDisplayed() { return routine == DISPLAY; }
}
