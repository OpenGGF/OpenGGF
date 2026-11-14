package com.openggf.game.soniccd.zones.ttz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdStageImage;
import com.openggf.game.soniccd.objects.ttz.ObjBubble;
import com.openggf.game.soniccd.objects.ttz.ObjSplash;
import com.openggf.game.soniccd.player.CdPlayerWater;
import com.openggf.game.soniccd.player.CdSpecialChunks;
import com.openggf.game.soniccd.player.CdWaterCurrent;
import com.openggf.game.soniccd.player.CdZonePlayerProfile;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Tidal Tempest's part of the common player (scddisasm pin 1ecc5f3a, USA): the zone-2 water
 * block of {@code PlayerCheckWater} (src/objects/player.asm:431-482), {@code CheckWaterCurrents}'
 * per-act records (src/r4/main.asm:776-871; act 3 returns before the table, main.asm:785-789),
 * the player's splash $0B and air bubbles $21, and {@code ObjSonic_SpecialChunks}
 * (Main.asm:2500-2508). {@code PlayerCheckBlock} is a bare {@code rts}
 * (src/common/player_block_check.asm:3-4).
 */
public final class TtzPlayerProfile implements CdZonePlayerProfile {
    /** {@code cmpi.w #$C8,obj.x(a0); bcs} in act 2 (player.asm:441-443). */
    private static final int ACT2_WATER_LEFT = 0xC8;

    private final CdSpecialChunks chunks;
    private final boolean act2;
    private final List<CdWaterCurrent> currents;

    public TtzPlayerProfile(CdStagePayload payload, CdStageImage image) {
        this.chunks = CdSpecialChunks.forStage(payload, image, -1, CdSpecialChunks.SpecialLoopRule.AIRBORNE_LOWER_PATH);
        int act = payload.stage().actNumber();
        this.act2 = act == 2;
        // cmpi.b #2,d0; bcc.w CheckWaterCurrentsDone: acts 1 and 2 only.
        this.currents = act <= 2 ? CdWaterCurrent.forStage(payload, image) : List.of();
    }

    @Override
    public CdSpecialChunks specialChunks() {
        return chunks;
    }

    @Override
    public boolean hasWater() {
        return true;
    }

    @Override
    public boolean runsWaterCheckAt(int centreX) {
        return !act2 || centreX >= ACT2_WATER_LEFT;
    }

    @Override
    public List<CdWaterCurrent> waterCurrents() {
        return currents;
    }

    /** {@code SpawnObject; move.b #$B,obj.id(a1); move.w obj.x(a0),obj.x(a1)}: subtype 0, Y left 0. */
    @Override
    public boolean spawnWaterSplash(AbstractPlayableSprite player) {
        LevelManager levels = player.currentLevelManagerIfAvailable();
        if (levels == null || levels.getObjectManager() == null) return true;
        levels.getObjectManager().addDynamicObject(
                new ObjSplash(new ObjectSpawn(player.getCentreX() & 0xFFFF, 0, 0x0B, 0, 0, false, 0)));
        return true;
    }

    @Override
    public CdPlayerWater.BubbleFactory airBubbles() {
        return (levels, spawn, angle, numberTimer) -> {
            ObjBubble bubble = new ObjBubble(spawn, true, angle, numberTimer);
            levels.getObjectManager().addDynamicObject(bubble);
            return bubble.getSlotIndex() >= 0 && !bubble.isDestroyed();
        };
    }
}
