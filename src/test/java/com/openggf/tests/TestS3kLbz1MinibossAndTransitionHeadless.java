package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.Sonic3kLBZEvents;
import com.openggf.game.sonic3k.objects.LbzInvisibleBarrierInstance;
import com.openggf.game.sonic3k.objects.LbzMinibossBoxInstance;
import com.openggf.game.sonic3k.objects.LbzMinibossInstance;
import com.openggf.game.sonic3k.objects.S3kBossDefeatSignpostFlow;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-parity coverage for the LBZ1 miniboss fight and the LBZ1 -> LBZ2 act
 * transition (Obj_LBZMiniboss, Obj_LBZMinibossBox, LBZ1_ScreenEvent
 * Events_fg_4=$55, LBZ1BGE_DoTransition, Adjust_LBZ2Layout, LBZ2_LayoutMod).
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLbz1MinibossAndTransitionHeadless {
    private static final int ARENA_X = 0x3EC0;
    private static final int ARENA_Y = 0x01B8;

    private Object oldSkipIntros;
    private Object oldMainCharacter;
    private Object oldSidekickCharacters;

    @BeforeEach
    void setUpConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        oldSidekickCharacters = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
    }

    @AfterEach
    void restoreConfig() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                oldMainCharacter != null ? oldMainCharacter : "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                oldSidekickCharacters != null ? oldSidekickCharacters : "");
    }

    @Test
    void minibossHitReactionRisesTwoPixelsPerFrameThenRestoresTracking() {
        HeadlessTestFixture fixture = lbzFixture();
        AbstractPlayableSprite player = fixture.sprite();
        LbzMinibossInstance miniboss = spawnMiniboss();
        miniboss.forceOpenForTest(ARENA_X, ARENA_Y);
        player.setCentreX((short) ARENA_X);
        player.setCentreY((short) (ARENA_Y + 0x38));

        miniboss.onPlayerAttack(player, enemyTouch());
        int yAfterHit = miniboss.getY();

        // ROM loc_72558: subq.w #2,y_pos every frame of the $20 hit window.
        miniboss.update(0, player);
        assertEquals((yAfterHit - 2) & 0xFFFF, miniboss.getY(),
                "Routine $A must raise the boss 2px per flash frame.");
        for (int frame = 1; frame < 0x20; frame++) {
            miniboss.update(frame, player);
        }
        assertEquals((yAfterHit - 2 * 0x20) & 0xFFFF, miniboss.getY(),
                "loc_72840 restores the saved routine only after all $20 flash frames.");
        assertEquals(0, miniboss.getHitReactionTimerForTest());
        assertEquals(0x08, miniboss.getRoutineForTest(),
                "The boss returns to the player-tracking routine after the flash.");
        assertEquals(0x06, miniboss.getCollisionFlags(),
                "collision_flags is restored from $25 when the flash ends.");
    }

    @Test
    void minibossDetachesSecondRingChainAtThreeHitsRemaining() {
        HeadlessTestFixture fixture = lbzFixture();
        AbstractPlayableSprite player = fixture.sprite();
        LbzMinibossInstance miniboss = spawnMiniboss();
        miniboss.forceOpenForTest(ARENA_X, ARENA_Y);
        player.setCentreX((short) ARENA_X);
        player.setCentreY((short) (ARENA_Y + 0x38));

        for (int hit = 0; hit < 3; hit++) {
            miniboss.onPlayerAttack(player, enemyTouch());
            for (int frame = 0; frame <= 0x20; frame++) {
                miniboss.update(frame, player);
            }
        }
        assertEquals(3, miniboss.getCollisionProperty());

        // ROM loc_7285A sets $38 bit 0 at collision_property==3; the bit-2
        // (second ring) chain then unravels one panel per frame via $44 links.
        long hurtArms = countHurtArmRegions(miniboss);
        assertTrue(hurtArms < 12,
                "The second-ring chain must start detaching after the third hit.");
        for (int frame = 0; frame < 8; frame++) {
            miniboss.update(0x40 + frame, player);
        }
        assertEquals(6, countHurtArmRegions(miniboss),
                "Only the first-ring arm chain keeps its $98 touch regions at three hits left.");
    }

    @Test
    void minibossDefeatRunsRomDelayBeforeSignpostFlow() {
        HeadlessTestFixture fixture = lbzFixture();
        AbstractPlayableSprite player = fixture.sprite();
        LbzMinibossInstance miniboss = spawnMiniboss();
        miniboss.forceOpenForTest(ARENA_X, ARENA_Y);
        player.setCentreX((short) ARENA_X);
        player.setCentreY((short) (ARENA_Y + 0x38));

        for (int hit = 0; hit < 6; hit++) {
            miniboss.onPlayerAttack(player, enemyTouch());
            for (int frame = 0; frame <= 0x20 && !miniboss.isDefeatedForTest(); frame++) {
                miniboss.update(frame, player);
            }
        }
        assertTrue(miniboss.isDefeatedForTest(), "Six hits defeat Obj_LBZMiniboss.");
        assertEquals(0, miniboss.getCollisionFlags());

        // ROM: BossDefeated sets $2E=$3F; Wait_NewDelay hands off to loc_72562
        // (song fade + Obj_EndSignControl) only after those frames elapse.
        for (int frame = 0; frame < 0x3F; frame++) {
            miniboss.update(frame, player);
            assertFalse(miniboss.isDefeatFlowSpawnedForTest(),
                    "The end-sign handoff must wait the full $3F Wait_NewDelay frames.");
        }
        miniboss.update(0x3F, player);
        assertTrue(miniboss.isDefeatFlowSpawnedForTest());
        assertNotNull(findActive(S3kBossDefeatSignpostFlow.class),
                "loc_72562 jumps into Obj_EndSignControl after the delay.");
    }

    @Test
    void minibossKnucklesSubtypeZeroTargetsPlayerMinusOffset() {
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        HeadlessTestFixture fixture = lbzFixture();
        AbstractPlayableSprite player = fixture.sprite();
        LbzMinibossInstance miniboss = spawnMiniboss();
        miniboss.forceOpenForTest(ARENA_X, ARENA_Y);
        player.setCentreX((short) ARENA_X);
        player.setCentreY((short) (ARENA_Y + 0x38));

        miniboss.update(0, player);

        // ROM loc_724E2 adds +$20 to the boss x before the player comparison
        // for subtype 0, so the boss settles at player x MINUS $20: standing at
        // the boss's exact x must push it left.
        assertEquals(0xFF00, miniboss.getXVelocityForTest() & 0xFFFF,
                "Knuckles subtype-0 boss must drift to Player_1 x_pos - $20.");
    }

    @Test
    void boxOpenedChunkSwapWritesBossAreaChunk() {
        lbzFixture();
        Sonic3kLBZEvents events = lbzEvents();
        var map = GameServices.level().getCurrentLevel().getMap();
        int before = map.getValue(0, 0x7D, 2) & 0xFF;

        events.applyMinibossBoxOpenedChunkSwap(false);

        assertEquals(0xDA, map.getValue(0, 0x7D, 2) & 0xFF,
                "Events_fg_4=$55 writes chunk $DA at FG ($7D,2) (sonic3k.asm LBZ1_ScreenEvent).");
        assertNotEquals(before, 0xDA,
                "The closed-box chunk must differ from the opened variant for the swap to be visible.");
    }

    @Test
    void collapseStartSpawnsInvisibleBarrierUntilCameraPassesRelease() {
        HeadlessTestFixture fixture = lbzFixture();
        AbstractPlayableSprite player = fixture.sprite();
        Sonic3kLBZEvents events = lbzEvents();
        Camera camera = GameServices.camera();
        camera.setX((short) 0x3B40);

        events.startEndingCollapse();
        LbzInvisibleBarrierInstance barrier = findActive(LbzInvisibleBarrierInstance.class);
        assertNotNull(barrier,
                "LBZ1_EventVScroll allocates Obj_LBZ1InvisibleBarrier when the collapse arms.");
        assertEquals(0x3BC0, barrier.getX());

        barrier.update(0, player);
        assertFalse(barrier.isDestroyed());
        camera.setX((short) 0x3D80);
        barrier.update(1, player);
        assertTrue(barrier.isDestroyed(),
                "Obj_LBZ1InvisibleBarrier deletes itself at Camera_X_pos=$3D80.");
    }

    @Test
    void standaloneBoxStagesRestartFightAndSpawnsMiniboss() {
        HeadlessTestFixture fixture = lbzFixture();
        AbstractPlayableSprite player = fixture.sprite();
        LbzMinibossBoxInstance box = GameServices.level().getObjectManager().createDynamicObject(
                () -> new LbzMinibossBoxInstance(new ObjectSpawn(
                        ARENA_X, 0x0160, Sonic3kObjectIds.LBZ_MINIBOSS_BOX, 0, 0, false, 0)));
        Camera camera = GameServices.camera();
        player.setCentreX((short) 0x3D00);
        player.setCentreY((short) 0x01F0);

        box.update(0, player);
        assertEquals(0x3C00, camera.getMinX() & 0xFFFF,
                "Obj_LBZMinibossBox locks Camera_min_X_pos=$3C00 on init.");
        assertEquals(0x3EA0, camera.getMaxX() & 0xFFFF,
                "Obj_LBZMinibossBox locks Camera_max_X_pos=$3EA0 on init.");
        assertNull(findActive(LbzMinibossInstance.class));

        player.setCentreX((short) (ARENA_X - 0x40));
        box.update(1, player);
        assertEquals(0x3DA0, camera.getMinX() & 0xFFFF,
                "loc_8CDF2 locks Camera_min_X_pos=$3DA0 once the player is within $70.");
        for (int frame = 0; frame <= 0x1F; frame++) {
            box.update(2 + frame, player);
        }
        assertNotNull(findActive(LbzMinibossInstance.class),
                "loc_8CD9C spawns Obj_LBZMiniboss after the $1F activation wait.");
    }

    @Test
    void eventsFg5ReloadsLbz2WithRomWorldOffsetAndAdjustedLayout() {
        HeadlessTestFixture fixture = lbzFixture();
        AbstractPlayableSprite player = fixture.sprite();
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        Camera camera = GameServices.camera();
        player.setCentreX((short) ARENA_X);
        player.setCentreY((short) 0x0160);
        camera.setX((short) 0x3DA0);
        camera.setY((short) 0x0100);
        camera.setMinX((short) 0x3DA0);
        camera.setMaxX((short) 0x3EA0);

        lbzEvents().setEventsFg5(true);
        manager.update();

        assertEquals(1, GameServices.level().getCurrentAct(),
                "LBZ1BGE_DoTransition writes Current_zone_and_act=$0601 before Load_Level.");
        assertEquals((ARENA_X - 0x3A00) & 0xFFFF, player.getCentreX() & 0xFFFF,
                "LBZ1BGE_DoTransition subtracts $3A00 from Player_1 x_pos.");
        assertEquals(0x0160, player.getCentreY() & 0xFFFF,
                "The LBZ transition does not offset y.");
        assertEquals((0x3DA0 - 0x3A00) & 0xFFFF, camera.getMinX() & 0xFFFF,
                "Camera_min_X_pos is shifted by the same delta.");
        assertEquals((0x3EA0 - 0x3A00) & 0xFFFF, camera.getMaxX() & 0xFFFF,
                "Camera_max_X_pos is shifted by the same delta.");

        var map = GameServices.level().getCurrentLevel().getMap();
        assertEquals(0xDB, map.getValue(0, 5, 18) & 0xFF,
                "Adjust_LBZ2Layout writes chunk $DB at FG (5,18) right after Load_Level.");
        assertEquals(0x58, map.getValue(0, 0x8A, 7) & 0xFF,
                "Adjust_LBZ2Layout writes chunk $58 at FG ($8A,7).");
        assertEquals(0x55, map.getValue(0, 0x8A, 8) & 0xFF,
                "Adjust_LBZ2Layout writes chunk $55 at FG ($8A,8).");

        // ROM LBZ2SE_FromTransition: LBZ2_LayoutMod waits for Player_1
        // x_pos >= $60A before opening the entry corridor.
        int[] corridorBefore = readCorridorRow(map);
        manager.update();
        assertTrue(Arrays.equals(corridorBefore, readCorridorRow(map)),
                "The entry corridor must stay closed until the player reaches x=$60A.");
        player.setCentreX((short) 0x060A);
        manager.update();
        int[] staging = new int[LbzCorridor.SIZE];
        for (int c = 0; c < LbzCorridor.SIZE; c++) {
            staging[c] = map.getValue(0, 0x94 + c, 0) & 0xFF;
        }
        assertTrue(Arrays.equals(staging, readCorridorRow(map)),
                "LBZ2_LayoutMod copies the staging columns $94.. into columns 6.. at the gate.");
    }

    private static final class LbzCorridor {
        private static final int SIZE = 6;
    }

    private int[] readCorridorRow(com.openggf.level.Map map) {
        int[] row = new int[LbzCorridor.SIZE];
        for (int c = 0; c < LbzCorridor.SIZE; c++) {
            row[c] = map.getValue(0, 6 + c, 0) & 0xFF;
        }
        return row;
    }

    private HeadlessTestFixture lbzFixture() {
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LBZ, 0)
                .build();
    }

    private Sonic3kLBZEvents lbzEvents() {
        return ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getLbzEvents();
    }

    private LbzMinibossInstance spawnMiniboss() {
        return GameServices.level().getObjectManager().createDynamicObject(
                () -> new LbzMinibossInstance(new ObjectSpawn(
                        ARENA_X, ARENA_Y, Sonic3kObjectIds.LBZ_MINIBOSS, 0, 0, false, 0)));
    }

    private TouchResponseResult enemyTouch() {
        return new TouchResponseResult(0x06, 0x20, 0x20, TouchCategory.ENEMY);
    }

    private long countHurtArmRegions(LbzMinibossInstance miniboss) {
        TouchResponseProvider.TouchRegion[] regions = miniboss.getMultiTouchRegions();
        if (regions == null) {
            return 0;
        }
        return Arrays.stream(regions)
                .filter(region -> region.collisionFlags() == 0x98)
                .count();
    }

    private <T> T findActive(Class<T> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElse(null);
    }
}
