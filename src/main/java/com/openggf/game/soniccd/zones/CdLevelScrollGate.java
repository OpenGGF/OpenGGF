package com.openggf.game.soniccd.zones;

import com.openggf.game.GameServices;
import com.openggf.game.soniccd.level.CdLevel;

/**
 * Whether this frame's zone scroll routine runs, for every zone's scroll handler. Two native gates
 * (scddisasm pin {@code 1ecc5f3a}):
 * <ul>
 *   <li>{@code Level_MainLoop} skips {@code LevelScroll} while the player is dead ({@code cmpi.b
 *       #6,objPlayerSlot+oRoutine; bcs .DoScroll}, Level/Main.asm:409-418; r4/main.asm:388-399;
 *       common/main.asm:422-441). The engine's dead flag is that routine.</li>
 *   <li>Every zone's scroll routine returns at once while {@code scrollLock} is set ({@code tst.b
 *       scrollLock; beq .DoScroll; rts}: Palmtree Panic/Scroll (Act 1 Present).asm:177-180, Collision
 *       Chaos/Scroll (Act 1 Present).asm:244-246, r4/scroll_1a.asm:165-169, r5/scroll_1a.asm:210-214,
 *       r6/scroll_1a.asm:199-203, r7/scroll_1a.asm:137-141, r8/scroll_1a.asm:157-161; each stage's copy has the
 *       same test). The title card (from its slide-out to the end of its PLC wait), the big ring and a time warp
 *       set it; the stage's {@code CdLevelFlags} holds it.</li>
 * </ul>
 * With either gate shut the layers, deformation and hscroll buffer stand still, so a handler keeps
 * publishing what it last built.
 */
public final class CdLevelScrollGate {
    private CdLevelScrollGate() { }

    /** True when the zone's scroll routine runs this frame. */
    public static boolean runs() {
        var levels = GameServices.levelOrNull();
        if (levels != null && levels.getCurrentLevel() instanceof CdLevel level && level.flags().scrollLock) {
            return false;
        }
        var camera = GameServices.cameraOrNull();
        var player = camera == null ? null : camera.getFocusedSprite();
        return player == null || !player.getDead();
    }
}
