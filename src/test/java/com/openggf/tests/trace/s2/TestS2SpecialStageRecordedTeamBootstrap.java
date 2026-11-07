package com.openggf.tests.trace.s2;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.SessionInvocationExtension;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceReplayRomCatalogueExtension;
import com.openggf.trace.SpecialStageTraceData;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.*;

/** The special-stage driver must preserve recorded teams and production patch resolution. */
@RequiresRom(SonicGame.SONIC_2)
@ExtendWith({SessionInvocationExtension.class, TraceReplayRomCatalogueExtension.class})
class TestS2SpecialStageRecordedTeamBootstrap {
    @Test void soloLockOnEntryDoesNotBecomeSonicAndTails() throws Exception {
        boot("kis2", "kis2-full-run-all-emeralds");
        assertEquals("knuckles", GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("", GameServices.configuration().getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        var session = SessionManager.getCurrentWorldSession();
        assertNotSame(session.rootGameModule(), session.resolvedGameModule(),
                "recorded solo Knuckles must activate the real built-in lock-on patch");
    }

    @Test void stockEntryKeepsRecordedSonicAndTails() throws Exception {
        boot("s2", "s2-sonic-tails-complete-emeralds");
        assertEquals("sonic", GameServices.configuration().getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("tails", GameServices.configuration().getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
        var session = SessionManager.getCurrentWorldSession();
        assertSame(session.rootGameModule(), session.resolvedGameModule());
    }

    private static void boot(String game, String run) throws Exception {
        Path dir = Path.of("src/test/resources/traces", game, "runs", run, "ss");
        var context = AbstractS2SpecialStageTraceReplayTest.segmentContext(dir);
        var trace = SpecialStageTraceData.load(dir, context.openingDynamicArtLedger());
        AbstractS2SpecialStageTraceReplayTest.bootHarness(trace, context,
                RomTestUtils.ensureSonic2RomAvailable());
    }
}
