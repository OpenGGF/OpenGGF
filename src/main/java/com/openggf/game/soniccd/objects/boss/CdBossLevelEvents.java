package com.openggf.game.soniccd.objects.boss;

import com.openggf.camera.Camera;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * The act 3 branch of {@code RunLevelEvents} (RunLevelEvents, Level/_Events.asm:7-12): on a boss stage the zone's
 * {@link CdBossFragment#levelEvents} runs where the USA {@code LevelScroll} calls RunLevelEvents, before
 * {@code ScrollCamX}/{@code ScrollCamY} (Palmtree Panic/Scroll (Act 2 Past).asm:265-285, REGION=USA), so the
 * camera step sees the bounds it writes. {@code LevelScroll} returns at once while {@code scrollLock} is set,
 * and the level loop skips it while the player is dead (Level_MainLoop, Level/Main.asm:333-421); events do not run then.
 *
 * <p>Camera bounds are the shared camera's: {@code leftBound} is the minimum camera X, {@code rightBound}
 * the maximum, {@code bottomBound} the maximum camera Y and {@code destBottomBound} its target.
 */
public final class CdBossLevelEvents {
    /** What a zone's act 3 events read and write. */
    public record Context(CdLevel level, Camera camera, AbstractPlayableSprite player, ObjectManager objects) { }

    private CdBossLevelEvents() { }

    /** Runs the loaded boss stage's events; a no-op on every other stage. */
    public static void run(CdLevel level, Camera camera, AbstractPlayableSprite player, ObjectManager objects) {
        if (level == null || camera == null || player == null || !CdBossObjects.bossStage(level.payload())) return;
        if (level.flags().scrollLock || player.getDead()) return;
        CdBossObjects.fragment(level.payload().stage().zone()).levelEvents(new Context(level, camera, player, objects));
    }

    /**
     * {@code CheckBossStart} and {@code SetBossBounds} (Level/_Events.asm:445-481): once the player's X reaches {@code startX}
     * ({@code cmp.w objPlayerSlot+oX,d0; ble.s SetBossBounds}), {@code SetBossBounds} sets
     * {@code destBottomBound} to {@code bottom}, snaps {@code bottomBound} to it when within 4 pixels, and
     * moves the left boundary to the player's X minus 160 unless the left boundary is already past it, capped at
     * the right boundary. Returns d0: true once the bounds are set.
     */
    public static boolean checkBossStart(Context context, int startX, int bottom) {
        AbstractPlayableSprite player = context.player();
        Camera camera = context.camera();
        int playerX = (short) player.getCentreX();
        if ((short) startX > playerX) return false;                      // moveq #0,d0; rts
        camera.setMaxYTarget((short) bottom);                             // move.w d1,destBottomBound.w
        int distance = (short) (bottom - camera.getMaxY());
        if (distance < 0) distance = -distance;                           // bpl.s .CheckNearBound; neg.w d1
        if (distance < 4) camera.setMaxYCurrent((short) bottom);          // cmpi.w #4,d1; bge.s .NoYLock
        int left = (short) (playerX - 320 / 2);                           // subi.w #320/2,d0
        if (left < camera.getMinX()) return true;                         // cmp.w leftBound,d0; blt.s .BoundsSet
        if (left > camera.getMaxX()) left = camera.getMaxX();             // cmp.w rightBound,d0; ble.s .NoBoundSet
        camera.setMinX((short) left);                                     // leftBound and destLeftBound
        return true;
    }
}
