package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.render.ObjectSpriteCapture;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCdReleasedAnimals {
    @TempDir Path tempDir;

    private record Position(int x, int y) { }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "R11C, 24, 32, 1, 73, 369",
            "R31C, 39, 67, 5330, 5398, 5755",
            "R41C, 2F, 41, 1740, 1800, 2065",
            "R51C, 2D, 34, 2, 70, 1306",
            "R61C, 29, 52, 2515, 2704, 2782",
            "R81C, 32, 32, 1, 80, 344"
    })
    void groundAnimalsMatchNativeAndDraw(CdStagePayload stage, String hexId, int slot,
                                         int first, int last, int retirement) throws Exception {
        checkAnimal(stage, hexId, slot, first, last, retirement);
    }

    @Test
    void stardustFlyingAnimalDrawsAndRetiresOnTheNativeRow() throws Exception {
        checkAnimal(CdStagePayload.R71C, "28", 44, 5070, 5202, 4950);
    }

    private void checkAnimal(CdStagePayload stage, String hexId, int slot,
                             int first, int last, int retirement) throws Exception {
        String recording = "rehost-long/" + stage;
        Path meta = CdTestMedia.nativeFile(recording + "/trace/metadata.json");
        Path movie = CdTestMedia.nativeFile(recording + "/movie.bk2");
        Path aux = CdTestMedia.nativeFile(recording + "/trace/aux_state.jsonl");
        var fixture = new CdFixtureReplay.Fixture(stage, meta.getParent(), movie);
        TraceData trace = TraceData.load(fixture.traceDir());
        var positions = nativePositions(aux, hexId, slot, first, last);
        int id = Integer.parseInt(hexId, 16);
        int end = Math.max(last, retirement);
        int[] comparisons = new int[3];
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var outcome = CdFixtureReplay.replay(disc, tempDir, fixture, end, true, -1, -1, row -> {
                int nativeRow = CdFixtureReplay.completedRow(trace, row);
                Position expected = positions.get(nativeRow);
                if (expected != null) {
                    var animal = animalInSlot(slot, id);
                    assertNotNull(animal, stage + " slot " + slot + " row " + row);
                    assertEquals(expected, new Position(animal.getX(), animal.getY()), stage + " row " + row);
                    comparisons[0]++;
                    if (animal.isOnScreenForTouch()) {
                        var draw = assertAnimalDraw(stage, row, animal);
                        if (stage == CdStagePayload.R71C && comparisons[2] == 0) {
                            assertRestoredDraw(stage, row, animal, draw);
                            comparisons[2]++;
                        }
                        comparisons[1]++;
                    }
                }
                if (retirement >= 0 && row == retirement - 1) {
                    assertNotNull(animalInSlot(slot, id), stage + " retired early");
                }
                if (retirement >= 0 && row == retirement) {
                    assertNull(animalInSlot(slot, id), stage + " still holds slot " + slot + " on row " + retirement);
                }
            });
            assertEquals(-1, outcome.firstErrorRow(), stage + " player or camera diverged");
            assertEquals(end + 1, outcome.rowsCompared());
        }
        assertTrue(comparisons[0] >= 4, stage + " native motion was not sampled");
        assertTrue(comparisons[1] > 0, stage + " never entered the camera");
        if (stage == CdStagePayload.R71C) assertEquals(1, comparisons[2]);
    }

    private static Map<Integer, Position> nativePositions(Path aux, String hexId, int slot, int first, int last)
            throws Exception {
        var pattern = java.util.regex.Pattern.compile(
                "\"frame\":(\\d+),\"vfc\":\\d+,\"event\":\"object_near\",\"slot\":" + slot
                        + ",\"type\":\"0x" + hexId + "\",\"x\":\"0x([0-9A-F]+)\",\"y\":\"0x([0-9A-F]+)\"");
        Map<Integer, Position> positions = new HashMap<>();
        try (var lines = Files.lines(aux)) {
            lines.forEach(line -> {
                var match = pattern.matcher(line);
                if (!match.find()) return;
                int row = Integer.parseInt(match.group(1));
                if (first <= row && row <= last) {
                    positions.put(row, new Position(Integer.parseInt(match.group(2), 16),
                            Integer.parseInt(match.group(3), 16)));
                }
            });
        }
        return positions;
    }

    private static AbstractObjectInstance animalInSlot(int slot, int id) {
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object instanceof AbstractObjectInstance instance && instance.getSlotIndex() == slot
                    && !instance.isDestroyed() && instance.getSpawn().objectId() == id) {
                return instance;
            }
        }
        return null;
    }

    private static void assertRestoredDraw(CdStagePayload stage, int row, AbstractObjectInstance animal,
                                           ObjectSpriteCapture.Draw expected) {
        var services = GameServices.level().getObjectManager().getObjectServices();
        var snapshot = animal.captureRewindState();
        var restored = ((RewindRecreatable) animal).recreateForRewind(
                new RewindRecreateContext(animal.getSpawn(), snapshot, services));
        restored.setServices(services);
        restored.restoreRewindState(snapshot);
        var draw = assertAnimalDraw(stage, row, restored);
        assertEquals(new Position(expected.x(), expected.y()), new Position(draw.x(), draw.y()));
        assertEquals(expected.pieces(), draw.pieces());
        assertEquals(expected.flipX(), draw.flipX());
        assertEquals(expected.flipY(), draw.flipY());
    }

    private static ObjectSpriteCapture.Draw assertAnimalDraw(CdStagePayload stage, int row,
                                                             AbstractObjectInstance animal) {
        List<ObjectSpriteCapture.Draw> draws = new ArrayList<>();
        ObjectSpriteCapture.observe(draws::add, () -> animal.appendRenderCommands(new ArrayList<>()));
        assertEquals(1, draws.size(), stage + " animal mapping draw on row " + row);
        var draw = draws.getFirst();
        assertEquals(animal.getX(), draw.x());
        assertEquals(animal.getY(), draw.y());
        assertFalse(draw.pieces().isEmpty());
        Pattern[] patterns = draw.sheet().getPatterns();
        boolean nonblank = false;
        for (var piece : draw.pieces()) {
            int endTile = piece.tileIndex() + piece.widthTiles() * piece.heightTiles();
            for (int tile = piece.tileIndex(); tile < endTile; tile++) {
                byte[] pixels = new byte[Pattern.PATTERN_SIZE_IN_MEM];
                patterns[tile].copyInto(pixels, 0);
                for (byte pixel : pixels) nonblank |= pixel != 0;
            }
        }
        assertTrue(nonblank, stage + " animal tiles are blank on row " + row);
        return draw;
    }
}
