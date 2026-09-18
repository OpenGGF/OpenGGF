package com.openggf.game.sonic3k;

import com.openggf.game.GameServices;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@RequiresRom(SonicGame.SONIC_3K)
class TestFbzAnimatedTiles {
    @Test void freshSetupRunsPatternPassOnceWithoutSpendingGameplayOrGlobalAnimationClock() {
        HeadlessTestFixture.builder().withZoneAndAct(4, 0).build();
        var level = GameServices.level();
        var manager = (Sonic3kLevelAnimationManager) level.getAnimatedPatternManager();
        var scripts = manager.patternAnimatorForTesting().scriptsForTesting();
        int frame = level.getFrameCounter();
        int globalAngle = manager.aizVineAngleWord();
        assertEquals(0, scripts.get(0).getFrameIndex());
        assertTrue(level.consumePendingInitialProcessSpritesPass());
        assertEquals(frame, level.getFrameCounter(), "setup runs before the LevelLoop clock");
        assertEquals(globalAngle, manager.aizVineAngleWord(), "setup has no ChangeRingFrame");
        assertEquals(List.of(63, 7, 1, 7, 7), scripts.stream().map(AniPlcScriptState::getTimer).toList());
        assertTrue(scripts.stream().allMatch(script -> script.getFrameIndex() == 1));
        assertFalse(level.consumePendingInitialProcessSpritesPass());
        assertEquals(63, scripts.get(0).getTimer(), "one-shot setup must not double tick");
        manager.update();
        assertEquals(List.of(62, 6, 0, 6, 6), scripts.stream().map(AniPlcScriptState::getTimer).toList(),
                "ROM LFC1 is already one ordinary tick after the setup animation pass");
    }

    @Test void productionVBlankPublishesQueuedArtAndRewindPreservesBothSidesOfExpiry() {
        HeadlessTestFixture.builder().withZoneAndAct(4, 0).build();
        var level = GameServices.level();
        var manager = (Sonic3kLevelAnimationManager) level.getAnimatedPatternManager();
        var animator = manager.patternAnimatorForTesting();
        var script = animator.scriptsForTesting().get(3);
        var profile = GameServices.module().getLevelInitProfile();
        assertTrue(level.consumePendingInitialProcessSpritesPass());
        ((com.openggf.game.internal.QueuedPatternDmaPublication) profile).serviceQueuedPatternDma(
                com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL, false);
        byte[] first = patternBytes(level.getCurrentLevel().getPattern(0x200));
        for (int i = 0; i < 7; i++) { ((com.openggf.game.internal.QueuedPatternDmaPublication) profile).serviceQueuedPatternDma(
                com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL, false); manager.update(); }
        assertEquals(0, script.getTimer());
        manager.update();
        assertEquals(2, script.getFrameIndex(), "logical frame advances on expiry");
        assertArrayEquals(first, patternBytes(level.getCurrentLevel().getPattern(0x200)),
                "submitted art cannot appear before VBlank");
        var pending = manager.capture();
        ((com.openggf.game.internal.QueuedPatternDmaPublication) profile).serviceQueuedPatternDma(
                com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL, false);
        byte[] second = patternBytes(level.getCurrentLevel().getPattern(0x200));
        assertFalse(java.util.Arrays.equals(first, second), "ROM spike frames differ");
        manager.restore(pending);
        assertArrayEquals(first, patternBytes(level.getCurrentLevel().getPattern(0x200)),
                "rewind restores presented art without consuming queued art");
        assertEquals(2, script.getFrameIndex());
        ((com.openggf.game.internal.QueuedPatternDmaPublication) profile).serviceQueuedPatternDma(
                com.openggf.game.resources.PlcLifecyclePhase.ORDINARY_LEVEL, false);
        assertArrayEquals(second, patternBytes(level.getCurrentLevel().getPattern(0x200)));
        assertEquals(2, script.getFrameIndex(), "VBlank does not tick animation counters");
    }

    private static byte[] patternBytes(Pattern pattern) {
        byte[] bytes = new byte[64]; pattern.copyInto(bytes, 0); return bytes;
    }

    @Test void bothActsInstallFiveExactRomOwnedChannels() {
        for (int act = 0; act < 2; act++) {
            HeadlessTestFixture.builder().withZoneAndAct(4, act).build();
            List<AnimatedTileChannel> channels = GameServices.animatedTileChannelGraph().channels();
            assertEquals(5, channels.size());
            assertEquals(List.of(0x210, 0x230, 0x238, 0x200, 0x208),
                    channels.stream().map(c -> c.destinationPlan().primaryTile()).toList());
            assertEquals(List.of(0x22F, 0x237, 0x247, 0x207, 0x20F),
                    channels.stream().map(c -> c.destinationPlan().secondaryTile()).toList());
            assertEquals(List.of("s3k.fbz.script.0", "s3k.fbz.script.1", "s3k.fbz.script.2",
                            "s3k.fbz.script.3", "s3k.fbz.script.4"),
                    channels.stream().map(AnimatedTileChannel::channelId).toList());
        }
        assertEquals(0x200, Sonic3kConstants.ARTTILE_FBZ_SPIKES,
                "spikes reference script 3's live AniPLC destination; no level PLC owns it");
    }

    @Test void lockedOnAniPlcAddressesAreSAndKSide() {
        assertEquals(0x28906, Sonic3kConstants.ANIPLC_FBZ1_ADDR);
        assertEquals(0x28948, Sonic3kConstants.ANIPLC_FBZ2_ADDR);
    }

    @Test void parserAndTicksPreserveExactActSpecificScriptSourcesAndTiming() {
        Sonic3kPatternAnimator act1 = loadAnimator(0);
        List<AniPlcScriptState> one = act1.scriptsForTesting();
        assertScript(one.get(0), 0x3F, 0x210, 32, new int[]{0, 0});
        assertScript(one.get(1), 7, 0x230, 8, new int[]{0, 8, 0x10, 0, 8, 0x10});
        assertScript(one.get(2), 1, 0x238, 16, new int[]{0, 0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70});
        assertScript(one.get(3), 7, 0x200, 8, new int[]{0, 8});
        assertScript(one.get(4), 7, 0x208, 8, new int[]{0, 8, 0x10, 0, 8, 0x10});
        act1.update();
        assertEquals(0x3F, one.get(0).getTimer());
        assertEquals(1, one.get(0).getFrameIndex());

        Sonic3kPatternAnimator act2 = loadAnimator(1);
        List<AniPlcScriptState> two = act2.scriptsForTesting();
        assertScript(two.get(0), 1, 0x210, 32,
                new int[]{0, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0, 0xE0});
        act2.update();
        act2.update();
        assertEquals(0, two.get(0).getTimer());
        assertEquals(1, two.get(0).getFrameIndex());
        act2.update();
        assertEquals(1, two.get(0).getTimer());
        assertEquals(2, two.get(0).getFrameIndex());
    }

    @Test void spikeDestinationStaysLiveAndRewindRestoresItsScriptPhase() {
        Sonic3kPatternAnimator animator = loadAnimator(0);
        Pattern[] spikeRefs = new Pattern[8];
        for (int i = 0; i < spikeRefs.length; i++) {
            spikeRefs[i] = GameServices.level().getCurrentLevel().getPattern(0x200 + i);
        }
        animator.update();
        PatternAnimatorSnapshot first = animator.capture();
        for (int i = 0; i < 9; i++) animator.update();
        assertEquals(2, animator.scriptsForTesting().get(3).getFrameIndex());
        for (int i = 0; i < spikeRefs.length; i++) {
            assertSame(spikeRefs[i], GameServices.level().getCurrentLevel().getPattern(0x200 + i));
        }
        animator.restore(first);
        assertEquals(1, animator.scriptsForTesting().get(3).getFrameIndex());
        assertEquals(7, animator.scriptsForTesting().get(3).getTimer());
    }

    private static Sonic3kPatternAnimator loadAnimator(int act) {
        HeadlessTestFixture.builder().withZoneAndAct(4, act).build();
        Sonic3kLevelAnimationManager manager =
                (Sonic3kLevelAnimationManager) GameServices.level().getAnimatedPatternManager();
        return manager.patternAnimatorForTesting();
    }

    private static void assertScript(AniPlcScriptState script, int duration, int destination,
                                     int tileCount, int[] sources) {
        assertEquals(duration, script.globalDurationForTesting());
        assertEquals(destination, script.destinationTileIndex());
        assertEquals(tileCount, script.tilesPerFrame());
        assertArrayEquals(sources, script.frameTileIdsForTesting());
    }
}
