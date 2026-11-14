package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.FixedRuntimeObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.trace.TraceCharacterState;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Time-warp stars: object $03 ({@code ObjPowerup}) with animation 5 to 8 in reserved slots
 * 12 to 15, created by {@code ObjSonic_MakeTimeWarpStars} (Sonic/Main.asm:112-128). The creation
 * frame runs {@code ObjPowerup_Init} and returns without drawing; the star then runs
 * {@code ObjPowerup_TimeStars}: while {@code timeWarp} is set each star follows the player's
 * position record a little further back and animates ({@code Ani_Powerup} 5 to 8), when it
 * clears the star deletes itself ({@code ObjPowerup_TimeStars}, Powerup/Main.asm:88-91;
 * {@code ObjPowerup_ShowStars}, Powerup/Main.asm:95-133). The star copies the player's
 * {@code oFlags}, whose facing and air bits {@code AnimateObject} XORs into the sprite's flip bits
 * (Object Animate.asm:36-41).
 *
 * <p>The art is {@code Art_TimeStars}, ShieldArtIndex entry 2, at tile $544 (LoadShieldArt,
 * Powerup/Main.asm:157-188); {@link CommonObjectArt#TIME_STARS} is that sheet.
 */
public final class CdTimeStars extends CdObject implements FixedRuntimeObjectInstance,
        ObjectVisualFacing.NonDirectional, RewindRecreatable {
    /** {@code cmpi.b #$18,d1}: the trail phase steps 4 bytes up to $14 (Powerup/Main.asm:118-126). */
    private static final int PHASE_WRAP = 0x18;
    /** {@code cmpi.b #6,zone}: Metallic Madness chooses the tile priority by layer (Powerup/Main.asm:96-101). */
    private static final int METALLIC_MADNESS = 6;

    private int animation;
    private boolean initialised;
    private int phase;
    private int spriteFlip;
    private int mapFrame;
    private CdAnimator animator;
    /** The animator's script bytes, kept as scalars so a rewind restore rebuilds it exactly. */
    private int prevAnim = -1;
    private int animFrame;
    private int animTime;
    private int animFlip;

    public CdTimeStars(int animation) {
        super(new ObjectSpawn(0, 0, 0x03, 0, 0, false, 0), "Time stars");
        this.animation = animation;
    }

    public int animation() { return animation; }

    @Override
    public AbstractObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new CdTimeStars(animation);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!initialised) {
            // ObjPowerup_Init: priority 1, oWidth $10, tile $544; oAnim 5 and up selects routine 6, which
            // runs from the next frame (the routine ends in rts, Powerup/Main.asm:21-36).
            initialised = true;
            priority = 1;
            width = 0x10;
            tileWord = CommonObjectArt.POWERUP_TILE;
            return;
        }
        CdTimeTravel tt = CdTimeTravel.of(services().gameModule());
        if (tt == null || !tt.starsActive()) {                              // tst.b timeWarp; jmp DeleteObject
            setDestroyed(true);
            return;
        }
        if (!(playerEntity instanceof AbstractPlayableSprite player)) return;
        int layer = playerLayerForObjects();
        if (layer >= 0) {                                                  // cmpi.b #6,zone ... tst.b layer
            tileWord = layer == 0 ? tileWord | 0x8000 : tileWord & 0x7FFF;
        }
        // .GotDelta: d1 = anim-1 (minus 4 from 5 up); bytes back = d1*24 + 4 + oVar30 (Powerup/Main.asm:104-117).
        int index = animation - 1;
        if (index >= 4) index -= 4;
        int bytesBack = index * 24 + 4 + phase;
        phase += 4;
        if (phase >= PHASE_WRAP) phase = 0;
        int entriesBack = bytesBack / 4;
        short[] xs = player.getXHistory();
        short[] ys = player.getYHistory();
        int pos = player.historyPos();
        // sonicRecordIndex is the NEXT slot to write (ObjSonic_RecordPos stores, then addq.b #4, Sonic/Main.asm:447-456),
        // so the ROM reads entry (index - bytesBack)/4; the shared history's historyPos is the latest entry written, one less.
        int slot = Math.floorMod(pos + 1 - entriesBack, xs.length);
        x = xs[slot] & 0xFFFF;
        y = ys[slot] & 0xFFFF;
        // move.b objPlayerSlot+oFlags,oFlags(a0); AnimateObject (Powerup/Main.asm:131-133).
        int status = TraceCharacterState.statusByteFromSprite(player);
        if (animator == null) {
            animator = animator(e -> e.offset("powerupAnimations"), animation);
            if (animator != null && prevAnim >= 0) {                  // after a rewind restore
                animator.restoreScriptState(new int[] {prevAnim, animFrame, animTime, mapFrame, animFlip});
            }
        }
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
            // Animations 5 to 8 all have speed 0 (Animations.asm: byte_23EDE2, byte_23EDEE, byte_23EE28,
            // byte_23EE62), so every call shows a frame and rewrites the flip bits (Object Animate.asm:25-41).
            spriteFlip = (status ^ animator.flipBits()) & 3;
            int[] state = animator.scriptState();
            prevAnim = state[0];
            animFrame = state[1];
            animTime = state[2];
            animFlip = state[4];
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised || animator == null) return;
        drawFrame(CommonObjectArt.TIME_STARS, mapFrame, (spriteFlip & 1) != 0, (spriteFlip & 2) != 0);
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
