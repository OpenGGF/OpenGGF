package com.openggf.tools;

import com.openggf.route.InitialState;
import com.openggf.route.Inlet;
import com.openggf.route.InputTape;
import com.openggf.route.LaunchContract;
import com.openggf.route.LevelId;
import com.openggf.route.PadRow;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;
import com.openggf.route.RomIdentity;
import com.openggf.route.Roster;
import com.openggf.route.Seeds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/** A recorded route track reads as a repair reference only for the exact movie it was recorded from. */
class TestRouteTrackReference {
    private static final LaunchContract LAUNCH = new LaunchContract(RomIdentity.standard("s3k"),
            new Inlet.ColdLevel(new LevelId("s3k", 10, 0), false), new Roster("sonic", "tails"), 320, "off",
            new InitialState(0, Seeds.NONE));

    @TempDir Path dir;

    @Test
    void readsPositionsAndEpochsAndStopsAtTheDeathRow() throws Exception {
        Path movie = Files.writeString(dir.resolve("route.bk2"), "movie bytes");
        Path track = track(movie, sha(movie), "0,256,4014,0,0\n1,257,4014,0,0\n2,259,4010,1,1\n3,260,4010,1,1\n");
        var path = RouteTrackReference.read(track, movie, LAUNCH, rows(4));
        assertEquals(3, path.size(), "the death row is the last index");
        assertEquals(257, path.x(1));
        assertEquals(4010, path.y(2));
        assertEquals(rows(4).slice(0, 3), path.source());
        assertTrue(path.environment().contains("36aaddb37"), path.environment());
        assertEquals("36aaddb37", RouteTrackReference.header(track).engine());
    }

    @Test
    void aRecorderTrackCarriesExactPlayerStates() throws Exception {
        Path movie = Files.writeString(dir.resolve("route.bk2"), "movie bytes");
        Path track = Files.writeString(dir.resolve("route.csv"), "# engine 36aaddb37 movie route.bk2 sha256 " + sha(movie)
                + " zone 10 width 320 roster sonic/tails vint 0\n" + RouteTrackRecorder.COLUMNS + "\n"
                + "0,256,4014,0,0,0,2048,0,0,0,0,0,224,3076,0,0,0,0,0,0,0\n"
                + "1,257,4014,0,0,9728,2048,24,0,24,0,0,224,3076,512,0,12,0,12,0,0\n");
        var path = RouteTrackReference.read(track, movie, LAUNCH, rows(2));
        assertEquals(2, path.size());
        assertTrue(path.hasStates());
        assertEquals(257, path.x(1));
    }

    @Test
    void refusesATrackRecordedFromOtherRows() throws Exception {
        Path movie = Files.writeString(dir.resolve("route.bk2"), "movie bytes");
        // Bound to another movie's hash: the same rows file edited after recording is not this track's source.
        Path other = Files.writeString(dir.resolve("other.bk2"), "movie bytes, edited");
        Path track = track(movie, sha(other), "0,256,4014,0,0\n");
        var refused = assertThrows(IOException.class, () -> RouteTrackReference.read(track, movie, LAUNCH, rows(1)));
        assertTrue(refused.getMessage().contains("was recorded from movie"), refused.getMessage());
    }

    @Test
    void refusesAGapInTheRows() throws Exception {
        Path movie = Files.writeString(dir.resolve("route.bk2"), "movie bytes");
        Path track = track(movie, sha(movie), "0,256,4014,0,0\n2,259,4010,0,0\n");
        assertThrows(IOException.class, () -> RouteTrackReference.read(track, movie, LAUNCH, rows(3)));
    }

    private Path track(Path movie, String sha, String body) throws IOException {
        return Files.writeString(dir.resolve("route.csv"), "# engine 36aaddb37 movie " + movie.getFileName() + " sha256 "
                + sha + " zone 10 width 320 roster sonic/tails vint 0\nrow,x,y,epoch,dead\n" + body);
    }

    private static InputTape rows(int n) {
        var tape = new InputTape.Builder();
        for (int i = 0; i < n; i++) tape.add(i % 2 == 0 ? PadRow.NEUTRAL : PadRow.of(PlayerSlot.P1, PhysicalButton.RIGHT));
        return tape.build();
    }

    private static String sha(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
