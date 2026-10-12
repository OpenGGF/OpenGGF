package com.openggf.tools;

import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.internal.NativeStagePacingOwners;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real surfaceless rendering controls; a context or audio drain alone is insufficient evidence. */
@Isolated
class TestMutatorShowcaseRendering {
    @TempDir Path temporary;
    private String previousBackend;

    @BeforeEach
    void selectDisplayFreeBackend() {
        previousBackend = System.getProperty(SurfacelessEglContext.PROPERTY);
        System.setProperty(SurfacelessEglContext.PROPERTY, SurfacelessEglContext.SURFACELESS);
        // Probe only backend creation: failures in the actual capture remain test failures.
        SurfacelessEglContext context;
        try {
            context = SurfacelessEglContext.create(64, 32, false);
        } catch (RuntimeException | UnsatisfiedLinkError unavailable) {
            assumeTrue(false, "surfaceless EGL unavailable: " + unavailable.getMessage());
            return;
        }
        context.close();
    }

    @AfterEach
    void restoreBackend() {
        if (previousBackend == null) System.clearProperty(SurfacelessEglContext.PROPERTY);
        else System.setProperty(SurfacelessEglContext.PROPERTY, previousBackend);
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void interactiveCapturePublishesPixelsStateAndOneOfflinePacketPerPresentation() throws Exception {
        MutatorGameplayCaptureTool.requireDisplayFreeContext();
        Path out = temporary.resolve("interactive");
        var report = MutatorGameplayCaptureTool.run(MutatorGameplayCaptureTool.Options.parse(new String[] {
                "--game", "s2", "--rom", RomTestUtils.ensureSonic2RomAvailable().getAbsolutePath(),
                "--zone", "ehz", "--act", "1", "--frames", "12", "--audio", "--interactive-pacing",
                "--no-video", "--out-dir", out.toString() }));
        assertEquals(12, report.framesStepped());
        var rows = Files.readAllLines(out.resolve("observe2.csv"));
        var header = Arrays.asList(rows.getFirst().split(","));
        assertEquals(13, rows.size());
        for (String row : rows.subList(1, rows.size())) {
            String[] fields = row.split(",", -1);
            assertEquals("800", fields[header.indexOf("pcm_frames")]);
            assertEquals("false", fields[header.indexOf("logical_override")]);
            assertEquals("false", fields[header.indexOf("external_owner")]);
        }
        try (var wav = AudioSystem.getAudioInputStream(out.resolve("audio.wav").toFile())) {
            assertEquals(12 * 800, wav.getFrameLength());
            assertEquals(48000, wav.getFormat().getSampleRate());
        }
        assertFalse(Files.exists(out.resolve("audio.pcm")));
        var image = ImageIO.read(out.resolve("frames/00011.png").toFile());
        int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        assertTrue(Arrays.stream(pixels).distinct().count() > 16, "actual native level pixels must be visible");
        assertEquals(320, image.getWidth());
        assertEquals(224, image.getHeight());
    }

    @Test
    @RequiresRom(SonicGame.SONIC_1)
    void s1SpecialStageDrawsItsNativeProviderWhileEntryFadeKeepsTheLevel() throws Exception {
        MutatorGameplayCaptureTool.requireDisplayFreeContext();
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic1RomAvailable().toPath(), 0, 0, settings);
            for (int frame = 0; frame < 120 && GameServices.fade().isActive(); frame++) {
                session.step(null);
                session.render();
            }
            assertFalse(GameServices.fade().isActive());
            // Declared manager-entry setup for the renderer control; natural giant-ring entry is
            // verified separately against the all-eleven film's actual controller input.
            session.loop().enterSpecialStage();
            boolean sawLevelFade = false;
            for (int frame = 0; frame < 500; frame++) {
                session.step(null);
                var stage = session.loop().getActiveSpecialStageProvider();
                if (session.loop().getCurrentGameMode() == GameMode.SPECIAL_STAGE && stage != null) {
                    if (stage.isEntryFadeToWhiteActive()) {
                        sawLevelFade = true;
                        assertArrayEquals(session.render().pixels(), MutatorGameplayCaptureTool.renderShowcase(session).pixels());
                    } else {
                        var nativeStage = MutatorGameplayCaptureTool.renderShowcase(session);
                        // The native PaletteWhiteIn hold and shared fade can still show white
                        // after provider ownership. Observe their completion before checking pixels.
                        var nativeOwner = NativeStagePacingOwners.special(stage);
                        assertNotNull(nativeOwner);
                        if (!nativeOwner.pacingState().interactive() || GameServices.fade().isActive()) continue;
                        assertTrue(Arrays.stream(nativeStage.pixels()).distinct().count() > 16);
                        assertFalse(Arrays.equals(session.render().pixels(), nativeStage.pixels()),
                                "the native S1 stage must differ from the canonical level-plane fallback");
                        assertEquals(0, GameServices.camera().getX());
                        assertEquals(0, GameServices.camera().getY());
                        assertTrue(sawLevelFade, "entry fade must be exercised before stage ownership");
                        return;
                    }
                } else session.render();
            }
            fail("native S1 special-stage owner did not take its frame within the bounded setup");
        }
    }
}
