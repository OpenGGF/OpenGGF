package com.openggf.level;

import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.graphics.PaletteView;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.TestAbortedException;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;
import static org.mockito.Mockito.*;

/** Completed production team setup, independently checked against supplied ROM colour words. */
@RequiresRom(SonicGame.SONIC_2)
class TestDonatedTeamPalettes {
    @ParameterizedTest
    @ValueSource(strings = {"s1", "s3k"})
    void missingSelectedDonorAbortsBeforeAnyHostBoot(String donor) {
        try (var discovery = mockStatic(RomTestUtils.class)) {
            TestAbortedException unavailable = assertThrows(TestAbortedException.class,
                    () -> completedTeamKeepsEachCharactersPaletteAcrossRefreshAndProviderReinit(
                            donor, "sonic", "sonic"));
            assertTrue(unavailable.getMessage().contains(donor));
            discovery.verify(donor.equals("s1") ? RomTestUtils::ensureSonic1RomAvailable
                    : RomTestUtils::ensureSonic3kRomAvailable);
            discovery.verifyNoMoreInteractions();
        }
    }

    @ParameterizedTest(name = "{0}: {1} + {2}")
    @CsvSource({"s3k,sonic,knuckles", "s3k,knuckles,sonic", "s3k,sonic,tails",
            "s3k,sonic,sonic", "s1,sonic,sonic"})
    void completedTeamKeepsEachCharactersPaletteAcrossRefreshAndProviderReinit(
            String donor, String main, String sidekick) throws Exception {
        var donorFile = donor.equals("s1") ? RomTestUtils.ensureSonic1RomAvailable()
                : RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(donorFile != null, () -> "Selected donor ROM unavailable: " + donor);
        Path donorRom = donorFile.toPath();
        byte[] rom = Files.readAllBytes(donorRom);
        var settings = new GameplayCaptureSession.Settings(320, main, sidekick,
                donor, donorRom, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, 0, settings);
            CrossGameFeatureProvider provider = GameServices.crossGameFeatures();
            RenderContext shared = provider.getDonorRenderContext();
            assertTrue(CrossGameFeatureProvider.isActive());
            assertTeam(rom, donor, main, sidekick, shared);

            // Standalone requests deliberately select the requested character. Team
            // refresh must then publish its own main binding without replacing it.
            String requested = main.equals("knuckles") ? "sonic" : "knuckles";
            if (donor.equals("s1")) requested = "sonic";
            assertNotNull(provider.loadPlayerSpriteArt(requested));
            assertPalette(rom, normalAddress(rom, donor, requested), shared.getPalette(0));
            GameServices.level().refreshPlayableSpriteArt();
            assertTeam(rom, donor, main, sidekick, shared);

            provider.resetState();
            provider.initialize(donor);
            assertSame(shared, provider.getDonorRenderContext());
            GameServices.level().refreshPlayableSpriteArt();
            assertTeam(rom, donor, main, sidekick, shared);
        }
    }

    private static void assertTeam(byte[] rom, String donor, String main, String sidekick,
                                   RenderContext shared) {
        var sprites = GameServices.sprites();
        assertEquals(1, sprites.getSidekicks().size());
        AbstractPlayableSprite player = sprites.getMainPlayable();
        AbstractPlayableSprite follower = sprites.getSidekicks().getFirst();
        RenderContext mainContext = player.getSpriteRenderer().getRenderContext();
        RenderContext sideContext = follower.getSpriteRenderer().getRenderContext();
        assertSame(shared, mainContext, "the main renderer retains the provider context");
        assertNotNull(sideContext);
        int mainAddress = normalAddress(rom, donor, main);
        int sideAddress = normalAddress(rom, donor, sidekick);
        if (mainAddress == sideAddress) assertSame(mainContext, sideContext);
        else assertNotSame(mainContext, sideContext);
        assertAll("completed character bindings",
                () -> assertPalette(rom, mainAddress, mainContext.getPalette(0)),
                () -> assertPalette(rom, sideAddress, sideContext.getPalette(0)));

        GraphicsManager graphics = GameServices.graphics();
        assertEquals(DisplayColorProfile.RAW_RGB, graphics.getDisplayColorProfile());
        assertEquals(DisplayColorExpansion.FULL, graphics.getDisplayColorExpansion());
        PaletteUploadPresentation.publishAndLatch(graphics);
        assertFalse(PaletteUploadPresentation.displayedFade(graphics).affects(mainContext.getPaletteLineBase()));
        assertTexturePalette(graphics.getCombinedPaletteTextureId(), mainContext, rom, mainAddress);
        assertTexturePalette(graphics.getCombinedPaletteTextureId(), sideContext, rom, sideAddress);
        if (donor.equals("s1")) {
            // Pal_LZSonWater in the verified World REV01 ROM. Unlike S3K's
            // zone-dependent water, this is an existing native donor contract.
            assertPalette(rom, 0x2820, mainContext.getUnderwaterPalette(0));
            assertPalette(rom, 0x2820, sideContext.getUnderwaterPalette(0));
            Palette[] water = {new Palette(), new Palette(), new Palette(), new Palette()};
            graphics.cacheUnderwaterPaletteTexture(water, new Palette());
            PaletteUploadPresentation.publishAndLatch(graphics);
            assertTexturePalette(graphics.getUnderwaterPaletteTextureId(), mainContext, rom, 0x2820);
            assertTexturePalette(graphics.getUnderwaterPaletteTextureId(), sideContext, rom, 0x2820);
        } else {
            assertNull(mainContext.getUnderwaterPalette(0));
            assertNull(sideContext.getUnderwaterPalette(0));
        }
    }

    private static int normalAddress(byte[] rom, String donor, String character) {
        if (donor.equals("s3k")) return character.equals("knuckles") ? 0xA8AFC : 0xA8A3C;
        // Resolve REV01 Pal_Sonic directly from PalPointers' palette ID 2,
        // independently of the provider's revision-aware destination scan.
        int pointer = 0x2168 + 2 * 8;
        return (rom[pointer + 1] & 255) << 16 | word(rom, pointer + 2);
    }

    private static void assertPalette(byte[] rom, int address, PaletteView actual) {
        assertNotNull(actual);
        for (int i = 0; i < 16; i++) {
            int expected = word(rom, address + i * 2);
            assertEquals(channel(expected, 1), actual.red(i) & 255, "colour " + i + " red");
            assertEquals(channel(expected, 5), actual.green(i) & 255, "colour " + i + " green");
            assertEquals(channel(expected, 9), actual.blue(i) & 255, "colour " + i + " blue");
        }
    }

    private static void assertTexturePalette(Integer texture, RenderContext context,
                                              byte[] rom, int address) {
        assertNotNull(texture);
        glBindTexture(GL_TEXTURE_2D, texture);
        int height = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        ByteBuffer pixels = ByteBuffer.allocateDirect(16 * height * 4);
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        for (int i = 0; i < 16; i++) {
            int offset = (context.getEffectivePaletteLine(0) * 16 + i) * 4;
            int expected = word(rom, address + i * 2);
            for (int c = 0; c < 3; c++) {
                assertEquals(channel(expected, 1 + c * 4), pixels.get(offset + c) & 255,
                        "uploaded row " + context.getPaletteLineBase() + " colour " + i + " channel " + c);
            }
        }
    }

    private static int word(byte[] rom, int address) {
        return (rom[address] & 255) << 8 | (rom[address + 1] & 255);
    }

    private static int channel(int word, int shift) {
        return (((word >> shift) & 7) * 255 + 3) / 7;
    }
}
