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
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.objects.PerObjectRewindSnapshot;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @ParameterizedTest(name = "native P2={0}: held jump has no logical press")
    @ValueSource(booleans = {false, true})
    void heldJumpAcrossActivationDoesNotExitSkim(boolean p2) {
        var pair = realSkimmingPlayers();
        var player = pair.get(p2 ? 1 : 0);
        var query = new ObjectPlayerQuery(() -> pair.get(0), () -> List.of(pair.get(1)));
        player.setJumpInputPressed(true, true);
        player.setLogicalInputState(false, false, false, false, true, true);
        HCZWaterSkimHandler.update(query, 0x200, 1); // activation does not consume jump
        player.setJumpInputPressed(true, false);
        player.setLogicalInputState(false, false, false, false, true, false);

        HCZWaterSkimHandler.update(query, 0x200, 2);

        assertTrue(HCZWaterSkimHandler.isSkimActive(p2 ? 1 : 0));
        assertEquals(0, player.getYSpeed());
        assertFalse(player.getAir());
    }

    @ParameterizedTest(name = "native P2={0}: logical press without raw press")
    @ValueSource(booleans = {false, true})
    void skimJumpReadsLogicalPressRatherThanRawController(boolean p2) {
        var pair = realSkimmingPlayers();
        var player = pair.get(p2 ? 1 : 0);
        var query = new ObjectPlayerQuery(() -> pair.get(0), () -> List.of(pair.get(1)));
        HCZWaterSkimHandler.update(query, 0x200, 1);
        player.setJumpInputPressed(false, false);
        player.setLogicalInputState(false, false, false, false, true, true);

        HCZWaterSkimHandler.update(query, 0x200, 2);

        assertFalse(HCZWaterSkimHandler.isSkimActive(p2 ? 1 : 0));
        assertEquals((short) -0x680, player.getYSpeed());
        assertTrue(player.getAir());
        assertTrue(player.getRolling());
    }

    @ParameterizedTest(name = "native P2={0}: raw press filtered from logical word")
    @ValueSource(booleans = {false, true})
    void rawJumpPressAbsentFromLogicalWordDoesNotExitSkim(boolean p2) {
        var pair = realSkimmingPlayers();
        var player = pair.get(p2 ? 1 : 0);
        var query = new ObjectPlayerQuery(() -> pair.get(0), () -> List.of(pair.get(1)));
        HCZWaterSkimHandler.update(query, 0x200, 1);
        player.setJumpInputPressed(true, true);
        player.setLogicalInputState(false, false, false, false, false, false);

        HCZWaterSkimHandler.update(query, 0x200, 2);

        assertTrue(HCZWaterSkimHandler.isSkimActive(p2 ? 1 : 0));
        assertEquals(0, player.getYSpeed());
    }

    @ParameterizedTest(name = "native P2={0}: registered skim and logical-input restore")
    @ValueSource(booleans = {false, true})
    void registeredRestoreRetainsHeldAndPressedSkimBoundaries(boolean p2) {
        var pair = realSkimmingPlayers();
        var player = pair.get(p2 ? 1 : 0);
        var query = new ObjectPlayerQuery(() -> pair.get(0), () -> List.of(pair.get(1)));
        var registry = new RewindRegistry();
        registry.register(new HCZWaterSkimStaticAdapter());
        for (int index = 0; index < pair.size(); index++) {
            var sprite = pair.get(index);
            String key = "player-" + index;
            registry.register(new RewindSnapshottable<PerObjectRewindSnapshot>() {
                public String key() { return key; }
                public PerObjectRewindSnapshot capture() { return sprite.captureRewindState(); }
                public void restore(PerObjectRewindSnapshot state) { sprite.restoreRewindState(state); }
            });
        }
        var before = registry.capture();
        HCZWaterSkimHandler.update(query, 0x200, 1);
        player.setJumpInputPressed(true, false);
        player.setLogicalInputState(false, false, false, false, true, false);
        var held = registry.capture();
        player.setLogicalInputState(false, false, false, false, true, true);
        var pressed = registry.capture();
        HCZWaterSkimHandler.update(query, 0x200, 2);
        var after = registry.capture();
        assertEquals((short) -0x680, player.getYSpeed());

        registry.restore(held);
        HCZWaterSkimHandler.update(query, 0x200, 2);
        assertTrue(HCZWaterSkimHandler.isSkimActive(p2 ? 1 : 0));
        assertEquals(0, player.getYSpeed());
        registry.restore(pressed);
        HCZWaterSkimHandler.update(query, 0x200, 2);
        assertFalse(HCZWaterSkimHandler.isSkimActive(p2 ? 1 : 0));
        assertEquals((short) -0x680, player.getYSpeed());
        registry.restore(after);
        assertFalse(HCZWaterSkimHandler.isSkimActive(p2 ? 1 : 0));
        assertEquals((short) -0x680, player.getYSpeed());
        registry.restore(before);
        HCZWaterSkimHandler.update(query, 0x200, 1);
        assertTrue(HCZWaterSkimHandler.isSkimActive(p2 ? 1 : 0));
        assertEquals(0, player.getYSpeed());
    }

    private static List<TestablePlayableSprite> realSkimmingPlayers() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        var p1 = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        var p2 = new TestablePlayableSprite("tails", (short) 0, (short) 0);
        p2.setCpuControlled(true);
        GameServices.camera().setFocusedSprite(p1);
        for (var player : List.of(p1, p2)) {
            player.setCentreY((short) (0x200 - player.getYRadius() - 1));
            player.setXSpeed((short) 0x700);
            GameServices.sprites().addSprite(player, player.getCode());
        }
        return List.of(p1, p2);
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
            // Select the actual zone handler before retained world capture.
            GameServices.parallax().initZone(GameServices.level().getCurrentZone(),
                    GameServices.level().getCurrentAct(), fixture.camera().getX(), fixture.camera().getY());
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
            assertTrue(saved.get("parallax") instanceof
                    com.openggf.game.rewind.snapshot.ParallaxSnapshot savedParallax
                    && savedParallax.handlerRewindState() != null,
                    "the saved parallax snapshot must retain the selected zone handler state");
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
