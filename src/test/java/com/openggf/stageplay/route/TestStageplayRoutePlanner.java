package com.openggf.stageplay.route;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.level.*;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.PlayablePuppet;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.tests.TestEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Native collision search and emitted-intent replay; no fake positional propagation. */
class TestStageplayRoutePlanner {
  private Sonic2GameModule module;

  @BeforeEach
  void setup() {
    module = new Sonic2GameModule();
    TestEnvironment.configureGameModuleFixture(module);
  }

  @AfterEach
  void cleanup() {
    SessionManager.clear();
  }

  @Test
  void walksAndSettlesThroughTheSameMoveToControllerWithoutMovingTheParentWorld() {
    var terrain = terrain();
    var world = SessionManager.getCurrentWorldSession();
    var camera = GameServices.camera().capture();
    var parentAudio = GameServices.audio().captureLogicalSnapshot();
    var request = request(448, 237);
    try (var probe = probe(terrain.level)) {
      var start = probe.capture();
      var result = search(probe, request);
      assertTrue(result.found(), result.message());
      assertTrue(result.commands().stream().anyMatch(c -> c.kind() == PuppetCommandKind.MOVE_TO));
      assertEquals(PuppetCommandKind.STOP, result.commands().getLast().kind());
      probe.restore(start);
      var samples = replay(probe, request, result.commands());
      assertNear(samples.getLast(), request);
      assertEquals(
          0,
          probe.audioLogicalSnapshot().commandEntryCount(),
          "createSilent suppresses native SFX admission");
    }
    assertSame(world, SessionManager.getCurrentWorldSession());
    assertEquals(camera, GameServices.camera().capture());
    assertTrue(
        RewindSnapshotDiff.keyEquals(
            "parent-audio", parentAudio, GameServices.audio().captureLogicalSnapshot()));
  }

  @Test
  void nativeWorldProbePreservesEntryAirAndControllerEdgesWithoutChangingDetachedSnap() {
    var terrain = terrain().level;
    try (var detached = probe(terrain);
        var nativeEntry =
            PlayablePuppet.createNativeWorldProbe(
                terrain,
                module,
                0,
                0,
                module,
                "sonic",
                SpriteArtSet.EMPTY,
                224,
                237,
                0,
                true,
                false)) {
      assertTrue(detached.sample().onGround(), "Existing detached snap behavior stays attached");
      assertTrue(
          nativeEntry.sample().airborne(), "Native world entry leaves the airborne status set");
      var entry = nativeEntry.capture();
      detached.tick(false, false, true, 0);
      nativeEntry.tick(false, false, true, 0);
      assertTrue(
          detached.sample().airborne(), "Detached grounded entry accepts its first jump edge");
      assertTrue(
          nativeEntry.sample().ySpeed() >= 0, "Airborne entry cannot invent an upward jump impulse");
      var first = nativeEntry.sample();
      nativeEntry.restore(entry);
      nativeEntry.tick(false, false, true, 0);
      assertEquals(first, nativeEntry.sample(), "Entry air/input state is rewindable");
      int frame = 1;
      while (nativeEntry.sample().airborne() && frame < 30)
        nativeEntry.tick(false, false, true, frame++);
      assertTrue(nativeEntry.sample().onGround(), "The held entry edge must allow native landing");
      nativeEntry.tick(false, false, false, frame++);
      nativeEntry.tick(false, false, true, frame);
      assertTrue(
          nativeEntry.sample().airborne(), "A fresh native jump edge after landing can take off");
    }
  }

  @Test
  void nativeJumpIsRequiredToCrossActualMissingCollisionTiles() {
    var terrain = terrain();
    // An empty 64px span is walkable under native falling/floor-reacquisition
    // rules. Use a genuine 128px gap ending at a 32px raised, all-sided ledge.
    for (int column = 20; column <= 27; column++) terrain.put(column, 16, 0);
    for (int column = 28; column < 64; column++) {
      terrain.put(column, 14, 0xF001);
      terrain.put(column, 15, 0xF001);
    }
    var request = request(512, 205);
    try (var probe = probe(terrain.level)) {
      var start = probe.capture();
      var walking =
          replay(
              probe,
              request,
              List.of(
                  new PuppetCommand(
                      "walk-only",
                      0,
                      PuppetCommandKind.MOVE_TO,
                      request.targetX(),
                      request.targetY(),
                      1,
                      0)));
      assertTrue(
          walking.stream().anyMatch(p -> p.airborne() && p.x() >= 320),
          "The native walking pilot must encounter the actual gap");
      assertFalse(
          walking.stream()
              .anyMatch(
                  p ->
                      p.onGround()
                          && Math.abs(p.x() - request.targetX()) <= 6
                          && Math.abs(p.y() - request.targetY()) <= 12),
          "A no-JUMP native input pilot must not reach the raised destination");
      probe.restore(start);
      var result = search(probe, request);
      assertTrue(result.found(), result.message());
      assertTrue(
          result.commands().stream().anyMatch(c -> c.kind() == PuppetCommandKind.JUMP),
          "The raised destination across the gap requires an actual native jump");
      probe.restore(start);
      var samples = replay(probe, request, result.commands());
      assertTrue(samples.stream().anyMatch(p -> p.airborne() && p.x() >= 320 && p.x() <= 448));
      assertNear(samples.getLast(), request);
    }
  }

  @Test
  void nativeSlopeAndAlternateCollisionPathAffectTheRouteInsteadOfAnImageMask() {
    var terrain = terrain();
    for (int half = 0; half < 2; half++) {
      byte[] heights = new byte[16], widths = new byte[16];
      for (int x = 0; x < 16; x++) heights[x] = (byte) (half * 8 + x / 2 + 1);
      for (int y = 0; y < 16; y++)
        for (int x = 0; x < 16; x++) if (y >= 16 - heights[x]) widths[y]++;
      var chunk = new Chunk();
      chunk.setSolidTileIndex(half + 2);
      chunk.setSolidTileAltIndex(half + 2);
      when(terrain.level.getChunk(half + 2)).thenReturn(chunk);
      when(terrain.level.getSolidTile(half + 2))
          .thenReturn(new SolidTile(half + 2, heights, widths, (byte) 0xF0));
      terrain.put(20 + half, 15, 0xF000 | half + 2);
    }
    when(terrain.level.getChunkCount()).thenReturn(4);
    for (int x = 22; x < 64; x++) terrain.put(x, 15, 0xF001);
    var request = request(448, 221);
    try (var probe = probe(terrain.level)) {
      var start = probe.capture();
      var result = search(probe, request);
      assertTrue(result.found(), result.message());
      probe.restore(start);
      assertNear(replay(probe, request, result.commands()).getLast(), request);
    }
    // The exact same art/layout now exposes no collision on path1.
    when(terrain.level.getChunk(1)).thenReturn(chunk(1, 0));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            PlayablePuppet.create(
                terrain.level,
                module,
                0,
                0,
                module,
                "sonic",
                SpriteArtSet.EMPTY,
                224,
                237,
                1,
                true,
                false));
  }

  @Test
  void sealedWallAndCeilingReportBoundedFailureWithoutMovingTheProbeToTheGoal() {
    var terrain = terrain();
    for (int x = 0; x < 64; x++) terrain.put(x, 12, 0xF001);
    for (int y = 0; y < 16; y++) terrain.put(20, y, 0xF001);
    var request =
        new StageplayRouteRequest("hero", 0, 448, 237, 120, 128, 16000, 10000, 1, null, 1);
    try (var probe = probe(terrain.level)) {
      var result = search(probe, request);
      assertFalse(result.found());
      assertTrue(result.commands().isEmpty());
      assertTrue(result.message().contains("budget") || result.message().contains("bounded"));
      assertNotEquals(448, probe.sample().x());
    }
  }

  @Test
  void cancellationAndWorkBudgetsAreExplicitAndSearchReplaysDeterministically() {
    var request = request(448, 237);
    try (var probe = probe(terrain().level)) {
      var start = probe.capture();
      var cancelled =
          StageplayRoutePlanner.search(document(), request, probe, () -> true, Long.MAX_VALUE);
      assertEquals(StageplayRouteResult.Status.CANCELLED, cancelled.status());
      var tiny = new StageplayRouteRequest("hero", 0, 448, 237, 240, 1, 1, 10000, 1, null, 1);
      assertEquals(StageplayRouteResult.Status.BUDGET_EXCEEDED, search(probe, tiny).status());
      probe.restore(start);
      var first = search(probe, request);
      probe.restore(start);
      var second = search(probe, request);
      assertTrue(first.found(), first.message());
      assertEquals(first.commands(), second.commands());
      assertEquals(first.path(), second.path());
      assertEquals(0, probe.audioLogicalSnapshot().commandEntryCount());
    }
  }

  @Test
  void privateAudioProducerAndNativeStateRestoreTogetherAcrossDifferentBranches() {
    try (var probe = probe(terrain().level)) {
      var before = probe.audioLogicalSnapshot();
      var start = probe.capture();
      List<PlayablePuppet.Observation> first = new ArrayList<>();
      for (int frame = 0; frame < 60; frame++) {
        probe.tick(false, true, frame < 12, frame);
        first.add(probe.sample());
      }
      var after = probe.audioLogicalSnapshot();
      assertEquals(
          0,
          after.commandEntryCount(),
          "jump/landing in a silent probe must not grow command history");
      probe.restore(start);
      assertTrue(
          RewindSnapshotDiff.keyEquals("private-audio", before, probe.audioLogicalSnapshot()));
      for (int frame = 0; frame < 30; frame++) probe.tick(true, false, frame < 6, frame);
      probe.restore(start);
      for (int frame = 0; frame < 60; frame++) {
        probe.tick(false, true, frame < 12, frame);
        assertEquals(first.get(frame), probe.sample());
      }
      assertTrue(
          RewindSnapshotDiff.keyEquals("private-audio", after, probe.audioLogicalSnapshot()));
    }
  }

  private StageplayRouteProposal search(PlayablePuppet probe, StageplayRouteRequest request) {
    return StageplayRoutePlanner.search(
        document(),
        request,
        probe,
        () -> false,
        System.nanoTime() + request.maxMillis() * 1_000_000L);
  }

  private static StageplayRouteRequest request(double x, double y) {
    return new StageplayRouteRequest("hero", 0, x, y, 240);
  }

  private PlayablePuppet probe(Level terrain) {
    return PlayablePuppet.create(
        terrain, module, 0, 0, module, "sonic", SpriteArtSet.EMPTY, 224, 237, 0, true, false);
  }

  private static StageplayDocument document() {
    return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224);
  }

  private static void assertNear(PlayablePuppet.Observation sample, StageplayRouteRequest request) {
    assertTrue(Math.abs(sample.x() - request.targetX()) <= 6, sample.toString());
    assertTrue(Math.abs(sample.y() - request.targetY()) <= 12, sample.toString());
    assertTrue(sample.onGround());
    assertTrue(Math.abs(sample.xSpeed()) <= 64);
    assertTrue(Math.abs(sample.groundSpeed()) <= 64);
  }

  private static List<PlayablePuppet.Observation> replay(
      PlayablePuppet probe, StageplayRouteRequest request, List<PuppetCommand> commands) {
    List<PlayablePuppet.Observation> result = new ArrayList<>();
    int index = 0, jumpEnd = -1;
    PuppetCommand goal = null;
    for (int frame = request.startFrame();
        frame < request.startFrame() + request.maxFrames();
        frame++) {
      while (index < commands.size() && commands.get(index).frame() <= frame) {
        var command = commands.get(index++);
        if (command.kind() == PuppetCommandKind.JUMP) jumpEnd = frame + command.holdFrames();
        else goal = command;
      }
      var before = probe.sample();
      int direction =
          StageplayMovementIntent.direction(
              Math.floor(before.x()),
              before.xSpeed(),
              before.runAccel(),
              before.runDecel(),
              before.maxSpeed(),
              before.airborne(),
              goal != null && goal.kind() == PuppetCommandKind.MOVE_TO ? goal.x() : before.x(),
              goal != null && goal.kind() == PuppetCommandKind.MOVE_TO ? goal.urgency() : 0);
      probe.tick(direction < 0, direction > 0, frame < jumpEnd, frame);
      result.add(probe.sample());
    }
    return result;
  }

  private static Chunk chunk(int primary, int secondary) {
    var chunk = new Chunk();
    chunk.setSolidTileIndex(primary);
    chunk.setSolidTileAltIndex(secondary);
    return chunk;
  }

  private static Geometry terrain() {
    var map = new com.openggf.level.Map(2, 8, 4);
    var blocks = new Block[32];
    for (int y = 0; y < 4; y++)
      for (int x = 0; x < 8; x++) {
        int index = y * 8 + x;
        blocks[index] = new Block();
        map.setValue(0, x, y, (byte) index);
      }
    Level level = mock(Level.class);
    when(level.getMap()).thenReturn(map);
    when(level.getBlockPixelSize()).thenReturn(128);
    when(level.getChunksPerBlockSide()).thenReturn(8);
    when(level.getLayerWidthBlocks(anyInt())).thenReturn(8);
    when(level.getLayerHeightBlocks(anyInt())).thenReturn(4);
    when(level.getBlockCount()).thenReturn(32);
    when(level.getBlock(anyInt())).thenAnswer(call -> blocks[(int) call.getArgument(0)]);
    byte[] full = new byte[16];
    Arrays.fill(full, (byte) 16);
    when(level.getChunk(1)).thenReturn(chunk(1, 1));
    when(level.getChunkCount()).thenReturn(2);
    when(level.getSolidTile(1)).thenReturn(new SolidTile(1, full, full.clone(), (byte) 0));
    when(level.getMaxX()).thenReturn(960);
    when(level.getMaxY()).thenReturn(480);
    var geometry = new Geometry(level, blocks);
    for (int x = 0; x < 64; x++) geometry.put(x, 16, 0xF001);
    return geometry;
  }

  private record Geometry(Level level, Block[] blocks) {
    void put(int x, int y, int descriptor) {
      blocks[y / 8 * 8 + x / 8].setChunkDesc(x % 8, y % 8, new ChunkDesc(descriptor));
    }
  }
}
