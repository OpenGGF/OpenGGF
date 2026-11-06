package com.openggf.sprites.managers;

import com.openggf.game.rules.CrossGameRuleComposer;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.kis2.Kis2Rules;
import com.openggf.sprites.playable.ObjectControlState;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.stream.Stream;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestPlayableSpriteMovementTailsFlight {
    private TestTails tails;
    private PlayableSpriteMovement movement;

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        tails = new TestTails("tails", (short) 100, (short) 100);
        tails.useRules(GameRules.SONIC_3K);
        movement = new PlayableSpriteMovement(tails);
        prepareReleasedAirJump(tails);
    }

    @Test
    void nativeS3kMainTailsActivatesFlightOnAirborneJumpRepress() throws Exception {
        repressJump(movement);

        assertTrue(tails.getTailsFlightController().isActive());
        assertEquals(TailsFlightController.FLIGHT_TIME, tails.getDoubleJumpProperty() & 0xFF);
        assertFalse(tails.getRolling(), "Tails_Fly restores standing form on activation");
        assertFalse(tails.getRollingJump(), "the activating repress is consumed by flight");
    }

    @Test
    void nativeSonic2RulesDoNotActivateTailsFlight() throws Exception {
        tails.useRules(GameRules.SONIC_2);

        repressJump(movement);

        assertFalse(tails.getTailsFlightController().isActive());
    }

    @Test
    void kis2RulesRetainS2ManualFlightAvailability() throws Exception {
        tails.useRules(Kis2Rules.RULES);
        repressJump(movement);
        assertFalse(tails.getTailsFlightController().isActive());
    }

    @Test
    void sonic3kDonorRulesEnableTailsFlightInSonic2Host() throws Exception {
        tails.useRules(CrossGameRuleComposer.compose(
                GameRules.SONIC_2, GameRules.SONIC_3K,
                new Sonic3kGameModule().getDonorCapabilities()));

        repressJump(movement);

        assertTrue(tails.getTailsFlightController().isActive());
    }

    @Test
    void sonic2DonorRulesDisableTailsFlightInSonic3kHost() throws Exception {
        tails.useRules(CrossGameRuleComposer.compose(
                GameRules.SONIC_3K, GameRules.SONIC_2,
                new Sonic2GameModule().getDonorCapabilities()));

        repressJump(movement);

        assertFalse(tails.getTailsFlightController().isActive());
    }

    @Test
    void sonicNeverActivatesTailsFlightEvenWithFlightCapability() throws Exception {
        TestSonic sonic = new TestSonic("sonic", (short) 100, (short) 100);
        sonic.useRules(GameRules.SONIC_3K);
        prepareReleasedAirJump(sonic);
        PlayableSpriteMovement sonicMovement = new PlayableSpriteMovement(sonic);

        repressJump(sonicMovement);

        assertEquals(0, sonic.getDoubleJumpProperty() & 0xFF,
                "Sonic may consume the repress with his own ability but never enters Tails flight");
    }

    @Test
    void activeManualFlightUpdatesVerticalVelocityExactlyOncePerAirborneFrame() {
        tails.setYSpeed((short) 0);
        tails.getTailsFlightController().activate();

        movement.handleMovement(false, false, false, false,
                false, false, false, false);

        assertEquals((short) 0x0008, tails.getYSpeed(),
                "Tails_Move_FlySwim runs once and replaces normal +$38 air gravity");
    }

    @Test
    void activationFrameKeepsNormalAirGravityBeforeFlightGravityStarts() throws Exception {
        // Recorded s3k-tails-full-chain-all-emeralds SOZ1 rows 533-535: y_vel
        // $FE18 -> $FE50 on the activating repress, then $FE58. Tails_Stand_Freespace
        // (sonic3k.asm:27593) sets double_jump_flag inside Tails_JumpHeight but still
        // runs MoveSprite_TestGravity (+$38) that frame; Tails_FlyingSwimming's
        // +$08 begins on the next frame.
        tails.setYSpeed((short) -0x1E8);
        setField(movement, "jumpReleasedSinceJump", true);
        tails.setForcedJumpPress(true);

        movement.handleMovement(false, false, false, false,
                true, false, false, false);

        assertTrue(tails.getTailsFlightController().isActive());
        assertEquals((short) -0x1B0, tails.getYSpeed(),
                "the activating frame applies MoveSprite_TestGravity's +$38");

        movement.handleMovement(false, false, false, false,
                true, false, false, false);

        assertEquals((short) -0x1A8, tails.getYSpeed(),
                "Tails_Move_FlySwim's +$08 owns the following frame");
    }

    @Test
    void activeManualSwimmingWithoutInputGentlySinksAtFlightGravity() {
        tails.setInWater(true);
        tails.setYSpeed((short) 0);
        tails.getTailsFlightController().activate();

        movement.handleMovement(false, false, false, false,
                false, false, false, false);
        assertEquals((short) 0x0008, tails.getYSpeed());

        movement.handleMovement(false, false, false, false,
                false, false, false, false);
        assertEquals((short) 0x0010, tails.getYSpeed());
    }

    @Test
    void landingClearsFlight() {
        tails.getTailsFlightController().activate();
        tails.setAir(false);

        assertFalse(tails.getTailsFlightController().isActive(), "landing clears flight");
    }

    @Test
    void deathClearsFlight() {
        tails.getTailsFlightController().activate();
        tails.setDead(true);

        assertFalse(tails.getTailsFlightController().isActive(), "death clears flight");
    }

    @Test
    void instantDeathClearsActiveFlightWithoutChangingTheStockDeathHop() {
        tails.getTailsFlightController().activate();

        assertTrue(tails.applyCrushDeath());
        assertTrue(tails.getDead());
        assertFalse(tails.getTailsFlightController().isActive(),
                "the instant-death transition must tear down native flight");
        assertEquals((short) -0x700, tails.getYSpeed(),
                "Kill_Character installs the stock upward death velocity");

        movement.handleMovement(false, false, false, false,
                false, false, false, false);

        assertEquals((short) -0x6C8, tails.getYSpeed(),
                "the following dead frame must use normal +$38 gravity, not flight gravity");
    }

    @Test
    void instantDeathReplacesHurtDispatchAndRetainsItsTimer() {
        tails.setHurt(true);
        tails.setInvulnerableFrames(0x78);

        assertTrue(tails.applyCrushDeath());
        assertFalse(tails.isHurt(), "Kill_Character replaces routine 4 with routine 6");
        assertEquals(0x78, tails.getInvulnerableFrames(),
                "Kill_Character does not write the post-hit invulnerability timer");

        movement.handleMovement(false, false, false, false,
                false, false, false, false);

        assertEquals((short) -0x6C8, tails.getYSpeed(),
                "The dead routine uses +$38 gravity even when the preceding state was hurt");
    }

    @Test
    void playerResetClearsFlight() {
        tails.getTailsFlightController().activate();
        tails.resetState();

        assertFalse(tails.getTailsFlightController().isActive(), "player reset clears flight");
    }

    @Test
    void objectControlTakeoverClearsFlight() {
        tails.getTailsFlightController().activate();
        tails.setObjectControlled(true);

        assertFalse(tails.getTailsFlightController().isActive(), "object-control takeover clears flight");
    }

    static Stream<Arguments> suppressedFlightProfiles() {
        var s3 = new Sonic3kGameModule().getDonorCapabilities();
        var s2 = new Sonic2GameModule().getDonorCapabilities();
        var s1 = new Sonic1GameModule().getDonorCapabilities();
        return Stream.of(
                Arguments.of("native S3K P1 bit0", GameRules.SONIC_3K, false,
                        ObjectControlState.movementSuppressedOnly(), false),
                Arguments.of("native S3K CPU bits0/7", GameRules.SONIC_3K, true,
                        ObjectControlState.nativeBit7FullControl(), true),
                Arguments.of("native S3K CPU bits0/1", GameRules.SONIC_3K, true,
                        ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed(), true),
                Arguments.of("S3K donor S1 host P1", CrossGameRuleComposer.compose(
                        GameRules.SONIC_1, GameRules.SONIC_3K, s3), false,
                        ObjectControlState.movementSuppressedOnly(), false),
                Arguments.of("S3K donor S2 host CPU", CrossGameRuleComposer.compose(
                        GameRules.SONIC_2, GameRules.SONIC_3K, s3), true,
                        ObjectControlState.nativeBit7FullControl(), true),
                // These profiles cannot activate manual flight. Their synthetic CPU
                // ability byte must not leak into shared +$08 gravity on release.
                Arguments.of("S2 donor S3K host CPU", CrossGameRuleComposer.compose(
                        GameRules.SONIC_3K, GameRules.SONIC_2, s2), true,
                        ObjectControlState.nativeBit7FullControl(), true),
                Arguments.of("S1 donor S3K host P1", CrossGameRuleComposer.compose(
                        GameRules.SONIC_3K, GameRules.SONIC_1, s1), false,
                        ObjectControlState.movementSuppressedOnly(), false),
                Arguments.of("native S2 CPU synthetic byte", GameRules.SONIC_2, true,
                        ObjectControlState.nativeBit7FullControl(), true),
                Arguments.of("KiS2 CPU synthetic byte", Kis2Rules.RULES, true,
                        ObjectControlState.nativeBit7FullControl(), true),
                Arguments.of("native S1 CPU synthetic byte", GameRules.SONIC_1, true,
                        ObjectControlState.nativeBit7FullControl(), true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("suppressedFlightProfiles")
    void suppressedFlightClearsOnlyDispatchFlagAndReleaseUsesNormalGravity(
            String label, GameRules rules, boolean cpu, ObjectControlState control, boolean rawMapping) {
        tails.useRules(rules);
        tails.setCpuControlled(cpu);
        tails.setRolling(false);
        tails.setJumping(false);
        control.applyTo(tails);
        // CPU control runs BEFORE Tails_Control and can publish this after takeover.
        tails.setDoubleJumpFlag(1);
        tails.setDoubleJumpProperty((byte) 0xA7);
        tails.setYSpeed((short) 0);
        tails.setForcedAnimationId(0x20);
        tails.setAnimationId(0x20);
        tails.setObjectMappingFrameControl(rawMapping);
        tails.setMappingFrame(0x55);
        tails.setForcedJumpPress(true);
        int radius = tails.getYRadius();
        movement.handleMovement(false, false, false, false, false, false, false, false);
        assertEquals(0, tails.getDoubleJumpFlag(), "suppressed normal dispatch clears flight");
        assertEquals(0xA7, tails.getDoubleJumpProperty() & 0xFF, "no fuel write");
        assertEquals(0, tails.getYSpeed(), "suppression runs neither gravity branch");
        assertEquals(radius, tails.getYRadius());
        assertEquals(0x20, tails.getForcedAnimationId(), "CPU/object animation owner survives");
        assertEquals(0x20, tails.getAnimationId());
        assertEquals(rawMapping, tails.isObjectMappingFrameControl());
        assertEquals(0x55, tails.getMappingFrame());
        assertEquals(!cpu, tails.isForcedJumpPress(), "only CPU one-shot input is consumed");
        ObjectControlState.none().applyTo(tails);
        tails.setForcedJumpPress(false);
        movement.handleMovement(false, false, false, false, false, false, false, false);
        assertEquals(0x38, tails.getYSpeed(), "next air dispatch uses normal gravity");
        assertEquals(0, tails.getDoubleJumpFlag());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void animationAndTouchControlWithoutBitZeroDoNotCancelFlight(boolean cpu) {
        tails.setCpuControlled(cpu);
        tails.setRolling(false);
        tails.setJumping(false);
        ObjectControlState.engineScriptedTouchSuppressedMovementActive().applyTo(tails);
        tails.setObjectMappingFrameControl(true); // native bit1 animation ownership
        tails.setDoubleJumpFlag(1);
        tails.setYSpeed((short) 0);
        movement.handleMovement(false, false, false, false, false, false, false, false);
        assertEquals(1, tails.getDoubleJumpFlag(), "bit0 alone selects the clear");
        assertEquals(8, tails.getYSpeed(), "unsuppressed flight retains +$08 gravity");
        assertTrue(tails.isObjectMappingFrameControl());
    }

    @Test
    void recreatedPlayerRestoresSuppressedFlightAndRepeatsReleaseContinuation() {
        tails.setRolling(false);
        tails.setJumping(false);
        tails.setObjectControlSuppressesMovement(true);
        tails.setDoubleJumpFlag(1);
        tails.setDoubleJumpProperty((byte) 0xA7);
        tails.setYSpeed((short) 0);
        var before = tails.captureRewindState();
        tails.getMovementManager().handleMovement(false, false, false, false, false, false, false, false);
        assertEquals(0, tails.getDoubleJumpFlag());
        tails.setObjectControlSuppressesMovement(false);
        tails.getMovementManager().handleMovement(false, false, false, false, false, false, false, false);
        var continued = tails.captureRewindState();
        var recreated = new TestTails("tails", (short) 0, (short) 0);
        recreated.useRules(GameRules.SONIC_3K);
        recreated.restoreRewindState(before);
        assertEquals(1, recreated.getDoubleJumpFlag(), "restore must recover the pre-clear byte");
        assertTrue(recreated.isObjectControlSuppressesMovement());
        recreated.getMovementManager().handleMovement(false, false, false, false, false, false, false, false);
        assertEquals(0, recreated.getDoubleJumpFlag());
        recreated.setObjectControlSuppressesMovement(false);
        recreated.getMovementManager().handleMovement(false, false, false, false, false, false, false, false);
        assertEquals(0x38, recreated.getYSpeed());
        assertTrue(com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                "player", continued, recreated.captureRewindState()).isEmpty(),
                "a new player/controller repeats the complete restored continuation");
    }

    private static void prepareReleasedAirJump(AbstractPlayableSprite sprite) {
        sprite.setAir(true);
        sprite.setJumping(true);
        sprite.setRolling(true);
        sprite.setRollingJump(true);
        sprite.setDoubleJumpFlag(0);
    }

    private static void repressJump(PlayableSpriteMovement movement) throws Exception {
        setField(movement, "jumpReleasedSinceJump", true);
        setField(movement, "inputJump", true);
        setField(movement, "inputJumpPress", true);
        Method jumpHeight = PlayableSpriteMovement.class.getDeclaredMethod("doJumpHeight");
        jumpHeight.setAccessible(true);
        jumpHeight.invoke(movement);
    }

    private static void setField(PlayableSpriteMovement movement, String name, boolean value) throws Exception {
        Field field = PlayableSpriteMovement.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(movement, value);
    }

    private static final class TestTails extends Tails {
        private TestTails(String code, short x, short y) {
            super(code, x, y);
        }

        private void useRules(GameRules rules) {
            setGameRulesForTest(rules);
        }
    }

    private static final class TestSonic extends Sonic {
        private TestSonic(String code, short x, short y) {
            super(code, x, y);
        }

        private void useRules(GameRules rules) {
            setGameRulesForTest(rules);
        }
    }
}
