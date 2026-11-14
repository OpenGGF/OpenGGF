package com.openggf.game.soniccd;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native movies authored for Metallic Madness's player ({@code <soniccd.native.root>/rehost-zph4/}, README there;
 * Go-Explore search {@code _tools/goal_search.py} on the in-memory search server, recorded cold from power-on):
 * <ul>
 *   <li>{@code disc/R82A}: Sonic reaches a Spin Disc and is held by it ({@code move.b #1,obj.var_38(a1)},
 *   objects/r8/spin_disc.asm:83-94), runs around it under the disc's ground-speed clamp, then jumps;</li>
 *   <li>{@code layer/R82A}: Sonic rides a Revolve Door ($3E at x $848): the grab at row 1896 sets the object-control bit
 *   and animation $33, the release at row 1929 (animation 5) flips {@code stage_layer}, the facing and the tile priority
 *   (objects/r8/revolve_door.asm:121-175). On the way it crosses a Spin Disc at row 780 (the disc's
 *   {@code move.b #1,obj.prev_anim_id(a1)} restarts the walk script, spin_disc.asm:93) and walks off a spring at row
 *   1188 after the disc's stick flag was cleared by {@code GetOffObject} (common/solid_object.asm:29). Equal to its
 *   end.</li>
 *   <li>{@code layer2/R82A}: walks past a layer-2 monitor at row 2332 (Metallic Madness's monitors are solid only on the
 *   player's layer, {@code ObjMonitor_Solid}, Monitor and Time Post/Main.asm:242-259) and rides the Revolve Door at
 *   x $E48 (grab row 2412, release row 2445 with {@code stage_layer} 1). Equal to its end.</li>
 *   <li>{@code rehost-boss-mmz/R83C-approach}: the Metallic Madness act 3 route rides the Revolve Door twice
 *   ({@code stage_layer} 0, 1, 0) to the Hotaru room. Equal to its end.</li>
 * </ul>
 * Trace data is comparison-only: the BK2 drives the engine.
 */
class TestMmzPlayerMovies {
    @TempDir
    Path tempDir;

    @Test
    void spinDiscHoldsThePlayer() throws Exception {
        replay("disc", 1000);
    }

    @Test
    void revolveDoorCarriesThePlayerToTheOtherLayer() throws Exception {
        replay("layer", 2104);
    }

    @Test
    void revolveDoorOnTheLowerRoute() throws Exception {
        replay("layer2", 2567);
    }

    @Test
    void actThreeApproachRidesTheRevolveDoorTwice() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-boss-mmz/R83C-approach/movie.bk2");
        replay(new CdFixtureReplay.Fixture(CdStagePayload.R83C, movie.getParent().resolve("trace"), movie),
                "R83C-approach", 2267);
    }

    /**
     * {@code rehost-boss-mmz/R83C-fight}: the act 3 route, the Hotaru room and the final fight to Amy. Row 3199: the
     * player's head meets the opening door $29's top half where it stood before the frame's move (both halves' solids run
     * first, door.asm:169-175). Row 3786: Left and Right held together turn the player twice a frame and restart the walk
     * script each time (Sonic/Main.asm:847-855, 1243-1247). Equal to native through row 6965.
     */
    @Test
    void actThreeFightMovieToAmy() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-boss-mmz/R83C-fight/movie.bk2");
        replay(new CdFixtureReplay.Fixture(CdStagePayload.R83C, movie.getParent().resolve("trace"), movie),
                "R83C-fight", 6965);
    }

    /** {@code rehost-boss-mmz/R83D-fight}: the bad future's route and fight; row 5577 (native is hurt) is the next stop. */
    @Test
    void badFutureFightMovieToTheThirdHitWindow() throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-boss-mmz/R83D-fight/movie.bk2");
        replay(new CdFixtureReplay.Fixture(CdStagePayload.R83D, movie.getParent().resolve("trace"), movie),
                "R83D-fight", 5576);
    }

    private void replay(String name, int lastRow) throws Exception {
        Path movie = CdTestMedia.nativeFile("rehost-zph4/" + name + "/R82A/movie.bk2");
        replay(new CdFixtureReplay.Fixture(CdStagePayload.R82A, movie.getParent().resolve("trace"), movie), name, lastRow);
    }

    /** Every row through {@code lastRow} (the movie's last for the green movies) equals native. */
    private void replay(CdFixtureReplay.Fixture fixture, String name, int lastRow) throws Exception {
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            Path config = Files.createDirectories(tempDir.resolve(name));
            var outcome = CdFixtureReplay.replay(disc, config, fixture, lastRow, true, -1, -1);
            for (CdFixtureReplay.Row row : outcome.rows()) System.out.println("[mmz-movie] " + name + " " + row.format());
            System.out.println("[mmz-movie] " + name + " first error " + outcome.firstErrorRow() + " of "
                    + outcome.rowsCompared() + " " + outcome.fields());
            assertTrue(outcome.firstErrorRow() < 0,
                    () -> name + " diverged at row " + outcome.firstErrorRow() + " " + outcome.fields());
            assertTrue(outcome.rowsCompared() > lastRow, () -> name + " compared only " + outcome.rowsCompared() + " rows");
        }
    }
}
