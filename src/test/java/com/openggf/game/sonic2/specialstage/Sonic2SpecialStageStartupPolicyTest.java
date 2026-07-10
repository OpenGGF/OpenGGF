package com.openggf.game.sonic2.specialstage;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageStartupPolicy;
import com.openggf.game.sonic2.Sonic2SpecialStageProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class Sonic2SpecialStageStartupPolicyTest {

    private Rom rom;

    @BeforeEach
    void setUp() throws Exception {
        Path romPath = Path.of("s2.gen");
        assumeTrue(Files.isRegularFile(romPath), "s2.gen ROM required for startup policy tests");

        GraphicsManager.getInstance().resetState();
        GraphicsManager.getInstance().initHeadless();
        rom = new Rom();
        assertTrue(rom.open(romPath.toAbsolutePath().toString()));
        TestEnvironment.configureRomFixture(rom);
        GraphicsManager.getInstance().initHeadless();
        GameServices.configuration().setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
        if (rom != null) {
            rom.close();
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void defaultInitializationFastForwardsToRevealRegardlessOfLag(double lagFactor) throws Exception {
        Sonic2SpecialStageProvider provider = new Sonic2SpecialStageProvider();
        provider.setLagCompensation(lagFactor);

        provider.initializeStage(0);

        assertEquals(Sonic2SpecialStageIntro.Phase.FADE_FROM_WHITE,
                provider.getManager().getIntro().getCurrentPhase());
        assertTrue(provider.isEntryPresentationReady());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0})
    void accurateInitializationPreservesPreRollRegardlessOfLag(double lagFactor) throws Exception {
        Sonic2SpecialStageProvider provider = new Sonic2SpecialStageProvider();
        provider.setLagCompensation(lagFactor);

        provider.initializeStage(0, SpecialStageStartupPolicy.TRACE_ACCURATE);

        assertEquals(Sonic2SpecialStageIntro.Phase.PRE_ROLL,
                provider.getManager().getIntro().getCurrentPhase());
        assertFalse(provider.isEntryPresentationReady());
    }

    @Test
    void accurateInitializationCannotLeakIntoLaterDefaultInitialization() throws Exception {
        Sonic2SpecialStageProvider provider = new Sonic2SpecialStageProvider();
        provider.initializeStage(0, SpecialStageStartupPolicy.TRACE_ACCURATE);
        provider.reset();

        provider.initializeStage(0);

        assertEquals(Sonic2SpecialStageIntro.Phase.FADE_FROM_WHITE,
                provider.getManager().getIntro().getCurrentPhase());
    }

    @Test
    void nullPolicyIsRejected() {
        Sonic2SpecialStageProvider provider = new Sonic2SpecialStageProvider();
        assertThrows(NullPointerException.class, () -> provider.initializeStage(0, null));
    }

    @Test
    void zeroUpdateBudgetReportsCurrentStartupPhase() throws Exception {
        Sonic2SpecialStageProvider provider = new Sonic2SpecialStageProvider();
        provider.initializeStage(0, SpecialStageStartupPolicy.TRACE_ACCURATE);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.getManager().advanceToEntryPresentation(0));

        assertTrue(error.getMessage().contains("PRE_ROLL"));
    }

    @Test
    void fastForwardAfterRevealBoundaryIsRejected() throws Exception {
        Sonic2SpecialStageProvider provider = new Sonic2SpecialStageProvider();
        provider.initializeStage(0);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider.getManager().advanceToEntryPresentation());

        assertTrue(error.getMessage().contains("FADE_FROM_WHITE"));
    }
}
