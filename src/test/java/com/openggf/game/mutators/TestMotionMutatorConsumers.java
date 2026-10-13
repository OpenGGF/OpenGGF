package com.openggf.game.mutators;

import com.openggf.game.GameStateInternalAccess;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelLoadCause;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.mods.mutators.*;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.*;
import com.openggf.tests.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.tests.MutatorPlayerStateAssertions.assertNativeStateEquals;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestMotionMutatorConsumers {
    private static final class Source implements TransientMutatorPolicySource,
            com.openggf.game.session.WorldSessionPolicyState,
            com.openggf.game.rewind.RewindSnapshottable<Source.State> {
        record State(List<Entry> entries, long ordinal, List<Long> acknowledgements, List<String> reports) { }
        final List<Entry> active = new ArrayList<>();
        final List<Long> acknowledgements = new ArrayList<>();
        final List<String> reports = new ArrayList<>();
        long ordinal;
        void policy(MutatorPolicy policy) { active.clear(); add(policy, false); }
        void add(MutatorPolicy policy, boolean once) { active.add(new Entry("fixture", ++ordinal, policy, 0, once ? 0 : 600, once)); }
        @Override public List<Entry> entries() { return List.copyOf(active); }
        @Override public boolean acknowledge(long id) { acknowledgements.add(id); return active.removeIf(e -> e.activationId() == id); }
        @Override public boolean report(long id, String status) { reports.add(id + ":" + status); return true; }
        @Override public <T> T getService(Class<T> type) {
            return type == TransientMutatorPolicySource.class ? type.cast(this) : null;
        }
        @Override public com.openggf.game.rewind.RewindSnapshottable<?> rewindAdapter() { return this; }
        @Override public String key() { return "fixture-transient-source"; }
        @Override public State capture() {
            return new State(List.copyOf(active), ordinal, List.copyOf(acknowledgements), List.copyOf(reports));
        }
        @Override public void restore(State state) {
            active.clear(); active.addAll(state.entries()); ordinal = state.ordinal();
            acknowledgements.clear(); acknowledgements.addAll(state.acknowledgements());
            reports.clear(); reports.addAll(state.reports());
        }
        @Override public void beforeAssembly(LevelLoadCause cause) { active.clear(); }
        @Override public void bindRoster(SpriteManager sprites) { }
        @Override public void failedAssembly(LevelLoadCause cause) { active.clear(); }
        @Override public void closeScreens() { }
        @Override public void retire() { active.clear(); }
    }
    private Source source;
    private MotionMutatorRuntime runtime;
    private GameStateManager gsm;
    private SpriteManager sprites;

    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        source = new Source(); runtime = new MotionMutatorRuntime();
        gsm = com.openggf.game.GameServices.gameState(); sprites = new SpriteManager();
        runtime.bind(sprites, gsm, source);
    }
    private AbstractPlayableSprite player(String character) {
        AbstractPlayableSprite player = switch (character) {
            case "tails" -> new Tails("sonic", (short) 100, (short) 100);
            case "knuckles" -> new Knuckles("sonic", (short) 100, (short) 100);
            default -> new Sonic("sonic", (short) 100, (short) 100);
        };
        sprites.addSprite(player);
        assertSame(player, sprites.getMainPlayable());
        return player;
    }
    private Object call(AbstractPlayableSprite player, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = PlayableSpriteMovement.class.getDeclaredMethod(name, types);
        method.setAccessible(true); return method.invoke(player.getMovementManager(), args);
    }
    private Object call(AbstractPlayableSprite player, String name) throws Exception { return call(player, name, new Class<?>[0]); }

    @ParameterizedTest @ValueSource(strings = {"sonic", "tails", "knuckles"})
    void scalesActualOrdinaryJumpForNativeCharactersWithoutChangingShieldOrPosition(String character) throws Exception {
        var player = player(character);
        int jump = player.getJump(); int x = player.getCentreX(), y = player.getCentreY();
        source.policy(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.JUMP_SCALE, 125));
        assertEquals(true, call(player, "doJump"));
        assertEquals(-jump * 125 / 100, player.getYSpeed());
        assertEquals(x, player.getCentreX());
        assertTrue(player.getAir()); assertTrue(player.getRolling());
        assertEquals(y + player.getStandYRadius() - player.getRollYRadius(), player.getCentreY());
    }

    @Test void tractionChangesActualCoastingAndDirectionalBrakingOnlyOnFreeGround() throws Exception {
        var player = player("sonic");
        source.policy(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.TRACTION, 25));
        player.setGSpeed((short) 0x400);
        int friction = player.getFriction();
        call(player, "storeInputState", new Class<?>[]{boolean.class, boolean.class, boolean.class, boolean.class, boolean.class}, false,false,false,false,false);
        call(player, "doGroundMove");
        assertEquals(0x400 - friction / 4, player.getGSpeed());
        player.setAir(true);
        assertEquals((short) friction, runtime.traction(player, (short) friction));
        player.setAir(false); player.setObjectControlled(true);
        assertEquals((short) friction, runtime.traction(player, (short) friction));
    }

    @Test void forcePreservesFasterMotionAndNeverMovesCoordinatesOrScriptedAttachments() {
        var player = player("sonic"); player.setAir(true); player.setXSpeed((short) 0x100);
        int centreX = player.getCentreX();
        source.policy(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.HORIZONTAL_FORCE, 0x10, 0x200));
        runtime.beforeMovement(player);
        assertEquals(0x110, player.getXSpeed());
        assertEquals(centreX, player.getCentreX());
        player.setXSpeed((short) 0x500); runtime.beforeMovement(player); assertEquals(0x500, player.getXSpeed());
        player.setObjectControlled(true); runtime.beforeMovement(player); assertEquals(0x500, player.getXSpeed());
    }

    @Test void composedForcesKeepTheSharedAccelerationBoundAndOppositeForcesCancel() {
        var player=player("sonic");player.setAir(true);
        source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.HORIZONTAL_FORCE,0x10),false);
        source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.HORIZONTAL_FORCE,0x10),false);
        runtime.beforeMovement(player);assertEquals(0x10,player.getXSpeed());
        source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.HORIZONTAL_FORCE,-0x10),false);
        source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.HORIZONTAL_FORCE,-0x10),false);
        runtime.beforeMovement(player);assertEquals(0x10,player.getXSpeed());
    }

    @Test void momentumAndGroundStopAreSingleAcknowledgedPulsesAndAirborneStopWaits() {
        var player = player("sonic"); player.setXSpeed((short) 0x240); player.setGSpeed((short) 0x240);
        source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.MOMENTUM_PULSE, 0), true);
        runtime.beforeMovement(player); assertEquals(-0x240, player.getXSpeed()); assertEquals(-0x240, player.getGSpeed());
        runtime.beforeMovement(player); assertEquals(-0x240, player.getXSpeed());
        source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.GROUND_STOP, 0), true);
        player.setAir(true); runtime.beforeMovement(player); assertEquals(-0x240, player.getXSpeed());
        assertEquals(1, source.entries().size());
        player.setAir(false); runtime.beforeMovement(player); assertEquals(0, player.getGSpeed()); assertEquals(0, player.getXSpeed());
        player.setGSpeed((short) 0x300); runtime.beforeMovement(player); assertEquals(0x300, player.getGSpeed());
    }

    @ParameterizedTest @ValueSource(strings = {"boss", "transport", "airborne", "stationary"})
    void oneShotNativeDeadlineRejectsSkippedOrUnqualifiedOwnersExactlyOnceAndReplays(String context) {
        var player = player("sonic");
        var mode = context.equals("airborne") ? PlayerMotionPolicy.Mode.GROUND_STOP : PlayerMotionPolicy.Mode.MOMENTUM_PULSE;
        source.add(new PlayerMotionPolicy(mode, 0), true);
        if (!context.equals("stationary")) { player.setXSpeed((short) 0x240); player.setGSpeed((short) 0x240); }
        if (context.equals("boss")) gsm.setCurrentBossId(1);
        if (context.equals("transport")) player.setObjectControlled(true);
        if (context.equals("airborne")) player.setAir(true);
        var frame = org.mockito.Mockito.mock(com.openggf.LevelFrameContext.class);
        for (int tick = 1; tick < MotionMutatorRuntime.ONE_SHOT_NATIVE_TICK_LIMIT; tick++) {
            runtime.beforeNativeTick(frame, com.openggf.control.LogicalInputSnapshot.neutral());
            if (context.equals("stationary") || context.equals("airborne")) runtime.beforeMovement(player);
            assertEquals(1, source.entries().size());
        }
        var before = runtime.capture(); var leasesBefore = source.capture();
        runtime.beforeNativeTick(frame, com.openggf.control.LogicalInputSnapshot.neutral());
        assertTrue(source.entries().isEmpty()); assertEquals(1, source.acknowledgements.size());
        assertEquals(1, source.reports.size()); assertTrue(source.reports.getFirst().contains("Failed:"));
        var expected = runtime.capture(); var expectedLeases = source.capture();
        for (int tick = 0; tick < 3; tick++) runtime.beforeNativeTick(frame, com.openggf.control.LogicalInputSnapshot.neutral());
        assertEquals(1, source.acknowledgements.size()); assertEquals(1, source.reports.size());
        source.restore(leasesBefore); runtime.restore(before);
        runtime.beforeNativeTick(frame, com.openggf.control.LogicalInputSnapshot.neutral());
        assertEquals(expected, runtime.capture()); assertEquals(expectedLeases, source.capture());
        assertEquals(context.equals("stationary") ? 0 : 0x240, player.getXSpeed());
    }

    @Test void oneShotPresentationDeadlineAndPausedBudgetAreCapturedAndQualifiedOpportunityStillSucceeds() {
        var player = player("sonic"); source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.GROUND_STOP, 0), true);
        var entry = source.active.getFirst();
        source.active.set(0, new TransientMutatorPolicySource.Entry(entry.owner(), entry.activationId(), entry.policy(),
                MotionMutatorRuntime.ONE_SHOT_PRESENTATION_LIMIT, 0, true));
        var frame = org.mockito.Mockito.mock(com.openggf.LevelFrameContext.class);
        var before = runtime.capture();
        for (var phase : List.of(com.openggf.game.GameplayObservation.Phase.PAUSED,
                com.openggf.game.GameplayObservation.Phase.HELD, com.openggf.game.GameplayObservation.Phase.REWIND)) {
            runtime.leaveOrdinaryGameplay(phase); assertEquals(before, runtime.capture());
        }
        runtime.beforeNativeTick(frame, com.openggf.control.LogicalInputSnapshot.neutral());
        assertTrue(source.entries().isEmpty()); assertEquals(1, source.acknowledgements.size());
        assertTrue(source.reports.getFirst().contains("Failed:"));
        source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.MOMENTUM_PULSE, 0), true);
        var pulse = source.active.getFirst(); source.active.add(pulse); // shared activation is acknowledged once
        player.setGSpeed((short) 0x240); player.setXSpeed((short) 0x240);
        runtime.beforeNativeTick(frame, com.openggf.control.LogicalInputSnapshot.neutral());
        runtime.beforeMovement(player);
        assertEquals(-0x240, player.getGSpeed()); assertTrue(source.entries().isEmpty());
        assertEquals(2, source.acknowledgements.size()); assertEquals(1, source.reports.size());
    }

    @Test void freshJumpGatePreservesExistingAirborneJumpAndSpindashButtons() throws Exception {
        var player = player("sonic"); source.policy(new InputControlPolicy(InputControlPolicy.Mode.NO_FRESH_JUMP));
        assertEquals(false, call(player,"doJump")); assertFalse(player.getAir());
        player.setAir(true); player.setYSpeed((short)-0x500); runtime.beforeMovement(player); assertEquals(-0x500, player.getYSpeed());
        player.setAir(false); player.setSpindash(true); assertFalse(runtime.blocksFreshJump(player));
    }

    @Test void freshJumpGateQualifiesOnlyFreeFlatGround() {
        var player = player("sonic");
        source.policy(new InputControlPolicy(InputControlPolicy.Mode.NO_FRESH_JUMP));
        assertTrue(runtime.blocksFreshJump(player));
        for (int angle : new int[] { 1, 0x20, 0x40, 0x80, 0xC0, 0xFF }) {
            player.setAngle((byte) angle);
            assertFalse(runtime.blocksFreshJump(player));
        }
        player.setAngle((byte) 0);
        player.setGroundMode(com.openggf.game.GroundMode.CEILING);
        assertFalse(runtime.blocksFreshJump(player));
        player.setGroundMode(com.openggf.game.GroundMode.GROUND);
        player.setAir(true);
        assertFalse(runtime.blocksFreshJump(player));
        player.setAir(false);
        player.setObjectControlled(true);
        assertFalse(runtime.blocksFreshJump(player));
    }

    @Test void nativeSpindashCancellationAndLevelResetDoNotPublishReleaseOrLandingEvents() {
        var player = player("sonic");
        try (var events = org.mockito.Mockito.mockStatic(PlayableAudioEvents.class)) {
            player.setSpindash(true);
            player.completeHurtLandingRecovery();
            assertFalse(player.getSpindash());
            player.setSpindash(true); player.applyHurtIgnoringIFrames(player.getCentreX(), false);
            assertFalse(player.getSpindash());
            player.setSpindash(true); player.resetState();
            assertFalse(player.getSpindash());
            events.verifyNoInteractions();
        }
    }

    @Test void aControlLeaseRestoredWithinOneNativeTickCannotCreateAnAutoHopAndReplayKeepsThatGeneration() {
        var player = player("sonic"); source.policy(new InputControlPolicy(InputControlPolicy.Mode.LANDING_JUMP));
        var none = com.openggf.control.PlayerInputState.neutral();
        player.setAir(true); runtime.admitInput(none, false);
        player.setObjectControlled(true); player.setAir(false); player.setObjectControlled(false);
        var nativeBefore = player.captureRewindState(); var before = runtime.capture();
        assertEquals(none, runtime.admitInput(none, false));
        var after = runtime.capture();
        player.restoreRewindState(nativeBefore); runtime.restore(before);
        assertEquals(none, runtime.admitInput(none, false)); assertEquals(after, runtime.capture());
        player.setAir(true); runtime.admitInput(none, false); player.setAir(false);
        assertEquals(com.openggf.control.InputActionMasks.ACTION_A, runtime.admitInput(none, false).actionPressedMask(),
                "an ordinary landing in the restored control generation still requests one jump");
    }

    @Test void rollingUsesNativeRadiiAndCruiseUsesNativeAccelerationAndBoundedSpeed() throws Exception {
        var player = player("sonic"); source.policy(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.ROLLING,0));
        player.setGSpeed((short)0x300); call(player,"doCheckStartRoll");
        assertTrue(player.getRolling()); assertEquals(player.getRollYRadius(), player.getYRadius());
        player.setRolling(false); source.policy(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.CRUISE,0x300));
        assertEquals(0x300,runtime.cruiseDirection(player));
        player.setAir(true); assertEquals(0,runtime.cruiseDirection(player));
        player.setAir(false); player.setAngle((byte)0x40); assertEquals(0,runtime.cruiseDirection(player));
    }

    @Test void nativeGravityWritesAndSnapshotsStayIndependentOfTemporaryPulses() {
        var player = player("sonic"); source.policy(new SignedGravityPolicy(List.of(true,false,true),30)); runtime.sampleGravityQualification();
        assertTrue(gsm.isReverseGravityActive()); assertFalse(gsm.capture().reverseGravityActive());
        gsm.setReverseGravityActive(true); assertFalse(gsm.isReverseGravityActive());
        assertTrue(GameStateInternalAccess.nativeReverseGravity(gsm));
        var entry=source.active.getFirst();
        source.active.set(0,new TransientMutatorPolicySource.Entry(entry.owner(),entry.activationId(),entry.policy(),30,100,false));
        assertTrue(gsm.isReverseGravityActive());
        source.active.clear(); assertTrue(gsm.isReverseGravityActive());
        source.policy(new SignedGravityPolicy()); player.setObjectControlled(true); runtime.sampleGravityQualification(); assertTrue(gsm.isReverseGravityActive());
    }

    @Test void nativeDezToggleChangesItsOwnRawFlagDuringTemporaryReversal() throws Exception {
        player("sonic"); source.policy(new SignedGravityPolicy()); runtime.sampleGravityQualification();
        var pad = new com.openggf.game.sonic3k.objects.S3kDezGravitySwitchObjectInstance(
                new com.openggf.level.objects.ObjectSpawn(100,100,0x5B,0,0,false,0));
        pad.setServices(new com.openggf.level.objects.TestObjectServices().withGameState(gsm).withIsolatedObjectManager());
        var toggle = pad.getClass().getDeclaredMethod("toggleFlag"); toggle.setAccessible(true);
        assertTrue(gsm.isReverseGravityActive());
        toggle.invoke(pad);
        assertTrue(gsm.capture().reverseGravityActive()); assertFalse(gsm.isReverseGravityActive());
        var saved=gsm.capture(); toggle.invoke(pad); gsm.restore(saved);
        source.active.clear(); assertTrue(gsm.isReverseGravityActive(), "expiry retains the native pad's write");
    }

    @Test void gravityContributionIsStableThroughOneSolidContactAndItsCapture() {
        var player=player("sonic");source.policy(new SignedGravityPolicy());runtime.sampleGravityQualification();
        assertTrue(gsm.isReverseGravityActive());
        player.setOnObject(true); assertTrue(gsm.isReverseGravityActive(),"a landing cannot flip the sign midway through its final snap");
        var checkpoint=runtime.capture();runtime.sampleGravityQualification();assertFalse(gsm.isReverseGravityActive());
        runtime.restore(checkpoint);assertTrue(gsm.isReverseGravityActive());
    }

    @Test void temporaryGravityDoesNotBecomeACachedSpringVariant() throws Exception {
        player("sonic");source.policy(new SignedGravityPolicy());runtime.sampleGravityQualification();
        var spring=new com.openggf.game.sonic3k.objects.Sonic3kSpringObjectInstance(
                new com.openggf.level.objects.ObjectSpawn(100,100,7,2,0,false,0));
        spring.setServices(new com.openggf.level.objects.TestObjectServices().withGameState(gsm).withIsolatedObjectManager());
        var init=spring.getClass().getDeclaredMethod("ensureInitialized");init.setAccessible(true);init.invoke(spring);
        var type=spring.getClass().getDeclaredField("springType");type.setAccessible(true);
        assertEquals(0,type.getInt(spring));
        source.active.clear();assertEquals(0,type.getInt(spring));
        gsm.setReverseGravityActive(true);
        var nativeSpring=new com.openggf.game.sonic3k.objects.Sonic3kSpringObjectInstance(
                new com.openggf.level.objects.ObjectSpawn(100,100,7,2,0,false,0));
        nativeSpring.setServices(new com.openggf.level.objects.TestObjectServices().withGameState(gsm).withIsolatedObjectManager());
        init.invoke(nativeSpring);assertNotEquals(0,type.getInt(nativeSpring),"the owning native flag still selects its native variant");
    }

    @Test void springPoliciesUseTheActualNativeLaunchAndKeepNudgesSubpixelsAndRouteMinimum() throws Exception {
        var player=player("sonic"); player.setSubpixelRaw(0x1234,0x5678);
        var spring=new com.openggf.game.sonic3k.objects.Sonic3kSpringObjectInstance(
                new com.openggf.level.objects.ObjectSpawn(100,100,7,2,0,false,0));
        spring.setServices(new com.openggf.level.objects.TestObjectServices().withGameState(gsm).withIsolatedObjectManager());
        var up=spring.getClass().getDeclaredMethod("applyUpSpring",AbstractPlayableSprite.class); up.setAccessible(true);
        var down=spring.getClass().getDeclaredMethod("applyDownSpring",AbstractPlayableSprite.class); down.setAccessible(true);
        source.policy(new SpringResponsePolicy(125));
        int y=player.getCentreY();up.invoke(spring,player);
        assertEquals(-0xC80,player.getYSpeed()); assertEquals(y+8,player.getCentreY()); assertEquals(0x5678,player.getYSubpixelRaw());
        assertFalse(player.isJumping());assertTrue(player.getAir());
        source.policy(new SpringResponsePolicy(75)); up.invoke(spring,player);
        assertEquals(-0xA00,player.getYSpeed(),"unreviewed route retains its launch minimum");
        down.invoke(spring,player);assertEquals(0x780,player.getYSpeed());
        gsm.setReverseGravityActive(true);down.invoke(spring,player);
        assertEquals(0xA00,player.getYSpeed(),"a reversed down variant can be a required upward route launch");
        gsm.setReverseGravityActive(false);
        source.active.clear();down.invoke(spring,player);assertEquals(0xA00,player.getYSpeed());
    }

    @Test void cruiseCancellationIsImmediateCapturedAndSurvivesForwardReplay() {
        var player=player("sonic");source.policy(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.CRUISE,0x300));
        var before=runtime.capture();
        var none=com.openggf.control.PlayerInputState.neutral();
        assertEquals(AbstractPlayableSprite.INPUT_RIGHT,runtime.admitInput(none,false).heldMask());
        var cancel=com.openggf.control.PlayerInputState.of(AbstractPlayableSprite.INPUT_DOWN,0,0,0,false,false);
        assertEquals(cancel,runtime.admitInput(cancel,false));assertEquals(0,runtime.cruiseDirection(player));
        var after=runtime.capture();runtime.restore(before);assertEquals(0x300,runtime.cruiseDirection(player));
        runtime.restore(after);assertEquals(none,runtime.admitInput(none,false));
    }

    @Test void fluidIsLocalBreathableAndRewindRestoresTheOriginalRegionWithoutTouchingNativeWater() {
        var player = player("sonic"); source.policy(new FluidContextPolicy(64));
        int nativeJump=player.getJump(); int nativeAccel=player.getRunAccel();
        runtime.beforeMovement(player);
        assertFalse(player.isInWater()); assertTrue(PlayerFluidContextAccess.movementUnderwater(player));
        assertEquals(0x380,player.getJump()); assertEquals(nativeAccel/2,player.getRunAccel());
        assertTrue(PlayerFluidContextAccess.breathProtected(player));
        var checkpoint=runtime.capture(); var region=runtime.fluidRegion();
        player.setCentreX((short)(player.getCentreX()+100));
        assertEquals(nativeJump,player.getJump());
        runtime.beforeAssembly(LevelLoadCause.FULL_RESTART); runtime.restore(checkpoint);
        assertEquals(region,runtime.fluidRegion());
        source.active.clear(); assertEquals(nativeJump,player.getJump()); assertNull(runtime.fluidRegion());
    }

    @Test void crossingLocalFluidUsesOneDecisionForDryGravityAndUnderwaterReduction() throws Exception {
        var player=player("sonic");source.policy(new FluidContextPolicy(64));runtime.beforeMovement(player);
        var region=runtime.fluidRegion();
        PlayableSpriteInternalAccess.bindMutatorPolicies(player,s -> new PlayableMutatorPolicy(25,false,false,false));
        player.setAir(true);player.setCentreX((short)(region.centreX()+65));player.setXSpeed((short)-0x200);player.setYSpeed((short)0);
        runtime.beginFluidMovement(player);
        call(player,"doObjectMoveAndFall");call(player,"applyUnderwaterAirGravityReduction");
        assertEquals(14,player.getYSpeed(),"entry cannot combine scaled dry gravity with underwater subtraction");
        runtime.endFluidMovement(player);assertTrue(PlayerFluidContextAccess.movementUnderwater(player));
        runtime.beginFluidMovement(player);
        call(player,"doObjectMoveAndFall");call(player,"applyUnderwaterAirGravityReduction");
        runtime.endFluidMovement(player);assertEquals(30,player.getYSpeed(),"the next complete wet dispatch uses native $10 gravity");
    }

    @Test void fluidProtectionReachesFixedNativeCountdownBeforeItsTimerAndRng() throws Exception {
        var player=player("sonic");player.setInWater(true);source.policy(new FluidContextPolicy(64));runtime.beforeMovement(player);
        var type=Class.forName("com.openggf.game.sonic3k.S3kFixedAirCountdownManager$FixedController");
        var constructor=type.getDeclaredConstructor(String.class);constructor.setAccessible(true);
        var controller=constructor.newInstance("Breathing_bubbles");
        var timer=type.getDeclaredField("obj3c");timer.setAccessible(true);timer.setInt(controller,0);
        var countdown=type.getDeclaredMethod("countdown",AbstractPlayableSprite.class);countdown.setAccessible(true);
        var rng=player.currentRng().capture();
        for (int tick=0;tick<90;tick++) countdown.invoke(controller,player);
        assertEquals(0,timer.getInt(controller));assertEquals(rng,player.currentRng().capture());
        assertEquals(30,player.getDrowningController().getRemainingAir());
        source.active.clear();countdown.invoke(controller,player);
        assertEquals(59,timer.getInt(controller));assertNotEquals(rng,player.currentRng().capture());
        assertEquals(29,player.getDrowningController().getRemainingAir());
    }

    @Test void relocationFluidQueryUsesTheCircleAtTheTargetAndQualifiedLeaderIdentity() {
        var player=player("sonic");source.policy(new FluidContextPolicy(64));runtime.beforeMovement(player);
        var region=runtime.fluidRegion();var query=runtime.getService(EffectiveFluidContextSource.class);
        player.setCentreX((short)(region.centreX()+100));
        assertTrue(query.underwaterAt(player,region.centreX(),region.centreY()));
        assertFalse(query.underwaterAt(player,region.centreX()+100,region.centreY()));
        var sidekick=new Tails("tails",(short)100,(short)100);
        assertFalse(query.underwaterAt(sidekick,region.centreX(),region.centreY()));
        assertFalse(player.isInWater());
    }

    @Test void relocationFluidQueryComposesNativePlaneAtTargetWithIndependentCapturedCircle() {
        var player = player("sonic"); source.policy(new FluidContextPolicy(64));
        var water = org.mockito.Mockito.mock(com.openggf.level.WaterSystem.class);
        org.mockito.Mockito.when(water.hasWater(5, 1)).thenReturn(true);
        org.mockito.Mockito.when(water.getGameplayWaterLevelY(5, 1)).thenReturn(200);
        var objects = org.mockito.Mockito.mock(com.openggf.level.objects.ObjectManager.class);
        org.mockito.Mockito.when(objects.getObjectServices()).thenReturn(
                new com.openggf.level.objects.TestObjectServices().withWaterSystem(water));
        var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(level.getObjectManager()).thenReturn(objects);
        org.mockito.Mockito.when(level.getFeatureZoneId()).thenReturn(5);
        org.mockito.Mockito.when(level.getFeatureActId()).thenReturn(1);
        var frame = org.mockito.Mockito.mock(com.openggf.LevelFrameContext.class);
        org.mockito.Mockito.when(frame.levelManager()).thenReturn(level);
        runtime.beforeNativeTick(frame, com.openggf.control.LogicalInputSnapshot.neutral());
        var query = runtime.getService(EffectiveFluidContextSource.class);
        var circle = runtime.fluidRegion();
        assertTrue(query.underwaterAt(player, circle.centreX(), circle.centreY()));
        assertFalse(query.underwaterAt(player, 500, 200), "native surface equality stays dry outside the local circle");
        assertTrue(query.underwaterAt(player, 500, 201), "native owner evaluates the target, not the old player status");
        assertFalse(player.isInWater());
        var checkpoint = runtime.capture();
        runtime.beforeAssembly(LevelLoadCause.FULL_RESTART); runtime.restore(checkpoint);
        assertEquals(circle, runtime.fluidRegion());
        assertTrue(query.underwaterAt(player, 500, 201));
        source.active.clear();
        assertFalse(query.underwaterAt(player, circle.centreX(), circle.centreY()));
        assertTrue(query.underwaterAt(player, 500, 201), "overlay expiry cannot erase native water");
    }

    @Test void emptyOrRetiredOwnerKeepsCompleteNativeStateAndCannotRestoreAcrossRetirement() throws Exception {
        var player = player("sonic"); var stock=new Sonic("sonic",(short)100,(short)100);
        player.setAir(true);stock.setAir(true); player.setYSpeed((short)-0x101);stock.setYSpeed((short)-0x101);
        call(player,"doObjectMoveAndFall");call(stock,"doObjectMoveAndFall");
        assertNativeStateEquals(stock.captureRewindState(),player.captureRewindState());
        var checkpoint=runtime.capture();runtime.retire(); assertThrows(IllegalStateException.class,()->runtime.restore(checkpoint));
    }

    @Test void snapshotsCannotCrossWorlds() {
        assertThrows(IllegalArgumentException.class,()->new MotionMutatorRuntime().restore(runtime.capture()));
    }

    @Test void assembledWorldBindsAfterPolicyCreationAndRewindsLeasesInputAndAcknowledgedPulseTogether() {
        var child = new MotionMutatorRuntime();
        var module = org.mockito.Mockito.mock(com.openggf.game.GameModule.class);
        org.mockito.Mockito.when(module.getIdentifier()).thenReturn("test:motion-assembly");
        org.mockito.Mockito.when(module.getGameService(com.openggf.game.session.WorldSessionPolicyProvider.class))
                .thenReturn(world -> com.openggf.game.session.CompositeWorldSessionPolicyState.of(source, child));
        var world = new com.openggf.game.session.WorldSession(module);
        var context = org.mockito.Mockito.mock(com.openggf.game.session.GameplayModeContext.class);
        org.mockito.Mockito.when(context.getWorldSession()).thenReturn(world);
        org.mockito.Mockito.when(context.getSpriteManager()).thenReturn(sprites);
        org.mockito.Mockito.when(context.getGameStateManager()).thenReturn(gsm);
        var player = player("sonic");
        source.add(new InputControlPolicy(InputControlPolicy.Mode.DELAY, 6), false);
        source.add(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.MOMENTUM_PULSE, 0), true);
        source.add(new FluidContextPolicy(64), false);
        source.add(new SignedGravityPolicy(), false);
        assertEquals(player.getJump(), child.jumpImpulse(player, player.getJump()), "provider construction is not a gameplay binding");
        com.openggf.game.session.WorldSessionPolicyAccess.bindGameplay(context);
        assertTrue(child.leader(player));
        var registry = new com.openggf.game.rewind.RewindRegistry();
        com.openggf.game.session.WorldSessionPolicyAccess.registerRewind(world, registry);
        var right = com.openggf.control.PlayerInputState.of(AbstractPlayableSprite.INPUT_RIGHT,
                AbstractPlayableSprite.INPUT_RIGHT, 0, 0, false, false);
        assertEquals(com.openggf.control.PlayerInputState.neutral(), child.admitInput(right, false));
        player.setGSpeed((short) 0x240); player.setXSpeed((short) 0x240);
        var nativeBefore = player.captureRewindState();
        var before = registry.capture();
        child.beforeMovement(player);
        assertEquals(-0x240, player.getGSpeed());
        assertEquals(3, source.entries().size(), "one-shot acknowledgement removes only the pulse");
        var nativeAfter = player.captureRewindState();
        var after = registry.capture();
        player.restoreRewindState(nativeBefore); registry.restore(before);
        assertEquals(4, source.entries().size(), "rewind restores the engine-owned activation alongside its consumer");
        child.beforeMovement(player);
        assertNativeStateEquals(nativeAfter, player.captureRewindState());
        assertEquals(after.entries(), registry.capture().entries());
        for (int tick = 0; tick < 5; tick++) child.admitInput(com.openggf.control.PlayerInputState.neutral(), false);
        assertEquals(right, child.admitInput(com.openggf.control.PlayerInputState.neutral(), false));
        var replacement = new SpriteManager();
        var recreated = new Sonic("sonic", (short) 100, (short) 100);
        replacement.addSprite(recreated);
        com.openggf.game.session.WorldSessionPolicyAccess.bindRoster(world, replacement);
        assertTrue(child.leader(recreated)); assertFalse(child.leader(player));
        assertTrue(PlayerFluidContextAccess.movementUnderwater(recreated));
    }

    @Test void worldPolicyHistoryRecordsBeforeDelayCommitAndReplaysPublicationAndMovementOnce() throws Exception {
        source.policy(new InputControlPolicy(InputControlPolicy.Mode.DELAY, 6));
        var player = new CapturingInputPlayer(); sprites.addSprite(player);
        var module = org.mockito.Mockito.mock(com.openggf.game.GameModule.class);
        org.mockito.Mockito.when(module.getIdentifier()).thenReturn("test:motion-history");
        org.mockito.Mockito.when(module.getGameService(com.openggf.game.session.WorldSessionPolicyProvider.class))
                .thenReturn(world -> com.openggf.game.session.CompositeWorldSessionPolicyState.of(source, runtime));
        var world = new com.openggf.game.session.WorldSession(module);
        var level = org.mockito.Mockito.mock(com.openggf.level.LevelManager.class);
        org.mockito.Mockito.when(level.getWorldSession()).thenReturn(world);
        TestEnvironment.activeGameplayMode().attachLevelManagers(new com.openggf.level.WaterSystem(),
                new com.openggf.level.ParallaxManager(), org.mockito.Mockito.mock(com.openggf.physics.TerrainCollisionManager.class),
                org.mockito.Mockito.mock(com.openggf.physics.CollisionSystem.class), sprites, level);
        var context = org.mockito.Mockito.mock(com.openggf.game.session.GameplayModeContext.class);
        org.mockito.Mockito.when(context.getWorldSession()).thenReturn(world);
        org.mockito.Mockito.when(context.getLevelManager()).thenReturn(level);
        org.mockito.Mockito.when(context.getSpriteManager()).thenReturn(sprites);
        org.mockito.Mockito.when(context.getGameStateManager()).thenReturn(gsm);
        com.openggf.game.session.WorldSessionPolicyAccess.bindGameplay(context);
        var frame = org.mockito.Mockito.mock(com.openggf.LevelFrameContext.class);
        org.mockito.Mockito.when(frame.levelManager()).thenReturn(level);
        org.mockito.Mockito.when(frame.spriteManager()).thenReturn(sprites);
        var tap = com.openggf.control.PlayerInputState.of(AbstractPlayableSprite.INPUT_LEFT,
                AbstractPlayableSprite.INPUT_LEFT, com.openggf.control.InputActionMasks.ACTION_C,
                com.openggf.control.InputActionMasks.ACTION_C, false, false);
        var input = logicalInput(tap);
        var beforeZeroTick = runtime.capture();
        for (int presentation = 0; presentation < 3; presentation++) {
            sprites.publishHeldInputForLevelEvents(input);
            com.openggf.game.session.WorldSessionPolicyAccess.onPresentation(context, input.logical(),
                    com.openggf.game.GameplayObservation.Phase.ACTIVE);
        }
        assertEquals(beforeZeroTick, runtime.capture());
        assertNull(com.openggf.game.session.WorldSessionPolicyAccess.captureHistory(world));
        var histories = new ArrayList<Object>();
        var beforeTicks = new ArrayList<MotionMutatorRuntime.Snapshot>();
        var afterTicks = new ArrayList<MotionMutatorRuntime.Snapshot>();
        var nativeBefore = new ArrayList<com.openggf.level.objects.PerObjectRewindSnapshot>();
        var heldOutcomes = new ArrayList<Integer>(); var pressOutcomes = new ArrayList<Integer>();
        // Several native ticks in one presentation: delay counts these ticks, not presentations.
        for (int tick = 0; tick <= 6; tick++) {
            input = logicalInput(tick == 0 ? tap : com.openggf.control.PlayerInputState.neutral());
            sprites.publishHeldInputForLevelEvents(input);
            beforeTicks.add(runtime.capture()); nativeBefore.add(player.captureRewindState());
            com.openggf.game.session.WorldSessionPolicyAccess.beforeNativeTick(frame);
            var committed = runtime.capture();
            sprites.update(input);
            assertEquals(committed, runtime.capture(), "movement consumes the prepared sample without committing a second FIFO step");
            heldOutcomes.add(player.movementHeld); pressOutcomes.add(sprites.getJoypadPressSnapshot().player1ActionPressed());
            com.openggf.game.session.WorldSessionPolicyAccess.afterNativeTick(frame);
            histories.add(com.openggf.game.session.WorldSessionPolicyAccess.captureHistory(world));
            assertNotNull(histories.getLast()); afterTicks.add(runtime.capture());
            assertEquals(tick == 6 ? tap.heldMask() : 0, player.movementHeld);
        }
        var prepared = SpriteManager.class.getDeclaredField("admittedMotionPlayerOne"); prepared.setAccessible(true);
        for (int tick = 0; tick <= 6; tick++) {
            // Leave a different prepared sample outstanding so restoreBefore must invalidate it itself.
            sprites.publishHeldInputForLevelEvents(logicalInput(com.openggf.control.PlayerInputState.neutral()));
            runtime.beforeNativeTick(frame, com.openggf.control.LogicalInputSnapshot.neutral());
            assertNotNull(prepared.get(sprites));
            com.openggf.game.session.WorldSessionPolicyAccess.restoreHistoryBefore(world, histories.get(tick));
            assertNull(prepared.get(sprites), "history restore clears scratch before replay publication");
            assertEquals(beforeTicks.get(tick), runtime.capture(), "the world before snapshot precedes DELAY queue commitment");
            player.restoreRewindState(nativeBefore.get(tick));
            input = logicalInput(tick == 0 ? tap : com.openggf.control.PlayerInputState.neutral());
            sprites.publishHeldInputForLevelEvents(input);
            com.openggf.game.session.WorldSessionPolicyAccess.beforeNativeTick(frame);
            sprites.update(input);
            com.openggf.game.session.WorldSessionPolicyAccess.afterNativeTick(frame);
            assertEquals(heldOutcomes.get(tick).intValue(), player.movementHeld);
            assertEquals(pressOutcomes.get(tick).intValue(), sprites.getJoypadPressSnapshot().player1ActionPressed());
            assertEquals(afterTicks.get(tick), runtime.capture());
            com.openggf.game.session.WorldSessionPolicyAccess.restoreHistoryAfter(world, histories.get(tick));
            assertFalse(com.openggf.game.session.WorldSessionPolicyAccess.replaying(world));
        }
    }

    private static com.openggf.control.InputHandler logicalInput(com.openggf.control.PlayerInputState p1) {
        var input = new com.openggf.control.InputHandler();
        input.setLogicalOverride(com.openggf.control.LogicalInputSnapshot.ofPlayers(p1,
                com.openggf.control.PlayerInputState.neutral()));
        return input;
    }

    private static final class CapturingInputPlayer extends AbstractPlayableSprite implements CustomPlayablePhysics {
        int movementHeld;
        CapturingInputPlayer() { super("sonic", (short) 100, (short) 100); }
        @Override protected void defineSpeeds() { }
        @Override protected void createSensorLines() {
            groundSensors = new com.openggf.physics.Sensor[0];
            ceilingSensors = new com.openggf.physics.Sensor[0];
            pushSensors = new com.openggf.physics.Sensor[0];
        }
        @Override public void draw() { }
        @Override public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                boolean jump, boolean test, boolean speedUp, boolean slowDown,
                com.openggf.level.LevelManager level, int frameCounter) {
            movementHeld = (left ? INPUT_LEFT : 0) | (right ? INPUT_RIGHT : 0) | (jump ? INPUT_JUMP : 0);
        }
    }

    @Test void leavingOrdinaryGameplayClearsQueuesAndContributionsWhilePauseHoldRewindPreserveThem() {
        var player=player("sonic");source.policy(new FluidContextPolicy(64));runtime.beforeMovement(player);
        var historical=runtime.capture();
        for (var phase : List.of(com.openggf.game.GameplayObservation.Phase.PAUSED,
                com.openggf.game.GameplayObservation.Phase.HELD,com.openggf.game.GameplayObservation.Phase.REWIND)) {
            runtime.leaveOrdinaryGameplay(phase);assertEquals(historical,runtime.capture());
        }
        runtime.leaveOrdinaryGameplay(com.openggf.game.GameplayObservation.Phase.STAGE);
        assertNull(runtime.fluidRegion());assertFalse(PlayerFluidContextAccess.movementUnderwater(player));
        assertTrue(runtime.capture().input().pending().isEmpty());assertFalse(gsm.isReverseGravityActive());
        runtime.restore(historical);assertTrue(PlayerFluidContextAccess.movementUnderwater(player));
    }
}
