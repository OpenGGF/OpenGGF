package com.openggf.game.sonic3k;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Pattern;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void iczPreSubmissionRestorePreservesEveryDirectRangeAndItsQueuedPublication() throws IOException {
        GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0).withFreshLevelStartLifecycle().build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        // Declared camera inlet: horizontal phase $08 exercises both split transfers;
        // Y $702 gives non-tile-aligned source rows in every vertical transfer.
        camera.setX((short) 0x3960);
        camera.setY((short) 0x0702);
        assertEquals(8, animator.computeIczHorizontalPhase());
        assertTrue(animator.pendingAniPlcTransfers().isEmpty(), "before the first submission");

        byte[] earlierHorizontal = distinctTileBytes(0x200, 0x31);
        byte[] earlierVertical = distinctTileBytes(0x1E0, 0x73);
        writeTiles(0x10E, earlierHorizontal);
        writeTiles(0x122, earlierVertical);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var beforeInit = registry.capture();
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        assertArrayEquals(earlierHorizontal, tiles(0x10E, 0x11D), "submission retains presented horizontal art");
        assertArrayEquals(earlierVertical, tiles(0x122, 0x130), "submission retains all four presented vertical ranges");
        assertIczSplitPending(animator);
        var queued = registry.capture();
        PatternAnimatorSnapshot queuedAnimator = animator.capture();

        // AnimateTiles_ICZ at the declared inlet: the horizontal $C0/$40-word split
        // wraps Art 1, while Arts 2-5 begin at byte offsets $FC/$3C/$3C/$1C.
        byte[] horizontal = concat(rom(Sonic3kConstants.ART_UNC_ANI_ICZ_1_ADDR + 0x80, 0x180),
                rom(Sonic3kConstants.ART_UNC_ANI_ICZ_1_ADDR, 0x80));
        byte[] vertical = concat(
                concat(rom(Sonic3kConstants.ART_UNC_ANI_ICZ_2_ADDR + 0xFC, 0x100),
                        rom(Sonic3kConstants.ART_UNC_ANI_ICZ_3_ADDR + 0x3C, 0x80)),
                concat(rom(Sonic3kConstants.ART_UNC_ANI_ICZ_4_ADDR + 0x3C, 0x40),
                        rom(Sonic3kConstants.ART_UNC_ANI_ICZ_5_ADDR + 0x1C, 0x20)));
        var coordinator = new PlcFrameLifecycleCoordinator((PlcLifecycleService) null);
        var context = LevelFrameContext.from(fixture.gameplayMode());
        var lag = coordinator.latchBeforeFadeUpdate();
        assertTrue(lag.claim(PlcLifecyclePhase.LAG));
        LevelFrameStep.dispatchGameVBlank(context, lag);
        assertArrayEquals(earlierHorizontal, tiles(0x10E, 0x11D), "lag does not publish horizontal art");
        assertArrayEquals(earlierVertical, tiles(0x122, 0x130), "lag does not publish vertical art");
        assertIczSplitPending(animator);
        lag.finish();
        var firstVBlank = coordinator.latchBeforeFadeUpdate();
        assertTrue(firstVBlank.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        LevelFrameStep.dispatchGameVBlank(context, firstVBlank);
        assertArrayEquals(horizontal, tiles(0x10E, 0x11D), "both horizontal slices, including both range ends");
        assertArrayEquals(vertical, tiles(0x122, 0x130), "all four ROM row slices, including every range end");
        assertTrue(animator.pendingAniPlcTransfers().isEmpty());

        // Capture must read a later other-owner write, rather than constructor-cached
        // art or the animator's last published payload.
        byte[] laterHorizontal = distinctTileBytes(0x200, 0xA4);
        byte[] laterVertical = distinctTileBytes(0x1E0, 0xE2);
        writeTiles(0x10E, laterHorizontal);
        writeTiles(0x122, laterVertical);
        var later = registry.capture();
        writeTiles(0x10E, distinctTileBytes(0x200, 0x17));
        writeTiles(0x122, distinctTileBytes(0x1E0, 0x58));
        registry.restore(later);
        assertArrayEquals(laterHorizontal, tiles(0x10E, 0x11D), "capture-time horizontal bytes from another owner");
        assertArrayEquals(laterVertical, tiles(0x122, 0x130), "capture-time vertical bytes from another owner");

        registry.restore(beforeInit);
        assertArrayEquals(earlierHorizontal, tiles(0x10E, 0x11D), "pre-submission restore removes future horizontal art");
        assertArrayEquals(earlierVertical, tiles(0x122, 0x130), "pre-submission restore removes future vertical art");
        assertTrue(animator.pendingAniPlcTransfers().isEmpty(), "pre-submission restore creates no work");
        assertTrue(GameServices.level().consumePendingInitialProcessSpritesPass());
        PatternAnimatorSnapshot repeatedAnimator = animator.capture();
        assertArrayEquals(queuedAnimator.scriptCounters(), repeatedAnimator.scriptCounters(), "repeated initial script counters");
        assertArrayEquals(queuedAnimator.handlerCounters(), repeatedAnimator.handlerCounters(), "repeated initial handler counters");
        assertArrayEquals(queuedAnimator.extra(), repeatedAnimator.extra(), "real initial pass repeats the complete animator bytes");
        assertIczSplitPending(animator);
        LevelFrameStep.dispatchGameVBlank(context, firstVBlank);
        assertArrayEquals(earlierHorizontal, tiles(0x10E, 0x11D), "a stale token cannot publish newly restored work");
        assertArrayEquals(earlierVertical, tiles(0x122, 0x130), "a stale token cannot drain vertical work twice");
        assertIczSplitPending(animator);
        firstVBlank.finish();

        var nextVBlank = coordinator.latchBeforeFadeUpdate();
        assertTrue(nextVBlank.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        LevelFrameStep.dispatchGameVBlank(context, nextVBlank);
        assertArrayEquals(horizontal, tiles(0x10E, 0x11D));
        assertArrayEquals(vertical, tiles(0x122, 0x130));
        nextVBlank.finish();
        registry.restore(queued);
        assertArrayEquals(earlierHorizontal, tiles(0x10E, 0x11D), "pending restore presents the earlier bytes immediately");
        assertArrayEquals(earlierVertical, tiles(0x122, 0x130));
        assertIczSplitPending(animator);
        var restoredVBlank = coordinator.latchBeforeFadeUpdate();
        assertTrue(restoredVBlank.claim(PlcLifecyclePhase.ORDINARY_LEVEL));
        LevelFrameStep.dispatchGameVBlank(context, restoredVBlank);
        assertArrayEquals(horizontal, tiles(0x10E, 0x11D), "restored pending horizontal slices publish legally");
        assertArrayEquals(vertical, tiles(0x122, 0x130), "restored pending vertical slices publish legally");
        assertTrue(animator.pendingAniPlcTransfers().isEmpty());
        restoredVBlank.finish();
    }

    @Test
    void iczAct2PreSubmissionSnapshotOwnsHorizontalArtWithoutClaimingAct1VerticalArt() {
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 1).build();
        Sonic3kPatternAnimator animator = animator();
        assertTrue(animator.pendingAniPlcTransfers().isEmpty(), "act 2 before the first submission");
        byte[] horizontal = distinctTileBytes(0x200, 0x42);
        byte[] vertical = distinctTileBytes(0x1E0, 0x65);
        writeTiles(0x10E, horizontal);
        writeTiles(0x122, vertical);
        PatternAnimatorSnapshot before = animator.capture();
        byte[] laterVertical = distinctTileBytes(0x1E0, 0xC9);
        writeTiles(0x10E, distinctTileBytes(0x200, 0x86));
        writeTiles(0x122, laterVertical);
        animator.restore(before);
        assertArrayEquals(horizontal, tiles(0x10E, 0x11D), "act 2 still owns its direct horizontal range before submission");
        assertArrayEquals(laterVertical, tiles(0x122, 0x130), "act 2 must not restore act 1's vertical destinations");
        assertTrue(animator.pendingAniPlcTransfers().isEmpty());
        assertFalse(animator.shouldRunIczAct1VerticalCustomChannels());
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

    @Test
    void aiz2FirstTreeIsQueuedOnEveryPassBelow1C0() throws IOException {
        // AnimateTiles_AIZ2 (sonic3k.asm:53953-53967): below Camera_X_pos $1C0 every pass ends by
        // queueing ArtUnc_AniAIZ2_FirstTree ($230 words to tile $0CA) with Add_To_DMA_Queue.
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 1).build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        camera.setX((short) 0x100);
        fixture.stepIdleFrames(2);
        byte[] tree = rom(Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_ADDR, Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE);
        assertArrayEquals(tree, tiles(0x0CA, 0x0EC), "first tree resident below $1C0");

        writeTiles(0x0CA, new byte[tree.length]);
        animator.update();
        assertArrayEquals(new byte[tree.length], tiles(0x0CA, 0x0EC), "first tree not presented before VInt");
        animator.publishAniPlcAtVBlank();
        assertArrayEquals(tree, tiles(0x0CA, 0x0EC), "every pass below $1C0 queues the first tree again");
    }

    @Test
    void aiz2FirstTreeReturnsAtTheFollowingVBlank() throws IOException {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 1).build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        camera.setX((short) 0x200);
        fixture.stepIdleFrames(3);
        byte[] tree = rom(Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_ADDR, Sonic3kConstants.ART_UNC_AIZ2_FIRST_TREE_SIZE);
        byte[] scripted = tiles(0x0CA, 0x0EC);
        assertFalse(Arrays.equals(tree, scripted), "AniPLC_AIZ2 scripts 1-4 replaced the tree at $1C0+");

        camera.setX((short) 0x100);
        animator.update();
        assertArrayEquals(scripted, tiles(0x0CA, 0x0EC), "Add_To_DMA_Queue art presented in its own iteration");
        animator.publishAniPlcAtVBlank();
        assertArrayEquals(tree, tiles(0x0CA, 0x0EC), "first tree presented by the following VInt");
    }

    @Test
    void aiz2Below1C0ClearsTheSecondAndThirdScriptCounters() {
        // After AnimateTiles_DoAniPLC_Part2 runs the first script only, a3 points at
        // Anim_Counters+2 and clr.l (a3) zeroes the counters of scripts 1 and 2 (sonic3k.asm:53958-53963).
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 1).build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        camera.setX((short) 0x200);
        fixture.stepIdleFrames(5);
        PatternAnimatorSnapshot.ScriptCounter[] above = animator.capture().scriptCounters();
        assertTrue(above.length >= 5, "AniPLC_AIZ2 has five scripts");
        assertNotEquals(new PatternAnimatorSnapshot.ScriptCounter(0, 0), above[1], "script 1 advanced at $1C0+");
        assertNotEquals(new PatternAnimatorSnapshot.ScriptCounter(0, 0), above[2], "script 2 advanced at $1C0+");

        camera.setX((short) 0x100);
        animator.update();
        PatternAnimatorSnapshot.ScriptCounter[] below = animator.capture().scriptCounters();
        assertEquals(new PatternAnimatorSnapshot.ScriptCounter(0, 0), below[1], "Anim_Counters+2 cleared");
        assertEquals(new PatternAnimatorSnapshot.ScriptCounter(0, 0), below[2], "Anim_Counters+4 cleared");
        assertEquals(above[3], below[3], "Anim_Counters+6 untouched");
        assertEquals(above[4], below[4], "Anim_Counters+8 untouched");
    }

    @Test
    void hcz1EquilibriumFallsThroughBothFixRoutinesAndSkipsAniPlc() throws IOException {
        // Events_bg+$10 == 0: loc_2797E stores 1 in Anim_Counters+4, calls AniHCZ_FixLowerBG and
        // falls through into AniHCZ_FixUpperBG, whose jmp returns before AnimateTiles_DoAniPLC
        // (sonic3k.asm:53993, 54078-54099). The stored 1 differs from 0, so every pass repeats it.
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        camera.setY((short) 0x640);
        fixture.stepIdleFrames(3);
        assertTrue(animator.computeHcz1WaterlineDelta() < 0, "below the waterline");
        byte[] upperFix = concat(rom(Sonic3kConstants.ART_UNC_FIX_HCZ1_UPPER_BG1_ADDR, 0x180),
                rom(Sonic3kConstants.ART_UNC_FIX_HCZ1_UPPER_BG2_ADDR, 0x180));
        byte[] lowerFix = concat(rom(Sonic3kConstants.ART_UNC_FIX_HCZ1_LOWER_BG1_ADDR, 0x180),
                rom(Sonic3kConstants.ART_UNC_FIX_HCZ1_LOWER_BG2_ADDR, 0x180));
        assertFalse(Arrays.equals(upperFix, tiles(0x2DC, 0x2F3)), "composed waterline strip at $2DC");

        camera.setY((short) 0x610);
        assertEquals(0, animator.computeHcz1WaterlineDelta());
        PatternAnimatorSnapshot.ScriptCounter[] counters = animator.capture().scriptCounters();
        fixture.stepIdleFrames(2);
        assertArrayEquals(upperFix, tiles(0x2DC, 0x2F3), "AniHCZ_FixUpperBG reached by fall-through");
        assertArrayEquals(lowerFix, tiles(0x2F4, 0x30B), "AniHCZ_FixLowerBG");
        for (int pass = 0; pass < 12; pass++) {
            fixture.stepIdleFrames(1);
            assertArrayEquals(counters, animator.capture().scriptCounters(),
                    "AnimateTiles_DoAniPLC skipped at the equilibrium, pass " + pass);
        }
        camera.setY((short) 0x640);
        fixture.stepIdleFrames(12);
        assertFalse(Arrays.equals(counters, animator.capture().scriptCounters()), "AniPLC resumes off the equilibrium");
    }

    @Test
    void hcz1BeyondSixtyLinesKeepsTheLastComposedStrip() throws IOException {
        // addi.w #$60 / bcc loc_2797A (sonic3k.asm:54001-54002, 54042-54044): past +-$60 the routine
        // queues nothing and VRAM keeps the strip composed on the last pass inside the range.
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        Sonic3kPatternAnimator animator = animator();

        camera.setY((short) 0x67A);
        fixture.stepIdleFrames(3);
        assertEquals(-0x50, animator.computeHcz1WaterlineDelta());
        byte[] below = composeHcz1Strip(Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR, 0x10);
        assertFalse(Arrays.equals(below, concat(rom(Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR, 0x180),
                rom(Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_BELOW2_ADDR, 0x180))), "composed strip differs from full art");
        assertArrayEquals(below, tiles(0x2DC, 0x2F3), "loc_278C6 composition at $2DC");
        camera.setY((short) 0x700);
        fixture.stepIdleFrames(3);
        assertTrue(animator.computeHcz1WaterlineDelta() < -0x60);
        assertArrayEquals(below, tiles(0x2DC, 0x2F3), "no transfer below -$60");

        camera.setY((short) 0x5A5);
        fixture.stepIdleFrames(3);
        assertEquals(0x50, animator.computeHcz1WaterlineDelta());
        byte[] above = composeHcz1Strip(Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE1_ADDR, 0x10);
        assertArrayEquals(above, tiles(0x2F4, 0x30B), "loc_27934 composition at $2F4");
        camera.setY((short) 0x500);
        fixture.stepIdleFrames(3);
        assertTrue(animator.computeHcz1WaterlineDelta() > 0x60);
        assertArrayEquals(above, tiles(0x2F4, 0x30B), "no transfer above +$60");
    }

    @Test
    void hcz1FreshLevelRunsAnimateInitWithTheCappedEventsBgValue() throws IOException {
        // HCZ1_BackgroundInit leaves Events_bg+$10 at $60 when it exceeds $60 (sonic3k.asm:105690-105699);
        // Animate_Init's sub_279D4 then runs AniHCZ_FixUpperBG and the loc_2788C composition at
        // offset 0 (sonic3k.asm:54117-54152). Above $60 the following passes queue nothing.
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        Sonic3kPatternAnimator animator = animator();
        fixture.camera().setFrozen(true);
        fixture.stepIdleFrames(1);
        assertTrue(animator.computeHcz1WaterlineDelta() > 0x60, "HCZ1 starts high above the waterline");
        assertArrayEquals(concat(rom(Sonic3kConstants.ART_UNC_FIX_HCZ1_UPPER_BG1_ADDR, 0x180),
                        rom(Sonic3kConstants.ART_UNC_FIX_HCZ1_UPPER_BG2_ADDR, 0x180)),
                tiles(0x2DC, 0x2F3), "AniHCZ_FixUpperBG from Animate_Init");
        assertArrayEquals(composeHcz1Strip(Sonic3kConstants.ART_UNC_HCZ1_WATERLINE_ABOVE1_ADDR, 0),
                tiles(0x2F4, 0x30B), "loc_27934 composition at offset 0 from Animate_Init");
    }

    @Test
    void hcz1StopsAnimatingOnceTheHcz2ArtIsQueued() {
        // AnimateTiles_HCZ1 returns at once while Events_bg+$16 is set (sonic3k.asm:53974-53977);
        // HCZ1BGE_Normal sets it when it queues the HCZ2 art (sonic3k.asm:105716-105738).
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        camera.setY((short) 0x640);
        fixture.stepIdleFrames(3);
        var events = ((Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider()).getHczEvents();
        events.setBgRoutine(4); // HCZ1BGE_DoTransition
        PatternAnimatorSnapshot.ScriptCounter[] counters = animator.capture().scriptCounters();
        byte[] strips = tiles(0x2DC, 0x30B);
        camera.setY((short) 0x67A);
        for (int pass = 0; pass < 12; pass++) {
            animator.update();
            animator.publishAniPlcAtVBlank();
        }
        assertArrayEquals(counters, animator.capture().scriptCounters(), "no AnimateTiles_DoAniPLC while the HCZ2 art loads");
        assertArrayEquals(strips, tiles(0x2DC, 0x30B), "no waterline transfer while the HCZ2 art loads");
        events.setBgRoutine(0);
    }

    @Test
    void hcz1RewindRestoresTheWaterlineCounters() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_HCZ, 0).build();
        Sonic3kPatternAnimator animator = animator();
        Camera camera = fixture.camera();
        camera.setFrozen(true);
        camera.setY((short) 0x640);
        fixture.stepIdleFrames(3);
        camera.setY((short) 0x610);
        fixture.stepIdleFrames(3);
        PatternAnimatorSnapshot atEquilibrium = animator.capture();
        byte[] presented = tiles(0x2DC, 0x30B);

        camera.setY((short) 0x640);
        fixture.stepIdleFrames(3);
        assertFalse(Arrays.equals(presented, tiles(0x2DC, 0x30B)), "composed strip after leaving the equilibrium");
        animator.restore(atEquilibrium);
        assertArrayEquals(presented, tiles(0x2DC, 0x30B), "restore presents the captured strips");

        // The equilibrium pass left Anim_Counters+4 at 1, so Events_bg+$10 = 1 compares equal and
        // AnimateTiles_HCZ1 queues nothing; a lost counter would compose the strip at $2F4.
        camera.setY((short) 0x60E);
        assertEquals(1, animator.computeHcz1WaterlineDelta());
        animator.update();
        animator.publishAniPlcAtVBlank();
        assertArrayEquals(presented, tiles(0x2DC, 0x30B), "restored Anim_Counters+4 suppresses the transfer");
    }

    /** loc_278C6 / loc_27934: $60 rows from HCZ_WaterlineScroll_Data pick longwords from art and art+$600. */
    private static byte[] composeHcz1Strip(int artAddress, int tableRow) throws IOException {
        byte[] table = rom(Sonic3kConstants.HCZ_WATERLINE_SCROLL_DATA_ADDR + tableRow * 0x60, 0x60);
        byte[] out = new byte[0x300];
        for (int row = 0; row < 0x60; row++) {
            int source = (table[row] & 0xFF) << 2;
            System.arraycopy(rom(artAddress + source, 4), 0, out, row << 2, 4);
            System.arraycopy(rom(artAddress + 0x600 + source, 4), 0, out, 0x180 + (row << 2), 4);
        }
        return out;
    }

    private static byte[] rom(int address, int length) throws IOException {
        return RomByteReader.fromRom(GameServices.rom().getRom()).slice(address, length);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] out = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, out, first.length, second.length);
        return out;
    }

    private static void writeTiles(int firstTile, byte[] segaBytes) {
        var level = GameServices.level().getCurrentLevel();
        for (int tile = 0; tile < segaBytes.length / 32; tile++) {
            level.getPattern(firstTile + tile).fromSegaFormat(Arrays.copyOfRange(segaBytes, tile * 32, tile * 32 + 32));
        }
    }

    private static byte[] distinctTileBytes(int length, int seed) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (seed + i * 13 + (i / 32) * 7);
        return bytes;
    }

    private static void assertIczSplitPending(Sonic3kPatternAnimator animator) {
        int[][] expected = {{0x11E, 0x80}, {0x10E, 0x180}, {0x11A, 0x80},
                {0x122, 0x100}, {0x12A, 0x80}, {0x12E, 0x40}, {0x130, 0x20}};
        var pending = animator.pendingAniPlcTransfers();
        assertEquals(expected.length, pending.size(), "script then both horizontal slices then all four vertical slices");
        for (int i = 0; i < expected.length; i++) assertArrayEquals(expected[i], pending.get(i), "submission order " + i);
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
