package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.graphics.GLCommand;
import com.openggf.level.Pattern;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.render.ObjectSpriteCapture;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestSszGroundAnimal {
    private static final int ANIMAL_ID = 0x28;
    private static final int FIRST_SLOT = 43;
    private static final int RESPAWN_SLOT = 38;
    private static final int RETIREMENT_ROW = 919;
    private static final Set<Integer> LIVE_ROWS = Set.of(796, 797, RETIREMENT_ROW - 1);
    private static final java.util.regex.Pattern NEAR_ANIMAL = java.util.regex.Pattern.compile(
            "\"frame\":(\\d+),\"vfc\":\\d+,\"event\":\"object_near\",\"slot\":(\\d+),"
                    + "\"type\":\"0x28\",\"x\":\"0x([0-9A-F]+)\",\"y\":\"0x([0-9A-F]+)\"");

    @TempDir Path tempDir;

    private record Position(int x, int y) { }
    private record Sample(int row, int slot) { }

    @Test
    void groundAnimalMatchesNativeHopsDrawAndRetirement() throws Exception {
        Path metadata = CdTestMedia.nativeFile("rehost-long/R71C/trace/metadata.json");
        Path movie = CdTestMedia.nativeFile("rehost-long/R71C/movie.bk2");
        Path aux = CdTestMedia.nativeFile("rehost-long/R71C/trace/aux_state.jsonl");
        var fixture = new CdFixtureReplay.Fixture(CdStagePayload.R71C, metadata.getParent(), movie);
        TraceData trace = TraceData.load(fixture.traceDir());
        Map<Sample, Position> nativePositions = nativePositions(aux);
        assertEquals(new Position(0x11C, 0x193), nativePositions.get(new Sample(293, FIRST_SLOT)));
        assertEquals(new Position(0x186, 0x351), nativePositions.get(new Sample(747, RESPAWN_SLOT)));

        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var outcome = CdFixtureReplay.replay(disc, tempDir, fixture, RETIREMENT_ROW, true, -1, -1, row -> {
                int nativeRow = CdFixtureReplay.completedRow(trace, row);
                int slot = row < 645 ? FIRST_SLOT : RESPAWN_SLOT;
                Position expected = nativePositions.get(new Sample(nativeRow, slot));
                if (expected != null) {
                    var animal = animalInSlot(slot);
                    assertNotNull(animal, "R71C animal slot " + slot + " on row " + row);
                    assertEquals(expected, new Position(animal.getX(), animal.getY()),
                            "R71C animal slot " + slot + " on row " + row);
                    if (row == 292 || row == 293 || row == 294) assertMappingDraw(row, animal);
                }
                if (LIVE_ROWS.contains(row)) {
                    assertNotNull(animalInSlot(RESPAWN_SLOT), "R71C animal retired early on row " + row);
                } else if (row == RETIREMENT_ROW) {
                    assertNull(animalInSlot(RESPAWN_SLOT), "R71C animal did not retire on row " + row);
                }
            });
            assertEquals(-1, outcome.firstErrorRow(), "R71C player and camera diverged before the animal retired");
            assertEquals(RETIREMENT_ROW + 1, outcome.rowsCompared());
        }
    }

    private static Map<Sample, Position> nativePositions(Path aux) throws Exception {
        Map<Sample, Position> positions = new HashMap<>();
        try (var lines = Files.lines(aux)) {
            lines.forEach(line -> {
                var match = NEAR_ANIMAL.matcher(line);
                if (!match.find()) return;
                int row = Integer.parseInt(match.group(1));
                int slot = Integer.parseInt(match.group(2));
                if ((slot == FIRST_SLOT && row >= 289 && row <= 359)
                        || (slot == RESPAWN_SLOT && row >= 645 && row < RETIREMENT_ROW)) {
                    positions.put(new Sample(row, slot),
                            new Position(Integer.parseInt(match.group(3), 16), Integer.parseInt(match.group(4), 16)));
                }
            });
        }
        return positions;
    }

    private static AbstractObjectInstance animalInSlot(int slot) {
        for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
            if (object instanceof AbstractObjectInstance instance && instance.getSlotIndex() == slot
                    && !instance.isDestroyed() && instance.getSpawn().objectId() == ANIMAL_ID) {
                return instance;
            }
        }
        return null;
    }

    private static void assertMappingDraw(int row, AbstractObjectInstance animal) {
        List<ObjectSpriteCapture.Draw> draws = new ArrayList<>();
        List<GLCommand> commands = new ArrayList<>();
        ObjectSpriteCapture.observe(draws::add, () -> animal.appendRenderCommands(commands));
        assertEquals(1, draws.size(), "R71C animal mapping draw on row " + row);
        var draw = draws.getFirst();
        // AnimalSprites2 (objects/r7/animal.asm:217-219) supplies these two mapping pieces.
        int tile = row == 294 ? 0xE : 8;
        assertEquals(List.of(new SpriteMappingPiece(-8, -12, 2, 3, tile, false, false, 0)), draw.pieces());
        assertEquals(row == 292, draw.flipX(), "R71C animal facing on row " + row);
        assertEquals(animal.getX(), draw.x());
        assertEquals(animal.getY(), draw.y());
        var patterns = draw.sheet().getPatterns();
        byte[] pixels = new byte[6 * Pattern.PATTERN_SIZE_IN_MEM];
        for (int i = 0; i < 6; i++) {
            patterns[tile + i].copyInto(pixels, i * Pattern.PATTERN_SIZE_IN_MEM);
        }
        assertFalse(Arrays.equals(new byte[pixels.length], pixels), "animal tiles are blank");
    }
}
