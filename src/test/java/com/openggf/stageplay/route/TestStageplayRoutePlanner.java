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
import com.openggf.route.InputTape;
import com.openggf.route.PadRow;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlanResult;
import com.openggf.route.PlayerSlot;
import com.openggf.route.StopReason;
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

/** Native collision search through the shared planner's probe tier; its frozen rows replay on the probe. */
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
            var rows = rows(search(probe, request));
            assertEquals(request.maxFrames(), rows.size(), "the frozen rows cover the whole window");
            assertTrue(rows.rows().stream().anyMatch(r -> r.held(PlayerSlot.P1, PhysicalButton.RIGHT)));
            probe.restore(start);
            var samples = replayRows(probe, request, rows);
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
    void nativeWorldProbePreservesEntryAirAndControllerEdges() {
        var terrain = terrain().level;
        try (var nativeEntry =
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
            assertTrue(
                    nativeEntry.sample().airborne(), "Native world entry leaves the airborne status set");
            var entry = nativeEntry.capture();
            nativeEntry.tick(false, false, true, 0);
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
            var rows = rows(search(probe, request));
            assertTrue(jumps(rows), "The raised destination across the gap requires an actual native jump");
            probe.restore(start);
            var samples = replayRows(probe, request, rows);
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
            var rows = rows(search(probe, request));
            probe.restore(start);
            assertNear(replayRows(probe, request, rows).getLast(), request);
        }
        // The exact same art/layout now exposes no collision on path1.
        when(terrain.level.getChunk(1)).thenReturn(chunk(1, 0));
        try (var missing = PlayablePuppet.createNativeWorldProbe(terrain.level, module, 0, 0, module,
                "sonic", SpriteArtSet.EMPTY, 224, 237, 1, true, false)) {
            assertTrue(missing.sample().airborne(), "Absent path collision cannot fabricate ground attachment");
        }
    }

    @Test void speedDependentGapSuggestsRunAndRunReallyCrossesTheSameTerrain() {
        var terrain=terrain();
        for(int column=20;column<=27;column++) terrain.put(column,16,0);
        for(int column=28;column<64;column++) {terrain.put(column,14,0xF001);terrain.put(column,15,0xF001);}
        var walk=new StageplayRouteRequest("hero",0,512,205,300,1024,120000,60000,1,null,1,RouteMotion.DEFAULT);
        try(var probe=probe(terrain.level)) {
            // A diagnostic second search used to advise Run; the one search now just reports the stop.
            var initial=probe.capture();var slow=search(probe,walk);
            var stopped=assertInstanceOf(PlanResult.Stopped.class,slow);
            assertFalse(stopped.detail().contains("Spindash"),stopped.detail());
            probe.restore(initial);var fast=rows(search(probe,request(512,205)));
            probe.restore(initial);assertNear(replayRows(probe,request(512,205),fast).getLast(),request(512,205));
        }
    }

    @Test void downhillWalkBrakesToKeepTheStrollCapWithoutSkidding() {
        var terrain=terrain();
        for(int half=0;half<2;half++) {
            byte[] heights=new byte[16],widths=new byte[16];
            for(int x=0;x<16;x++) heights[x]=(byte)(half*8+x/2+1);
            for(int y=0;y<16;y++) for(int x=0;x<16;x++) if(y>=16-heights[x]) widths[y]++;
            when(terrain.level.getChunk(half+2)).thenReturn(chunk(half+2,half+2));
            when(terrain.level.getSolidTile(half+2)).thenReturn(new SolidTile(half+2,heights,widths,(byte)0xF0));
            terrain.put(20+half,15,0xF000|half+2);
        }
        when(terrain.level.getChunkCount()).thenReturn(4);
        for(int x=22;x<64;x++) terrain.put(x,15,0xF001);
        var request=new StageplayRouteRequest("hero",0,224,237,400);
        try(var probe=PlayablePuppet.createNativeWorldProbe(terrain.level,module,0,0,module,
                "sonic",SpriteArtSet.EMPTY,448,221,0,true,true)) {
            var initial=probe.capture();var rows=rows(search(probe,request));
            assertFalse(jumps(rows));
            probe.restore(initial);var samples=replayRows(probe,request,rows);assertNear(samples.getLast(),request);
            assertTrue(samples.stream().allMatch(o->Math.abs(o.groundSpeed())<=StageplayRouteSteering.STROLL_CAP),
                    "Native downhill acceleration must be held by real braking inputs");
            assertTrue(samples.stream().noneMatch(PlayablePuppet.Observation::skidding));
        }
    }

    @Test
    void sealedWallAndCeilingReportBoundedFailureWithoutMovingTheProbeToTheGoal() {
        var terrain = terrain();
        for (int x = 0; x < 64; x++) terrain.put(x, 12, 0xF001);
        for (int y = 0; y < 16; y++) terrain.put(20, y, 0xF001);
        var request =
                new StageplayRouteRequest("hero", 0, 448, 237, 120, 128, 16000, 10000, 1, null, 1);
        try (var probe = probe(terrain.level)) {
            var result = assertInstanceOf(PlanResult.Stopped.class, search(probe, request));
            assertTrue(result.reason() == StopReason.BUDGET_EXHAUSTED || result.reason() == StopReason.NO_PROGRESS,
                    result.toString());
            assertFalse(result.detail().contains("Spindash"),result.detail());
            assertNotEquals(448, probe.sample().x());
        }
    }

    @Test
    void cancellationAndWorkBudgetsAreExplicitAndSearchReplaysDeterministically() {
        var request = request(448, 237);
        try (var probe = probe(terrain().level)) {
            var start = probe.capture();
            var cancelled = StageplayRoutePlanner.draft(document(), request, probe, () -> true);
            assertEquals(StopReason.CANCELLED, assertInstanceOf(PlanResult.Stopped.class, cancelled).reason());
            var tiny = new StageplayRouteRequest("hero", 0, 448, 237, 240, 1, 1, 10000, 1, null, 1);
            assertEquals(StopReason.BUDGET_EXHAUSTED,
                    assertInstanceOf(PlanResult.Stopped.class, search(probe, tiny)).reason());
            probe.restore(start);
            var first = assertInstanceOf(PlanResult.Solved.class, search(probe, request));
            probe.restore(start);
            var second = assertInstanceOf(PlanResult.Solved.class, search(probe, request));
            assertEquals(first.route().tape(), second.route().tape());
            assertEquals(first.work(), second.work());
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

    @Test void anAlreadyReachedWalkGoalEmitsOnlyOneValidStop() {
        var request=new StageplayRouteRequest("hero",0,224,237,60);
        try(var probe=probe(terrain().level)) {
            var start=probe.capture();var rows=rows(search(probe,request));
            assertEquals(request.maxFrames(),rows.size());
            assertFalse(jumps(rows));
            probe.restore(start);assertNear(replayRows(probe,request,rows).getLast(),request);
        }
    }

    @Test void defaultWalkUsesOneDestinationWithoutJumpingAndKeepsItsStrollCap() {
        var request=new StageplayRouteRequest("hero",0,448,237,300);
        try(var probe=probe(terrain().level)) {
            var start=probe.capture();var rows=rows(search(probe,request));
            assertFalse(jumps(rows));
            probe.restore(start);var samples=replayRows(probe,request,rows);
            assertTrue(samples.stream().allMatch(o->o.x()>=223),"no run-up detour backwards");
            assertNear(samples.getLast(),request);
            assertTrue(samples.stream().noneMatch(PlayablePuppet.Observation::skidding));
            assertTrue(samples.stream().allMatch(o->Math.abs(o.groundSpeed())<=StageplayRouteSteering.STROLL_CAP));
        }
    }

    @Test void jumpStartingActionLandsFirst() {
        var motion=new RouteMotion(StartAction.JUMP,TravelSpeed.RUN,EndingAction.HARD_STOP,EndingFacing.INHERIT);
        var request=new StageplayRouteRequest("hero",0,600,237,600,1024,120000,60000,1,null,1,motion);
        try(var probe=probe(terrain().level)) {
            var start=probe.capture();var rows=rows(search(probe,request));
            assertTrue(rows.row(0).isNeutral(),"Falling entry lands before starting action");
            assertTrue(jumps(rows));
            probe.restore(start);var samples=replayRows(probe,request,rows);
            assertTrue(samples.stream().anyMatch(o->o.ySpeed()<0));assertNear(samples.getLast(),request);
        }
        // Native spindash requires the ROM animation profile's Duck state. The EMPTY
        // art sentinel deliberately has no profile; actual charge/release is covered
        // by TestStageplayRouteMotionRom, never by inventing a crouch pose here.
    }

    @Test void endingActionsAndSingleFacingTapsSettleThroughNativeInputs() {
        for(var ending:EndingAction.values()) for(var facing:List.of(EndingFacing.LEFT,EndingFacing.RIGHT)) {
            var motion=new RouteMotion(StartAction.WALK,TravelSpeed.RUN,ending,facing);
            var request=new StageplayRouteRequest("hero",0,600,237,600,1024,120000,60000,1,null,1,motion);
            try(var probe=probe(terrain().level)) {
                var start=probe.capture();var plan=search(probe,request);
                var rows=assertInstanceOf(PlanResult.Solved.class,plan,ending+": "+plan).route().tape();
                probe.restore(start);var samples=replayRows(probe,request,rows);
                assertNear(samples.getLast(),request);
                assertEquals(facing==EndingFacing.LEFT,samples.getLast().flipX());
                if(ending==EndingAction.GENTLE_STOP) assertTrue(samples.stream().noneMatch(PlayablePuppet.Observation::skidding));
                if(ending==EndingAction.OVERSHOOT) assertTrue(samples.stream().anyMatch(o->o.x()>request.targetX()+12));
            }
        }
    }

    @Test void aGapAtTheStartingFeetUsesBackupOnlyAfterDirectSteeringFails() {
        var terrain=terrain();
        for(int column=15;column<=27;column++) terrain.put(column,16,0);
        var request=new StageplayRouteRequest("hero",0,512,237,500,2048,240000,60000,1,null,1,RouteMotion.NATIVE);
        try(var probe=probe(terrain.level)) {
            var start=probe.capture();
            var direct=replay(probe,request,List.of(new PuppetCommand("direct",0,PuppetCommandKind.MOVE_TO,512,237,1,0)));
            assertFalse(Math.abs(direct.getLast().x()-512)<=6 && direct.getLast().onGround(),
                    "Fixture must require a back-up run-up");
            probe.restore(start);var rows=rows(search(probe,request));
            probe.restore(start);var samples=replayRows(probe,request,rows);
            assertTrue(samples.stream().anyMatch(o->o.x()<224-32),"The rows must actually perform the run-up");
            assertNear(samples.getLast(),request);
        }
    }

    @Test void aStartWithoutObservedNativeChargeCannotProduceASpindashProposal() {
        var motion=new RouteMotion(StartAction.SPINDASH,TravelSpeed.RUN,EndingAction.HARD_STOP,EndingFacing.INHERIT);
        var request=new StageplayRouteRequest("hero",0,512,237,500,1024,120000,60000,1,null,1,motion);
        try(var probe=probe(terrain().level)) {
            var result=assertInstanceOf(PlanResult.Stopped.class,search(probe,request));
            assertTrue(result.detail().contains("Native spindash charge and release did not occur"),result.detail());
        }
    }

    @Test void everyWalkEndingHoldsTheCapThroughTerrainJumps() {
        for(var ending:EndingAction.values()) {
            var terrain=terrain();
            // A narrow gap with a raised lip requires a real native jump.
            for(int x=20;x<=21;x++) terrain.put(x,16,0);
            for(int x=22;x<64;x++) terrain.put(x,15,0xF001);
            var motion=new RouteMotion(StartAction.WALK,TravelSpeed.WALK,ending,EndingFacing.INHERIT);
            var request=new StageplayRouteRequest("hero",0,480,221,600,1024,120000,60000,1,null,1,motion);
            try(var probe=probe(terrain.level)) {
                var start=probe.capture();var plan=search(probe,request);
                var rows=assertInstanceOf(PlanResult.Solved.class,plan,ending+": "+plan).route().tape();
                assertTrue(jumps(rows));
                probe.restore(start);var samples=replayRows(probe,request,rows);
                assertNear(samples.getLast(),request);
                assertTrue(samples.stream().allMatch(o->Math.max(Math.abs(o.groundSpeed()),Math.abs(o.xSpeed()))
                        <=StageplayRouteSteering.STROLL_CAP),ending.toString());
            }
        }
    }

    @Test void everyWalkEndingReservesGroundAndAirAccelerationHeadroom() {
        for(var ending:EndingAction.values()) for(boolean airborne:List.of(false,true)) {
            var motion=new RouteMotion(StartAction.WALK,TravelSpeed.WALK,ending,EndingFacing.INHERIT);
            int direction=StageplayRouteSteering.direction(100,0x174,0x180,12,128,12,0x600,
                    -8,airborne,400,1,motion,false,1);
            assertEquals(0,direction,ending+" airborne="+airborne);
        }
    }

    /** The draft tier: the shared planner searching and replaying on the probe. */
    private PlanResult search(PlayablePuppet probe, StageplayRouteRequest request) {
        return StageplayRoutePlanner.draft(document(), request, probe, () -> false);
    }

    private static InputTape rows(PlanResult result) {
        return assertInstanceOf(PlanResult.Solved.class, result, result.toString()).route().tape();
    }

    private static boolean jumps(InputTape rows) {
        return rows.rows().stream().anyMatch(r -> r.held(PlayerSlot.P1, PhysicalButton.C));
    }

    /** Replays frozen rows on the probe from its current state, one native tick per row. */
    static List<PlayablePuppet.Observation> replayRows(PlayablePuppet probe, StageplayRouteRequest request, InputTape rows) {
        List<PlayablePuppet.Observation> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            PadRow row = rows.row(i);
            boolean jump = row.held(PlayerSlot.P1, PhysicalButton.A) || row.held(PlayerSlot.P1, PhysicalButton.B)
                    || row.held(PlayerSlot.P1, PhysicalButton.C);
            probe.tick(row.held(PlayerSlot.P1, PhysicalButton.LEFT), row.held(PlayerSlot.P1, PhysicalButton.RIGHT),
                    row.held(PlayerSlot.P1, PhysicalButton.DOWN), jump, request.startFrame() + i);
            result.add(probe.sample());
        }
        return result;
    }

    private static StageplayRouteRequest request(double x, double y) {
        return new StageplayRouteRequest("hero", 0, x, y, 240,1024,120000,60000,1,null,1,RouteMotion.NATIVE);
    }

    private PlayablePuppet probe(Level terrain) {
        return PlayablePuppet.createNativeWorldProbe(
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

    static List<PlayablePuppet.Observation> replay(
            PlayablePuppet probe, StageplayRouteRequest request, List<PuppetCommand> commands) {
        List<PlayablePuppet.Observation> result = new ArrayList<>();
        int index = 0, jumpEnd = -1;
        PuppetCommand goal = null;
        int goalStart=0,approach=1; boolean returning=false;
        for (int frame = request.startFrame();
                frame < request.startFrame() + request.maxFrames();
                frame++) {
            while (index < commands.size() && commands.get(index).frame() <= frame) {
                var command = commands.get(index++);
                if (command.kind() == PuppetCommandKind.JUMP) jumpEnd = frame + command.holdFrames();
                else {goal = command; goalStart=frame;approach=command.x()<probe.sample().x()?-1:1;returning=false;}
            }
            var before = probe.sample();
            var buttons = goal == null ? new StageplayRouteSteering.Buttons(0,false,frame<jumpEnd,returning,false)
                    : com.openggf.stageplay.runtime.StageplayLiveWorld.routeInput(goal,frame-goalStart,before,frame<jumpEnd,returning,approach,goal.x());
            returning=buttons.returning();
            if(buttons.finished()) goal=null;
            probe.tick(buttons.direction()<0,buttons.direction()>0,buttons.down(),buttons.jump(),frame);
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

    static Geometry terrain() {
        var map = new com.openggf.level.Map(2, 8, 4);
        var blocks = new Block[32];
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 8; x++) {
                int index = y * 8 + x;
                blocks[index] = new Block();
                map.setValue(0, x, y, (byte) index);
            }
        Level level = mock(Level.class, withSettings().stubOnly());
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

    record Geometry(Level level, Block[] blocks) {
        void put(int x, int y, int descriptor) {
            blocks[y / 8 * 8 + x / 8].setChunkDesc(x % 8, y % 8, new ChunkDesc(descriptor));
        }
    }
}
