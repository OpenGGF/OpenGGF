package com.openggf.sprites.playable;

import com.openggf.game.*;
import com.openggf.game.mutators.MotionMutatorRuntime;
import com.openggf.game.mutators.PlayerFluidContextAccess;
import com.openggf.game.mutators.TransientMutatorPolicySource;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2PhysicsProvider;
import com.openggf.mods.mutators.FluidContextPolicy;
import com.openggf.mods.mutators.PlayerMotionPolicy;
import com.openggf.sprites.managers.PlayableSpriteMovement;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** REV01 effective WORDs retain their last writer independently of status. No route oracle. */
class TestNativePlayerSpeedWords {
    @BeforeEach void setup() { TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2); }
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    private static AbstractPlayableSprite player(String character) {
        var player = character.equals("tails") ? new Tails("tails", (short) 100, (short) 200)
                : new Sonic("sonic", (short) 100, (short) 200);
        PlayableSpriteInternalAccess.bindNativeSpeedOwner(player, GameServices.level().getWorldSession());
        return player;
    }

    @Test void structuralBindingNeverSeedsAndUnboundRestoreImmediatelyForgetsRawWords() {
        var owned = player("sonic");
        owned.giveSpeedShoes(8);
        var saved = owned.captureRewindState();
        var unbound = new Sonic("unbound", (short) 100, (short) 200);
        unbound.giveSpeedShoes(8);
        PlayableSpriteInternalAccess.initializeNativeSpeedWords(unbound);
        assertEquals(-1, unbound.nativeSpeedWords.maximum(), "unassembled birth and writers convey no owner");
        unbound.restoreRewindState(saved);
        assertEquals(-1, unbound.nativeSpeedWords.maximum(), "restore must be UNKNOWN before any getter can invalidate it");
        PlayableSpriteInternalAccess.bindNativeSpeedOwner(unbound, GameServices.level().getWorldSession());
        assertEquals(-1, unbound.nativeSpeedWords.maximum(), "structural binding is not native init");
        unbound.restoreRewindState(saved);
        words(unbound, 0xC00, 0x18, 0x80);
        unbound.restoreRewindState(saved.withObjectSubclassExtra(null));
        assertEquals(-1, unbound.nativeSpeedWords.maximum(), "legacy payload cannot invent words");
        unbound.restoreRewindState(saved);
        words(unbound, 0xC00, 0x18, 0x80);
        PlayableSpriteInternalAccess.bindNativeSpeedOwner(unbound,
                new com.openggf.game.session.WorldSession(new Sonic2GameModule()));
        assertEquals(-1, unbound.nativeSpeedWords.maximum(), "a changed live owner immediately invalidates knowledge");
    }

    @Test void currentGlobalModuleCannotConveyOrReplaceBoundWorldAuthority() {
        var owned = player("sonic");
        owned.giveSpeedShoes(8);
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_1);
        words(owned, 0xC00, 0x18, 0x80);
        var currentS1World = GameServices.level().getWorldSession();
        PlayableSpriteInternalAccess.bindNativeSpeedOwner(owned, currentS1World);
        assertEquals(-1, owned.nativeSpeedWords.maximum());
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        owned.giveSpeedShoes(8);
        assertEquals(-1, owned.nativeSpeedWords.maximum(), "current S2 cannot qualify an actor owned by S1");
    }

    private static void words(AbstractPlayableSprite player, int maximum, int acceleration, int deceleration) {
        assertEquals(maximum, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player), "raw max knownness/value");
        assertEquals(acceleration, PlayableSpriteInternalAccess.nativeAccelerationWord(player), "raw acceleration");
        assertEquals(deceleration, PlayableSpriteInternalAccess.nativeDecelerationWord(player), "raw deceleration");
        assertEquals(maximum, Short.toUnsignedInt(player.getMax()));
        assertEquals(acceleration, Short.toUnsignedInt(player.getRunAccel()));
        assertEquals(deceleration, Short.toUnsignedInt(player.getRunDecel()));
        assertEquals(maximum, Short.toUnsignedInt(player.getEffectiveMax()));
        assertEquals(acceleration, Short.toUnsignedInt(player.getEffectiveRunAccel()));
        assertEquals(deceleration, Short.toUnsignedInt(player.getEffectiveRunDecel()));
    }

    private static void expire(AbstractPlayableSprite player) {
        for (int tick = 0; tick < 8; tick++) player.currentTimerManager().updateDisplayPhaseTimersFor(player);
        assertFalse(player.hasSpeedShoes());
    }

    @ParameterizedTest @ValueSource(strings = {"sonic", "tails"})
    void shoesOverwriteAlreadyWetSonicAndTailsWithoutChangingWaterGravity(String character) {
        var player = player(character);
        player.setInWater(true);
        assertEquals(0x10, player.getEffectiveGravity());
        player.giveSpeedShoes(8);
        words(player, 0xC00, 0x18, 0x80);
        player.updateWaterState(player.getCentreY() - 1);
        words(player, 0xC00, 0x18, 0x80); // bset/bne: no new water edge
        expire(player);
        words(player, 0x600, 0xC, 0x80); // expiry has no water test
        assertTrue(player.isInWater());
        assertEquals(0x10, player.getEffectiveGravity());
        assertEquals(0x380, player.getJump());
    }

    @ParameterizedTest @ValueSource(strings = {"sonic", "tails"})
    void waterEdgesOverwriteActiveShoesAndAlreadyWetRetainsTheWriter(String character) {
        var player = player(character);
        player.updateWaterState(player.getCentreY() - 1);
        words(player, 0x300, 6, 0x40);
        player.giveSpeedShoes(8);
        words(player, 0xC00, 0x18, 0x80);
        player.updateWaterState(player.getCentreY());
        words(player, 0x600, 0xC, 0x80);
        assertTrue(player.hasSpeedShoes());
        assertFalse(player.isInWater());
        player.updateWaterState(player.getCentreY() - 1);
        words(player, 0x300, 6, 0x40);
        player.updateWaterState(player.getCentreY() - 1);
        words(player, 0x300, 6, 0x40);
        expire(player);
        words(player, 0x600, 0xC, 0x80);
        assertTrue(player.isInWater());
    }

    @Test void superWetPickupAndExpiryUseLiteralTripletsWhileKeepingGravity() {
        var player = player("sonic");
        player.applyExternalPhysicsProfile(PhysicsProfile.SONIC_2_SUPER_SONIC);
        player.setSuperSonic(true);
        player.updateWaterState(player.getCentreY() - 1);
        words(player, 0x500, 0x18, 0x80);
        player.giveSpeedShoes(8);
        words(player, 0xC00, 0x18, 0x80);
        expire(player);
        words(player, 0xA00, 0x30, 0x100);
        assertTrue(player.isInWater());
        assertEquals(0x10, player.getEffectiveGravity());
    }

    @Test void unsignedZeroKnownnessSnapshotsAndPrimitiveSwapNeverInferFromFlags() {
        var first = player("sonic"); var second = player("tails");
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(first));
        PlayableSpriteInternalAccess.publishNativeSpeedWords(first, 0, 0, 0);
        words(first, 0, 0, 0);
        var saved = first.captureRewindState();
        first.giveSpeedShoes(8);
        first.restoreRewindState(saved);
        words(first, 0, 0, 0);
        PlayableSpriteInternalAccess.swapNativeSpeedWords(first, second);
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(first));
        words(second, 0, 0, 0);
        PlayableSpriteInternalAccess.publishNativeSpeedWords(first, 0xFFFF, 0x8000, 0xFFFF);
        words(first, 0xFFFF, 0x8000, 0xFFFF);
        var immutable = first.captureRewindState();
        PlayableSpriteInternalAccess.publishNativeSpeedWords(first, 1, 2, 3);
        first.restoreRewindState(immutable);
        words(first, 0xFFFF, 0x8000, 0xFFFF);
        first.restoreRewindState(saved.withObjectSubclassExtra(null));
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(first), "legacy unwrapped extra is UNKNOWN");
        assertEquals(0x600, first.getMax());
        assertThrows(IllegalArgumentException.class,
                () -> PlayableSpriteInternalAccess.publishNativeSpeedWords(first, 0x10000, 0, 0));
    }

    @Test void publicProfilesAndGenericResetForgetWithoutSeedingAndUnknownGettersKeepTheirDistinctFallbacks() {
        var player = player("sonic");
        player.giveSpeedShoes(8); words(player, 0xC00, 0x18, 0x80);
        player.resetState();
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player));
        assertEquals(0x600, player.getMax());
        player.applyExternalPhysicsProfile(PhysicsProfile.SONIC_2_SUPER_SONIC);
        player.setSuperSonic(true); player.giveSpeedShoes(8);
        PlayableSpriteInternalAccess.invalidateNativeSpeedWords(player);
        assertEquals(0x30, player.getRunAccel());
        assertEquals(0x60, player.getEffectiveRunAccel(), "legacy effective getter keeps its independent shoes rule");
        assertEquals(0xA00, player.getMax());
        assertEquals(0x1400, player.getEffectiveMax());
        player.giveSpeedShoes(8);
        player.applyExternalPhysicsProfile(PhysicsProfile.SONIC_2_SONIC);
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player));
    }

    @Test void realCpuRoutineTwoRespawnRetainsTheDiscriminatingShoesBank() throws Exception {
        var leader = player("sonic"); var tails = player("tails");
        tails.setCpuControlled(true);
        var cpu = new SidekickCpuController(tails, leader);
        tails.giveSpeedShoes(8);
        tails.setDead(true);
        Method respawn = SidekickCpuController.class.getDeclaredMethod("respawnToApproaching", AbstractPlayableSprite.class);
        respawn.setAccessible(true);
        respawn.invoke(cpu, leader); // production TailsCPU_Respawn/strategy path, not Obj02_Init
        assertEquals(SidekickCpuController.State.APPROACHING, cpu.getState());
        words(tails, 0xC00, 0x18, 0x80);
    }

    @Test void neutralGroundUsesBankAccelerationAndOnlyActualSonicSuperFlagOverridesIt() throws Exception {
        var sonic = player("sonic");
        sonic.applyExternalPhysicsProfile(PhysicsProfile.SONIC_2_SUPER_SONIC);
        PlayableSpriteInternalAccess.publishNativeSpeedWords(sonic, 0xC00, 0x18, 0x80);
        coast(sonic); assertEquals(0x400 - 0x18, sonic.getGSpeed(), "profile alone is not Super_Sonic_flag");
        sonic.setSuperSonic(true);
        coast(sonic); assertEquals(0x400 - 0xC, sonic.getGSpeed());
        var tails = player("tails"); tails.setSuperSonic(true);
        PlayableSpriteInternalAccess.publishNativeSpeedWords(tails, 0xC00, 0x18, 0x80);
        coast(tails); assertEquals(0x400 - 0x18, tails.getGSpeed(), "Obj02 has no literal override");
    }

    private static void coast(AbstractPlayableSprite player) throws Exception {
        player.setGSpeed((short) 0x400);
        var movement = player.getMovementManager();
        Method input = PlayableSpriteMovement.class.getDeclaredMethod("storeInputState", boolean.class,
                boolean.class, boolean.class, boolean.class, boolean.class);
        input.setAccessible(true); input.invoke(movement, false, false, false, false, false);
        Method ground = PlayableSpriteMovement.class.getDeclaredMethod("doGroundMove");
        ground.setAccessible(true); ground.invoke(movement);
    }

    private static final class Source implements TransientMutatorPolicySource {
        private final List<Entry> entries;
        Source(com.openggf.mods.mutators.MutatorPolicy policy) {
            entries = List.of(new Entry("fixture", 1, policy, 0, 600, false));
        }
        public List<Entry> entries() { return entries; }
        public boolean acknowledge(long id) { return false; }
        public boolean report(long id, String status) { return true; }
    }

    @Test void localFluidKeepsExistingDynamicFallbackAndTractionAppliesOnceToNativeOperand() throws Exception {
        var player = player("sonic");
        GameServices.sprites().addSprite(player);
        player.giveSpeedShoes(8);
        var fluid = new MotionMutatorRuntime();
        fluid.bind(GameServices.sprites(), GameServices.gameState(), new Source(new FluidContextPolicy(64)));
        fluid.beforeMovement(player);
        assertTrue(PlayerFluidContextAccess.movementUnderwater(player));
        assertEquals(0x300, player.getMax(), "creator fluid preserves its existing profile calculation");
        assertEquals(6, PlayableSpriteInternalAccess.neutralGroundFrictionBase(player));
        assertEquals(0x10, player.getEffectiveGravity());
        var traction = new MotionMutatorRuntime();
        traction.bind(GameServices.sprites(), GameServices.gameState(),
                new Source(new PlayerMotionPolicy(PlayerMotionPolicy.Mode.TRACTION, 25)));
        traction.beforeMovement(player);
        coast(player);
        assertEquals(0x400 - 6, player.getGSpeed(), "native acceleration24 scaled once to6");
        player.setSuperSonic(true);
        coast(player); assertEquals(0x400 - 3, player.getGSpeed(), "literal12 scaled once to3");
    }

    @ParameterizedTest @EnumSource(value = SonicGame.class, names = {"SONIC_1", "SONIC_3K"})
    void otherGamesAndTheirWaterShoesRemainUnknown(SonicGame game) {
        TestEnvironment.configureGameModuleFixture(game);
        var player = player("sonic"); player.setInWater(true); player.giveSpeedShoes(8);
        int expected = player.getMax();
        PlayableSpriteInternalAccess.publishNativeSpeedWords(player, 0, 0, 0);
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player));
        assertEquals(expected, player.getMax());
    }

    @Test void customSubclassProviderWrapperAndActiveDonorDoNotAcquireNativeWords() throws Exception {
        class CustomSonic extends Sonic { CustomSonic() { super("sonic", (short) 100, (short) 200); } }
        var custom = new CustomSonic();
        PlayableSpriteInternalAccess.bindNativeSpeedOwner(custom, GameServices.level().getWorldSession());
        custom.setInWater(true); custom.giveSpeedShoes(8);
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(custom));
        assertEquals(0x300, custom.getMax());
        for (PhysicsProvider provider : new PhysicsProvider[] {new Sonic2PhysicsProvider(), new Sonic2PhysicsProvider() {},
                new Sonic2PhysicsProvider().transform(character -> true, profile -> profile)}) {
            TestEnvironment.configureGameModuleFixture(new DelegatingGameModule(new Sonic2GameModule(), "test:custom") {
                @Override public PhysicsProvider getPhysicsProvider() { return provider; }
            });
            var player = player("sonic"); player.setInWater(true); player.giveSpeedShoes(8);
            assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player));
            assertEquals(0x300, player.getMax());
        }
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_2);
        var player = player("sonic"); player.giveSpeedShoes(8);
        player.applyExternalPhysicsProfile(PhysicsProfile.SONIC_2_SONIC.withMax(0x777));
        player.giveSpeedShoes(8);
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player), "custom profile has no native authority");
        assertEquals(0xEEE, player.getMax());
        player.applyExternalPhysicsProfile(PhysicsProfile.SONIC_2_SONIC); player.giveSpeedShoes(8);
        var donor = CrossGameFeatureProvider.getInstance();
        Field active = CrossGameFeatureProvider.class.getDeclaredField("active"); active.setAccessible(true);
        active.setBoolean(donor, true);
        try {
            assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player));
            assertEquals(0xC00, player.getMax()); // unchanged dry shoes fallback
        } finally { active.setBoolean(donor, false); }
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player), "ineligible ownership cannot revive stale words");
    }
    @Test void ownerWorldPoliciesAndForeignHostServicesRemainUnknown() throws Exception {
        var player = player("sonic"); GameServices.sprites().addSprite(player);
        var world = player.currentLevelManagerIfAvailable().getWorldSession();
        Field policies = com.openggf.game.session.WorldSession.class.getDeclaredField("policies");
        policies.setAccessible(true); Object original = policies.get(world);
        var foreignPolicy = org.mockito.Mockito.mock(com.openggf.game.session.WorldSessionPolicyState.class);
        player.giveSpeedShoes(8); words(player, 0xC00, 0x18, 0x80);
        try {
            policies.set(world, foreignPolicy);
            PlayableSpriteInternalAccess.initializeNativeSpeedWords(player);
            assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player));
            assertEquals(0xC00, player.getMax(), "custom world retains the existing shoes fallback");
            var host = org.mockito.Mockito.mock(com.openggf.game.sonic2.competition.Sonic2NativeCompetitionHost.class);
            var foreignContext = org.mockito.Mockito.mock(com.openggf.game.session.GameplayModeContext.class);
            org.mockito.Mockito.when(host.context()).thenReturn(foreignContext);
            org.mockito.Mockito.when(foreignPolicy.getService(com.openggf.game.sonic2.competition.Sonic2CompetitionObjectHost.class))
                    .thenReturn(host);
            PlayableSpriteInternalAccess.initializeNativeSpeedWords(player);
            assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player),
                    "a Host service from another world conveys no authority");
        } finally { policies.set(world, original); }
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player));
    }

    @ParameterizedTest @EnumSource(value = SonicGame.class, names = {"SONIC_1", "SONIC_2", "SONIC_3K"})
    void pendingRealInstaPayloadSurvivesTheEngineAggregateAndLegacyUnwrappedRestore(SonicGame game) {
        TestEnvironment.configureGameModuleFixture(game);
        var player = player("sonic");
        var manager = new com.openggf.level.objects.ObjectManager(List.of(), null, 0, null, null);
        var services = new com.openggf.level.objects.TestObjectServices().withDirectObjectManager(manager);
        var shield = com.openggf.level.objects.ObjectConstructionContext.construct(services,
                () -> new com.openggf.game.sonic3k.objects.InstaShieldObjectInstance(player));
        shield.setServices(services); // Keep the real handle pending, with its normal post-construction services.
        player.setInstaShieldObject(shield); player.markInstaShieldForReregistration();
        if (game == SonicGame.SONIC_2) player.giveSpeedShoes(8);
        var saved = player.captureRewindState();
        var pending = assertInstanceOf(PendingInstaShieldRewindExtra.class,
                PlayableEngineRewindExtra.pending(saved.objectSubclassExtra()));
        var expected = shield.captureRewindState();
        shield.triggerAttack();
        assertNotEquals(expected, shield.captureRewindState());
        player.restoreRewindState(saved);
        assertSame(shield, player.getInstaShieldObject());
        assertEquals(expected, shield.captureRewindState());
        assertFalse(player.captureRewindState().playerExtra().instaShieldRegistered());
        if (game == SonicGame.SONIC_2) words(player, 0xC00, 0x18, 0x80);
        else assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player));
        shield.triggerAttack();
        player.restoreRewindState(saved.withObjectSubclassExtra(pending));
        assertEquals(expected, shield.captureRewindState());
        assertEquals(-1, PlayableSpriteInternalAccess.nativeMaximumSpeedWord(player), "old unwrapped payload has no speed words");
    }

}
