package com.openggf.game.presentation;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.Pattern;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestGolfScenePresentation {
    private SharedLevel bootstrap;

    @AfterEach void reset() {
        if (bootstrap != null) bootstrap.dispose();
        SonicConfigurationService.getInstance().clearSessionOverrides();
    }

    @Test void codecRoundTripOwnsAllArraysAndCarriesOnlyRecipeReferences() throws Exception {
        int[] palette = palette();
        var tile = tile(ScenePresentationFrame.Layer.OBJECT, "object/bridge", 37, false);
        var primitive = new ScenePresentationFrame.Primitive(1, ScenePresentationFrame.PrimitiveKind.RECTANGLE, 0,
                List.of(new ScenePresentationFrame.Vertex(1, 2, 4, 5, 0xFFFF00FF)));
        var tiles = new ArrayList<>(List.of(tile));
        var source = new ScenePresentationFrame(99, 1, 320, 224, 8192, 400, 0xFF123456,
                palette, tiles, List.of(primitive));
        palette[1] = 0; tiles.clear(); source.paletteArgb()[1] = 0;
        byte[] encoded = SceneFrameCodec.encode(source);
        var decoded = SceneFrameCodec.decode(encoded);
        assertEquals(source.tiles(), decoded.tiles());
        assertEquals(source.primitives(), decoded.primitives());
        assertArrayEquals(source.paletteArgb(), decoded.paletteArgb());
        assertEquals(99, decoded.revision()); assertEquals(8192, decoded.cameraX());
        assertTrue(encoded.length < 1024);
        assertTrue(new String(encoded, java.nio.charset.StandardCharsets.ISO_8859_1).contains("object/bridge"));
        assertThrows(UnsupportedOperationException.class, () -> decoded.tiles().clear());
    }

    @Test void unsupportedVertexMethodsAreRejectedBeforeAFrameCanBePublished() {
        var vertices = List.of(new ScenePresentationFrame.Vertex(0, 0, 0, 0, 0xFFFFFFFF));
        for (int method = 4; method <= 7; method++) {
            int unsupported = method;
            assertThrows(IllegalArgumentException.class, () -> new ScenePresentationFrame.Primitive(
                    0, ScenePresentationFrame.PrimitiveKind.VERTEX, unsupported, vertices));
        }
        for (int method = 0; method <= 3; method++) {
            var primitive = new ScenePresentationFrame.Primitive(0,
                    ScenePresentationFrame.PrimitiveKind.VERTEX, method, vertices);
            var source = new ScenePresentationFrame(1, 0, 320, 224, 0, 0, 0,
                    palette(), List.of(), List.of(primitive));
            assertDoesNotThrow(() -> SceneCompositor.compose(source, new RomSceneArtCatalog(), 0, 0));
        }
    }

    @Test void decoderRejectsOversizeMalformedCountsUnknownVersionsAndTrailingBytes() throws Exception {
        byte[] valid = SceneFrameCodec.encode(frame(List.of()));
        assertThrows(IOException.class, () -> SceneFrameCodec.decode(new byte[SceneFrameCodec.MAX_BYTES + 1]));
        assertThrows(IOException.class, () -> SceneFrameCodec.decode(Arrays.copyOf(valid, valid.length - 1)));
        assertThrows(IOException.class, () -> SceneFrameCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
        byte[] wrongSchema = valid.clone(); wrongSchema[5] = 99;
        assertThrows(IOException.class, () -> SceneFrameCodec.decode(wrongSchema));
        byte[] count = valid.clone();
        int tileCountOffset = 4 + 2 + 8 + 1 + 2 + 2 + 4 + 4 + 4 + 2 + 64 * 4 + 2;
        java.nio.ByteBuffer.wrap(count).putInt(tileCountOffset, Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> SceneFrameCodec.decode(count));
    }

    @Test void localCatalogDoesNotAcceptHostArtAndPriorityMatchesNativeOcclusion() {
        var art = new RomSceneArtCatalog();
        art.addRecipe("test/terrain", new Pattern[]{solid(1)});
        art.addRecipe("test/sprite", new Pattern[]{solid(2)});
        var terrain = tile(ScenePresentationFrame.Layer.FOREGROUND, "test/terrain", 0, true);
        var low = tile(ScenePresentationFrame.Layer.PLAYER, "test/sprite", 0, false);
        var high = tile(ScenePresentationFrame.Layer.PLAYER, "test/sprite", 0, true);
        assertEquals(0xFFFF0000, SceneCompositor.compose(frame(List.of(terrain, low)), art, 0, 0).argb()[0]);
        assertEquals(0xFF0000FF, SceneCompositor.compose(frame(List.of(terrain, high)), art, 0, 0).argb()[0]);
        assertThrows(IllegalArgumentException.class, () -> SceneCompositor.compose(
                frame(List.of(tile(ScenePresentationFrame.Layer.OBJECT, "host/invented-art", 0, false))), art, 0, 0));
        var mutable = solid(1); art.addRecipe("test/immutable", new Pattern[]{mutable}); mutable.clear();
        assertEquals(0xFFFF0000, SceneCompositor.compose(frame(List.of(
                tile(ScenePresentationFrame.Layer.OBJECT, "test/immutable", 0, false))), art, 0, 0).argb()[0]);
    }

    @Test void staleFramesViewportMismatchAndUnknownArtLeaveTheLastGoodViewIntact() {
        var art = new RomSceneArtCatalog(); art.addRecipe("test/art", new Pattern[]{solid(1)});
        var presenter = new RomSceneViewPresenter(art, new com.openggf.graphics.GraphicsManager(), 320, 224);
        var first = frame(List.of(tile(ScenePresentationFrame.Layer.OBJECT, "test/art", 0, false)));
        assertTrue(presenter.accept(first)); assertFalse(presenter.accept(first));
        var bad = new ScenePresentationFrame(2, 0, 400, 224, 0, 0, 0, palette(), first.tiles(), List.of());
        assertThrows(IllegalArgumentException.class, () -> presenter.accept(bad));
        var unknown = new ScenePresentationFrame(2, 0, 320, 224, 0, 0, 0, palette(),
                List.of(tile(ScenePresentationFrame.Layer.OBJECT, "host/missing", 0, false)), List.of());
        assertThrows(IllegalArgumentException.class, () -> presenter.accept(unknown));
        assertEquals(1, presenter.revision()); presenter.close();
        assertThrows(IllegalStateException.class, () -> presenter.accept(first));
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @CsvSource({"0,320,sonic", "0,800,tails", "1,320,tails", "1,800,sonic"})
    void independentRomConsumersRenderCompleteSceneWithoutAdvancingEitherCourse(
            int act, int width, String character) throws Exception {
        var fixture = launch(act, width, character); fixture.stepIdleFrames(20);
        var level = GameServices.level();
        var visiblePlacement = level.getCurrentLevel().getObjects().stream().filter(s -> s.x() < 0x2000).findFirst().orElseThrow();
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), visiblePlacement.x());
        NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), Math.max(40, visiblePlacement.y() - 32));
        fixture.camera().setX((short) Math.max(0, visiblePlacement.x() - 160));
        fixture.camera().setY((short) Math.max(0, visiblePlacement.y() - 96));
        fixture.stepIdleFrames(3);
        var first = level.captureScene(1, PlayerPresentationPose.nativePose());
        for (int i = 0; i < 40 && first.tiles().stream().noneMatch(t -> t.layer() == ScenePresentationFrame.Layer.PLAYER); i++) {
            fixture.stepIdleFrames(1);
            first = level.captureScene(1, PlayerPresentationPose.nativePose());
        }
        assertTrue(first.tiles().stream().anyMatch(t -> t.layer() == ScenePresentationFrame.Layer.PLAYER));
        assertTrue(first.tiles().stream().anyMatch(t -> t.layer() == ScenePresentationFrame.Layer.OBJECT));
        assertTrue(first.tiles().stream().anyMatch(t -> t.layer() == ScenePresentationFrame.Layer.BACKGROUND));
        try (var presenter = level.createScenePresenter()) {
            assertTrue(presenter.accept(SceneFrameCodec.decode(SceneFrameCodec.encode(first))));
            var expected = presenter.image(0, 0);
            var before = snapshot();
            for (int i = 0; i < 20; i++) {
                presenter.image(i * 7, i * 3); presenter.draw(i, i);
                level.captureScene(2 + i, PlayerPresentationPose.nativePose());
            }
            assertUnchanged(before, snapshot());
            bootstrap.dispose(); bootstrap = null;
            launch(act, width, character);
            var guestBefore = snapshot();
            try (var guest = GameServices.level().createScenePresenter()) {
                assertTrue(guest.accept(SceneFrameCodec.decode(SceneFrameCodec.encode(first))));
                assertArrayEquals(expected.argb(), guest.image(0, 0).argb());
                for (int i = 0; i < 20; i++) guest.image(i * 4, 0);
                assertUnchanged(guestBefore, snapshot());
            }
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @Test void secondActSceneUsesFirstActHeldRomSessionWithoutLoadingOrSteppingGuest() throws Exception {
        var fixture = launch(1, 528, "tails"); fixture.stepIdleFrames(20);
        var host = GameServices.level().captureScene(42,
                new PlayerPresentationPose(PlayerPresentationPose.Kind.SPINDASH, 12, -1));
        int[] expected;
        try (var view = GameServices.level().createScenePresenter()) {
            view.accept(host); expected = view.image(0, 0).argb();
        }
        bootstrap.dispose(); bootstrap = null;
        launch(0, 528, "sonic");
        var guestBefore = snapshot();
        try (var guest = GameServices.level().createScenePresenter()) {
            assertTrue(guest.accept(SceneFrameCodec.decode(SceneFrameCodec.encode(host))));
            assertArrayEquals(expected, guest.image(0, 0).argb());
            for (int i = 0; i < 20; i++) guest.image(i * 4, 0);
            assertEquals(0, GameServices.level().getCurrentAct());
            assertUnchanged(guestBefore, snapshot());
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @Test void guestLocalPoseAndCharacterMonitorArtKeepAcceptedFrameAndWorldImmutable() throws Exception {
        var fixture = launch(0, 352, "tails");
        var level = GameServices.level();
        var monitor = level.getCurrentLevel().getObjects().stream()
                .filter(value -> value.objectId() == 0x26 && (value.subtype() & 15) == 1).findFirst().orElseThrow();
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), monitor.x());
        NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), Math.max(40, monitor.y() - 64));
        fixture.camera().setX((short) Math.max(0, monitor.x() - 160));
        fixture.camera().setY((short) Math.max(0, monitor.y() - 96));
        fixture.sprite().setInvulnerableFrames(1000); fixture.stepIdleFrames(20);
        var host = level.captureScene(101, PlayerPresentationPose.nativePose());
        for (int i = 0; i < 64 && host.tiles().stream().noneMatch(tile -> tile.art().recipe().equals("life/tails")); i++) {
            fixture.stepIdleFrames(1); host = level.captureScene(101, PlayerPresentationPose.nativePose());
        }
        assertTrue(host.tiles().stream().anyMatch(tile -> tile.layer() == ScenePresentationFrame.Layer.OBJECT
                && tile.art().recipe().equals("life/tails")), "Fixture must display the native Tails 1-up face");
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), monitor.x() - 64);
        host = level.captureScene(101, PlayerPresentationPose.nativePose());
        int[] expected;
        try (var view = level.createScenePresenter()) { view.accept(host); expected = view.image(0, 0).argb(); }
        bootstrap.dispose(); bootstrap = null; launch(0, 352, "sonic");
        var before = snapshot();
        byte[] acceptedBytes = SceneFrameCodec.encode(host);
        try (var guest = GameServices.level().createScenePresenter()) {
            guest.accept(SceneFrameCodec.decode(acceptedBytes));
            assertArrayEquals(expected, guest.image(0, 0).argb(), "Tails 1-up icon must not resolve as the local Sonic icon");
            var pose = new ScenePlayerPose("tails", monitor.x(), monitor.y() - 32,
                    new PlayerPresentationPose(PlayerPresentationPose.Kind.DUCK, 0, -1));
            var local = guest.image(0, 0, pose);
            guest.draw(7, 3, pose);
            var charge = new ScenePlayerPose("tails", monitor.x(), monitor.y() - 32,
                    new PlayerPresentationPose(PlayerPresentationPose.Kind.SPINDASH, 10, 1));
            assertFalse(Arrays.equals(local.argb(), guest.image(0, 0, charge).argb()));
            assertFalse(Arrays.equals(expected, local.argb()));
            assertArrayEquals(expected, guest.image(0, 0).argb(), "Local meter pose must not replace the accepted host frame");
            assertArrayEquals(acceptedBytes, SceneFrameCodec.encode(host));
            assertEquals(101, guest.revision());
            assertUnchanged(before, snapshot());
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @ValueSource(ints = {0, 1})
    void everyPlacedEhzObjectKindUsesPortableProductionArt(int act) throws Exception {
        var fixture = launch(act, 800, "sonic");
        var level = GameServices.level();
        var placements = new LinkedHashMap<Integer, com.openggf.level.objects.ObjectSpawn>();
        for (var spawn : level.getCurrentLevel().getObjects()) placements.putIfAbsent(spawn.objectId(), spawn);
        long[] revision = {0}; int objectTiles = 0;
        try (var presenter = level.createScenePresenter()) {
            for (var spawn : placements.values()) {
                if (act == 1 && spawn.x() >= 0x2700) continue;
                NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), spawn.x());
                NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), Math.max(40, spawn.y() - 32));
                fixture.sprite().setInvulnerableFrames(1000);
                fixture.camera().setX((short) Math.max(0, spawn.x() - 160));
                fixture.camera().setY((short) Math.max(0, spawn.y() - 96));
                fixture.stepIdleFrames(3);
                var projected = assertDoesNotThrow(() -> level.captureScene(++revision[0], PlayerPresentationPose.nativePose()),
                        "production EHZ object ID=" + Integer.toHexString(spawn.objectId()));
                assertTrue(presenter.accept(SceneFrameCodec.decode(SceneFrameCodec.encode(projected))));
                presenter.image(0, 0);
                objectTiles += (int) projected.tiles().stream().filter(t -> t.layer() == ScenePresentationFrame.Layer.OBJECT).count();
            }
        }
        assertTrue(placements.size() >= 8); assertTrue(objectTiles > 30);
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @CsvSource({"0,sonic", "0,tails", "1,sonic", "1,tails"})
    void heldDuckChargeDustResolveLocallyWithoutChangingPlayerState(int act, String character) throws Exception {
        var fixture = launch(act, 400, character); fixture.stepIdleFrames(10);
        var level = GameServices.level();
        try (var presenter = level.createScenePresenter()) {
            var before = snapshot();
            var duck = level.captureScene(1, new PlayerPresentationPose(PlayerPresentationPose.Kind.DUCK, 0, -1));
            var dash = level.captureScene(2, new PlayerPresentationPose(PlayerPresentationPose.Kind.SPINDASH, 8, 1));
            var dashNext = level.captureScene(3, new PlayerPresentationPose(PlayerPresentationPose.Kind.SPINDASH, 10, 1));
            assertNotEquals(duck.tiles(), dash.tiles()); assertNotEquals(dash.tiles(), dashNext.tiles());
            presenter.accept(dashNext); presenter.image(0, 0);
            assertUnchanged(before, snapshot());
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @CsvSource({"IDLE,-1", "IDLE,1", "DUCK,-1", "DUCK,1", "SPINDASH,-1", "SPINDASH,1"})
    void heldTailsPosesKeepBodyAndIndependentAppendageWithoutSteppingTheWorld(String kind, int facing) throws Exception {
        var fixture = launch(0, 320, "tails"); fixture.stepIdleFrames(20);
        var level = GameServices.level(); var before = snapshot();
        var pose = new PlayerPresentationPose(PlayerPresentationPose.Kind.valueOf(kind), 8, facing);
        var frame = level.captureScene(101, pose);
        assertTrue(frame.tiles().stream().anyMatch(t -> t.art().recipe().equals("player/tails")), "Tails body");
        assertTrue(frame.tiles().stream().anyMatch(t -> t.art().recipe().equals("tail/tails")), "Tails appendage");
        try (var host = level.createScenePresenter(); var guest = level.createScenePresenter()) {
            host.accept(frame); guest.accept(SceneFrameCodec.decode(SceneFrameCodec.encode(frame)));
            var player = new ScenePlayerPose("tails", fixture.sprite().getRenderCentreX(),
                    fixture.sprite().getRenderCentreY(), pose);
            assertArrayEquals(host.image(0, 0).argb(), guest.image(0, 0, player).argb());
            var later = level.captureScene(102, new PlayerPresentationPose(pose.kind(), 16, facing));
            assertNotEquals(frame.tiles().stream().filter(t -> t.art().recipe().equals("tail/tails")).toList(),
                    later.tiles().stream().filter(t -> t.art().recipe().equals("tail/tails")).toList(),
                    "Only the view clock animates the held appendage");
            if (pose.kind() == PlayerPresentationPose.Kind.DUCK)
                assertEquals(frame.tiles().stream().filter(t -> t.art().recipe().equals("player/tails")).toList(),
                        later.tiles().stream().filter(t -> t.art().recipe().equals("player/tails")).toList(),
                        "The duck body holds its native position while the tail swishes");
        }
        assertUnchanged(before, snapshot());
    }

    @RequiresRom(SonicGame.SONIC_2)
    @Test void heldTailsIdleRepeatsOnlyTheNativeWaitLoopAfterItsIntro() throws Exception {
        var fixture = launch(0, 320, "tails"); fixture.stepIdleFrames(20);
        var level = GameServices.level(); var before = snapshot();
        // s2.asm TailsAni_Wait: 59 mappings, delay 7, $FE,$1C repeats
        // the final 28 mappings (index 31/$05), never the $01 introduction.
        java.util.function.LongFunction<List<ScenePresentationFrame.Tile>> body = tick -> level.captureScene(tick + 1,
                new PlayerPresentationPose(PlayerPresentationPose.Kind.IDLE, tick, 1)).tiles().stream()
                .filter(tile -> tile.art().recipe().equals("player/tails")).toList();
        var firstLoopFrame = body.apply(31L * 8);
        assertNotEquals(body.apply(0), firstLoopFrame, "Wait loop starts at a different ROM mapping from the intro");
        assertEquals(firstLoopFrame, body.apply(59L * 8), "First $FE,$1C loop");
        assertEquals(firstLoopFrame, body.apply((59L + 28) * 8), "Later repeat keeps the same native loop");
        assertEquals(body.apply(47L * 8), body.apply((59L + 16) * 8), "Interior loop frame also advances correctly");
        assertUnchanged(before, snapshot());
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @ValueSource(ints = {-1, 1})
    void heldDuckMatchesNativeDownWithoutRepeatingTheEntryFrame(int facing) throws Exception {
        var fixture = launch(0, 320, "sonic"); fixture.stepIdleFrames(20);
        fixture.sprite().setDirection(facing < 0 ? com.openggf.physics.Direction.LEFT : com.openggf.physics.Direction.RIGHT);
        for (int i = 0; i < 32; i++) fixture.stepFrame(false, true, false, false, false);
        // s2.asm: SonAni_Duck is 5,$4C,$4D,$FE,1, holding $4D after its entry frame.
        assertTrue(fixture.sprite().getCrouching());
        assertEquals(0x4D, fixture.sprite().getMappingFrame(), "Native Down must reach the held ROM duck mapping");
        var level = GameServices.level();
        var nativeFrame = level.captureScene(1, PlayerPresentationPose.nativePose());
        byte[] acceptedBytes = SceneFrameCodec.encode(nativeFrame);
        var before = snapshot();
        try (var host = level.createScenePresenter(); var guest = level.createScenePresenter()) {
            host.accept(nativeFrame); guest.accept(SceneFrameCodec.decode(acceptedBytes));
            int[] expected = host.image(0, 0).argb();
            host.accept(level.captureScene(2, new PlayerPresentationPose(PlayerPresentationPose.Kind.DUCK, 0, facing)));
            assertFalse(Arrays.equals(expected, host.image(0, 0).argb()), "Duck must still play its native entry frame once");
            long revision = 2;
            for (long tick : new long[]{6, 7, 11, 12, 18, 24, 60, 120, 1000, Long.MAX_VALUE}) {
                var pose = new PlayerPresentationPose(PlayerPresentationPose.Kind.DUCK, tick, facing);
                host.accept(level.captureScene(++revision, pose));
                assertArrayEquals(expected, host.image(0, 0).argb(), "Host must hold native duck at pose tick " + tick);
                var player = new ScenePlayerPose("sonic", fixture.sprite().getRenderCentreX(),
                        fixture.sprite().getRenderCentreY(), pose);
                assertArrayEquals(expected, guest.image(0, 0, player).argb(), "Guest must hold native duck at pose tick " + tick);
            }
            assertEquals(1, guest.revision());
            assertArrayEquals(acceptedBytes, SceneFrameCodec.encode(nativeFrame));
            assertUnchanged(before, snapshot());
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @CsvSource({"0,320,sonic", "1,800,tails"})
    void locallyComposedSceneMatchesTheProductionFramebuffer(int act, int width, String character) throws Exception {
        var settings = new com.openggf.tools.GameplayCaptureSession.Settings(width, character, "", "off", null, 0x600, 0x2C0);
        try (var session = new com.openggf.tools.GameplayCaptureSession(settings)) {
            session.boot(com.openggf.tests.RomTestUtils.ensureSonic2RomAvailable().toPath(), 0, act, settings);
            for (int i = 0; i < 12; i++) session.step(null);
            var level = GameServices.level(); var graphics = GameServices.graphics();
            var frame = level.captureScene(1, PlayerPresentationPose.nativePose());
            try (var presenter = level.createScenePresenter()) {
                presenter.accept(frame);
                level.setClearColor();
                org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
                level.drawWithRenderOptions(GameServices.sprites(), new com.openggf.level.LevelManager.LevelRenderOptions(
                        true, true, true, false, false, false, false));
                graphics.flush(); org.lwjgl.opengl.GL11.glFinish();
                var nativeImage = com.openggf.graphics.ScreenshotCapture.captureFramebuffer(width, 224);
                var projected = presenter.image(0, 0);
                int[] projectedPixels = projected.argb();
                int mismatches = 0;
                for (int i = 0; i < nativeImage.pixels().length; i++) {
                    if ((nativeImage.pixels()[i] & 0xFFFFFF) != (projectedPixels[i] & 0xFFFFFF)) mismatches++;
                }
                if (mismatches != 0) {
                    var directory = java.nio.file.Path.of("target", "scene-diagnostic");
                    java.nio.file.Files.createDirectories(directory);
                    com.openggf.graphics.ScreenshotCapture.savePNG(nativeImage, directory.resolve("native-" + act + "-" + width + ".png"));
                    com.openggf.graphics.ScreenshotCapture.savePNG(new com.openggf.graphics.RgbaImage(width, 224, projectedPixels),
                            directory.resolve("scene-" + act + "-" + width + ".png"));
                }
                assertEquals(0, mismatches, "same-revision production/projection pixels; act=" + act + ", width=" + width);
                presenter.draw(0, 0); graphics.flush(); org.lwjgl.opengl.GL11.glFinish();
                var guestImage = com.openggf.graphics.ScreenshotCapture.captureFramebuffer(width, 224);
                assertArrayEquals(projected.argb(), guestImage.pixels(), "RGBA presentation must preserve local composition");
                System.out.println("Golf scene act=" + act + " width=" + width + " bytes=" + SceneFrameCodec.encode(frame).length
                        + " tiles=" + frame.tiles().size());
            }
        }
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @CsvSource({"0,320", "0,800", "1,320", "1,800"})
    void terrainEnvelopePreservesNativeCenterCodecAndHeldWorld(int act, int width) throws Exception {
        var fixture = launch(act, width, "sonic"); fixture.stepIdleFrames(20);
        var level = GameServices.level();
        var original = level.captureScene(1, PlayerPresentationPose.nativePose());
        var before = snapshot();
        var extended = level.captureScene(2, PlayerPresentationPose.nativePose(), width / 2, 112);
        var decoded = SceneFrameCodec.decode(SceneFrameCodec.encode(extended));
        assertEquals(width, decoded.width());
        assertEquals(extended.tiles(), decoded.tiles());
        assertTrue(decoded.tiles().stream().anyMatch(t -> t.layer() == ScenePresentationFrame.Layer.FOREGROUND
                && t.x() < 0));
        assertTrue(decoded.tiles().stream().anyMatch(t -> t.layer() == ScenePresentationFrame.Layer.FOREGROUND
                && t.x() >= width));
        try (var presenter = level.createScenePresenter()) {
            presenter.accept(original); var center = presenter.image(0, 0).argb();
            presenter.accept(decoded); assertArrayEquals(center, presenter.image(0, 0).argb());
            presenter.image(width / 2, 112); presenter.image(-width / 2, -112);
        }
        assertThrows(IllegalArgumentException.class, () ->
                level.captureScene(3, PlayerPresentationPose.nativePose(), -1, 0));
        assertThrows(IllegalArgumentException.class, () ->
                level.captureScene(3, PlayerPresentationPose.nativePose(), width + 1, 0));
        assertThrows(IllegalArgumentException.class, () ->
                level.captureScene(3, PlayerPresentationPose.nativePose(), 0, 225));
        assertUnchanged(before, snapshot());
    }

    @RequiresRom(SonicGame.SONIC_2)
    @Test void sceneUsesLiveViewportAfterTitleSelectionWithoutReloadingCourse() throws Exception {
        var fixture = launch(0, 320, "sonic"); fixture.stepIdleFrames(20);
        var config = SonicConfigurationService.getInstance();
        // The fixture deliberately installs a raw pixel-width override. A real
        // title uses derived aspect dimensions; remove that test-only override.
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.TEST_MODE_ENABLED, false);
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "SUPER_32_9");
        config.resolveDisplayAspect();
        fixture.camera().refreshViewportDimensions(config);
        GameServices.graphics().setProjectionWidth(800);
        var before = snapshot();
        var frame = GameServices.level().captureScene(1, PlayerPresentationPose.nativePose());
        assertEquals(800, frame.width());
        try (var presenter = GameServices.level().createScenePresenter()) {
            presenter.accept(frame); assertEquals(800, presenter.image(0, 0).width());
        }
        assertUnchanged(before, snapshot());
    }

    @RequiresRom(SonicGame.SONIC_2)
    @ParameterizedTest @CsvSource({"0,320", "0,800", "1,320", "1,800"})
    void terrainEnvelopeCoversRomEndBandsWithinWireLimit(int act, int width) throws Exception {
        var fixture = launch(act, width, "sonic"); fixture.stepIdleFrames(20);
        var level = GameServices.level();
        var endpoint = level.getGame().loadLevel(act).getObjects().stream()
                .filter(spawn -> spawn.objectId() == 0x0D || spawn.objectId() == 0x3E)
                .max(java.util.Comparator.comparingInt(com.openggf.level.objects.ObjectSpawn::x)).orElseThrow();
        fixture.camera().setX((short) (endpoint.x() - width / 2));
        fixture.camera().setY((short) (endpoint.y() - 160));
        GameServices.parallax().update(0, act, fixture.camera(), level.getFrameCounter(), level.getCurrentLevel());
        var original = level.captureScene(1, PlayerPresentationPose.nativePose());
        var before = snapshot();
        var extended = level.captureScene(2, PlayerPresentationPose.nativePose(), width / 2, 112);
        var wire = SceneFrameCodec.encode(extended);
        assertTrue(wire.length <= SceneFrameCodec.MAX_BYTES);
        assertTrue(extended.tiles().size() < ScenePresentationFrame.MAX_TILES);
        try (var presenter = level.createScenePresenter()) {
            presenter.accept(original); var center = presenter.image(0, 0).argb();
            presenter.accept(SceneFrameCodec.decode(wire));
            assertArrayEquals(center, presenter.image(0, 0).argb());
            for (int dx : new int[]{-width / 2, width / 2})
                for (int dy : new int[]{-112, 112}) presenter.image(dx, dy);
        }
        assertUnchanged(before, snapshot());
    }

    private HeadlessTestFixture launch(int act, int width, String character) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, "NATIVE_4_3"); config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
        bootstrap = SharedLevel.load(SonicGame.SONIC_2, 0, act);
        return HeadlessTestFixture.builder().withSharedLevel(bootstrap).build();
    }
    private CompositeSnapshot snapshot() {
        return com.openggf.game.session.SessionManager.getCurrentGameplayMode().getRewindRegistry().capture();
    }
    private static void assertUnchanged(CompositeSnapshot before, CompositeSnapshot after) {
        assertEquals(before.entries().keySet(), after.entries().keySet());
        for (String key : before.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)).isEmpty(),
                    () -> "Scene rendering changed " + RewindSnapshotDiff.diffKey(key, before.get(key), after.get(key)));
        }
    }
    private static Pattern solid(int index) {
        var pattern = new Pattern();
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) pattern.setPixel(x, y, (byte) index);
        return pattern;
    }
    private static int[] palette() {
        int[] colors = new int[64]; Arrays.fill(colors, 0xFF008800);
        colors[1] = 0xFFFF0000; colors[2] = 0xFF0000FF; return colors;
    }
    private static ScenePresentationFrame.Tile tile(ScenePresentationFrame.Layer layer, String recipe, int index, boolean high) {
        return new ScenePresentationFrame.Tile(layer, new ScenePresentationFrame.ArtReference(recipe, index), 0,
                false, false, high, 0, 0, 8, 8, 0, 8, 15, 255);
    }
    private static ScenePresentationFrame frame(List<ScenePresentationFrame.Tile> tiles) {
        return new ScenePresentationFrame(1, 0, 320, 224, 0, 0, 0xFF000000, palette(), tiles, List.of());
    }
}
