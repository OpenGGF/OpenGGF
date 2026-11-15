package com.openggf.game.soniccd;

import com.openggf.audio.AudioRequestObserver;
import com.openggf.game.GameServices;
import com.openggf.game.soniccd.audio.CdSoundRequests;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The act 3 copies of {@code StartResults} (Level End/Main.asm:816-866): the flower capsule ($15) and Stardust Speedway
 * act 3's Amy ($33) request the results music by ID on the row native runs it. {@code StartResults} writes
 * {@code SCMD_RESULTMUS} (after {@code SCMD_FADEPCM} in the past only; every act 3 is a future) in the same block as its
 * {@code addq.b #2,oRoutine}, so the native row is the first whose post-frame RAM (the boss probe's
 * {@code probe/objects.csv}) shows the object's next routine: the capsule's 8, Amy's $C. The engine replays the movie
 * (comparison only), and the end-of-act requests made during each row are logged by ID, as
 * {@code TestSonicCdActEndNative} does: they must be exactly {@code SCMD_RESULTMUS} on that row and none on the rows
 * before it.
 */
class TestCdBossResultsMusic {
    @TempDir
    Path tempDir;

    /** R33C-fight: the capsule breaks (+1763) and starts the results; native routine 8 from row 2139. */
    @Test
    void capsuleRequestsTheResultsMusicOnNativesRow() throws Exception {
        check("rehost-boss-ccz/R33C-fight", CdStagePayload.R33C, 0x15, 8);
    }

    /** R73C-hug: Amy's hug starts the results (routine $A to $C); native routine $C from row 4919. */
    @Test
    void amysHugRequestsTheResultsMusicOnNativesRow() throws Exception {
        check("rehost-boss-ssz/R73C-hug", CdStagePayload.R73C, 0x33, 0xC);
    }

    private void check(String movie, CdStagePayload payload, int id, int nextRoutine) throws Exception {
        Path root = CdTestMedia.nativeFile(movie + "/manifest.json").getParent();
        int offset = Integer.parseInt(CczBossNativeReplay.json(Files.readString(root.resolve("trace/metadata.json")),
                "bk2_frame_offset"));
        int nativeRow = -1;
        List<String> lines = Files.readAllLines(root.resolve("probe/objects.csv"));
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            int row = Integer.parseInt(c[0]) - offset - 1;
            if (row >= 0 && Integer.parseInt(c[2], 16) == id && Integer.parseInt(c[4]) == nextRoutine) {
                nativeRow = row;
                break;
            }
        }
        assertTrue(nativeRow > 0, movie + ": native never reaches routine " + Integer.toHexString(nextRoutine));
        int last = nativeRow + 3;
        Map<Integer, String> requests = new TreeMap<>();
        StringBuilder pending = new StringBuilder();
        java.util.Set<Integer> endSounds = java.util.Set.of(CdSoundRequests.FADE_PCM, CdSoundRequests.RESULTS_MUSIC);
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            CdFixtureReplay.replay(disc, tempDir, new CdFixtureReplay.Fixture(payload, root.resolve("trace"),
                    root.resolve("movie.bk2")), last, false, -1, -1, new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            if (row == 0) {
                                GameServices.audio().setRequestObserver((kind, sound) -> {
                                    if ((kind == AudioRequestObserver.RequestClass.MUSIC
                                            || kind == AudioRequestObserver.RequestClass.COMMAND)
                                            && endSounds.contains(sound)) {
                                        pending.append(' ').append(Integer.toHexString(sound).toUpperCase());
                                    }
                                });
                                pending.setLength(0);
                                return;
                            }
                            if (pending.length() > 0) requests.put(row, pending.toString().trim());
                            pending.setLength(0);
                        }
                    });
        } finally {
            GameServices.audio().setRequestObserver(AudioRequestObserver.NONE);
        }
        System.out.println("CDRESULTS " + movie + ": native StartResults row " + nativeRow + ", engine requests " + requests);
        assertEquals(Map.of(nativeRow, Integer.toHexString(CdSoundRequests.RESULTS_MUSIC).toUpperCase()), requests,
                movie + ": the end-of-act requests by row (only SCMD_RESULTMUS, on native's StartResults row)");
    }
}
