package com.openggf.game.soniccd;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Native parity of the full-act routes (rehost-long: cold power-on movies from the stage start to the signpost) for the
 * stages of the Quartz Quadrant, Wacky Workbench, Stardust Speedway and Metallic Madness routes lanes (B and B2): tunnel path ($07, three routines), platform ($30), snake blocks ($2A), Dango ($2A), crusher ($26), tube door ($20)
 * the Metallic Madness moving spring ($0F), the seesaws ($2C, $35) and the free-path junction ($07 subtype $80). Each stage must equal the native trace, player and camera, through the row
 * pinned here; the row after it is the divergence the next owner takes. The pins only move up: a lane that fixes the next
 * divergence raises its stage's pin. Comparison only; the movie and trace are native.
 */
class TestSonicCdRoutesBLongNative {
    @TempDir
    Path tempDir;

    /**
     * Stage, first divergent row ({@link CdLongRoutePins}; a green stage is pinned at its row count: any later divergence fails), and what stops it:
     * Green to the end (pinned at the row count): R51A, R51B, R51C, R52A, R61A, R61B, R61C, R62A, R71A, R71B, R71C, R72A (R72A: the
     * landing's roll clear no longer moves X in a wall ground mode), R81C, R81A (3691 was not the crusher $34: the speed shoes'
     * countdown ticked on the frame hurt ended, one frame early, so the acceleration fell from $18 to $C two rows early; the powerup
     * check belongs to the turn of the routine that began the frame, objects/r6/player.asm:590-621, :649-710). R81B (3851 was a spin platform at ($19EC,$4C2) with no native counterpart: its followers outlived
     * a leader that CheckObjectDespawn2 unloaded, because they tested the leader's Java object, not the ID byte in its slot, as
     * loc_20D30C does, objects/r8/spin_platform_1.asm:141-160); R82A 4577: the player moves 5 of 6 pixels and loses its x_speed ($600 against 0) in the air, at x $1690, the edge of the twin
     * walker's left platform; native moves 6 and keeps the speed; slots equal to row 4580.
     */
    @ParameterizedTest(name = "{0} equals native through row {1}")
    @org.junit.jupiter.params.provider.MethodSource("pins")
    void routeEqualsNativeThroughItsPinnedRow(CdStagePayload payload, int pinnedFirstDivergence) throws Exception {
        Path cue = CdTestMedia.usaCue();
        Path root = CdTestMedia.nativeFile("rehost-long/README.md").getParent();
        var fixture = CdFixtureReplay.Fixture.under(root, payload);
        org.junit.jupiter.api.Assumptions.assumeTrue(fixture.present(), "Native route absent: " + payload);
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            Path config = Files.createDirectories(tempDir.resolve(payload.name()));
            var outcome = CdFixtureReplay.replay(disc, config, fixture, Integer.MAX_VALUE, true, -1, -1);
            assertTrue(outcome.firstErrorRow() < 0 || outcome.firstErrorRow() >= pinnedFirstDivergence,
                    payload + " diverges from the native trace at row " + outcome.firstErrorRow()
                            + ", before the pinned row " + pinnedFirstDivergence + ": " + outcome.fields());
        }
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> pins() {
        return CdLongRoutePins.FIRST_DIVERGENCE.entrySet().stream()
                .map(e -> org.junit.jupiter.params.provider.Arguments.of(e.getKey(), e.getValue()));
    }
}
