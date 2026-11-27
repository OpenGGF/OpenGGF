package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.ppz.PpzAmyRose;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.level.Palette;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.physics.Direction;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPpzAmyRose {
    private static final int AMY = 0x2F;
    private static final int HEART = 0x30;
    private static final String RECORDING = "rehost-long/R11A/";
    private static final Pattern AMY_EVENT = Pattern.compile(
            "\"frame\":(\\d+),\"vfc\":\\d+,\"event\":\"object_(appeared|near|removed)\","
                    + "\"slot\":(\\d+),\"(?:object_type|type)\":\"0x2F\"(?:,\"x\":\"0x([0-9A-F]+)\",\"y\":\"0x([0-9A-F]+)\")?");

    @TempDir
    Path tempDir;

    private record Position(int slot, int x, int y) { }

    @Test
    void nativeSlotAndPositionMatchUntilDespawn() throws Exception {
        Path aux = CdTestMedia.nativeFile(RECORDING + "trace/aux_state.jsonl");
        Path movie = CdTestMedia.nativeFile(RECORDING + "movie.bk2");
        TraceData trace = TraceData.load(aux.getParent());
        var samples = new TreeMap<Integer, Position>();
        int appeared = -1;
        Position initial = null;
        int removed = -1;
        for (String line : Files.readAllLines(aux)) {
            var event = AMY_EVENT.matcher(line);
            if (!event.find()) continue;
            int row = Integer.parseInt(event.group(1));
            switch (event.group(2)) {
                case "appeared" -> {
                    appeared = row;
                    initial = position(event);
                }
                case "removed" -> removed = row;
                case "near" -> samples.put(row, position(event));
                default -> throw new AssertionError(line);
            }
        }
        assertEquals(3278, appeared);
        assertEquals(3398, removed);
        assertFalse(samples.isEmpty(), "the native observer sampled Amy near Sonic");

        var actual = new TreeMap<Integer, Position>();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R11A, aux.getParent(), movie);
            var replay = CdFixtureReplay.replay(disc, tempDir, fixture, removed + 1, true, -1, -1,
                    new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            var amy = actors(AMY);
                            if (!amy.isEmpty()) {
                                var object = assertInstanceOf(PpzAmyRose.class, amy.getFirst());
                                actual.put(CdFixtureReplay.completedRow(trace, row),
                                        new Position(object.getSlotIndex(), object.getX(), object.getY()));
                            }
                        }
                    });
            assertEquals(-1, replay.firstErrorRow(), "the player and camera must agree through the encounter");
        }

        assertFalse(actual.isEmpty(), "Amy occupied a live slot");
        assertEquals(appeared, actual.firstKey(), "first live row");
        assertEquals(removed - 1, actual.lastKey(), "last live row");
        assertEquals(initial, actual.firstEntry().getValue(), "first live position and slot");
        for (var sample : samples.entrySet()) {
            assertEquals(sample.getValue(), actual.get(sample.getKey()), "native row " + sample.getKey());
        }
        System.out.printf("[scd-amy] slot %d, live rows %d–%d, %d native nearby positions matched%n",
                initial.slot(), appeared, removed - 1, samples.size());
    }

    private static Position position(java.util.regex.Matcher event) {
        return new Position(Integer.parseInt(event.group(3)), Integer.parseInt(event.group(4), 16),
                Integer.parseInt(event.group(5), 16));
    }

    @Test
    void campaignAmyGrabsAndSonicJumpsFree() throws Exception {
        inStage(sonic -> {
            var amy = grabSonic(sonic);

            sonic.setJumpInputPressed(true, true);
            amy.update(0, sonic);
            assertFalse(sonic.isObjectControlled());
            assertTrue(sonic.getAir());
            assertTrue(sonic.getRolling());
            assertEquals(-0x680, sonic.getYSpeed());

            int y = amy.getY();
            amy.update(0, sonic);
            assertEquals(y - 3, amy.getY(), "Amy follows the released player with her own jump");
        });
    }

    @Test
    void amyChasesStandingSonicAndWaitsOnlyWithinNativeRanges() throws Exception {
        inStage(sonic -> {
            var amy = spawnPlacedAmy();
            int startX = amy.getX();
            NativePositionOps.writeXPosResetSubpixel(sonic, startX - 0x40);
            sonic.setXSpeed((short) 0);
            amy.update(0, sonic);
            for (int frame = 1; frame <= 16; frame++) amy.update(frame, sonic);
            int chasedX = amy.getX();

            NativePositionOps.writeXPosResetSubpixel(sonic, chasedX - 9);
            amy.update(17, sonic);
            int closeX = amy.getX();
            NativePositionOps.writeXPosResetSubpixel(sonic, closeX - 0x1F);
            amy.update(18, sonic);
            int waitingX = amy.getX();
            NativePositionOps.writeXPosResetSubpixel(sonic, waitingX - 0x20);
            for (int frame = 19; frame <= 22; frame++) amy.update(frame, sonic);
            assertEquals(List.of(startX - 9, startX - 9, startX - 9, startX - 10),
                    List.of(chasedX, closeX, waitingX, amy.getX()),
                    "Amy accelerates toward still Sonic, waits inside $A, and resumes at $20");
        });
    }

    @Test
    void removingAmyReleasesSonicBeforeAndAfterRewind() throws Exception {
        inStage(sonic -> {
            var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var beforeGrab = registry.capture();
            var amy = grabSonic(sonic);
            var held = registry.capture();
            var objects = GameServices.level().getObjectManager();
            objects.removeDynamicObject(amy);
            assertFalse(sonic.isObjectControlled(), "Removing Amy frees Sonic without a jump");
            assertFalse(sonic.getAir());

            registry.restore(held);
            var restoredAmy = assertInstanceOf(PpzAmyRose.class, actors(AMY).getFirst());
            assertTrue(sonic.isObjectControlled(), "The held snapshot restores Sonic's control state");
            objects.removeDynamicObject(restoredAmy);
            assertFalse(sonic.isObjectControlled(), "The recreated Amy still owns the hold");

            registry.restore(held);
            registry.restore(beforeGrab);
            assertTrue(actors(AMY).isEmpty(), "Rewind retires the future Amy");
            assertFalse(sonic.isObjectControlled(), "Rewind restores Sonic's pre-grab control");
        });
    }

    private static PpzAmyRose grabSonic(AbstractPlayableSprite sonic) {
        var amy = spawnPlacedAmy();
        NativePositionOps.writeXPosResetSubpixel(sonic, amy.getX() - 0x12);
        sonic.setDirection(Direction.LEFT);
        sonic.setAir(false);
        sonic.setXSpeed((short) -0x200);
        sonic.setYSpeed((short) 0);
        sonic.setAngle((byte) 0);
        amy.update(0, sonic);
        NativePositionOps.writeYPosResetSubpixel(sonic, amy.getY());
        amy.update(0, sonic);
        assertFalse(sonic.isObjectControlled(), "the grab changes Amy's routine before it takes player control");
        amy.update(0, sonic);
        assertTrue(sonic.isObjectControlled(), "the hold routine takes control");
        assertEquals(0xC, amy.getX() - sonic.getCentreX());
        return amy;
    }

    @Test
    void timeAttackDoesNotInitializeAmyOrEmitHearts() throws Exception {
        inStage(sonic -> {
            var scenes = SessionManager.requireCurrentGameModule().getGameService(CdSceneCoordinator.class);
            scenes.flow().timeAttackSelected(1);
            assertTrue(scenes.timeAttackMode());

            var image = SessionManager.requireCurrentGameModule().getGameService(CdObjectArtProvider.class).image();
            var level = GameServices.level().getCurrentLevel();
            var palette = level.getPalette(1);
            int[] expected = new int[Palette.PALETTE_SIZE];
            // Pal_LevelEnd (Level/Palmtree Panic/Palette Data (Act 1 Present).asm:58-60).
            for (int i = 0; i < expected.length; i++) {
                expected[i] = image.u16(0x7B4 + Palette.BYTES_PER_COLOR * i) & 0xEEE;
                palette.getColor(i).fromSegaFormat(expected[i] ^ 0xEEE);
            }

            var amy = spawnPlacedAmy();
            int y = amy.getY();
            for (int i = 0; i < 45; i++) amy.update(i, sonic);
            assertEquals(y, amy.getY());
            assertEquals(0, amy.getOnScreenHalfWidth(), "the slot never sets its sprite dimensions");
            assertFalse(amy.isCustomOutOfRange(0), "the palette-only branch never checks despawn");
            assertTrue(actors(HEART).isEmpty());
            assertArrayEquals(expected, Arrays.stream(level.getPalette(1).colors)
                    .mapToInt(PaletteWriteSupport::segaWordFromColor).toArray(),
                    "Time Attack restores Pal_LevelEnd to palette line 1");
        });
    }

    @Test
    void rewindRestoresAmyAndHerHeart() throws Exception {
        Path movie = CdTestMedia.nativeFile(RECORDING + "movie.bk2");
        Path metadata = CdTestMedia.nativeFile(RECORDING + "trace/metadata.json");
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R11A, metadata.getParent(), movie);
            CdFixtureReplay.replay(disc, tempDir, fixture, 3363, false, -1, -1, new CdFixtureReplay.Hooks() {
                @Override
                public void after(HeadlessTestRunner runner, AbstractPlayableSprite sonic) {
                    assertTrue(actors(AMY).size() == 1 && !actors(HEART).isEmpty());
                    var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
                    var before = population();
                    var snapshot = registry.capture();
                    var firstRun = step(runner);
                    registry.restore(snapshot);
                    assertEquals(before, population());
                    assertEquals(firstRun, step(runner));
                }
            });
        }
    }

    private static List<List<Position>> step(HeadlessTestRunner runner) {
        List<List<Position>> result = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            runner.stepIdleFrames(1);
            result.add(population());
        }
        return result;
    }

    private static List<Position> population() {
        return java.util.stream.Stream.concat(actors(AMY).stream(), actors(HEART).stream())
                .map(actor -> new Position(actor.getSlotIndex(), actor.getX(), actor.getY())).toList();
    }

    private static List<AbstractObjectInstance> actors(int id) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(object -> !object.isDestroyed() && object.getSpawn() != null
                        && object.getSpawn().objectId() == id)
                .map(AbstractObjectInstance.class::cast).toList();
    }

    private static PpzAmyRose spawnPlacedAmy() {
        var placement = GameServices.level().getCurrentLevel().getObjects().stream()
                .filter(spawn -> spawn.objectId() == AMY).findFirst().orElseThrow();
        var registry = SessionManager.requireCurrentGameModule().createObjectRegistry();
        var amy = assertInstanceOf(PpzAmyRose.class, registry.create(placement));
        GameServices.level().getObjectManager().addDynamicObject(amy);
        return amy;
    }

    private void inStage(java.util.function.Consumer<AbstractPlayableSprite> test) throws Exception {
        var previous = EngineServices.current();
        TestEnvironment.resetAll();
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            test.accept(CdFixtureReplay.boot(tempDir, disc, CdStagePayload.R11A));
        } finally {
            GroundSensor.setLevelManager(null);
            SessionManager.clear();
            GameModuleRegistry.reset();
            EngineServices.configure(previous);
            TestEnvironment.resetAll();
        }
    }
}
