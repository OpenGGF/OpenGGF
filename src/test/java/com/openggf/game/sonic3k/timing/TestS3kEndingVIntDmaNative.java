package com.openggf.game.sonic3k.timing;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.resources.S3kRuntimeArtCoordinator;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.FixedTransfers;
import com.openggf.game.sonic3k.timing.S3kVIntDmaLedger.Record;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Long route check: the engine's own ending run, entered through the declared component ingress used by the ending
 * tests (zone 13 act 1 fresh start, then {@code restoreS3kEmeraldProgress}), records per-V-int DMA work that must
 * equal the retained native observation for LF 4400-4406: the drained Add_To_DMA_Queue entries and the
 * {@code dma_freeze_mclk} counter. Native rows are comparison-only. PAL products run with the configured
 * {@code REGION} set to PAL before the level initializes, the same owner FrameRateResolver and the ending credits
 * read; the record must carry that region.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kEndingVIntDmaNative {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    private record NativeRow(String region, int levelFrame, List<String> entries, long freeze) {
    }

    private static Map<String, List<NativeRow>> nativeRows() throws IOException {
        Map<String, List<NativeRow>> rows = new LinkedHashMap<>();
        try (InputStream in = TestS3kEndingVIntDmaNative.class.getResourceAsStream(
                "/timing/s3k-ending-vint-dma-native-v1.tsv")) {
            assertNotNull(in, "fixture resource");
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] f = line.split("\t");
                rows.computeIfAbsent(f[0], k -> new ArrayList<>()).add(new NativeRow(f[1], Integer.parseInt(f[2]),
                        "-".equals(f[3]) ? List.of() : List.of(f[3].split(",")), Long.parseLong(f[4])));
            }
        }
        return rows;
    }

    @ParameterizedTest
    @CsvSource({"tails-super,tails,3333333,ntsc", "sonic-super,sonic,3333333,ntsc", "sonic-none,sonic,0000000,ntsc",
            "tails-super,tails,3333333,pal", "sonic-super,sonic,3333333,pal"})
    void engineRecordsTheNativeVIntDmaWorkAtLf4400To4406(String product, String main, String emeralds,
                                                         String region) throws IOException {
        List<NativeRow> expected = nativeRows().get(product).stream().filter(r -> r.region().equals(region)).toList();
        assertEquals(7, expected.size(), product + " " + region);
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.REGION, region.toUpperCase(java.util.Locale.ROOT));
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        GameServices.camera().refreshViewportDimensions(config);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(13, 1).withFreshLevelStartLifecycle().build();
        var digits = emeralds.chars().map(c -> c - '0').boxed().toList();
        GameServices.gameState().restoreS3kEmeraldProgress(digits, digits.stream().anyMatch(c -> c >= 2));
        var ledger = S3kRuntimeArtCoordinator.from(GameServices.level().getObjectManager().getObjectServices())
                .vintDmaLedger();
        Map<Integer, Record> byLevelFrame = new LinkedHashMap<>();
        int guard = 0;
        // Native frame-dma.csv keys a V-int by Level_frame_counter read at Process_DMA_Queue ($1588): the value during
        // the V-int, before LevelLoop's addq at $6520. The engine drains in the represented V-blank before
        // advanceLevelFrameCounter, so its key is the counter before the step.
        while (GameServices.level().getFrameCounter() <= 4406) {
            if (++guard > 6000) {
                throw new AssertionError("ending did not reach LF 4406; at " + GameServices.level().getFrameCounter());
            }
            int counterDuringVInt = GameServices.level().getFrameCounter();
            Record before = ledger.lastRecord();
            fixture.stepFrame(false, false, false, false, false);
            Record after = ledger.lastRecord();
            if (after != null && after != before) {
                byLevelFrame.put(counterDuringVInt, after);
            }
        }
        StringBuilder mismatches = new StringBuilder();
        for (NativeRow row : expected) {
            Record record = byLevelFrame.get(row.levelFrame());
            List<String> entries = new ArrayList<>();
            if (record != null) {
                for (var entry : record.drained()) {
                    entries.add(String.format("%d@%04X", entry.words(), entry.vramAddress()));
                }
            }
            boolean equal = record != null && record.fixed() == FixedTransfers.VINT_8
                    && record.region().name().equalsIgnoreCase(region)
                    && entries.equals(row.entries())
                    && record.freezeStatus() == S3kVIntDmaLedger.FreezeStatus.CONDITIONAL_ON_VINT_START
                    && record.conditionalFreezeMclk() == row.freeze();
            if (!equal) {
                mismatches.append(String.format("%n%s %s LF %d native %s/%d engine %s", product, row.region(),
                        row.levelFrame(), row.entries(), row.freeze(), record == null ? "no draining V-int"
                                : record.fixed() + " " + entries + "/" + record.conditionalFreezeMclk() + " " + record.phase()
                                        + " " + record.region()));
            }
        }
        assertEquals("", mismatches.toString());
    }
}
