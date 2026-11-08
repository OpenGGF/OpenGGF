package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** A real initial SSZ1 sample exercises the preserved raw-field observer, not a campaign run. */
@RequiresRom(SonicGame.SONIC_3K)
class TestColdCampaignPoolJournal {
    @Test
    void firstSszSampleReadsTheCurrentPopulationAndKeepsItsQualificationIncomplete() throws Exception {
        TestEnvironment.resetAll();
        String property = "openggf.cold.poolJournal";
        String previous = System.getProperty(property);
        Path directory = Files.createTempDirectory(Path.of("target"), "pool-journal-smoke-");
        Path output = directory.resolve("pool.jsonl");
        var settings = new GameplayCaptureSession.Settings(320, "sonic", "", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            assertTrue(session.usesNullAudioBackend());
            assertEquals(10, GameServices.level().getCurrentZone());
            assertEquals(0, GameServices.level().getCurrentAct());
            System.setProperty(property, output.toString());
            ColdCampaignPoolJournal.r30Open(session);
            try {
                var input = new Bk2FrameInput(0, 0, 0, false, "initial SSZ journal sample");
                ColdCampaignPoolJournal.before(session, 0, 0, "smoke", 0, 0, "initial", input);
            } finally {
                ColdCampaignPoolJournal.close();
            }

            var mapper = new ObjectMapper();
            var rows = Files.readAllLines(output).stream().map(line -> {
                try {
                    return mapper.readTree(line);
                } catch (java.io.IOException failure) {
                    throw new java.io.UncheckedIOException(failure);
                }
            }).toList();
            var sample = rows.stream().filter(row -> "INITIAL_OBSERVED_POOL".equals(row.path("type").asText()))
                    .findFirst().orElseThrow();
            assertTrue(sample.path("fullState").has("idCounter"));
            assertTrue(sample.path("fullState").has("childReservations"));
            assertFalse(sample.path("orderedPool").isEmpty());
            var footer = rows.getLast();
            assertEquals("FOOTER", footer.path("type").asText());
            assertEquals(1, footer.path("samples").asInt());
            assertEquals(1, footer.path("verifiedPassivityChecks").asInt());
            assertEquals("INCOMPLETE_DO_NOT_ATTRIBUTE", footer.path("qualification").asText());
            assertFalse(footer.path("fullRouteFinalAssertionsReached").asBoolean());
        } finally {
            if (previous == null) System.clearProperty(property);
            else System.setProperty(property, previous);
            Files.deleteIfExists(output);
            Files.deleteIfExists(directory);
            TestEnvironment.resetAll();
        }
    }
}
