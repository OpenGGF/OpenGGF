package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.bosses.CnzEndBossInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzEndBossHeadless {
    @AfterEach
    void tearDown() {
        SessionManager.clear();
    }

    @Test
    void cameraGateStartsNativeBossAndLoadsRomPalette() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        fixture.camera().setX((short) 0x4660);
        fixture.camera().setY((short) 0x0240);

        CnzEndBossInstance boss = createBoss();
        boss.update(0, fixture.sprite());

        assertTrue(boss.isStartupCompleteForTest());
        assertEquals("ENTRY", boss.getRoutineForTest());
        assertEquals(Sonic3kObjectIds.CNZ_END_BOSS, GameServices.gameState().getCurrentBossId());
        assertEquals(5, boss.getPriorityBucket());
    }

    @Test
    void attackCycleReachesMagneticAttractionPhase() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .build();
        fixture.camera().setX((short) 0x4660);
        fixture.camera().setY((short) 0x0240);
        fixture.sprite().setCentreX((short) 0x4740);

        CnzEndBossInstance boss = createBoss();
        for (int frame = 0; frame < 1400 && !boss.isMagneticFieldActiveForTest(); frame++) {
            boss.update(frame, fixture.sprite());
        }

        assertTrue(boss.isMagneticFieldActiveForTest(),
                "off_6E4E2 must reach loc_6E632's magnetic attack window; routine=" + boss.getRoutineForTest());
        assertEquals(3, boss.getMappingFrameForTest());
    }

    private static CnzEndBossInstance createBoss() {
        CnzEndBossInstance boss = new CnzEndBossInstance(new ObjectSpawn(
                0x4740, 0x0240, Sonic3kObjectIds.CNZ_END_BOSS, 0, 0, false, 0));
        boss.setServices(TestEnvironment.objectServices());
        return boss;
    }
}
