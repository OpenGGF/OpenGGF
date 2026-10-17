package com.openggf.game.sonic3k.features;

import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.tests.TestablePlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestHCZWaterSkimHandler {

    @BeforeEach
    @AfterEach
    void resetSkimHandler() {
        HCZWaterSkimHandler.reset();
    }

    @Test
    void updateProcessesNativePlayersThenExtraSidekicks() {
        AbstractPlayableSprite main = skimmingCandidate();
        AbstractPlayableSprite nativeP2 = skimmingCandidate();
        AbstractPlayableSprite extraSidekick = skimmingCandidate();
        ObjectPlayerQuery query = new ObjectPlayerQuery(
                () -> main,
                () -> List.of(nativeP2, extraSidekick));

        HCZWaterSkimHandler.update(query, 0x200, 1);

        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
        assertTrue(HCZWaterSkimHandler.isSkimActiveP2());
        assertTrue(HCZWaterSkimHandler.isSkimActive(2));
        verify(main).setWaterSkimActive(true);
        verify(nativeP2).setWaterSkimActive(true);
        verify(extraSidekick).setWaterSkimActive(true);
    }

    @Test
    void updateClearsDeadNativeP2WithoutClearingExtraSidekick() {
        AbstractPlayableSprite main = skimmingCandidate();
        AbstractPlayableSprite nativeP2 = skimmingCandidate();
        AbstractPlayableSprite extraSidekick = skimmingCandidate();
        ObjectPlayerQuery activeQuery = new ObjectPlayerQuery(
                () -> main,
                () -> List.of(nativeP2, extraSidekick));
        HCZWaterSkimHandler.update(activeQuery, 0x200, 1);
        when(nativeP2.getDead()).thenReturn(true);
        ObjectPlayerQuery deadNativeP2Query = new ObjectPlayerQuery(
                () -> main,
                () -> List.of(nativeP2, extraSidekick));

        HCZWaterSkimHandler.update(deadNativeP2Query, 0x200, 2);

        assertFalse(HCZWaterSkimHandler.isSkimActiveP2());
        assertTrue(HCZWaterSkimHandler.isSkimActive(2));
    }

    @Test
    void omittedNativeP2KeepsIdentityStateWithoutLeavingNativeSlotActive() {
        AbstractPlayableSprite main = skimmingCandidate();
        AbstractPlayableSprite nativeP2 = skimmingCandidate();
        ObjectPlayerQuery activeQuery = new ObjectPlayerQuery(
                () -> main,
                () -> List.of(nativeP2));
        HCZWaterSkimHandler.update(activeQuery, 0x200, 1);
        ObjectPlayerQuery missingNativeP2Query = new ObjectPlayerQuery(
                () -> main,
                List::of);

        HCZWaterSkimHandler.update(missingNativeP2Query, 0x200, 2);

        assertFalse(HCZWaterSkimHandler.isSkimActiveP2());

        HCZWaterSkimHandler.update(activeQuery, 0x200, 3);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP2());
    }

    @Test
    void sidekickReorderKeepsSkimStateWithPlayerIdentity() {
        AbstractPlayableSprite main = skimmingCandidate();
        AbstractPlayableSprite first = skimmingCandidate();
        AbstractPlayableSprite second = skimmingCandidate();
        ObjectPlayerQuery initial = new ObjectPlayerQuery(() -> main, () -> List.of(first, second));
        HCZWaterSkimHandler.update(initial, 0x200, 1);
        when(second.getXSpeed()).thenReturn((short) 0x6FF);

        ObjectPlayerQuery reordered = new ObjectPlayerQuery(() -> main, () -> List.of(second, first));
        HCZWaterSkimHandler.update(reordered, 0x200, 2);

        assertFalse(HCZWaterSkimHandler.isSkimActive(1));
        assertTrue(HCZWaterSkimHandler.isSkimActive(2));
    }

    @Test
    void rewindSnapshotRestoresThirdPlayerSkimStateByPlayerRefSlot() {
        AbstractPlayableSprite main = skimmingCandidate();
        AbstractPlayableSprite nativeP2 = skimmingCandidate();
        AbstractPlayableSprite third = skimmingCandidate();
        ObjectPlayerQuery query = new ObjectPlayerQuery(() -> main, () -> List.of(nativeP2, third));
        HCZWaterSkimHandler.update(query, 0x200, 1);
        HCZWaterSkimHandler.Snapshot snapshot = HCZWaterSkimHandler.snapshot();

        HCZWaterSkimHandler.reset();
        HCZWaterSkimHandler.restore(snapshot);
        assertTrue(HCZWaterSkimHandler.snapshot().extensionStates().stream()
                .anyMatch(state -> state.playerRef().equals(PlayerRefId.sidekick(1))));
        HCZWaterSkimHandler.update(query, 0x200, 2);

        assertTrue(HCZWaterSkimHandler.isSkimActive(2));
        assertFalse(snapshot.extensionStates().isEmpty());
    }

    @Test
    void restoredThirdPlayerStateSurvivesOmissionAndDoesNotRenderGhostSplash() {
        AbstractPlayableSprite main = skimmingCandidate();
        AbstractPlayableSprite nativeP2 = skimmingCandidate();
        AbstractPlayableSprite third = skimmingCandidate();
        ObjectPlayerQuery fullRoster = new ObjectPlayerQuery(
                () -> main, () -> List.of(nativeP2, third));
        HCZWaterSkimHandler.update(fullRoster, 0x200, 1);
        assertTrue(HCZWaterSkimHandler.activeExtensionSplashPlayers().contains(third));
        HCZWaterSkimHandler.Snapshot snapshot = HCZWaterSkimHandler.snapshot();

        HCZWaterSkimHandler.reset();
        HCZWaterSkimHandler.restore(snapshot);
        HCZWaterSkimHandler.update(new ObjectPlayerQuery(
                () -> main, () -> List.of(nativeP2)), 0x200, 2);

        assertFalse(HCZWaterSkimHandler.activeExtensionSplashPlayers().contains(third));
        assertTrue(HCZWaterSkimHandler.snapshot().extensionStates().stream()
                .anyMatch(state -> state.playerRef().equals(PlayerRefId.sidekick(1))));
        HCZWaterSkimHandler.update(fullRoster, 0x200, 3);
        assertTrue(HCZWaterSkimHandler.isSkimActive(2));
    }

    @Test
    void airborneSustainSuppressesSameFrameGravityBecauseRomObjectRunsAfterPlayer() {
        AbstractPlayableSprite main = skimmingCandidate();
        when(main.getAir()).thenReturn(true);
        ObjectPlayerQuery query = new ObjectPlayerQuery(
                () -> main,
                List::of);

        HCZWaterSkimHandler.update(query, 0x200, 1);
        HCZWaterSkimHandler.update(query, 0x200, 2);

        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
        verify(main).setCentreYPreserveSubpixel((short) 0x1EF);
        verify(main).suppressNextGravityStep();
    }

    @Test
    void airborneSpeedExitSuppressesSameFrameGravityBecauseRomObjectRunsAfterPlayer() {
        AbstractPlayableSprite main = skimmingCandidate();
        when(main.getAir()).thenReturn(true);
        when(main.getXSpeed()).thenReturn((short) 0x700, (short) 0x6FF);
        ObjectPlayerQuery query = new ObjectPlayerQuery(
                () -> main,
                List::of);

        HCZWaterSkimHandler.update(query, 0x200, 1);
        HCZWaterSkimHandler.update(query, 0x200, 2);

        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
        verify(main).setWaterSkimActive(false);
        verify(main).suppressNextGravityStep();
    }

    @Test
    void heldActionAcrossActivationAndSustainDoesNotCreateJump() {
        var main = realCandidate("sonic");
        var query = new ObjectPlayerQuery(() -> main, List::of);
        publishPad(main, true, false);
        HCZWaterSkimHandler.update(query, 0x200, 1);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1(), "ordinary feet/speed activation");
        HCZWaterSkimHandler.update(query, 0x200, 2);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1(), "held A/B/C without a logical press sustains skim");
        assertEquals(0, main.getYSpeed());
        assertFalse(main.getAir());
        assertFalse(main.isJumping());
    }

    @Test
    void logicalEdgeWithoutRawHeldTriggersTheNativeJumpExit() {
        var main = realCandidate("sonic");
        var query = new ObjectPlayerQuery(() -> main, List::of);
        publishPad(main, false, false);
        HCZWaterSkimHandler.update(query, 0x200, 1);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
        // Declared semantic input inlet: ROM Ctrl_*_logical may be written by
        // a CPU/script owner independently of the raw hardware held sample.
        publishPad(main, false, true);
        HCZWaterSkimHandler.update(query, 0x200, 2);
        assertEquals(-0x680, main.getYSpeed(), "loc_385D2 tests the logical word's low pressed byte");
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
        assertTrue(main.getAir());
        assertTrue(main.isJumping());
        assertTrue(main.getRolling());
    }

    @Test
    void heldActionOnARaisedCurveExitsWithoutLaunching() {
        var main = realCandidate("sonic");
        var query = new ObjectPlayerQuery(() -> main, List::of);
        publishPad(main, true, false);
        HCZWaterSkimHandler.update(query, 0x200, 1);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
        int raisedY = main.getCentreY()-1;
        main.setCentreYPreserveSubpixel((short)raisedY);
        HCZWaterSkimHandler.update(query, 0x200, 2);
        assertFalse(HCZWaterSkimHandler.isSkimActiveP1());
        assertEquals(0, main.getYSpeed(), "loc_38646 clears skim without loc_38652's launch writes");
        assertEquals(raisedY, main.getCentreY());
        assertFalse(main.getAir());
        assertFalse(main.isJumping());
    }

    @Test
    void nativeP2UsesItsOwnLogicalEdgeWhileHeldMainContinuesSkimming() {
        var main = realCandidate("sonic");
        var p2 = realCandidate("tails");
        var query = new ObjectPlayerQuery(() -> main, () -> List.of(p2));
        publishPad(main, false, false);
        publishPad(p2, false, false);
        HCZWaterSkimHandler.update(query, 0x200, 1);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
        assertTrue(HCZWaterSkimHandler.isSkimActiveP2());
        publishPad(main, true, false);
        publishPad(p2, false, true);
        HCZWaterSkimHandler.update(query, 0x200, 2);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
        assertEquals(0, main.getYSpeed());
        assertFalse(HCZWaterSkimHandler.isSkimActiveP2());
        assertEquals(-0x680, p2.getYSpeed());
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void registeredWorldRestoresActiveSkimAndReplaysTheLogicalInputTailTwice() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS, true);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        try {
            var fixture = HeadlessTestFixture.builder().withZoneAndAct(1,0).build();
            var main = fixture.sprite();
            var p2 = GameServices.sprites().getSidekicks().getFirst();
            configureCandidate(main);
            configureCandidate(p2);
            var query = new ObjectPlayerQuery(() -> main, () -> List.of(p2));
            publishPad(main, true, false);
            publishPad(p2, false, false);
            HCZWaterSkimHandler.update(query, 0x200, 1);
            HCZWaterSkimHandler.update(query, 0x200, 2);
            assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
            assertTrue(HCZWaterSkimHandler.isSkimActiveP2());
            var registry = fixture.gameplayMode().getRewindRegistry();
            var saved = registry.capture();
            assertTrue(saved.entries().containsKey("hcz-water-skim"), "use the installed production static adapter");
            logicalInputTail(query,main,p2);
            var expected = registry.capture();
            for (int replay=0;replay<2;replay++) {
                registry.restore(saved);
                sameWorld(saved,registry.capture());
                logicalInputTail(query,main,p2);
                sameWorld(expected,registry.capture());
            }
        } finally {
            config.clearSessionOverrides();
            TestEnvironment.resetAll();
        }
    }

    private static void logicalInputTail(ObjectPlayerQuery query, AbstractPlayableSprite main,
            AbstractPlayableSprite p2) {
        // This is an isolated owner-phase sequence using declared semantic pad
        // publications and arbitrary water200, not a terrain route or full frame.
        publishPad(main,true,false);
        publishPad(p2,false,true);
        HCZWaterSkimHandler.update(query,0x200,3);
        assertTrue(HCZWaterSkimHandler.isSkimActiveP1());
        assertFalse(HCZWaterSkimHandler.isSkimActiveP2());
        assertEquals(-0x680,p2.getYSpeed());
        publishPad(p2,false,false);
        for (int frame=4;frame<=7;frame++) HCZWaterSkimHandler.update(query,0x200,frame);
        assertEquals(0,main.getYSpeed());
    }

    private static AbstractPlayableSprite realCandidate(String code) {
        var player = new TestablePlayableSprite(code,(short)0x100,(short)0x100);
        configureCandidate(player);
        return player;
    }

    private static void configureCandidate(AbstractPlayableSprite player) {
        player.applyRollingRadii(false);
        player.setRollingFlagPreserveRadii(true);
        player.setCentreYPreserveSubpixel((short)(0x200-player.getYRadius()-1));
        player.setXSpeed((short)0x800);
        player.setGSpeed((short)0x800);
        player.setYSpeed((short)0);
        player.setAir(false);
    }

    private static void publishPad(AbstractPlayableSprite player, boolean rawHeld, boolean logicalPress) {
        player.setJumpInputPressed(rawHeld,false);
        player.setLogicalInputState(false,false,false,false,rawHeld || logicalPress,logicalPress);
    }

    private static void sameWorld(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for (String key : expected.entries().keySet())
            assertTrue(RewindSnapshotDiff.keyEquals(key,expected.get(key),actual.get(key)),
                    () -> key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }

    private static AbstractPlayableSprite skimmingCandidate() {
        AbstractPlayableSprite player = mock(AbstractPlayableSprite.class);
        when(player.getYSpeed()).thenReturn((short) 0);
        when(player.getCentreY()).thenReturn((short) 0x1EF);
        when(player.getYRadius()).thenReturn((short) 0x10);
        when(player.getXSpeed()).thenReturn((short) 0x700);
        when(player.getDead()).thenReturn(false);
        when(player.isDebugMode()).thenReturn(false);
        when(player.getDirection()).thenReturn(Direction.RIGHT);
        return player;
    }
}
