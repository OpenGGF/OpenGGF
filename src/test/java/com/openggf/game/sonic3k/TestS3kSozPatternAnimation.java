package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kSozPatternAnimation {
    @BeforeAll
    public static void configure() {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @Test
    public void soz1ScrollDrivenAnimatedTileStillChanges() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0x08, 0)
                .build();

        List<String> channelIds = GameServices.animatedTileChannelGraph().channels().stream()
                .map(AnimatedTileChannel::channelId)
                .toList();
        assertTrue(channelIds.stream().noneMatch(id -> id.startsWith("s3k.soz.script.")),
                "SOZ custom routines return without executing the unused LRZ list: " + channelIds);
        assertTrue(channelIds.contains("s3k.soz1.scroll"),
                "Expected SOZ1 scroll channel in graph but found " + channelIds);

        Level level = GameServices.level().getCurrentLevel();
        Pattern pattern = level.getPattern(0x330);
        assertNotNull(pattern, "Expected SOZ1 animated destination tile");

        Camera camera = fixture.camera();
        camera.setFrozen(true);

        Sonic3kPatternAnimator animator = resolvePatternAnimator();
        camera.setX((short) 0);
        camera.setY((short) 0);
        animator.updateSoz1BackgroundTilesForGraph();
        byte[] phaseZero = snapshotTiles(level, 0x330, 0x333, 0x336, 0x339, 0x33C);
        int phaseZeroValue = animator.computeSoz1Phase();

        int[] candidateXs = {
                0x10, 0x20, 0x30, 0x40, 0x60, 0x80, 0xA0, 0xC0,
                0x100, 0x140, 0x180, 0x1C0, 0x200, 0x240, 0x280, 0x2C0
        };
        for (int candidateX : candidateXs) {
            camera.setX((short) candidateX);
            int candidatePhase = animator.computeSoz1Phase();
            if (candidatePhase == phaseZeroValue) {
                continue;
            }
            animator.updateSoz1BackgroundTilesForGraph();
            byte[] shifted = snapshotTiles(level, 0x330, 0x333, 0x336, 0x339, 0x33C);
            if (!Arrays.equals(phaseZero, shifted)) {
                return;
            }
        }

        throw new AssertionError("Expected SOZ1 scroll-driven animated tile to change for at least one phase shift");
    }

    @Test
    public void bothActsPreserveStaticDesertArtAcrossLavaReefScriptCycles() {
        for (int act : new int[]{0, 1}) {
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, act).build();
            fixture.camera().setFrozen(true);
            var level = GameServices.level().getCurrentLevel();
            var animator = resolvePatternAnimator();
            // AniPLC_LRZ1 targets $350-$357, but AnimateTiles_SOZ1/2 never
            // branch to AnimateTiles_DoAniPLC. Preserve the level-loaded art.
            byte[] initial = snapshotTiles(level, 0x350, 0x351, 0x352, 0x353,
                    0x354, 0x355, 0x356, 0x357);
            for (int frame = 0; frame < 48; frame++) {
                animator.update();
                animator.publishAniPlcAtVBlank();
                org.junit.jupiter.api.Assertions.assertArrayEquals(initial,
                        snapshotTiles(level, 0x350, 0x351, 0x352, 0x353,
                                0x354, 0x355, 0x356, 0x357),
                        "act=" + act + " animation pass=" + frame);
            }
        }
    }

    @Test
    public void soz1ScrollTileRemainsStableWhileCameraPhaseIsStable() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0x08, 0)
                .build();

        Camera camera = fixture.camera();
        camera.setFrozen(true);

        Level level = GameServices.level().getCurrentLevel();
        Sonic3kPatternAnimator animator = resolvePatternAnimator();
        animator.updateSoz1BackgroundTilesForGraph();
        byte[] initial = snapshot(level.getPattern(0x330));

        for (int i = 0; i < 8; i++) {
            animator.updateSoz1BackgroundTilesForGraph();
        }

        assertTrue(Arrays.equals(initial, snapshot(level.getPattern(0x330))),
                "Expected SOZ1 custom destination tile to remain stable while camera phase is unchanged");
    }

    @Test
    public void soz1NativeArenaRoutineForcesPhaseZero() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(0x08, 0)
                .build();

        Camera camera = fixture.camera();
        camera.setFrozen(true);
        camera.setX((short) 0x4380);
        camera.setY((short) 0x0960);

        assertFalse(com.openggf.game.GameServices.level().getZoneFeatureProvider().bgWrapsHorizontally(), "Normal desert retains its multi-band plane");
        Sonic3kPatternAnimator animator = resolvePatternAnimator();
        assertNotEquals(0, animator.computeSoz1Phase(),
                "Sanity check: SOZ1 phase should not already be zero before the boss lock bridge is active");

        camera.setMinX((short) 0x4180);
        camera.setMinY((short) 0x0960);

        assertNotEquals(0, animator.computeSoz1Phase(), "Camera bounds alone do not own animation phase");
        com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(
                com.openggf.game.GameServices.zoneRuntimeRegistry()).orElseThrow().events().backgroundRoutine(4);
        assertEquals(0, animator.computeSoz1Phase(), "Native arena background words are equal");
        assertTrue(com.openggf.game.GameServices.level().getZoneFeatureProvider().bgWrapsHorizontally());
        assertTrue(com.openggf.game.GameServices.level().getZoneFeatureProvider().useLinearBackgroundLayoutOverflow(8));
    }

    @Test
    public void soz1SecondaryDmaWritesAllSixTilesAtEveryPhase() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(8, 0).build();
        var level = GameServices.level().getCurrentLevel();
        var animator = resolvePatternAnimator();
        fixture.camera().setFrozen(true);
        for (int phase = 0; phase < 32; phase++) {
            // At multiples of 32 the native phase is -cameraX/32 modulo 32.
            fixture.camera().setX((short) (((32 - phase) & 31) * 32));
            assertEquals(phase, animator.computeSoz1Phase());
            animator.updateSoz1BackgroundTilesForGraph();
            byte[] source = GameServices.rom().getRom().readBytes(0xBE5C0 + phase * 192, 192);
            for (int pixel = 0; pixel < 384; pixel++) {
                int packed = source[pixel / 2] & 255;
                int expected = (pixel % 2 == 0 ? packed >> 4 : packed) & 15;
                int actual = level.getPattern(0x33C + pixel / 64)
                        .getPixel(pixel % 8, (pixel % 64) / 8);
                assertEquals(expected, actual, "phase=" + phase + " pixel=" + pixel);
            }
        }
    }

    @Test
    public void soz1ProductionScrollUsesNativeBandsAndIndependentShimmerPhases() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(8, 0).build();
        var provider = new com.openggf.game.sonic3k.scroll.Sonic3kScrollHandlerProvider();
        provider.load(GameServices.rom().getRom());
        var handler = provider.getHandler(8);
        byte[] wave = GameServices.rom().getRom().readBytes(0x5077E, 512);
        int[] lines = new int[224];
        for (int x : new int[]{0, 17, 511, 1023, 0x4380, -17}) {
            for (int y : new int[]{0, 767, 768, 1024, 1535, 1536, -16}) {
                for (int frame : new int[]{0, 1, 2, 63, 0xFFFF}) {
                    handler.update(lines, x, y, frame, 0);
                    assertEquals((short)y >> 4, handler.getVscrollFactorBG());
                    assertEquals((short)x >> 4, handler.getBgCameraX());
                    int fgStart = ((((short) frame >> 1) + 2 * (short)y) & 62) / 2;
                    int bgStart = ((((short) frame >> 1) + 2 * ((short)y >> 4)) & 62) / 2;
                    for (int row = 0; row < 224; row++) {
                        int worldY = ((short)y >> 4) + row;
                        int band = worldY < 272 ? 0 : Math.min(6, 1 + (worldY - 272) / 8);
                        // Independent rational form of sub_55D56's 16.16 accumulator.
                        int bg = Math.floorDiv((short)x * (4 + band), 64);
                        int fgDelta = wave[2 * (fgStart + row) + 1];
                        int bgDelta = wave[2 * (bgStart + row) + 1];
                        assertEquals((short)(-x + fgDelta), (short)(lines[row] >> 16));
                        assertEquals((short)(-bg + bgDelta), (short)lines[row],
                                "x=" + x + " y=" + y + " row=" + row);
                    }
                }
            }
        }
    }

    @Test
    public void soz1PaletteReadsBeforeIncrementAndRestoresItsSixPassTimer() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(8, 0).build();
        var rom = GameServices.rom().getRom();
        var level = GameServices.level().getCurrentLevel();
        var cycler = new Sonic3kPaletteCycler(com.openggf.data.RomByteReader.fromRom(rom), level, 8, 0);
        byte[] source = rom.readBytes(0x30DA, 32);
        for (int pass = 0; pass < 49; pass++) {
            byte[] before = cycler.captureCyclerState();
            cycler.update();
            byte[] after = cycler.captureCyclerState();
            for (int color = 0; color < 4; color++) {
                int index = ((pass / 6) % 4) * 8 + color * 2;
                int expected = ((source[index] & 255) << 8) | (source[index + 1] & 255);
                assertEquals(expected, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                        level.getPalette(2).getColor(12 + color)), "pass=" + pass);
            }
            cycler.restoreCyclerState(before);
            cycler.update();
            org.junit.jupiter.api.Assertions.assertArrayEquals(after, cycler.captureCyclerState());
        }
    }

    @Test
    public void soz2NormalBackgroundUsesSignedHalfSpeedBeforeNegation() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(8, 1).build();
        var handler = new com.openggf.game.sonic3k.scroll.SwScrlSoz(GameServices.rom().getRom());
        int[] lines = new int[224];
        for (int x : new int[]{0, 1, 511, 0x2980, 0xFFFF, 0x8001}) {
            for (int y : new int[]{0, 1, 0x7FF, 0xFFFF}) {
                handler.update(lines, x, y, 5, 1);
                assertEquals(Math.floorDiv((short) x, 2), handler.getBgCameraX());
                assertEquals(Math.floorDiv((short) y, 2), handler.getVscrollFactorBG());
                for (int line : lines) {
                    assertEquals((short) -x, (short) (line >> 16));
                    assertEquals((short) -Math.floorDiv((short) x, 2), (short) line);
                }
            }
        }
    }

    @Test
    public void soz2SharedLightingUploadsExactRomPaletteAndTorchBanks() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(8, 1).build();
        var runtime = com.openggf.game.sonic3k.runtime.S3kRuntimeStates.currentSoz(
                GameServices.zoneRuntimeRegistry()).orElseThrow();
        var light = runtime.lighting();
        var level = GameServices.level().getCurrentLevel();
        var rom = GameServices.rom().getRom();
        var cycler = new Sonic3kPaletteCycler(com.openggf.data.RomByteReader.fromRom(rom), level, 8, 1);
        var animator = resolvePatternAnimator();
        assertTrue(GameServices.animatedTileChannelGraph().channels().stream()
                .anyMatch(channel -> channel.channelId().equals("s3k.soz2.torches")));
        light.initializeSeamlessDarkness();
        light.resetLight();
        // Exercise all five palette banks and all seven torch frames through production owners.
        boolean[] seenTorchFrames = new boolean[7];
        for (int pass = 0; pass < 3700; pass++) {
            int priorFadeStep = light.fadeStep();
            cycler.update();
            if (priorFadeStep != light.fadeStep()) {
                byte[] palette = rom.readBytes(0x317A + light.fadeOffset(), 52);
                for (int color = 0; color < 26; color++) {
                    int expected = ((palette[color * 2] & 255) << 8) | (palette[color * 2 + 1] & 255);
                    int line = color < 11 ? 2 : 3;
                    int index = color < 11 ? color + 1 : color - 10;
                    assertEquals(expected, com.openggf.game.palette.PaletteWriteSupport.segaWordFromColor(
                            level.getPalette(line).getColor(index)), "pass=" + pass + " color=" + color);
                }
            }
            int torchTimer = light.torchTimer();
            int oldFrame = light.torchFrame();
            animator.update();
            animator.publishAniPlcAtVBlank();
            if (torchTimer == 0) {
                int bank = light.fadeStep() < 2 ? 0 : light.fadeStep() < 4 ? 3 : 6;
                int frame = bank == 6 ? 6 : bank + oldFrame;
                seenTorchFrames[frame] = true;
                byte[] source = rom.readBytes(0xBFDC0 + frame * 192, 192);
                for (int pixel = 0; pixel < 384; pixel++) {
                    int packed = source[pixel / 2] & 255;
                    int expected = (pixel % 2 == 0 ? packed >> 4 : packed) & 15;
                    assertEquals(expected, level.getPattern(0x330 + pixel / 64)
                            .getPixel(pixel % 8, (pixel % 64) / 8), "pass=" + pass + " pixel=" + pixel);
                }
            }
        }
        for (int frame = 0; frame < 7; frame++) assertTrue(seenTorchFrames[frame], "torch frame=" + frame);
    }

    private static Sonic3kPatternAnimator resolvePatternAnimator() {
        AnimatedPatternManager manager = GameServices.level().getAnimatedPatternManager();
        assertNotNull(manager, "AnimatedPatternManager must be present");
        if (manager instanceof Sonic3kPatternAnimator animator) {
            return animator;
        }
        if (manager instanceof Sonic3kLevelAnimationManager levelAnimator) {
            try {
                Field field = Sonic3kLevelAnimationManager.class.getDeclaredField("patternAnimator");
                field.setAccessible(true);
                Object value = field.get(levelAnimator);
                if (value instanceof Sonic3kPatternAnimator animator) {
                    return animator;
                }
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Unable to access Sonic3kPatternAnimator", e);
            }
        }
        throw new AssertionError("Unexpected AnimatedPatternManager type: " + manager.getClass().getName());
    }

    private static byte[] snapshot(Pattern pattern) {
        byte[] data = new byte[Pattern.PATTERN_SIZE_IN_MEM];
        int index = 0;
        for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
            for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                data[index++] = pattern.getPixel(x, y);
            }
        }
        return data;
    }

    private static byte[] snapshotTiles(Level level, int... tileIndices) {
        byte[] data = new byte[tileIndices.length * Pattern.PATTERN_SIZE_IN_MEM];
        int writeIndex = 0;
        for (int tileIndex : tileIndices) {
            byte[] tile = snapshot(level.getPattern(tileIndex));
            System.arraycopy(tile, 0, data, writeIndex, tile.length);
            writeIndex += tile.length;
        }
        return data;
    }
}
