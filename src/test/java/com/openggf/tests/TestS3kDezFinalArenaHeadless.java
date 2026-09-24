package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.S3kDezFinalArenaControllerInstance;
import com.openggf.game.sonic3k.objects.S3kDezFinalBossInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Production-load coverage for the dynamically populated {@code $1700} Death Egg arena. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDezFinalArenaHeadless {

    @Test
    void directLoadInstallsTheArenaAndRomBackedPresentation() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0)
                .build();
        fixture.stepIdleFrames(2);

        assertTrue((fixture.sprite().getCentreX() & 0xFFFF) >= 0x30
                        && (fixture.sprite().getCentreX() & 0xFFFF) <= 0x360,
                "the controlled ROM run-in must stay inside its scripted interval");
        assertEquals(0x6C0, S3kRuntimeStates.currentDez(GameServices.zoneRuntimeRegistry())
                .orElseThrow().act3BackgroundWord(0x00));
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(S3kDezFinalArenaControllerInstance.class::isInstance));
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(S3kDezFinalBossInstance.class::isInstance));

        var art = GameServices.module().getObjectArtProvider();
        for (String key : new String[]{Sonic3kObjectArtKeys.DEZ3_BLOCKS,
                Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MISC,
                Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MASTER_EMERALD,
                Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_DEBRIS}) {
            assertNotNull(art.getRenderer(key), "missing renderer " + key);
            assertNotNull(art.getSheet(key), "missing ROM art " + key);
        }

        fixture.stepIdleFrames(120);
        assertTrue(!fixture.sprite().getDead(), "the generated arena floor must prevent the baseline pit death");
    }
}
