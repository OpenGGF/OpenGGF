package com.openggf.game.sonic2.titlescreen;

import com.openggf.Engine;
import com.openggf.GameLoop;
import com.openggf.ModSubsystem;
import com.openggf.audio.AudioBackend;
import com.openggf.audio.AudioManager;
import com.openggf.audio.HeadlessSmpsAudioBackend;
import com.openggf.audio.LWJGLAudioBackend;
import com.openggf.audio.NullAudioBackend;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.synth.ChipWriteObserver;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.control.RecordedInputOwnership;
import com.openggf.data.Rom;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.graphics.GraphicsManager;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockConstruction;

/**
 * Production title iterations and real ROM-backed presentation admission. The
 * existing 106-update visual sequence and 96-update text hold remain approximations;
 * these checks establish their input ownership, not a fitted coldboot offset.
 */
@RequiresRom(SonicGame.SONIC_2)
@Isolated
class TestSonic2IntroInputPhases {
    private EngineContext services;
    private GraphicsManager graphics;
    private AudioManager audio;
    private Rom rom;
    private Engine engine;
    private GameLoop loop;
    private InputHandler input;
    private TitleScreenManager manager;
    private final List<Integer> dacBytes = new ArrayList<>();

    @AfterEach
    void releaseOwnedRuntime() throws Exception {
        try {
            if (engine != null) engine.closeConfiguredHeadlessSession();
        } finally {
            try {
                SessionManager.clear();
                if (audio != null) {
                    audio.destroy();
                    audio.resetState();
                    audio.setBackend(new NullAudioBackend());
                }
                if (services != null) services.roms().setRom(null);
                if (rom != null) rom.close();
                if (graphics != null) graphics.cleanup();
            } finally {
                Engine.clearGlobalInstance();
                ModSubsystem.clearProcess();
                TestEnvironment.resetAll();
            }
        }
    }

    @Test
    void chantGetsItsExtraVintBeforeEitherPlayersStartMayEndTheScreen() throws Exception {
        boot(true);
        advanceToChantSubmission(false);
        int before = audio.commandTimeline().entryCount();
        dacBytes.clear();

        // ObjB0's completing iteration submits FA. It must not also cancel it
        // when Start is pressed (SegaScreen, s2.asm:4177-4191).
        stepOnly(players(start(true), neutral()));
        assertNoStopSince(before);
        assertEquals("EXTRA_VINT", field("segaLogoPhase").toString());
        assertEquals(106, field("segaLogoTimer"));
        assertTrue(dacBytes.isEmpty(), "the queued request has not reached presentation yet");
        present(false);
        assertTrue(dacBytes.size() >= 5, "real FORWARD admission must start the ROM chant");
        byte[] nativePrefix = rom.readBytes(0x0F1E8C, 5); // Snd_Sega, native Z80 HL=$9E8C.
        for (int i = 0; i < nativePrefix.length; i++) {
            assertEquals(Byte.toUnsignedInt(nativePrefix[i]), dacBytes.get(i).intValue());
        }

        // The next admitted title iteration consumes the extra Vint_SEGA.
        tick(players(neutral(), start(true)), false);
        assertEquals("END_WAIT", field("segaLogoPhase").toString());
        assertEquals(180, field("segaEndWaitTimer"));
        assertNoStopSince(before);

        // Neither pad's face buttons nor a held Start edge can own the exit.
        tick(players(actions(), neutral()), false);
        tick(players(neutral(), actions()), false);
        tick(players(start(false), start(false)), false);
        assertEquals("ACTIVE", field("segaLogoFadePhase").toString());
        assertEquals(106, field("segaLogoTimer"), "the visual clock stopped at its handoff");
        assertNoStopSince(before);
        tick(players(neutral(), neutral()), false);
        tick(players(neutral(), start(true)), false);
        assertEquals("FADING_OUT", field("segaLogoFadePhase").toString());
        assertEquals(1, stopsSince(before));

        // Reach text naturally, then press both pads throughout all three text
        // owners. No input branch may skip their existing load/fade sequence.
        for (int iterations = 0; manager.getState() == TitleScreenProvider.State.SEGA_LOGO; iterations++) {
            assertTrue(iterations < 23, "the native exit fade did not reach the text owner");
            tick(LogicalInputSnapshot.neutral(), false);
        }
        assertEquals(TitleScreenProvider.State.INTRO_TEXT_FADE_IN, manager.getState());
        for (int elapsed = 0; elapsed < 22 + 96 + 22; elapsed++) {
            assertNotEquals(TitleScreenProvider.State.FADE_IN, manager.getState());
            assertFalse(((boolean[]) field("sparklePlayedAt"))[0]);
            tick(players(allButtons(), allButtons()), false);
        }
        assertEquals(TitleScreenProvider.State.FADE_IN, manager.getState());
        assertTrue(((boolean[]) field("sparklePlayedAt"))[0], "natural title init owns the twinkle");

        // A new screen lifetime must not inherit the completed handoff. Exercise
        // P1 independently after P2 owned the previous screen's exit.
        manager.reset();
        manager.initialize();
        assertEquals("VISUAL", field("segaLogoPhase").toString());
        assertEquals(0, field("segaEndWaitTimer"));
        assertEquals("ACTIVE", field("segaLogoFadePhase").toString());
        advanceToChantSubmission(false);
        tick(players(start(true), neutral()), false);
        tick(players(start(false), neutral()), false);
        assertEquals("ACTIVE", field("segaLogoFadePhase").toString());
        tick(LogicalInputSnapshot.neutral(), false);
        tick(players(start(true), neutral()), false);
        assertEquals("FADING_OUT", field("segaLogoFadePhase").toString());
    }

    @Test
    void mutedSilentPresentationDoesNotBecomeTheNativeWaitAuthority() throws Exception {
        boot(false);
        advanceToChantSubmission(true);
        tick(players(start(true), neutral()), true);
        assertEquals("EXTRA_VINT", field("segaLogoPhase").toString());
        tick(players(neutral(), start(true)), true);
        assertEquals("END_WAIT", field("segaLogoPhase").toString());
        assertEquals(180, field("segaEndWaitTimer"));
        tick(players(start(false), start(false)), true);
        assertEquals(179, field("segaEndWaitTimer"));
        assertEquals("ACTIVE", field("segaLogoFadePhase").toString());
        tick(players(start(true), neutral()), true);
        assertEquals("FADING_OUT", field("segaLogoFadePhase").toString());
    }

    @Test
    void unavailableChantDoesNotParkTheNativeScreenLoop() throws Exception {
        boot(true);
        audio.setAudioProfile(new Sonic2AudioProfile() {
            @Override public byte[] loadSegaPcm(Object source) { return null; }
        });
        advanceToChantSubmission(false);
        tick(players(start(true), neutral()), false);
        assertEquals("EXTRA_VINT", field("segaLogoPhase").toString());
        tick(LogicalInputSnapshot.neutral(), false);
        assertEquals("END_WAIT", field("segaLogoPhase").toString());
        tick(players(neutral(), start(true)), false);
        assertEquals("FADING_OUT", field("segaLogoFadePhase").toString());
    }

    private void boot(boolean synthesisEnabled) {
        TestEnvironment.resetAll();
        ModSubsystem.clearProcess();
        EngineContext bootstrap = EngineServices.current();
        graphics = new GraphicsManager();
        graphics.initHeadless();
        audio = bootstrap.audio();
        audio.destroy();
        audio.resetState();
        audio.setBackend(new NullAudioBackend());
        services = new EngineContext(bootstrap.configuration(), graphics, audio,
                bootstrap.roms(), bootstrap.profiler(), bootstrap.debugOverlay(),
                bootstrap.playbackDebug(), bootstrap.romDetection(), bootstrap.crossGameFeatures());
        EngineServices.configure(services);
        var config = services.configuration();
        config.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, true);
        config.setConfigValue(SonicConfiguration.SHOW_LEGAL_DISCLAIMER_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.TITLE_SCREEN_ON_STARTUP, true);
        config.setConfigValue(SonicConfiguration.LEVEL_SELECT_ON_STARTUP, false);
        config.setConfigValue(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        config.setConfigValue(SonicConfiguration.DEFAULT_ROM, "s2");
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setConfigValue(SonicConfiguration.AUDIO_ENABLED, synthesisEnabled);
        rom = new Rom();
        assertTrue(rom.open(RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath()));
        services.roms().setRom(rom);
        input = new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.neutral());
        AudioBackend backend = synthesisEnabled
                ? new HeadlessSmpsAudioBackend(config, services.profiler()) : new NullAudioBackend();
        engine = new Engine(services);
        try (var devices = mockConstruction(LWJGLAudioBackend.class,
                (device, context) -> fail("headless title startup constructed a device backend"))) {
            engine.initializeConfiguredHeadlessSession(input, backend);
        }
        loop = engine.getGameLoop();
        assertEquals(GameMode.TITLE_SCREEN, loop.getCurrentGameMode());
        manager = assertInstanceOf(TitleScreenManager.class, GameServices.module().getTitleScreenProvider());
        audio.setChipWriteObserver(new ChipWriteObserver() {
            @Override public void onYm2612Write(int port, int register, int value) {
                if (port == 0 && register == 0x2A) dacBytes.add(value);
            }
            @Override public void onPsgWrite(int value) { }
        });
    }

    private void advanceToChantSubmission(boolean silent) throws Exception {
        for (int iterations = 0; (int) field("segaLogoTimer") < 105; iterations++) {
            assertTrue(iterations < 200, "production title admission did not advance the visual owner");
            tick(LogicalInputSnapshot.neutral(), silent);
        }
        assertEquals("VISUAL", field("segaLogoPhase").toString());
    }

    private void stepOnly(LogicalInputSnapshot snapshot) {
        try (var recordedInput = RecordedInputOwnership.acquire(input)) {
            input.setLogicalOverride(snapshot);
            loop.step();
        }
    }

    private void present(boolean silent) {
        if (silent) {
            audio.presentFrame(PresentationMode.SILENT);
            audio.update();
        } else {
            Engine.presentOuterAudioFrame(loop, false, false);
        }
    }

    private void tick(LogicalInputSnapshot snapshot, boolean silent) {
        stepOnly(snapshot);
        present(silent);
    }

    private void assertNoStopSince(int firstEntry) {
        assertEquals(0, stopsSince(firstEntry), "chant submission/extra Vint must not queue its cancellation");
    }

    private long stopsSince(int firstEntry) {
        return audio.commandTimeline().entries().stream().skip(firstEntry)
                .filter(entry -> entry.command() instanceof AudioCommand.StopRawPcm).count();
    }

    private Object field(String name) throws Exception {
        Field field = TitleScreenManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(manager);
    }

    private static LogicalInputSnapshot players(PlayerInputState p1, PlayerInputState p2) {
        return LogicalInputSnapshot.ofPlayers(p1, p2);
    }
    private static PlayerInputState neutral() { return PlayerInputState.neutral(); }
    private static PlayerInputState start(boolean pressed) { return PlayerInputState.of(0, 0, 0, 0, true, pressed); }
    private static PlayerInputState actions() {
        int abc = InputActionMasks.ACTION_A | InputActionMasks.ACTION_B | InputActionMasks.ACTION_C;
        return PlayerInputState.of(0, 0, abc, abc, false, false);
    }
    private static PlayerInputState allButtons() {
        var abc = actions();
        return PlayerInputState.of(0, 0, abc.actionHeldMask(), abc.actionPressedMask(), true, true);
    }
}
