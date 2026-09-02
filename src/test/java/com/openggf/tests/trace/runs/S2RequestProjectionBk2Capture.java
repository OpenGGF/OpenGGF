package com.openggf.tests.trace.runs;

import com.openggf.audio.GameAudioProfile;
import com.openggf.audio.presentation.PresentationMode;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.tests.TestEnvironment;
import com.openggf.tools.audio.completerun.s2.S2ProductionRequestProjector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;

/** Test-only production-BK2 request observation for the fixed S2 window. */
final class S2RequestProjectionBk2Capture extends AbstractRunChainTest {
    private static final Path RUN_DIR = Path.of("src", "test", "resources",
            "traces", "s2", "runs", "s2-sonic-tails-complete-emeralds");
    private static final Path RUN_BK2 = RUN_DIR.resolve(
            "sonic-2-sonic-tails-complete-emeralds.bk2");
    private static final String ROM_SHA1 =
            "8bca5dcef1af3e00098666fd892dc1c2a76333f9";
    private static final String BK2_SHA256 =
            "e850798f882b8c580aad148bc97cb50f260cae1d336dd649fe2f4dfae6796aa5";
    private static final int FIRST_ROW = 10_150;
    private static final int EXCLUSIVE_END = 10_900;
    private static final int DESTINATION_SEGMENT = 2;
    private static final int DESTINATION_START_ROW = 10_334;

    S2RequestProjectionBk2TestBridge.Capture capture(
            Path romPath, Path bk2Path)
            throws Exception {
        requireIdentity(romPath, "SHA-1", ROM_SHA1, "S2 REV01 ROM");
        requireIdentity(bk2Path, "SHA-256", BK2_SHA256, "S2 complete-run BK2");
        requireIdentity(RUN_BK2, "SHA-256", BK2_SHA256,
                "repository S2 complete-run BK2");
        S2ProductionRequestProjector projector = new S2ProductionRequestProjector();
        List<Integer> requestRows = new ArrayList<>();
        int[] productionOutputRow = {-1};
        Sonic2AudioProfile profile = new Sonic2AudioProfile(event -> {
            int row = productionOutputRow[0];
            if (row >= FIRST_ROW && row < EXCLUSIVE_END) {
                int before = projector.requests().size();
                projector.accept(event);
                if (projector.requests().size() > before) {
                    requestRows.add(row);
                }
            }
        });
        Rom rom = new Rom();
        if (!rom.open(romPath.toString())) {
            throw new IllegalArgumentException("cannot open verified S2 REV01 ROM");
        }
        try {
            TestEnvironment.configureGameModuleFixture(
                    new ObservedSonic2Module(profile));
            RomManager.getInstance().setRom(rom);
            productionOutputRowObserver = row -> productionOutputRow[0] = row;
            afterProductionStep = () -> GameServices.audio()
                    .presentFrame(PresentationMode.FORWARD);
            assertChainReplayThroughSegmentRow(RUN_DIR, DESTINATION_SEGMENT,
                    EXCLUSIVE_END - DESTINATION_START_ROW);
            return new S2RequestProjectionBk2TestBridge.Capture(
                    projector, requestRows);
        } finally {
            productionOutputRowObserver = row -> { };
            afterProductionStep = () -> { };
            RomManager.getInstance().setRom(null);
            rom.close();
        }
    }

    private static void requireIdentity(Path path, String algorithm,
            String expected, String label) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException(label + " is absent");
        }
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, count);
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException(label + " identity differs");
            }
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalArgumentException("cannot verify " + label, failure);
        }
    }

    private static final class ObservedSonic2Module extends Sonic2GameModule {
        private final GameAudioProfile profile;

        private ObservedSonic2Module(GameAudioProfile profile) {
            this.profile = profile;
        }

        @Override
        public GameAudioProfile getAudioProfile() {
            return profile;
        }
    }
}
