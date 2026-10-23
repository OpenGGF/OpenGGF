package com.openggf.game.sonic2.competition.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.InputActionMasks;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.GameServices;
import com.openggf.game.SpecialStageViewport;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic2.competition.Sonic2CompetitiveSpecialStageAdapter.Events;
import com.openggf.game.sonic2.specialstage.CompetitiveSpecialStagePlayerAccess;
import com.openggf.game.sonic2.specialstage.Sonic2TrackFrameDecoder;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSpriteMappings;
import com.openggf.graphics.SpriteSatEntry;
import com.openggf.graphics.RenderContext;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.graphics.color.DisplayColorExpansion;
import com.openggf.graphics.color.DisplayColorProfile;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Actual display-free production load, two-human stepping, native SAT draw and restored pixels. */
@Isolated
@RequiresRom(SonicGame.SONIC_2)
class TestCompetitiveHalfpipeEgl {
    @Test
    void realHalfpipeBootsAndReplaysBothHumansThroughTheDefaultNativeShadowPath() throws Exception {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")), "owned EGL required");
        assertNull(System.getenv("DISPLAY"));
        assertNull(System.getenv("WAYLAND_DISPLAY"));
        GameServices.audio().setBackend(new NullAudioBackend());
        // The boot supplies the real graphics/services foundation only. No 1P
        // campaign, ordinary special-stage manager, CPU, or emerald owner boots.
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224);
             AudioOwner audioOwner = new AudioOwner()) {
            GameServices.audio().setBackend(new NullAudioBackend());
            assertInstanceOf(NullAudioBackend.class, GameServices.audio().getBackend());
            var graphics = EngineServices.current().graphics();
            assertFalse(graphics.isHeadlessMode(), "logic-only graphics cannot prove production drawing");
            var clock = new AtomicInteger();
            try (var halfpipe = new Sonic2CompetitiveHalfpipe(
                    new CompetitiveHalfpipeRomData(TestEnvironment.currentRom()), graphics,
                    SpecialStageViewport.nativeViewport(), audioOwner.audio, clock::get)) {
                var events = new Events() {
                    @Override public void collectedRing(int slot) { }
                    @Override public void hitBomb(int slot) { }
                    @Override public void checkpoint() { fail("production must use explicit native events"); }
                    @Override public void archivedSector(int sector, int p1, int p2) { }
                    @Override public void tiedSector(int sector) { }
                    @Override public void stageComplete() { }
                };
                var input = new InputHandler();
                halfpipe.loadCompetitiveStage(4, events);
                for (int i = 0; i < 22; i++) { clock.incrementAndGet(); halfpipe.step(input); }
                var neutral = PlayerInputState.neutral();
                for (int i = 0; i < 12; i++) {
                    input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(
                            PlayerInputState.of(8, i == 0 ? 8 : 0, 0, 0, false, false),
                            PlayerInputState.of(4, i == 0 ? 4 : 0, 0, 0, false, false)));
                    clock.incrementAndGet(); halfpipe.step(input);
                }
                assertNotEquals(halfpipe.player(0).getAngle(), halfpipe.player(1).getAngle());
                for (int slot = 0; slot < 2; slot++) {
                    assertTrue(halfpipe.player(slot).getXPos() >= 16 && halfpipe.player(slot).getXPos() < 240);
                    assertTrue(halfpipe.player(slot).getYPos() >= 24 && halfpipe.player(slot).getYPos() < 204);
                }
                byte[] separated = draw(halfpipe);
                assertTrue(colors(separated) > 24, "ROM terrain, HUD and both actors must reach actual pixels");
                assertBlackMargins(separated);
                png(separated, "separate-human-actors.png");
                var saved = halfpipe.capture();
                int savedClock = clock.get();
                for (int i = 0; i < 8; i++) {
                    input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(neutral,
                            PlayerInputState.of(0, 0, i == 0 ? InputActionMasks.ACTION_A : 0,
                                    i == 0 ? InputActionMasks.ACTION_A : 0, false, false)));
                    clock.incrementAndGet(); halfpipe.step(input);
                }
                byte[] forward = draw(halfpipe);
                png(forward, "p2-jump-native-shadows.png");
                var recreated = halfpipe.capture();
                halfpipe.restore(saved); clock.set(savedClock);
                for (int i = 0; i < 8; i++) {
                    input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(neutral,
                            PlayerInputState.of(0, 0, i == 0 ? InputActionMasks.ACTION_A : 0,
                                    i == 0 ? InputActionMasks.ACTION_A : 0, false, false)));
                    clock.incrementAndGet(); halfpipe.step(input);
                }
                var replayed = halfpipe.capture();
                assertEquals(recreated.phase(), replayed.phase());
                assertEquals(recreated.track(), replayed.track());
                assertEquals(recreated.checkpoint(), replayed.checkpoint());
                assertEquals(recreated.objects(), replayed.objects());
                assertArrayEquals(forward, draw(halfpipe), "restored simulation must reproduce the actual framebuffer");
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

    @Test
    void nativeShadowOperatorsSelectTheSameRomPlanesAtDacIntensityBeforeProfilesAndFade() throws Exception {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")), "owned EGL required");
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        GameServices.audio().setBackend(new NullAudioBackend());
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224)) {
            var graphics = EngineServices.current().graphics();
            var data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
            var renderer = new CompetitiveHalfpipeRenderer(data, graphics, SpecialStageViewport.nativeViewport());
            var palettes = new CompetitiveHalfpipePalettes(data);
            try {
                renderer.initialize(4);
                var pair = CompetitiveSpecialStagePlayerAccess.create();
                pair.step(0, 0, 0x40, 4); pair.step(1, 0, 0x40, 4);
                for (int i = 0; i < 6; i++) { pair.step(0, 0, 0, 4); pair.step(1, 0, 0, 4); }
                int[] tiles = Sonic2TrackFrameDecoder.decodeFrame(data.trackFrame(0x11), false);
                List<SpriteSatEntry> geometry = renderer.satEntries(0x11, pair.players(), List.of(), 0, 0,
                        CompetitiveHalfpipeRenderer.Overlay.none());
                int profilesChecked = 0;
                for (var profile : new DisplayColorProfile[]{DisplayColorProfile.RAW_RGB, DisplayColorProfile.MD_ANALOG}) {
                    for (var expansion : DisplayColorExpansion.values()) for (int fadePasses : new int[]{11, 22}) {
                        graphics.setDisplayColorProfile(profile); graphics.setDisplayColorExpansion(expansion);
                        palettes.load(4);
                        for (int i = 0; i < fadePasses; i++) palettes.fadeStep();
                        Palette[] source = palettes.palettes(); renderer.setPalettes(source);
                        byte[] normal = draw(() -> renderer.drawCleared(tiles, 0, 0));
                        byte[] actual = draw(() -> renderer.draw(0x11, tiles, 0, 0,
                                pair.players(), List.of(), 0, 0, CompetitiveHalfpipeRenderer.Overlay.none()));
                        Map<Integer, Integer> expected = intensityPairs(source, profile, expansion);
                        int operators = 0, holes = 0;
                        for (SpriteSatEntry shadow : geometry) {
                            if (shadow.pixelOperation() != SpriteSatEntry.PixelOperation.SHADOW_15) continue;
                            for (int y = shadow.piece().y(); y < shadow.piece().y() + shadow.piece().heightTiles() * 8; y++) {
                                for (int x = shadow.piece().x(); x < shadow.piece().x() + shadow.piece().widthTiles() * 8; x++) {
                                    if (x < 32 || x >= 288 || y < 0 || y >= 224) continue;
                                    final int px = x, py = y;
                                    if (geometry.stream().anyMatch(entry -> entry.pixelOperation() == SpriteSatEntry.PixelOperation.NORMAL
                                            && inside(entry, px, py))) continue;
                                    int color = shadowColor(data, shadow, x, y);
                                    if (color == 15) {
                                        Integer wanted = expected.get(rgb(normal, x, y));
                                        if (wanted == null || wanted == rgb(normal, x, y)) continue;
                                        assertEquals(wanted.intValue(), rgb(actual, x, y), profile + "/" + expansion
                                                + " fade " + fadePasses + " shadow pixel " + x + "," + y);
                                        operators++;
                                    } else if (geometry.stream().noneMatch(entry -> entry != shadow
                                            && entry.pixelOperation() == SpriteSatEntry.PixelOperation.SHADOW_15
                                            && inside(entry, px, py) && shadowColor(data, entry, px, py) == 15)) {
                                        assertEquals(rgb(normal, x, y), rgb(actual, x, y), "transparent operator texel");
                                        holes++;
                                    }
                                }
                            }
                        }
                        assertTrue(operators >= 16, "actual ROM shadows must qualify the companion under " + profile + "/" + expansion);
                        assertTrue(holes > 0, "ROM shadow transparency must retain the source planes");
                        profilesChecked++;
                    }
                }
                assertEquals(8, profilesChecked);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                try { renderer.reset(); }
                finally { graphics.cleanup(); RenderContext.reset(); }
            }
        }
    }

    @Test
    void nativeObj5BSpillsRenderBothOwnerBirthPosesAndReplayRecreatedSlotsThroughRisingAndFalling() throws Exception {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")), "owned EGL required");
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        GameServices.audio().setBackend(new NullAudioBackend());
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224);
             AudioOwner audioOwner = new AudioOwner()) {
            var graphics = EngineServices.current().graphics();
            var data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
            var renderer = new CompetitiveHalfpipeRenderer(data, graphics, SpecialStageViewport.nativeViewport());
            try {
                graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
                graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                renderer.initialize(4);
                Palette[] source = data.palettes(4);
                renderer.setPalettes(source);
                var pair = CompetitiveSpecialStagePlayerAccess.create();
                var debitedOwners = new java.util.ArrayList<Integer>();
                var events = new Events() {
                    @Override public void collectedRing(int slot) { fail("no stream items in this object-pass regression"); }
                    @Override public void hitBomb(int slot) { debitedOwners.add(slot); }
                    @Override public void checkpoint() { fail("no ordinary checkpoint owner"); }
                    @Override public void archivedSector(int sector, int p1, int p2) { fail("no sector marker"); }
                    @Override public void tiedSector(int sector) { fail("no sector marker"); }
                    @Override public void stageComplete() { fail("no sector marker"); }
                };
                var track = new CompetitiveHalfpipeTrack(data); track.load(4);
                var machine = new CompetitiveHalfpipeObjectMachine(data, pair, audioOwner.audio, events);
                machine.load(4);
                machine.runPass(track, track.frame(), ignored -> { });
                for (int pass = 0; pass < 12; pass++) {
                    track.tick();
                    machine.runPass(track, track.frame(), slot -> pair.step(slot, slot == 0 ? 8 : 4,
                            0, track.animationTimer()));
                }
                assertNotEquals(pair.player(0).getAngle(), pair.player(1).getAngle());
                pair.collectRing(0); pair.collectRing(1);
                int p1x = pair.player(0).getXPos() + track.offsetX();
                int p1y = pair.player(0).getYPos() + track.offsetY();
                int p2x = pair.player(1).getXPos() + track.offsetX();
                int p2y = pair.player(1).getYPos() + track.offsetY();
                assertNotEquals(p1x, p2x, "two independently controlled owner poses");
                int one = machine.requestSpill(0), two = machine.requestSpill(1);
                assertTrue(one >= 8 && two > one);
                machine.runPass(track, track.frame(), ignored -> { });
                var oneNode = machine.ram().at(one); var twoNode = machine.ram().at(two);
                assertEquals(CompetitiveHalfpipeObjectMachine.Kind.SPILL, oneNode.kind());
                assertEquals(CompetitiveHalfpipeObjectMachine.Kind.SPILL, twoNode.kind());
                assertEquals(p1x, oneNode.spill.x()); assertEquals(p1y, oneNode.spill.y());
                assertEquals(p2x, twoNode.spill.x()); assertEquals(p2y, twoNode.spill.y());
                assertEquals(0, oneNode.spill.mappingFrame()); assertEquals(0, twoNode.spill.mappingFrame());
                assertEquals(List.of(0, 1), debitedOwners);
                assertEquals(0, pair.player(0).getRings()); assertEquals(0, pair.player(1).getRings());
                assertTrue(machine.renderFrame().draws().stream()
                        .noneMatch(event -> event.type() == CompetitiveHalfpipeRenderFrame.Type.SPILL),
                        "Obj5B init reuses its current slot but does not run Main/DisplaySprite twice");
                var birth = machine.capture(); var birthTrack = track.capture(); var birthPlayers = pair.capture();
                var oldParticle = oneNode.spill;

                track.tick(); machine.runPass(track, track.frame(), ignored -> { });
                assertEquals(5, machine.ram().at(one).spill.priority());
                assertEquals(5, machine.ram().at(two).spill.priority());
                assertEquals(8, machine.ram().at(one).spill.mappingFrame());
                assertEquals(8, machine.ram().at(two).spill.mappingFrame());
                var risingFrame = machine.renderFrame();
                byte[] rising = drawNativeSpills(renderer, data, pair, track, risingFrame);
                assertNativeSpillPixels(renderer, data, pair, risingFrame, source, rising);

                int passes = 1;
                boolean fallingVisible = false;
                while (passes < 96 && !fallingVisible) {
                    track.tick(); machine.runPass(track, track.frame(), ignored -> { }); passes++;
                    var first = machine.ram().at(one); var second = machine.ram().at(two);
                    assertNotNull(first, "P1 spill remains in its native slot through the apex");
                    assertNotNull(second, "P2 spill remains in its native slot through the apex");
                    fallingVisible = first.spill.priority() == 0 && second.spill.priority() == 0
                            && first.spill.y() >= 16 && second.spill.y() >= 16;
                }
                assertTrue(fallingVisible, "both real particles reach the visible falling branch");
                var fallingFrame = machine.renderFrame();
                assertEquals(2, fallingFrame.draws().stream()
                        .filter(event -> event.type() == CompetitiveHalfpipeRenderFrame.Type.SPILL
                                && event.bucket() == 0 && (event.mappingFrame() == 9
                                || event.mappingFrame() == 0x13 || event.mappingFrame() == 0x1D)).count());
                byte[] falling = drawNativeSpills(renderer, data, pair, track, fallingFrame);
                assertNativeSpillPixels(renderer, data, pair, fallingFrame, source, falling);
                var expected = machine.capture(); var expectedTrack = track.capture();

                machine.restore(birth); track.restore(birthTrack); pair.restore(birthPlayers);
                assertNotSame(oneNode, machine.ram().at(one));
                assertNotSame(twoNode, machine.ram().at(two));
                assertNotSame(oldParticle, machine.ram().at(one).spill);
                assertEquals(birth, machine.capture(), "native RAM restoration recreates all stable slot positions");
                for (int pass = 0; pass < passes; pass++) {
                    track.tick(); machine.runPass(track, track.frame(), ignored -> { });
                    if (pass == 0) assertArrayEquals(rising,
                            drawNativeSpills(renderer, data, pair, track, machine.renderFrame()));
                }
                assertEquals(expected, machine.capture()); assertEquals(expectedTrack, track.capture());
                assertArrayEquals(falling, drawNativeSpills(renderer, data, pair, track, machine.renderFrame()),
                        "recreated Obj5B RAM slots reproduce the forward native framebuffer");
                assertBlackMargins(falling);
                assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                try { renderer.reset(); }
                finally { graphics.cleanup(); RenderContext.reset(); }
            }
        }
    }

    @Test
    void failedNativeWinnerAllocationRecolorsAlreadyQueuedSonicAndRestoresTheSameFramebuffer() throws Exception {
        assumeTrue("surfaceless-egl".equals(System.getProperty("openggf.headless.gl")), "owned EGL required");
        assertNull(System.getenv("DISPLAY")); assertNull(System.getenv("WAYLAND_DISPLAY"));
        GameServices.audio().setBackend(new NullAudioBackend());
        try (HeadlessGameBoot boot = new HeadlessGameBoot(320, 224);
             AudioOwner audioOwner = new AudioOwner()) {
            var graphics = EngineServices.current().graphics();
            var data = new CompetitiveHalfpipeRomData(TestEnvironment.currentRom());
            var renderer = new CompetitiveHalfpipeRenderer(data, graphics, SpecialStageViewport.nativeViewport());
            try {
                graphics.setDisplayColorProfile(DisplayColorProfile.RAW_RGB);
                graphics.setDisplayColorExpansion(DisplayColorExpansion.FULL);
                renderer.initialize(4);
                Palette[] source = data.palettes(4); renderer.setPalettes(source);
                var pair = CompetitiveSpecialStagePlayerAccess.create();
                var events = new Events() {
                    @Override public void collectedRing(int slot) { fail("the earlier size-eight item misses both humans"); }
                    @Override public void hitBomb(int slot) { fail("no bomb owner"); }
                    @Override public void checkpoint() { fail("no ordinary checkpoint owner"); }
                    @Override public void archivedSector(int sector, int p1, int p2) { fail("failed wings create no hand/archive owner"); }
                    @Override public void tiedSector(int sector) { fail("Tails wins this comparison"); }
                    @Override public void stageComplete() { fail("no terminal timer"); }
                };
                var track = new CompetitiveHalfpipeTrack(data); track.load(4);
                var machine = new CompetitiveHalfpipeObjectMachine(data, pair, audioOwner.audio, events);
                machine.load(4); machine.initializeHiddenMessages();
                machine.runPass(track, track.frame(), ignored -> { });
                for (int pass = 0; pass < 12; pass++) {
                    track.tick();
                    machine.runPass(track, track.frame(), slot -> pair.step(slot, slot == 0 ? 8 : 4,
                            0, track.animationTimer()));
                }
                for (int ring = 0; ring < 10; ring++) pair.collectRing(0);
                for (int ring = 0; ring < 20; ring++) pair.collectRing(1);
                for (int slot = 8; slot <= 126; slot++) machine.ram().replace(slot,
                        new CompetitiveHalfpipeObjectMachine.Node(CompetitiveHalfpipeObjectMachine.Kind.HIDDEN));
                var item = new CompetitiveHalfpipeObjectMachine.Node(CompetitiveHalfpipeObjectMachine.Kind.ITEM);
                item.item = CompetitiveHalfpipeItems.create(data, false, 5, 0x80);
                machine.ram().replace(30, item);
                var completedRainbow = new CompetitiveHalfpipeObjectMachine.Node(CompetitiveHalfpipeObjectMachine.Kind.RAINBOW);
                completedRainbow.frame = 9; completedRainbow.x = 0xE8; completedRainbow.mapping = 9; completedRainbow.base = 6;
                assertEquals(0xFF, data.rom().readU8(0x357F4 + completedRainbow.frame));
                machine.ram().replace(100, completedRainbow);
                for (int pass = 0; pass < 10 && track.drawingIndex() != 4; pass++) track.tick();
                assertEquals(4, track.drawingIndex());
                assertEquals(-1, machine.ram().findFree());
                assertEquals(0x22E5, machine.nativeArtWord(0));
                var before = machine.capture(); var beforeTrack = track.capture(); var beforePlayers = pair.capture();
                var originalSonicSlot = machine.ram().at(0);

                machine.runPass(track, track.frame(), ignored -> { });
                assertEquals(0x42E5, machine.nativeArtWord(0));
                assertEquals(0x2000, machine.nativeArtWord(127));
                assertNull(machine.ram().at(127), "the art-word write does not allocate the native guard");
                var failedFrame = machine.renderFrame();
                var sonic = failedFrame.draws().stream().filter(event -> event.type() == CompetitiveHalfpipeRenderFrame.Type.PLAYER
                        && event.owner() == 0).findFirst().orElseThrow();
                assertEquals(2, sonic.palette()); assertEquals(0x42E5, sonic.nativeArt());
                byte[] recolored = drawNativeSpills(renderer, data, pair, track, failedFrame);
                // This is a separately cleared control framebuffer, not a second
                // sprite pass in the production frame. The real event is untouched.
                var controlFrame = new CompetitiveHalfpipeRenderFrame(failedFrame.draws().stream().map(event -> {
                    if (event.type() != CompetitiveHalfpipeRenderFrame.Type.PLAYER || event.owner() != 0) return event;
                    return new CompetitiveHalfpipeRenderFrame.Draw(event.type(), event.slot(), event.bucket(), event.owner(),
                            event.x(), event.y(), event.mappingFrame(), 0x22E5, 1, event.xFlip(), event.item(), event.spill());
                }).toList());
                byte[] originalPalette = drawNativeSpills(renderer, data, pair, track, controlFrame);
                assertNativeSonicRecolorPixels(renderer, data, pair, failedFrame, sonic, source, originalPalette, recolored);
                var afterFailure = machine.capture();
                machine.runPass(track, track.frame(), ignored -> { });
                assertEquals(0x42E5, machine.nativeArtWord(0), "the failed write persists beyond the comparison pass");
                byte[] forward = drawNativeSpills(renderer, data, pair, track, machine.renderFrame());
                var expectedForward = machine.capture();

                machine.restore(before); track.restore(beforeTrack); pair.restore(beforePlayers);
                assertNotSame(originalSonicSlot, machine.ram().at(0));
                assertEquals(0x22E5, machine.nativeArtWord(0));
                machine.runPass(track, track.frame(), ignored -> { });
                assertEquals(afterFailure, machine.capture());
                assertArrayEquals(recolored, drawNativeSpills(renderer, data, pair, track, machine.renderFrame()),
                        "restored full RAM repeats the native failed-allocation recolor in the same displayed frame");
                machine.runPass(track, track.frame(), ignored -> { });
                assertEquals(expectedForward, machine.capture());
                assertArrayEquals(forward, drawNativeSpills(renderer, data, pair, track, machine.renderFrame()));
                assertBlackMargins(recolored); assertEquals(GL_NO_ERROR, glGetError());
            } finally {
                try { renderer.reset(); }
                finally { graphics.cleanup(); RenderContext.reset(); }
            }
        }
    }

    private static void assertNativeSonicRecolorPixels(CompetitiveHalfpipeRenderer renderer, CompetitiveHalfpipeRomData data,
            CompetitiveSpecialStagePlayerAccess.Pair pair, CompetitiveHalfpipeRenderFrame frame,
            CompetitiveHalfpipeRenderFrame.Draw sonic, Palette[] source, byte[] original, byte[] actual) {
        List<SpriteSatEntry> geometry = renderer.nativeSatEntries(pair.players(), frame);
        List<SpriteSatEntry> sonicPieces = renderer.nativeSatEntries(pair.players(), new CompetitiveHalfpipeRenderFrame(List.of(sonic)));
        var sourcePieces = Sonic2SpecialStageSpriteMappings.getSonicFrame(pair.player(0).getMappingFrame()).pieces;
        Pattern[] art = data.playerPatterns();
        assertEquals(sourcePieces.length, sonicPieces.size());
        int compared = 0;
        for (int part = 0; part < sonicPieces.size(); part++) {
            var piece = sonicPieces.get(part); int ordinal = geometry.indexOf(piece);
            assertTrue(ordinal >= 0); assertEquals(2, piece.piece().paletteIndex()); assertTrue(piece.piece().piecePriority());
            for (int y = Math.max(0, piece.piece().y()); y < Math.min(224, piece.piece().y() + piece.piece().heightTiles() * 8); y++) {
                for (int x = Math.max(32, piece.piece().x()); x < Math.min(288, piece.piece().x() + piece.piece().widthTiles() * 8); x++) {
                    final int px = x, py = y;
                    if (geometry.subList(0, ordinal).stream().anyMatch(front -> inside(front, px, py))) continue;
                    int localX = x - piece.piece().x(), localY = y - piece.piece().y();
                    if (piece.piece().hFlip()) localX = piece.piece().widthTiles() * 8 - 1 - localX;
                    if (piece.piece().vFlip()) localY = piece.piece().heightTiles() * 8 - 1 - localY;
                    int color = art[sourcePieces[part].tileIndex + localX / 8 * piece.piece().heightTiles() + localY / 8]
                            .getPixel(localX & 7, localY & 7);
                    if (color == 0) continue;
                    Palette.Color from = source[1].colors[color], to = source[2].colors[color];
                    int oldColor = DisplayColorConverter.toPackedRgb(from.r & 255, from.g & 255, from.b & 255,
                            DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL);
                    int newColor = DisplayColorConverter.toPackedRgb(to.r & 255, to.g & 255, to.b & 255,
                            DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL);
                    if (oldColor == newColor) continue;
                    assertEquals(oldColor, rgb(original, x, y), "native Sonic palette-one control texel");
                    assertEquals(newColor, rgb(actual, x, y), "same-pass failed-allocation palette-two Sonic texel");
                    compared++;
                }
            }
        }
        assertTrue(compared >= 32, "the authentic failed allocation must visibly recolor Sonic");
    }

    private static byte[] drawNativeSpills(CompetitiveHalfpipeRenderer renderer, CompetitiveHalfpipeRomData data,
            CompetitiveSpecialStagePlayerAccess.Pair pair, CompetitiveHalfpipeTrack track,
            CompetitiveHalfpipeRenderFrame frame) {
        renderer.setGeometryOffset(track.offsetX(), track.offsetY());
        int[] tiles = Sonic2TrackFrameDecoder.decodeFrame(data.trackFrame(track.frame()), track.flipped());
        return draw(() -> renderer.drawNative(tiles, track.bgScroll(), track.vScroll(), pair.players(), frame));
    }

    private static void assertNativeSpillPixels(CompetitiveHalfpipeRenderer renderer, CompetitiveHalfpipeRomData data,
            CompetitiveSpecialStagePlayerAccess.Pair pair, CompetitiveHalfpipeRenderFrame frame,
            Palette[] source, byte[] actual) {
        List<SpriteSatEntry> geometry = renderer.nativeSatEntries(pair.players(), frame);
        List<CompetitiveHalfpipeRenderFrame.Draw> particles = frame.draws().stream()
                .filter(event -> event.type() == CompetitiveHalfpipeRenderFrame.Type.SPILL).toList();
        List<SpriteSatEntry> pieces = geometry.stream().filter(entry -> "spill".equals(entry.piece().debugSource())).toList();
        assertEquals(2, particles.size()); assertEquals(2, pieces.size());
        Pattern[] art = data.ringPatterns();
        int compared = 0;
        for (int particle = 0; particle < particles.size(); particle++) {
            var event = particles.get(particle); var piece = pieces.get(particle);
            int nativeMap = 0x3632A + data.rom().readS16BE(0x3632A + event.mappingFrame() * 2);
            assertEquals(1, data.rom().readU16BE(nativeMap));
            int size = data.rom().readU8(nativeMap + 3);
            int nativeTile = (0x322 + data.rom().readU16BE(nativeMap + 4)) & 0x7FF;
            assertEquals(0x322, event.nativeArt()); assertEquals(3, event.palette());
            assertEquals(nativeTile, piece.piece().rawTileWordLow11());
            assertEquals((size >>> 2 & 3) + 1, piece.piece().widthTiles());
            assertEquals((size & 3) + 1, piece.piece().heightTiles());
            assertEquals(event.bucket(), piece.priorityBucket());
            assertEquals(SpriteSatEntry.PixelOperation.NORMAL, piece.pixelOperation());
            int ordinal = geometry.indexOf(piece);
            for (int y = Math.max(0, piece.piece().y()); y < Math.min(224, piece.piece().y() + piece.piece().heightTiles() * 8); y++) {
                for (int x = Math.max(32, piece.piece().x()); x < Math.min(288, piece.piece().x() + piece.piece().widthTiles() * 8); x++) {
                    final int px = x, py = y;
                    if (geometry.subList(0, ordinal).stream().anyMatch(front -> inside(front, px, py))) continue;
                    int localX = x - piece.piece().x(), localY = y - piece.piece().y();
                    if (piece.piece().hFlip()) localX = piece.piece().widthTiles() * 8 - 1 - localX;
                    if (piece.piece().vFlip()) localY = piece.piece().heightTiles() * 8 - 1 - localY;
                    int color = art[nativeTile - 0x322 + localX / 8 * piece.piece().heightTiles() + localY / 8]
                            .getPixel(localX & 7, localY & 7);
                    if (color == 0) continue;
                    Palette.Color expected = source[3].colors[color];
                    assertEquals(DisplayColorConverter.toPackedRgb(expected.r & 255, expected.g & 255,
                            expected.b & 255, DisplayColorProfile.RAW_RGB, DisplayColorExpansion.FULL), rgb(actual, x, y),
                            "actual native Obj5B ring texel at " + x + "," + y);
                    compared++;
                }
            }
        }
        assertTrue(compared >= 16, "visible native particle art must reach the actual framebuffer");
    }

    private static Map<Integer, Integer> intensityPairs(Palette[] source, DisplayColorProfile profile,
                                                       DisplayColorExpansion expansion) {
        var result = new HashMap<Integer, Integer>();
        for (Palette line : source) for (Palette.Color color : line.colors) {
            int r = color.r & 255, g = color.g & 255, b = color.b & 255;
            result.put(DisplayColorConverter.toPackedRgb(r, g, b, profile, expansion),
                    DisplayColorConverter.toNativeShadowPackedRgb((r * 7 + 127) / 255,
                            (g * 7 + 127) / 255, (b * 7 + 127) / 255, profile, expansion));
        }
        return result;
    }
    private static boolean inside(SpriteSatEntry entry, int x, int y) {
        return x >= entry.piece().x() && x < entry.piece().x() + entry.piece().widthTiles() * 8
                && y >= entry.piece().y() && y < entry.piece().y() + entry.piece().heightTiles() * 8;
    }
    private static int shadowColor(CompetitiveHalfpipeRomData data, SpriteSatEntry entry, int x, int y) {
        int nativeTile = entry.piece().rawTileWordLow11();
        int base = nativeTile >= 0x29C ? 0x29C : nativeTile >= 0x262 ? 0x262 : 0x23C;
        Pattern[] art = base == 0x23C ? data.shadowHorizPatterns()
                : base == 0x262 ? data.shadowDiagPatterns() : data.shadowVertPatterns();
        int localX = x - entry.piece().x(), localY = y - entry.piece().y();
        if (entry.piece().hFlip()) localX = entry.piece().widthTiles() * 8 - 1 - localX;
        if (entry.piece().vFlip()) localY = entry.piece().heightTiles() * 8 - 1 - localY;
        return art[nativeTile - base + localX / 8 * entry.piece().heightTiles() + localY / 8].getPixel(localX & 7, localY & 7);
    }
    private static int rgb(byte[] rgba, int x, int y) {
        int index = ((223 - y) * 320 + x) * 4;
        return (rgba[index] & 255) << 16 | (rgba[index + 1] & 255) << 8 | rgba[index + 2] & 255;
    }

    private static byte[] draw(Sonic2CompetitiveHalfpipe halfpipe) {
        return draw(halfpipe::draw);
    }
    private static byte[] draw(Runnable draw) {
        glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        draw.run();
        EngineServices.current().graphics().flushWithCamera((short) 0, (short) 0, (short) 320, (short) 224);
        glFinish();
        ByteBuffer buffer = BufferUtils.createByteBuffer(320 * 224 * 4);
        glReadPixels(0, 0, 320, 224, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        byte[] pixels = new byte[buffer.remaining()]; buffer.get(pixels); return pixels;
    }
    private static int colors(byte[] rgba) {
        var colors = new HashSet<Integer>();
        for (int i = 0; i < rgba.length; i += 4)
            colors.add((rgba[i] & 255) << 16 | (rgba[i + 1] & 255) << 8 | rgba[i + 2] & 255);
        return colors.size();
    }
    private static void assertBlackMargins(byte[] rgba) {
        for (int y = 0; y < 224; y++) for (int x : new int[]{0, 31, 288, 319}) {
            int index = (y * 320 + x) * 4;
            assertEquals(0, (rgba[index] & 255) | (rgba[index + 1] & 255) | (rgba[index + 2] & 255),
                    "native H32 clipping at " + x + "," + y);
        }
    }
    private static void png(byte[] rgba, String name) throws Exception {
        var image = new BufferedImage(320, 224, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 224; y++) for (int x = 0; x < 320; x++) {
            int index = ((223 - y) * 320 + x) * 4;
            image.setRGB(x, y, 0xFF000000 | (rgba[index] & 255) << 16
                    | (rgba[index + 1] & 255) << 8 | rgba[index + 2] & 255);
        }
        Path path = Path.of("target", "competitive-halfpipe-egl", name);
        Files.createDirectories(path.getParent()); ImageIO.write(image, "png", path.toFile());
    }
    private static final class AudioOwner implements AutoCloseable {
        final AudioManager audio = AudioManager.createSilent();
        @Override public void close() { audio.destroy(); }
    }
}
