package com.openggf.game.soniccd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.objects.ccz.CczSpikeChain;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCczSpikeChainNative {
    @TempDir
    Path tempDir;

    private record Position(int x, int y) { }

    @ParameterizedTest
    @CsvSource({"R31A, 3300", "R31B, 5900", "R31C, 4550", "R32A, 4250"})
    void positionsMatchTheUsaDiscRoutes(CdStagePayload payload, int lastRow) throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-long/" + payload + "/movie.bk2").getParent().getParent();
        var fixture = CdFixtureReplay.Fixture.under(root, payload);
        TraceData trace = TraceData.load(fixture.traceDir());
        Map<Integer, List<Position>> nativePositions = new HashMap<>();
        var mapper = new ObjectMapper();
        try (var lines = Files.lines(fixture.traceDir().resolve("aux_state.jsonl"))) {
            for (String line : lines.filter(s -> s.contains("\"event\":\"object_near\"")
                    && s.contains("\"type\":\"0x2C\"")).toList()) {
                var event = mapper.readTree(line);
                nativePositions.computeIfAbsent(event.get("frame").asInt(), ignored -> new ArrayList<>())
                        .add(new Position(Integer.decode(event.get("x").asText()),
                                Integer.decode(event.get("y").asText())));
            }
        }
        List<String> misses = new ArrayList<>();
        Set<Integer> compared = new HashSet<>();
        CdFixtureReplay.Outcome outcome;
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            outcome = CdFixtureReplay.replay(disc, tempDir, fixture, lastRow, true, -1, -1, row -> {
                int completed = CdFixtureReplay.completedRow(trace, row);
                List<Position> expected = nativePositions.get(completed);
                if (expected == null || !compared.add(completed)) return;
                var positions = GameServices.level().getObjectManager().getActiveObjects().stream()
                        .filter(o -> !o.isDestroyed() && (o instanceof CczSpikeChain || o instanceof CczSpikeChain.Ball))
                        .map(o -> new Position(o.getX() & 0xFFFF, o.getY() & 0xFFFF)).toList();
                for (Position position : expected) {
                    if (!positions.contains(position)) {
                        misses.add("row " + completed + " missing " + position + " in " + positions);
                    }
                }
            });
        }
        assertTrue(outcome.firstErrorRow() < 0, () -> payload + " route diverged at " + outcome.firstErrorRow()
                + ": " + outcome.fields());
        assertTrue(!compared.isEmpty(), "No native rows near a spike chain");
        assertEquals(0, misses.size(), () -> String.join("\n", misses.stream().limit(8).toList()));
        System.out.println(payload + ": all recorded spike-chain positions matched on " + compared.size() + " rows");
    }
}
