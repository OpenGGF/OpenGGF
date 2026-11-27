package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.ttz.ObjVerticalMoveBlock;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestTtzVerticalMoveBlockNative {
    private record Position(int x, int y) { }

    @TempDir Path tempDir;

    @Test
    void pastWaveStartsInTheSameSlotsAndPositionsAsTheDisc() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-long/R41B/movie.bk2");
        Path aux = CdTestMedia.nativeFile("rehost-long/R41B/trace/aux_state.jsonl");
        var fixture = CdFixtureReplay.Fixture.under(movie.getParent().getParent(), CdStagePayload.R41B);
        Map<Integer, Position> nativePositions = new TreeMap<>();
        var mapper = new ObjectMapper();
        try (var reader = Files.newBufferedReader(aux)) {
            for (String line; (line = reader.readLine()) != null;) {
                var event = mapper.readTree(line);
                if (event.path("frame").asInt() != 0) break;
                if (!event.path("event").asText().equals("object_appeared")) continue;
                if (!event.path("object_type").asText().equals("0x23")) continue;
                nativePositions.put(event.path("slot").asInt(), new Position(
                        Integer.decode(event.path("x").asText()), Integer.decode(event.path("y").asText())));
            }
        }
        // The placed block and its six companions initialized on the entry iteration.
        assertEquals(7, nativePositions.size());
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, fixture, 0, false, -1, -1, new CdFixtureReplay.Hooks() {
                @Override
                public void row(int row, AbstractPlayableSprite sonic) {
                    Map<Integer, Position> hostedPositions = new TreeMap<>();
                    for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                        if (object instanceof ObjVerticalMoveBlock block) {
                            hostedPositions.put(block.getSlotIndex(), new Position(block.getX(), block.getY()));
                        }
                    }
                    assertEquals(nativePositions, hostedPositions);
                }
            });
        }
    }
}
