package com.openggf.sprites.playable;

import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Map;
import com.openggf.level.SolidTile;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Native physics, deterministic replay and service isolation without graphics/devices. */
class TestPlayablePuppet {
    private Sonic2GameModule module;

    @BeforeEach void setup() {
        module = new Sonic2GameModule();
        TestEnvironment.configureGameModuleFixture(module);
    }

    @AfterEach void cleanup() {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
    }

    @Test void explicitCameraRestoreKeepsItsPrivateOwnerAndSavedCoordinates() {
        var live = new Sonic("sonic", (short) 10, (short) 20);
        GameServices.sprites().addSprite(live);
        var privateActor = new Sonic("private", (short) 100, (short) 200);
        var camera = new com.openggf.camera.Camera(GameServices.configuration());
        camera.setFocusedSprite(privateActor);
        camera.setX((short) 321); camera.setY((short) 432);
        var checkpoint = camera.capture();
        camera.setFocusedSprite(live);
        camera.restore(checkpoint, privateActor);
        assertSame(privateActor, camera.getFocusedSprite());
        assertEquals(checkpoint, camera.capture(), "explicit focus must not overwrite saved camera coordinates");
        camera.restore(checkpoint);
        assertSame(live, camera.getFocusedSprite(), "ordinary native restore retains its roster-rebinding behavior");
    }

    @Test void unsnappedAnchorIsTheExactNativeCentreAndConstructionNeedsNoLiveSession() {
        SessionManager.clear();
        try (var puppet = puppet(terrain(0xF001, 256, -1), 123, 101, false, 0)) {
            assertEquals(123, puppet.sample().x()); assertEquals(101, puppet.sample().y());
            assertTrue(puppet.sample().airborne());
            puppet.tick(false, false, false, 0);
            assertNull(SessionManager.getCurrentWorldSession());
        }
    }

    @Test void midSlopeCheckpointRestoresNativeCollisionAngleRegisters() {
        Level terrain = terrain(0xF001, 256, -1);
        // A continuous 1:2 slope enters in two half-height tiles. An abrupt
        // 45-degree tile triggers native cardinal-angle snapping instead.
        for (int half = 0; half < 2; half++) {
            byte[] heights = new byte[16], widths = new byte[16];
            for (int x = 0; x < 16; x++) heights[x] = (byte) (half * 8 + x / 2 + 1);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                if (y >= 16 - heights[x]) widths[y]++;
            var slope = new SolidTile(2 + half, heights, widths, (byte) 0xF0);
            var chunk = new Chunk(); chunk.setSolidTileIndex(2 + half); chunk.setSolidTileAltIndex(2 + half);
            when(terrain.getChunk(2 + half)).thenReturn(chunk);
            when(terrain.getSolidTile(2 + half)).thenReturn(slope);
        }
        when(terrain.getChunkCount()).thenReturn(4);
        for (int x = 256; x < 1024; x += 16) {
            var block = terrain.getBlock(8 + x / 128);
            block.setChunkDesc((x % 128) / 16, 7,
                    new ChunkDesc(x == 256 ? 0xF002 : x == 272 ? 0xF003 : 0xF001));
        }
        try (var puppet = puppet(terrain, 210, 230, true, 0)) {
            int frame = 0;
            while (frame < 80 && puppet.sample().angle() == 0) puppet.tick(false, true, false, frame++);
            assertNotEquals(0, puppet.sample().angle(), "fixture must reach its native ramp");
            var checkpoint = puppet.capture();
            List<PlayablePuppet.Observation> expected = new ArrayList<>();
            for (int i = 0; i < 35; i++) { puppet.tick(false, true, i == 10, frame + i); expected.add(puppet.sample()); }
            puppet.restore(checkpoint);
            for (int i = 0; i < 35; i++) {
                puppet.tick(false, true, i == 10, frame + i);
                assertEquals(expected.get(i), puppet.sample(), "slope replay " + i);
            }
        }
    }

    @Test void buttonsMatchAnOrdinaryNativePlayableTickIncludingReleaseAndLanding() {
        Level terrain = terrain(0xF001, 256, -1);
        GameServices.level().initializeCollisionOnly(terrain, 0, 0);
        var ordinary = new Sonic("sonic", (short) 0, (short) 0);
        ordinary.setAnimationFrameCount(0);
        GameServices.sprites().addSprite(ordinary);
        var camera = GameServices.camera();
        camera.setFocusedSprite(ordinary);
        camera.setMinX((short) terrain.getMinX()); camera.setMaxX((short) terrain.getMaxX());
        camera.setMinY((short) terrain.getMinY()); camera.setMaxY((short) terrain.getMaxY());
        camera.setLevelStarted(false);
        try (var puppet = PlayablePuppet.createNativeWorldProbe(terrain, module, 0, 0, module,
                "sonic", SpriteArtSet.EMPTY, 150, 230, 0, true, false)) {
            NativePositionOps.writeXPosResetSubpixel(ordinary, (int) puppet.sample().x());
            NativePositionOps.writeYPosResetSubpixel(ordinary, (int) puppet.sample().y());
            ordinary.setAir(puppet.sample().airborne());
            boolean jumped = false, landed = false;
            for (int frame = 0; frame < 110; frame++) {
                boolean right = frame < 60, left = frame >= 75 && frame < 95;
                boolean jump = frame >= 18 && frame < 24;
                puppet.tick(left, right, jump, frame);
                ordinary.captureOnObjectAtFrameStart();
                ordinary.setJumpInputPressed(jump);
                ordinary.setDirectionalInputPressed(false, false, left, right);
                ordinary.setLogicalInputState(false, false, left, right, jump);
                GameServices.sprites().setFrameCounter(frame);
                GameServices.timers().update();
                SpriteManager.tickPlayablePhysics(ordinary, false, false, left, right, jump,
                        false, false, false, GameServices.level(), frame);
                var pose = puppet.sample();
                assertEquals(ordinary.getCentreX() + ordinary.getXSubpixelRaw() / 65536.0, pose.x(), "x at " + frame);
                assertEquals(ordinary.getCentreY() + ordinary.getYSubpixelRaw() / 65536.0, pose.y(), "y at " + frame);
                assertEquals(ordinary.getXSpeed(), pose.xSpeed(), "x velocity at " + frame);
                assertEquals(ordinary.getYSpeed(), pose.ySpeed(), "y velocity at " + frame);
                assertEquals(ordinary.getGSpeed(), pose.groundSpeed());
                assertEquals(ordinary.getAir(), pose.airborne());
                assertEquals(ordinary.getAngle() & 255, pose.angle());
                assertEquals(ordinary.getAnimationId(), pose.animation());
                assertEquals(ordinary.getMappingFrame(), pose.mappingFrame());
                jumped |= pose.airborne();
                landed |= jumped && pose.onGround();
            }
            assertTrue(jumped); assertTrue(landed);
        }
    }

    @Test void shortReleaseMakesNativeJumpLowerAndActorLandsWithoutPositionWrites() {
        Level terrain = terrain(0xF001, 256, -1);
        try (var tap = puppet(terrain, 180, 230, true, 0);
             var held = puppet(terrain, 180, 230, true, 0)) {
            double tapApex = tap.sample().y(), heldApex = held.sample().y();
            for (int frame = 0; frame < 110; frame++) {
                tap.tick(false, false, frame == 0, frame);
                held.tick(false, false, frame < 25, frame);
                tapApex = Math.min(tapApex, tap.sample().y());
                heldApex = Math.min(heldApex, held.sample().y());
            }
            assertTrue(heldApex < tapApex - 20, "jump-release latch: tap=" + tapApex + " held=" + heldApex + " final=" + tap.sample());
            assertTrue(tap.sample().onGround()); assertTrue(held.sample().onGround());
            // Native collision uses add.w/sub.w on the pixel word and retains
            // each jump's subpixel fraction; both feet rest on the same pixel.
            assertEquals(255, (int) Math.floor(tap.sample().y()) + tap.sample().yRadius());
            assertEquals(255, (int) Math.floor(held.sample().y()) + held.sample().yRadius());
        }
    }

    @Test void rewindRestoresJumpLatchBodyAndTailAnimationAndRejectsForeignCheckpoints() {
        Level terrain = terrain(0xF001, 256, -1);
        var actorModule = new Sonic3kGameModule();
        try (var puppet = PlayablePuppet.createNativeWorldProbe(terrain, module, 0, 0, actorModule, "tails",
                SpriteArtSet.EMPTY, 200, 235, 0, true, true);
             var other = puppet(terrain, 150, 230, true, 0)) {
            assertEquals(actorModule.getPhysicsProvider().getProfile("tails").runAccel(), puppet.sample().runAccel());
            for (int frame = 0; frame < 8; frame++) puppet.tick(false, true, frame >= 3, frame);
            var checkpoint = puppet.capture();
            List<PlayablePuppet.Observation> expected = new ArrayList<>();
            for (int frame = 8; frame < 45; frame++) {
                puppet.tick(frame > 30, frame < 20, frame < 15, frame);
                expected.add(puppet.sample());
            }
            puppet.restore(checkpoint);
            for (int frame = 8; frame < 45; frame++) {
                puppet.tick(frame > 30, frame < 20, frame < 15, frame);
                assertEquals(expected.get(frame - 8), puppet.sample(), "restored native frame " + frame);
            }
            assertThrows(IllegalArgumentException.class, () -> other.restore(checkpoint));
        }
    }

    @Test void privateWorldIgnoresLegacySensorOverrideAndNeverMutatesTheLiveVisit() {
        var world = SessionManager.getCurrentWorldSession();
        var level = GameServices.level(); var camera = GameServices.camera();
        var timers = GameServices.timers(); var audio = GameServices.audio();
        var cameraBefore = camera.capture(); var stateBefore = GameServices.gameState().capture();
        var audioBefore = audio.captureLogicalSnapshot();
        int frameBefore = GameServices.sprites().getFrameCounter();
        long seedBefore = GameServices.rng().getSeed();
        GroundSensor.setLevelManager(mock(LevelManager.class));
        try (var puppet = puppet(terrain(0xF001, 256, -1), 200, 230, true, 0)) {
            var checkpoint = puppet.capture();
            for (int frame = 0; frame < 80; frame++) puppet.tick(false, true, frame < 8, frame);
            puppet.restore(checkpoint);
            assertTrue(puppet.sample().onGround(), puppet.sample().toString());
        }
        assertSame(world, SessionManager.getCurrentWorldSession());
        assertSame(level, GameServices.level()); assertSame(camera, GameServices.camera());
        assertSame(timers, GameServices.timers()); assertSame(audio, GameServices.audio());
        assertEquals(cameraBefore, camera.capture());
        assertEquals(stateBefore.score(), GameServices.gameState().capture().score());
        assertEquals(audioBefore.commandEntryCount(), audio.captureLogicalSnapshot().commandEntryCount());
        assertEquals(frameBefore, GameServices.sprites().getFrameCounter());
        assertEquals(seedBefore, GameServices.rng().getSeed());
    }

    @Test void nativeAttachmentRespectsNearbyStackedRoutesTopOnlySolidityAndAlternatePath() {
        Level stacked = terrain(0xF001, 256, 128);
        try (var low = puppet(stacked, 200, 224, true, 0);
             var high = puppet(stacked, 200, 96, true, 0);
             var topOnly = puppet(terrain(0x1001, 256, -1), 200, 230, true, 0);
             var alternate = puppet(terrain(0xC001, 256, -1), 200, 230, true, 1)) {
            assertEquals(128, low.sample().y() - high.sample().y());
            assertEquals(low.sample().y(), topOnly.sample().y());
            assertEquals(low.sample().y(), alternate.sample().y());
            for (int frame = 0; frame < 15; frame++) {
                low.tick(false, true, false, frame); alternate.tick(false, true, false, frame);
                assertTrue(low.sample().onGround()); assertEquals(low.sample().y(), alternate.sample().y());
            }
        }
        try (var missing = entry(terrain(0x2001, 256, -1), 200, 230, true, 0)) { assertTrue(missing.sample().airborne()); }
        try (var missing = entry(terrain(0xC001, 256, -1), 200, 230, true, 0)) { assertTrue(missing.sample().airborne()); }
        try (var missing = entry(stacked, 200, -100, true, 0)) { assertTrue(missing.sample().airborne()); }
    }

    @Test void nativeWallCollisionPreventsTheActorWalkingThroughASetObstacle() {
        Level terrain = terrain(0xF001, 256, -1);
        // At x=320 the solid wall extends above the actor; walking remains native input.
        for (int y = 192; y < 256; y += 16) {
            var block = terrain.getBlock((y / 128) * 8 + 320 / 128);
            block.setChunkDesc((320 % 128) / 16, (y % 128) / 16, new ChunkDesc(0xF001));
        }
        try (var puppet = puppet(terrain, 200, 230, true, 0)) {
            for (int frame = 0; frame < 130; frame++) puppet.tick(false, true, false, frame);
            assertTrue(puppet.sample().x() < 320 - puppet.sample().xRadius());
            double blockedX = Math.floor(puppet.sample().x());
            for (int frame = 130; frame < 145; frame++) {
                puppet.tick(false, true, false, frame);
                assertEquals(blockedX, Math.floor(puppet.sample().x()), "native wall must hold the actor in place");
            }
            assertTrue(puppet.sample().onGround());
        }
    }

    private PlayablePuppet puppet(Level level, int x, int y, boolean snap, int path) {
        var probe = entry(level, x, y, snap, path);
        // Let near-floor fixtures land through ordinary native physics before testing
        // an already grounded jump. Missing terrain remains airborne; no search is added.
        if (snap) for (int frame = 0; frame < 60 && probe.sample().airborne(); frame++)
            probe.tick(false, false, false, frame);
        return probe;
    }

    private PlayablePuppet entry(Level level, int x, int y, boolean snap, int path) {
        return PlayablePuppet.createNativeWorldProbe(level, module, 0, 0, module, "sonic", SpriteArtSet.EMPTY,
                x, y, path, snap, false);
    }

    /** Full native block/chunk/height geometry; only artwork and unused level metadata are stubbed. */
    private static Level terrain(int descriptor, int floorY, int extraFloorY) {
        var map = new Map(2, 8, 4);
        var blocks = new Block[32];
        for (int by = 0; by < 4; by++) for (int bx = 0; bx < 8; bx++) {
            int index = by * 8 + bx;
            map.setValue(0, bx, by, (byte) index); blocks[index] = new Block();
            for (int cy = 0; cy < 8; cy++) for (int cx = 0; cx < 8; cx++) {
                int y = by * 128 + cy * 16;
                if (y == floorY || y == extraFloorY) blocks[index].setChunkDesc(cx, cy, new ChunkDesc(descriptor));
            }
        }
        var chunk = new Chunk(); chunk.setSolidTileIndex(1); chunk.setSolidTileAltIndex(1);
        byte[] heights = new byte[16]; Arrays.fill(heights, (byte) 16);
        var solid = new SolidTile(1, heights, heights.clone(), (byte) 0);
        Level level = mock(Level.class);
        when(level.getMap()).thenReturn(map);
        when(level.getBlockPixelSize()).thenReturn(128); when(level.getChunksPerBlockSide()).thenReturn(8);
        when(level.getLayerWidthBlocks(anyInt())).thenReturn(8); when(level.getLayerHeightBlocks(anyInt())).thenReturn(4);
        when(level.getBlockCount()).thenReturn(blocks.length);
        when(level.getBlock(anyInt())).thenAnswer(call -> blocks[(int) call.getArgument(0)]);
        when(level.getChunk(1)).thenReturn(chunk); when(level.getChunkCount()).thenReturn(2);
        when(level.getSolidTile(1)).thenReturn(solid);
        when(level.getMaxX()).thenReturn(704); when(level.getMaxY()).thenReturn(288);
        return level;
    }
}
