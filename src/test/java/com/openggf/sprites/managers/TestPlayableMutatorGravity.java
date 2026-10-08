package com.openggf.sprites.managers;

import com.openggf.game.GameServices;
import com.openggf.game.rules.GameRules;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.PlayableMutatorPolicy;
import com.openggf.sprites.playable.PlayableSpriteInternalAccess;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.tests.MutatorPlayerStateAssertions.assertNativeStateEquals;

@ExtendWith(SingletonResetExtension.class)
@FullReset
@Isolated
class TestPlayableMutatorGravity {
    @BeforeEach void setup() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
    }

    private Sonic airborne() throws Exception {
        var sonic = new Sonic("sonic", (short) 100, (short) 100);
        var rules = AbstractPlayableSprite.class.getDeclaredField("gameRules");
        rules.setAccessible(true);
        rules.set(sonic, GameRules.SONIC_2);
        sonic.setAir(true);
        sonic.setXSpeed((short) 0x123);
        sonic.setYSpeed((short) -0x101);
        sonic.setSubpixelRaw(0x8000, 0x4000);
        return sonic;
    }

    private void move(Sonic sonic) throws Exception {
        Method owner = PlayableSpriteMovement.class.getDeclaredMethod("doObjectMoveAndFall");
        owner.setAccessible(true);
        owner.invoke(sonic.getMovementManager());
    }

    private void bind(Sonic sonic, int percent) {
        PlayableSpriteInternalAccess.bindMutatorPolicies(sonic,
                sprite -> new PlayableMutatorPolicy(percent, false, false, false));
    }

    @ParameterizedTest
    @CsvSource({"25,14", "33,18", "100,56", "200,112"})
    void scalesOnlyAccelerationWhileIntegratingOldVelocity(int percent, int acceleration) throws Exception {
        var stock = airborne();
        var modified = airborne();
        bind(modified, percent);
        move(stock);
        move(modified);
        assertEquals(-0x101 + acceleration, modified.getYSpeed());
        assertEquals(stock.getCentreX(), modified.getCentreX());
        assertEquals(stock.getCentreY(), modified.getCentreY());
        assertEquals(stock.getXSubpixelRaw(), modified.getXSubpixelRaw());
        assertEquals(stock.getYSubpixelRaw(), modified.getYSubpixelRaw());
        assertEquals(stock.getXSpeed(), modified.getXSpeed());
    }

    @Test void scaledAccelerationRetainsNativeWordOverflow() throws Exception {
        var sonic = airborne();
        sonic.setYSpeed((short) (Short.MAX_VALUE - 16));
        bind(sonic, 200);
        move(sonic);
        assertEquals((short) (Short.MAX_VALUE - 16 + 112), sonic.getYSpeed());
    }

    @Test void boundStockIsExactIdentityOverConsecutiveNativeMoves() throws Exception {
        var stock = airborne();
        var bound = airborne();
        bind(bound, 100);
        for (int tick = 0; tick < 120; tick++) {
            move(stock);
            move(bound);
            assertNativeStateEquals(stock.captureRewindState(), bound.captureRewindState(), "native state at tick " + tick);
        }
    }

    @Test void gravityDoesNotScaleTheNativeJumpImpulse() throws Exception {
        var stock = airborne();
        var modified = airborne();
        stock.setAir(false); modified.setAir(false);
        stock.setYSpeed((short) 0); modified.setYSpeed((short) 0);
        bind(modified, 25);
        Method jump = PlayableSpriteMovement.class.getDeclaredMethod("doJump");
        jump.setAccessible(true);
        assertEquals(true, jump.invoke(stock.getMovementManager()));
        assertEquals(true, jump.invoke(modified.getMovementManager()));
        assertEquals(-0x680, modified.getYSpeed());
        assertNativeStateEquals(stock.captureRewindState(), modified.captureRewindState());
    }

    @Test void reverseGravityKeepsItsNativeIntegrationDirection() throws Exception {
        GameServices.gameState().setReverseGravityActive(true);
        var stock = airborne();
        var modified = airborne();
        bind(modified, 25);
        int before = modified.getCentreY();
        move(stock);
        move(modified);
        assertTrue(modified.getCentreY() > before, "native reverse integration mirrors upward velocity");
        assertEquals(stock.getCentreY(), modified.getCentreY());
        assertEquals(stock.getYSubpixelRaw(), modified.getYSubpixelRaw());
        assertEquals(-0x101 + 14, modified.getYSpeed());
    }

    @Test void excludesWaterHurtDeathGroundAndObjectControl() throws Exception {
        for (int mode = 0; mode < 5; mode++) {
            var stock = airborne();
            var modified = airborne();
            switch (mode) {
                case 0 -> { stock.setInWater(true); modified.setInWater(true); }
                case 1 -> { stock.setHurt(true); modified.setHurt(true); }
                case 2 -> { stock.setDead(true); modified.setDead(true); }
                case 3 -> { stock.setAir(false); modified.setAir(false); }
                case 4 -> { stock.setObjectControlled(true); modified.setObjectControlled(true); }
            }
            bind(modified, 200);
            move(stock);
            move(modified);
            assertNativeStateEquals(stock.captureRewindState(), modified.captureRewindState(), "excluded mode " + mode);
        }
    }

    @Test void policyPublicationPreservesVelocityAndReadsRestoredEffectiveRevision() throws Exception {
        var sonic = airborne();
        var policy = new AtomicReference<>(PlayableMutatorPolicy.STOCK);
        PlayableSpriteInternalAccess.bindMutatorPolicies(sonic, sprite -> policy.get());
        var checkpoint = sonic.captureRewindState();
        policy.set(new PlayableMutatorPolicy(25, false, false, false));
        assertEquals(-0x101, sonic.getYSpeed(), "publication does not rewrite velocity");
        move(sonic);
        var expected = sonic.captureRewindState();
        policy.set(new PlayableMutatorPolicy(200, false, false, false));
        move(sonic);
        policy.set(new PlayableMutatorPolicy(25, false, false, false));
        sonic.restoreRewindState(checkpoint);
        move(sonic);
        assertNativeStateEquals(expected, sonic.captureRewindState());
    }

    @Test void independentRostersBindReplacementSpritesAndRetireRemovedOwners() throws Exception {
        var left = new SpriteManager();
        var right = new SpriteManager();
        SpriteManagerInternalAccess.bindMutatorPolicies(left, sprite -> new PlayableMutatorPolicy(25, false, false, false));
        SpriteManagerInternalAccess.bindMutatorPolicies(right, sprite -> new PlayableMutatorPolicy(200, false, false, false));
        var first = airborne();
        var second = airborne();
        left.addSprite(first);
        right.addSprite(second);
        move(first); move(second);
        assertEquals(-0x101 + 14, first.getYSpeed());
        assertEquals(-0x101 + 112, second.getYSpeed());
        var replacement = airborne();
        left.addSprite(replacement);
        assertSame(PlayableMutatorPolicy.STOCK, PlayableSpriteInternalAccess.mutatorPolicy(first));
        move(replacement);
        assertEquals(-0x101 + 14, replacement.getYSpeed());
        left.clearAllSprites();
        var afterLoad = airborne();
        left.addSprite(afterLoad);
        move(afterLoad);
        assertEquals(-0x101 + 14, afterLoad.getYSpeed());
        left.resetState();
        assertSame(PlayableMutatorPolicy.STOCK, PlayableSpriteInternalAccess.mutatorPolicy(afterLoad));
    }

    @Test void forwardCallbackRunsOncePerOrdinaryDispatchIncludingUnpolledFrames() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var roster = new SpriteManager();
        SpriteManagerInternalAccess.bindMutatorPolicies(roster, new com.openggf.sprites.playable.PlayableMutatorPolicySource() {
            @Override public PlayableMutatorPolicy policyFor(AbstractPlayableSprite sprite) {
                return PlayableMutatorPolicy.STOCK;
            }
            @Override public void beforeForwardTick() { calls.incrementAndGet(); }
        });
        roster.update(new com.openggf.control.InputHandler());
        assertEquals(1, calls.get());
        roster.updateWithoutInput();
        assertEquals(2, calls.get());
        roster.draw();
        assertEquals(2, calls.get(), "drawing cannot advance configuration history");
    }

    @Test void rejectsUnsafeGravityValues() {
        assertThrows(IllegalArgumentException.class, () -> new PlayableMutatorPolicy(24, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new PlayableMutatorPolicy(201, false, false, false));
    }

    @ParameterizedTest
    @CsvSource({"S1,25,14", "S1,100,56", "S1,200,112", "S2,25,14", "S2,100,56", "S2,200,112",
            "S3K,25,14", "S3K,100,56", "S3K,200,112"})
    void qualifiesOrdinaryDryGravityForEveryNativeGame(String game, int percent, int acceleration) throws Exception {
        com.openggf.game.GameModule module = switch (game) {
            case "S1" -> new com.openggf.game.sonic1.Sonic1GameModule();
            case "S3K" -> new com.openggf.game.sonic3k.Sonic3kGameModule();
            default -> new Sonic2GameModule();
        };
        TestEnvironment.configureGameModuleFixture(module);
        var stock = airborne();
        var modified = airborne();
        var rules = AbstractPlayableSprite.class.getDeclaredField("gameRules");
        rules.setAccessible(true);
        rules.set(stock, module.getRules()); rules.set(modified, module.getRules());
        bind(modified, percent);
        move(stock); move(modified);
        assertEquals(-0x101 + acceleration, modified.getYSpeed());
        assertEquals(stock.getCentreY(), modified.getCentreY());
        assertEquals(stock.getYSubpixelRaw(), modified.getYSubpixelRaw());
        if (percent == 100) assertNativeStateEquals(stock.captureRewindState(), modified.captureRewindState());
    }
}
