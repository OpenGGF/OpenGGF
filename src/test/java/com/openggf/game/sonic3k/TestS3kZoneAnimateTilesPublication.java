package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Pattern;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The custom zone routines in Offs_AniFunc (AnimateTiles_HCZ1 sonic3k.asm:53974, _HCZ2 :54163,
 * _CNZ :54373, _ICZ :54439, _LBZ1 :54544, _LBZ2 :54639, _MHZ :54820, _SOZ1 :54944, _SOZ2 :55002,
 * _LRZ1/2 :55045-55051) send every transfer through Add_To_DMA_Queue (:1673). LevelLoop calls
 * Animate_Tiles (:7906) after DeformBgLayer/ScreenEvents, and the queue reaches VRAM only in the
 * next VInt_8_Cont Process_DMA_Queue (:764), together with that iteration's scroll and SAT. The
 * engine presents scroll/SAT published at the top of the following iteration, so art written
 * directly by the animator appeared one frame early. Found by the 2026-10-15 bonus-stage spot
 * stills (commit 938aef1f6) and reported for these zones by the same lane.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kZoneAnimateTilesPublication {
    /** {@code ranges} holds inclusive first/last tile pairs written only by the custom routine. */
    private record Route(String name, int zone, int act, boolean vertical, int start, int step,
                         int[] ranges, ToIntFunction<Sonic3kPatternAnimator> phase) { }

    @BeforeAll
    static void configure() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
    }

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    void hcz1WaterlineStripsPublishAtTheFollowingVBlank() {
        // AnimateTiles_HCZ1 reads Events_bg+$10 (HCZ1 deform: camera Y against the $610 waterline).
        assertQueuedUntilNextVBlank(new Route("HCZ1", Sonic3kZoneIds.ZONE_HCZ, 0, true, 0x560, 0x10,
                new int[] {0x2DC, 0x30B}, Sonic3kPatternAnimator::computeHcz1WaterlineDelta));
    }

    @Test
    void hcz2BackgroundStripsPublishAtTheFollowingVBlank() {
        assertQueuedUntilNextVBlank(new Route("HCZ2", Sonic3kZoneIds.ZONE_HCZ, 1, false, 0x100, 0x18,
                new int[] {0x2D2, 0x31D}, Sonic3kPatternAnimator::computeHcz2CompositePhase));
    }

    @Test
    void cnzBackgroundPublishesAtTheFollowingVBlank() {
        assertQueuedUntilNextVBlank(new Route("CNZ1", Sonic3kZoneIds.ZONE_CNZ, 0, false, 0x100, 0x18,
                new int[] {0x308, 0x327}, Sonic3kPatternAnimator::computeCnzPhase));
    }

    @Test
    void mhzBackgroundPublishesAtTheFollowingVBlank() {
        assertQueuedUntilNextVBlank(new Route("MHZ1", Sonic3kZoneIds.ZONE_MHZ, 0, false, 0x100, 0x18,
                new int[] {0x1B8, 0x1BF, 0x1D5, 0x1F4}, animator -> animator.computeMhzBackgroundLayer1Phase() << 8
                        | animator.computeMhzBackgroundLayer2Phase()));
    }

    @Test
    void lrzBackgroundPublishesAtTheFollowingVBlank() {
        assertQueuedUntilNextVBlank(new Route("LRZ1", Sonic3kZoneIds.ZONE_LRZ, 0, false, 0x100, 0x18,
                new int[] {0x320, 0x34F}, animator -> animator.computeLrzBackgroundLayer1Phase() << 8
                        | animator.computeLrzBackgroundLayer2Phase()));
    }

    @Test
    void soz1BackgroundPublishesAtTheFollowingVBlank() {
        assertQueuedUntilNextVBlank(new Route("SOZ1", Sonic3kZoneIds.ZONE_SOZ, 0, false, 0x100, 0x18,
                new int[] {0x330, 0x341}, Sonic3kPatternAnimator::computeSoz1Phase));
    }

    @Test
    void iczBackgroundPublishesAtTheFollowingVBlank() {
        assertQueuedUntilNextVBlank(new Route("ICZ1", Sonic3kZoneIds.ZONE_ICZ, 0, false, 0x3A00, 0x18,
                new int[] {0x10E, 0x11D}, Sonic3kPatternAnimator::computeIczHorizontalPhase));
    }

    @Test
    void lbz1ScrollTilesPublishAtTheFollowingVBlank() {
        assertQueuedUntilNextVBlank(new Route("LBZ1", Sonic3kZoneIds.ZONE_LBZ, 0, false, 0x100, 0x18,
                new int[] {0x350, 0x364}, Sonic3kPatternAnimator::computeLbz1ScrollPhase));
    }

    @Test
    void soz2TorchArtIsQueuedForTheFollowingVBlank() {
        // AnimateTiles_SOZ2 (sonic3k.asm:55002-55035) steps its frame timer now and DMAs
        // ArtUnc_AniSOZ2_BG to VRAM $6600 (tile $330) through Add_To_DMA_Queue.
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_SOZ, 1).build();
        Sonic3kPatternAnimator animator = animator();
        boolean sawStep = false;
        for (int iteration = 0; iteration < 40; iteration++) {
            byte[] before = tiles(0x330, 0x335);
            animator.update();
            assertArrayEquals(before, tiles(0x330, 0x335),
                    "SOZ2 torch art is not visible before VInt, iteration " + iteration);
            animator.publishAniPlcAtVBlank();
            sawStep |= !Arrays.equals(before, tiles(0x330, 0x335));
        }
        assertTrue(sawStep, "SOZ2 torch frame changed within five timer periods");
    }

    @Test
    void rewindRestoresPresentedAndQueuedCustomArt() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0).build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        camera.setX((short) 0x100);
        fixture.stepIdleFrames(3);
        for (int x = 0x118; x < 0x400; x += 0x18) {
            byte[] presented = tiles(0x308, 0x327);
            int phase = animator.computeCnzPhase();
            camera.setX((short) x);
            fixture.stepIdleFrames(1);
            if (animator.computeCnzPhase() == phase) {
                continue;
            }
            PatternAnimatorSnapshot pending = animator.capture();
            fixture.stepIdleFrames(1);
            byte[] published = tiles(0x308, 0x327);
            if (Arrays.equals(presented, published)) {
                continue;
            }
            animator.restore(pending);
            assertArrayEquals(presented, tiles(0x308, 0x327), "restore presents the captured VRAM");
            animator.publishAniPlcAtVBlank();
            assertArrayEquals(published, tiles(0x308, 0x327), "restore keeps the queued transfer");
            return;
        }
        throw new AssertionError("CNZ sweep produced no published background change");
    }

    private static void assertQueuedUntilNextVBlank(Route route) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(route.zone(), route.act()).build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        move(camera, route, route.start());
        fixture.stepIdleFrames(3);
        int previousPhase = route.phase().applyAsInt(animator);
        byte[] presented = tiles(route.ranges());
        int changes = 0;
        for (int i = 1; i <= 24; i++) {
            move(camera, route, route.start() + i * route.step());
            fixture.stepIdleFrames(1);
            int phase = route.phase().applyAsInt(animator);
            byte[] afterIteration = tiles(route.ranges());
            if (phase == previousPhase) {
                assertArrayEquals(presented, afterIteration,
                        route.name() + " art changed without an AnimateTiles phase change, step " + i);
                continue;
            }
            // The iteration that computed the new phase queued its art; VRAM still holds the old frame.
            assertArrayEquals(presented, afterIteration,
                    route.name() + " Add_To_DMA_Queue art presented in its own iteration, step " + i
                            + " phase $" + Integer.toHexString(previousPhase)
                            + " -> $" + Integer.toHexString(phase));
            fixture.stepIdleFrames(1);
            assertEquals(phase, route.phase().applyAsInt(animator), route.name() + " frozen camera keeps the phase");
            byte[] afterVBlank = tiles(route.ranges());
            if (!Arrays.equals(presented, afterVBlank)) {
                changes++;
            }
            previousPhase = phase;
            presented = afterVBlank;
        }
        assertTrue(changes > 0, route.name() + " sweep produced no published background change");
        assertFalse(presented.length == 0);
    }

    private static void move(Camera camera, Route route, int position) {
        if (route.vertical()) {
            camera.setY((short) position);
        } else {
            camera.setX((short) position);
        }
    }

    private static Sonic3kPatternAnimator animator() {
        var manager = GameServices.level().getAnimatedPatternManager();
        assertTrue(manager instanceof Sonic3kLevelAnimationManager, "S3K level animation manager");
        return ((Sonic3kLevelAnimationManager) manager).patternAnimatorForTesting();
    }

    private static byte[] tiles(int... ranges) {
        var level = GameServices.level().getCurrentLevel();
        int count = 0;
        for (int r = 0; r < ranges.length; r += 2) count += ranges[r + 1] - ranges[r] + 1;
        byte[] packed = new byte[count * Pattern.PATTERN_SIZE_IN_ROM];
        int t = 0;
        for (int r = 0; r < ranges.length; r += 2) {
            for (int tile = ranges[r]; tile <= ranges[r + 1]; tile++, t++) {
                Pattern pattern = level.getPattern(tile);
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x += 2) {
                        packed[t * 32 + y * 4 + x / 2] = (byte) ((pattern.getPixel(x, y) & 15) << 4
                                | pattern.getPixel(x + 1, y) & 15);
                    }
                }
            }
        }
        return packed;
    }
}
