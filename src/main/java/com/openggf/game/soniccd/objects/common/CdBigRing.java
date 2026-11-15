package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Big ring, object $14 ({@code ObjBigRing}, Level/_Objects/Level End/Main.asm:429-562). With fewer than
 * 50 rings it only runs its despawn check; it deletes itself when all seven time stones are held or
 * in Time Attack. Otherwise (tile $2488, width and Y radius $20) it animates until the player's box,
 * X radius + 16 by Y radius + 32, overlaps its centre: then it sets {@code specialStage}, moves the
 * player 336 pixels right of the camera, stops it, forces Right ({@code ctrlLocked} bit 0,
 * {@code playerCtrlHold} $808), sets {@code scrollLock} and spawns the flash (subtype 1, tile $3EF,
 * Ani_BigRingFlash then delete). Time stones and Time Attack are other lanes' state: none held.
 */
public final class CdBigRing extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** cmpi.w #50,rings (Level End/Main.asm:432). */
    private static final int RING_THRESHOLD = 50;
    /** ObjBigRing_Main: addi.w #336,d0 (Level End/Main.asm:488-497). */
    private static final int OFFSCREEN_X = 336;

    private final boolean flash;
    private int routine;
    private CdAnimator animator;
    private int mapFrame;

    public CdBigRing(ObjectSpawn spawn) {
        super(spawn, "BigRing");
        this.flash = spawn.subtype() != 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (flash) {
            updateFlash();
            return;
        }
        var state = services().levelGamestate();
        if (state == null || state.getRings() < RING_THRESHOLD) return;   // LevelEndDespawn only
        switch (routine) {
            case 0 -> {
                // ObjBigRing_Init (Level End/Main.asm:465-485).
                routine = 2;
                tileWord = CommonObjectArt.BIG_RING_TILE;
                width = 0x20;
                yRadius = 0x20;
                main(player);
            }
            case 2 -> main(player);
            default -> animate("bigRingAnimations");
        }
    }

    /** ObjBigRing_Main (Level End/Main.asm:488-517). */
    private void main(PlayableEntity entity) {
        if (entity instanceof AbstractPlayableSprite player && touches(player)) {
            CdLevelFlags flags = flags();
            flags.specialStage = true;
            routine = 4;
            var camera = services().camera();
            if (camera != null) NativePositionOps.writeXPosPreserveSubpixel(player, camera.getX() + OFFSCREEN_X);
            flags.ctrlLocked = true;
            flags.playerCtrlHold = 0x808;
            player.setXSpeed((short) 0);
            player.setGSpeed((short) 0);
            flags.scrollLock = true;
            var objects = services().objectManager();
            if (objects != null) objects.addDynamicObject(new CdBigRing(new ObjectSpawn(x, y, 0x14, 1, 0, false, 0)));
        }
        animate("bigRingAnimations");
    }

    /** ObjBigRing_CheckTouch (Level End/Main.asm:533-562). */
    private boolean touches(AbstractPlayableSprite player) {
        int w = (byte) player.getXRadius() + 16;
        int dx = (short) (player.getCentreX() - x + w);
        if (dx < 0 || dx >= 2 * w) return false;
        int h = (byte) player.getYRadius() + 32;
        int dy = (short) (player.getCentreY() - y + h);
        return dy >= 0 && dy < 2 * h;
    }

    /** ObjBigRingFlash (Level End/Main.asm:385-424): tile $3EF, its animation, delete after the $FC. */
    private void updateFlash() {
        if (routine == 0) {
            routine = 2;
            tileWord = CommonObjectArt.BIG_RING_FLASH_TILE;
        }
        if (routine == 4) {
            setDestroyed(true);
            return;
        }
        if (animate("bigRingFlashAnimations") == 2) routine = 4;
    }

    private int animate(String column) {
        if (animator == null) animator = animator(e -> e.offset(column), 0);
        if (animator == null) return 0;
        int step = animator.step();
        mapFrame = animator.mapFrame();
        return step;
    }

    int routine() { return routine; }

    @Override protected boolean despawnsByCheckObjDespawn() { return !flash; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // cmpi.b #4,oRoutine; beq .End: a touched ring is not drawn (Level End/Main.asm:429-449).
        if (flash) {
            if (routine == 2) drawFrame(CommonObjectArt.BIG_RING_FLASH, mapFrame, hFlip(), false);
        } else if (routine == 2) {
            drawFrame(CommonObjectArt.BIG_RING, mapFrame, false, false);
        }
    }
}
