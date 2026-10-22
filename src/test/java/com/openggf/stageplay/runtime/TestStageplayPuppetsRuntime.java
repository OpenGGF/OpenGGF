package com.openggf.stageplay.runtime;

import com.openggf.game.GameModule;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.*;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.PlayablePuppet;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.StageplayTimeline.PuppetStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Real native terrain/body integration; steering supplies buttons, never fabricated trajectories. */
class TestStageplayPuppetsRuntime {
    private final GameModule s2 = new Sonic2GameModule(), s3k = new Sonic3kGameModule();
    private Level terrain;
    @BeforeEach void setup() { TestEnvironment.configureGameModuleFixture(s2); terrain = floor(); }
    @AfterEach void cleanup() { GroundSensor.setLevelManager(null); SessionManager.clear(); }

    private PlayablePuppet body(String game, String character, int x, int y, boolean snap) {
        return PlayablePuppet.create(terrain, s2, 0, 0, game.equals("s2") ? s2 : s3k,
                character, SpriteArtSet.EMPTY, x, y, 0, snap, false);
    }
    private StageplayPuppets simulation() {
        return new StageplayPuppets((doc, set, actor, anchor, track) -> PlayablePuppet.create(terrain,
                set.gameId().equals("s2") ? s2 : s3k, set.zone(), set.act(), actor.gameId().equals("s2") ? s2 : s3k,
                actor.character(), SpriteArtSet.EMPTY, (int) anchor.x(), (int) anchor.y(), track.collisionPath(),
                track.snapToFloor(), anchor.flipX()));
    }
    private static ActorKey key(int frame, int x, int y, boolean visible) {
        return new ActorKey(frame, x, y, 0, -1, false, visible, 1, Interpolation.HOLD);
    }
    private StageplayDocument scene(List<PuppetCommand> commands) {
        return StageplayDocument.empty("s2", 0, 0, 0, 100, 320, 224).withActors(List.of(
                new Actor("hero", "Hero", "s2", "sonic", List.of(key(0, 180, 230, true)))))
                .withPuppets(List.of(new PuppetTrack("hero", true, 0, commands)));
    }
    private static StageplayTimeline.Frame resolve(StageplayPuppets simulation, StageplayDocument doc, int frame) {
        return simulation.resolve(doc, StageplayTimeline.sample(doc, frame));
    }
    private double standingY() { try (var body = body("s2", "sonic", 180, 230, true)) { return body.sample().y(); } }

    @Test void urgencyControlsNativeCruisingAndBothGoalsBrakeToGroundedArrival() {
        double y = standingY();
        var slow = scene(List.of(new PuppetCommand("go", 0, PuppetCommandKind.MOVE_TO, 400, y, .2, 0)));
        var fast = scene(List.of(new PuppetCommand("go", 0, PuppetCommandKind.MOVE_TO, 400, y, 1, 0)));
        try (var a = simulation(); var b = simulation()) {
            double slowMax = 0, fastMax = 0;
            for (int frame = 0; frame < 300; frame++) {
                var low = resolve(a, slow, frame); var high = resolve(b, fast, frame);
                slowMax = Math.max(slowMax, low.puppets().getFirst().velocityX());
                fastMax = Math.max(fastMax, high.puppets().getFirst().velocityX());
                assertFalse(low.puppets().getFirst().airborne());
            }
            assertTrue(slowMax > .5 && slowMax < 2, "low urgency still advances using native inputs");
            assertTrue(fastMax > slowMax + 2);
            for (var result : List.of(resolve(a, slow, 599), resolve(b, fast, 599))) {
                assertEquals(ARRIVED, result.puppets().getFirst().status());
                assertEquals(400, result.actors().getFirst().x(), 4);
                assertEquals(y, result.actors().getFirst().y());
                assertEquals(0, result.puppets().getFirst().groundSpeed(), .25);
            }
        }
    }

    @Test void stopBrakesAndBlockedIntentKeepsTryingWithoutAutomaticJumping() {
        double y = standingY();
        var stopped = scene(List.of(new PuppetCommand("go", 0, PuppetCommandKind.MOVE_TO, 650, y, 1, 0),
                new PuppetCommand("stop", 60, PuppetCommandKind.STOP, 0, 0, 0, 0)));
        try (var sim = simulation()) {
            var running = resolve(sim, stopped, 59);
            assertTrue(running.puppets().getFirst().velocityX() > 1);
            var result = resolve(sim, stopped, 100);
            assertEquals(IDLE, result.puppets().getFirst().status());
            assertEquals("stop", result.puppets().getFirst().commandId());
            assertEquals(0, result.puppets().getFirst().velocityX(), .25);
            assertTrue(result.actors().getFirst().x() < 650);
        }
        for (int yTile = 192; yTile < 256; yTile += 16)
            terrain.getBlock((yTile / 128) * 8 + 2).setChunkDesc(4, (yTile % 128) / 16, new ChunkDesc(0xF001));
        var blocked = scene(List.of(new PuppetCommand("go", 0, PuppetCommandKind.MOVE_TO, 650, y, 1, 0)));
        try (var sim = simulation()) {
            var first = resolve(sim, blocked, 300); var later = resolve(sim, blocked, 400);
            assertEquals(BLOCKED, first.puppets().getFirst().status());
            assertEquals(BLOCKED, later.puppets().getFirst().status());
            assertEquals("go", later.puppets().getFirst().commandId());
            assertTrue(later.actors().getFirst().x() < 312);
            assertFalse(later.puppets().getFirst().airborne());
        }
    }

    @Test void explicitJumpMatchesNativeTicksAndRandomSeekReplaysBodyAndInputLatchExactly() {
        var document = scene(List.of(new PuppetCommand("jump", 10, PuppetCommandKind.JUMP, 0, 0, 0, 12)));
        var expected = new ArrayList<StageplayTimeline.Frame>();
        try (var sim = simulation(); var ordinary = body("s2", "sonic", 180, 230, true)) {
            boolean airborne = false, landed = false;
            for (int frame = 0; frame < 160; frame++) {
                ordinary.tick(false, false, frame >= 10 && frame < 22, frame);
                var sample = resolve(sim, document, frame); expected.add(sample);
                assertEquals(ordinary.sample().y(), sample.actors().getFirst().y());
                assertEquals(ordinary.sample().ySpeed() / 256.0, sample.puppets().getFirst().velocityY());
                assertEquals(ordinary.sample().airborne(), sample.puppets().getFirst().airborne());
                assertEquals(sample, resolve(sim, document, frame), "same-frame drawing must not advance simulation");
                airborne |= ordinary.sample().airborne(); landed |= airborne && ordinary.sample().onGround();
            }
            assertTrue(airborne, "native jump must leave the floor");
            assertTrue(landed, "native gravity and collision must land the actor");
            for (int frame : new int[]{159, 3, 82, 0, 121, 40, 159})
                assertEquals(expected.get(frame), resolve(sim, document, frame), "seek frame " + frame);
        }
    }

    @Test void absentCrossGameCastHasNoBodyUntilItsShotAndEditsResetPhysics() {
        var opening = scene(List.of());
        var hero = new Actor("hero", "Hero", "s2", "sonic", List.of(key(0, 180, 230, true), key(100, 0, 0, false)));
        var guest = new Actor("guest", "Guest", "s3k", "tails", List.of(key(0, 0, 0, false), key(100, 240, 230, true)));
        var document = new StageplayDocument(VERSION, "Two worlds", 200, 320, 224, opening.set(), List.of(hero, guest),
                List.of(opening.camera().getFirst(), new CameraKey(100, 160, 100, 1, Interpolation.HOLD)), List.of(), List.of(),
                List.of(), List.of(new SceneChange("cut", 100, new SetSource("s3k", 0, 0, "", ""), Transition.CUT, 0)),
                List.of(), List.of(), List.of(), List.of(new PuppetTrack("hero", true, 0, List.of()), new PuppetTrack("guest", true, 0, List.of())));
        try (var sim = simulation()) {
            var first = resolve(sim, document, 99);
            assertEquals(INACTIVE, first.puppets().get(1).status());
            var second = resolve(sim, document, 100);
            assertEquals(INACTIVE, second.puppets().getFirst().status());
            assertEquals(IDLE, second.puppets().get(1).status());
            assertEquals("s3k", second.actors().get(1).gameId());
            assertEquals(first, resolve(sim, document, 99));
            var moved = opening.withActors(List.of(new Actor("hero", "Hero", "s2", "sonic", List.of(key(0, 250, 230, true)))));
            assertEquals(250, resolve(sim, moved, 0).actors().getFirst().x());
            assertEquals(first, resolve(sim, document, 99), "undo restores the old simulation inputs");
        }
    }

    /** Geometry matches the native facade's test floor; unused artwork/metadata is stubbed. */
    private static Level floor() {
        var map = new com.openggf.level.Map(2, 8, 4); var blocks = new Block[32];
        for (int by = 0; by < 4; by++) for (int bx = 0; bx < 8; bx++) {
            int index = by * 8 + bx; map.setValue(0, bx, by, (byte) index); blocks[index] = new Block();
            for (int cy = 0; cy < 8; cy++) for (int cx = 0; cx < 8; cx++)
                if (by * 128 + cy * 16 == 256) blocks[index].setChunkDesc(cx, cy, new ChunkDesc(0xF001));
        }
        var chunk = new Chunk(); chunk.setSolidTileIndex(1); chunk.setSolidTileAltIndex(1);
        byte[] heights = new byte[16]; Arrays.fill(heights, (byte) 16);
        var solid = new SolidTile(1, heights, heights.clone(), (byte) 0);
        Level level = mock(Level.class);
        when(level.getMap()).thenReturn(map); when(level.getBlockPixelSize()).thenReturn(128);
        when(level.getChunksPerBlockSide()).thenReturn(8);
        when(level.getLayerWidthBlocks(anyInt())).thenReturn(8); when(level.getLayerHeightBlocks(anyInt())).thenReturn(4);
        when(level.getBlockCount()).thenReturn(blocks.length);
        when(level.getBlock(anyInt())).thenAnswer(call -> blocks[(int) call.getArgument(0)]);
        when(level.getChunk(1)).thenReturn(chunk); when(level.getChunkCount()).thenReturn(2);
        when(level.getSolidTile(1)).thenReturn(solid); when(level.getMaxX()).thenReturn(704); when(level.getMaxY()).thenReturn(288);
        return level;
    }
}
