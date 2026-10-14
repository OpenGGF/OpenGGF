package com.openggf.tests;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.LevelLoadMode;
import com.openggf.game.session.SessionManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Level/loc_60DE's Lag_frame_count+$58 clear includes RNG_seed, before native sprite dispatch. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kFreshLevelRngState {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    @ParameterizedTest @CsvSource({"1,0", "13,1"})
    void actualFreshBoundaryClearsRngAndTheRegisteredWorldRestoresIt(int zone,int act) throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(4,0).build();
        var level=GameServices.level();var rng=GameServices.rng();
        rng.setSeed(0x6E37EB8BL);
        level.loadZoneAndActAtFreshTitleCardBoundary(zone,act);
        assertSame(rng,GameServices.rng(),"native clear mutates the retained global owner");
        if(zone==0x0D) {
            assertFalse(level.isTitleCardRequested(), "native D01 skips the ordinary title owner");
            assertFalse(level.hasPendingFreshLevelTransitionBoundary());
        } else {
            assertTrue(level.hasPendingFreshLevelTransitionBoundary());
        }
        assertEquals(0,rng.getSeed(),"loc_60DE clears RNG_seed before deferred assembly/Process_Sprites");
        var registry=fixture.gameplayMode().getRewindRegistry();
        var before=registry.capture();
        rng.nextRaw();assertNotEquals(0,rng.getSeed());
        registry.restore(before);
        assertEquals(0,rng.getSeed(),"restoring the registered complete world restores native RNG");
        long expected;
        for(int cycle=0;cycle<2;cycle++) {
            if(cycle!=0)registry.restore(before);
            long first=Integer.toUnsignedLong(rng.nextRaw());
            expected=rng.getSeed();
            // Random_Number returns folded D0 low=$8010; the stored long
            // swaps it into the high word, preserving low=$B493.
            assertEquals(0x8010,first&0xFFFF,"native returned word from cleared S3K seed");
            assertEquals(0x8010B493L,expected);
        }
    }

    @Test void previewLoadPreservesTheExistingRandomSequence() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(4,0).build();
        GameServices.rng().setSeed(0x12345678L);
        GameServices.level().loadZoneAndAct(1,0,LevelLoadMode.PREVIEW_CAPTURE);
        assertEquals(0x12345678L,GameServices.rng().getSeed(),"preview does not execute native Level entry");
    }

    @Test void seamlessReloadPreservesTheExistingRandomSequence() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        GameServices.rng().setSeed(0x12345678L);
        GameServices.level().applySeamlessTransition(SeamlessLevelTransitionRequest.builder(
                SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(0,1).build());
        assertEquals(0x12345678L,GameServices.rng().getSeed(),"in-place continuation does not execute loc_60DE");
    }
}
