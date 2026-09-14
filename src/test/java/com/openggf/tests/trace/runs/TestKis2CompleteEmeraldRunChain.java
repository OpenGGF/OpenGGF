package com.openggf.tests.trace.runs;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.data.RomIdentity;
import com.openggf.game.GameServices;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;

/** Continuous KiS2 route through all seven emerald stages and Death Egg. */
@RequiresRom(SonicGame.SONIC_2)
class TestKis2CompleteEmeraldRunChain extends AbstractRunChainTest {
    @Test
    void ehz1ThroughAllEmeraldsToDeathEgg() throws Exception {
        String candidate = System.getProperty("openggf.trace.kis2.run.dir");
        Path run = candidate == null
                ? Path.of("src/test/resources/traces/kis2/runs/kis2-full-run-all-emeralds")
                : Path.of(candidate);
        if (candidate == null) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    Files.isRegularFile(run.resolve("run_manifest.json")),
                    "Published KiS2 full-run manifest is missing");
        }
        var config = GameServices.configuration();
        String previousRom = config.getString(SonicConfiguration.SONIC_2_ROM);
        String chip = System.getProperty("kis2.rom.path");
        try {
            if (chip != null && !chip.isBlank()) {
                var supplied = new com.openggf.data.RomImageCatalogue(
                        java.util.List.of(com.openggf.data.PhysicalImage.probe(Path.of(chip))),
                        java.util.Map.of(), false);
                org.junit.jupiter.api.Assertions.assertTrue(supplied.isAvailable(RomIdentity.KIS2),
                        "kis2.rom.path must identify the KiS2 lock-on image");
                // The catalogue extracts both the S2 and chip windows from the
                // supplied physical image; replay does not provide asset bytes.
                config.setConfigValue(SonicConfiguration.SONIC_2_ROM, chip);
            }
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    GameServices.rom().resolveLogicalRom(RomIdentity.KIS2).isPresent(),
                    "KiS2 full-run replay requires the lock-on dump (-Dkis2.rom.path)");
            assertChainReplay(run);
        } finally {
            config.setConfigValue(SonicConfiguration.SONIC_2_ROM, previousRom);
        }
    }
}
