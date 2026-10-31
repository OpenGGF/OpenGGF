package com.openggf.game.mutators;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;
import com.openggf.game.session.WorldSessionPolicyProvider;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.badniks.BuzzerBadnikInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.homing.HomingTarget;
import com.openggf.level.objects.homing.HomingTargetKind;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.mutators.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Real S2 EHZ1 assembly and native touch/defeat owners; the mutator only aims the roll. */
@Isolated
class TestHomingAttackMutator {
    private static final String KEY = "test-mutators:homing";
    @TempDir Path temp;
    private String oldRoot;
    private ModFaultBoundary faults;

    @BeforeEach void setup() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        SessionManager.clear();
        oldRoot = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", temp.resolve("player").toString());
        faults = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { });
    }

    @AfterEach void cleanup() {
        SessionManager.clear();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        if (oldRoot == null) System.clearProperty("openggf.saveRoot");
        else System.setProperty("openggf.saveRoot", oldRoot);
    }

    private WorldSession open(MutatorPolicy.HomingMode mode) {
        return open(mode, new Sonic2GameModule(), new Sonic2GameModule());
    }

    private WorldSession open(MutatorPolicy.HomingMode mode, com.openggf.game.GameModule base,
                              com.openggf.game.GameModule nativeModule) {
        var definition = new MutatorDefinition("homing", "Homing", "", MutatorScope.LIVE, MutatorScope.LIVE, List.of(),
                Set.of(MutatorCapability.HOMING_ATTACK),
                options -> List.of(new MutatorPolicy.HomingAttack(mode, Set.of(MutatorPolicy.HomingCharacter.SONIC))));
        var catalog = new MutatorCatalog(List.of(new OwnedMutator("test-mutators", definition)), faults);
        var module = new DelegatingGameModule(base, "test-mutators:homing-fixture") {
            @Override public boolean supportsSidekick() { return false; }
            @Override public <T> T getGameService(Class<T> type) {
                if (type == WorldSessionPolicyProvider.class) return type.cast(new MutatorWorldProvider(catalog));
                if (type == MutatorCatalog.class) return type.cast(catalog);
                if (type == MutatorSupportProfile.class) return type.cast(new MutatorSupportProfile() {
                    public Set<MutatorCapability> capabilities(int zone, int act) { return Set.of(MutatorCapability.HOMING_ATTACK); }
                    public boolean supportsPlayer(String key, boolean leader) { return leader; }
                    public boolean supportsPlayer(MutatorCapability capability, String key, boolean leader) {
                        return leader && key.equals("sonic");
                    }
                });
                return super.getGameService(type);
            }
        };
        GameModuleRegistry.setCurrent(module);
        SessionManager.openGameplaySession(nativeModule, module, null);
        TestEnvironment.activeGameplayMode();
        var world = SessionManager.getCurrentWorldSession();
        var state = MutatorWorldAccess.state(world);
        state.requestEnabled(KEY, true);
        assertTrue(state.boundary(MutatorScope.LIVE).accepted());
        return world;
    }

    private static HomingAttackRuntime runtime(WorldSession world) {
        return WorldSessionPolicyAccess.getService(world, HomingAttackRuntime.class);
    }

    /** Jump, release so the native variable-height cap opens the air-ability window, then re-press. */
    private static void jumpAndRepress(HeadlessTestFixture fixture) {
        fixture.stepFrame(false, false, false, false, true);
        for (int frame = 0; frame < 6; frame++) fixture.stepFrame(false, false, false, false, false);
        fixture.stepFrame(false, false, false, false, true);
    }

    private static HeadlessTestFixture settledEhz1() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).build();
        fixture.stepIdleFrames(60);
        assertFalse(fixture.sprite().getAir(), "Sonic must start the probe standing");
        return fixture;
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void homingStrikesAVisibleBadnikThroughTheNativeKillAndRefreshesTheAirAction() {
        var world = open(MutatorPolicy.HomingMode.HOMING);
        var fixture = settledEhz1();
        var sonic = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var buzzer = objects.createDynamicObject(() -> new BuzzerBadnikInstance(new ObjectSpawn(
                sonic.getCentreX() + 96, sonic.getCentreY() - 72, Sonic2ObjectIds.BUZZER, 0, 0, false, 0)));
        fixture.stepIdleFrames(2);
        jumpAndRepress(fixture);
        var homing = runtime(world);
        assertEquals(HomingAttackRuntime.Phase.HOMING, homing.phase(), "A visible badnik ahead takes the re-press");
        assertTrue(sonic.getXSpeed() > 0 && sonic.getYSpeed() < 0, "The roll aims up and right at the Buzzer");
        for (int frame = 0; frame < 40 && !buzzer.isDestroyed(); frame++) fixture.stepFrame(false, false, false, false, false);
        assertTrue(buzzer.isDestroyed(), "The native touch pass kills the badnik on arrival");
        assertEquals(HomingAttackRuntime.Phase.IDLE, homing.phase());
        assertTrue(sonic.getYSpeed() < 0, "A strike pops the player upwards for the next attack");
        assertTrue(sonic.getAir());
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void withoutATargetSonic2SonicAirDashesForward() {
        var world = open(MutatorPolicy.HomingMode.HOMING);
        var fixture = settledEhz1();
        var sonic = fixture.sprite();
        jumpAndRepress(fixture);
        // Native air drag (x_vel -= x_vel/32 while rising slowly) still runs after the dash.
        assertEquals(HomingAttackRuntime.DASH_SPEED - (HomingAttackRuntime.DASH_SPEED >> 5), sonic.getXSpeed(),
                "S2 Sonic has no native double-jump move, so the forward dash fires");
        // A spent air action does not dash twice before landing.
        for (int frame = 0; frame < 3; frame++) fixture.stepFrame(false, false, false, false, false);
        short before = sonic.getXSpeed();
        fixture.stepFrame(false, false, false, false, true);
        assertTrue(sonic.getXSpeed() <= before, "One air action per jump");
        assertNotNull(runtime(world));
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void restoredHistoryReplaysTheSameHomingFlight() {
        var world = open(MutatorPolicy.HomingMode.HOMING);
        var fixture = settledEhz1();
        var sonic = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        objects.createDynamicObject(() -> new BuzzerBadnikInstance(new ObjectSpawn(
                sonic.getCentreX() + 120, sonic.getCentreY() - 72, Sonic2ObjectIds.BUZZER, 0, 0, false, 0)));
        fixture.stepIdleFrames(2);
        jumpAndRepress(fixture);
        assertEquals(HomingAttackRuntime.Phase.HOMING, runtime(world).phase());
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var before = registry.capture();
        assertTrue(before.containsKey("mutators"));
        var expected = new java.util.ArrayList<List<Integer>>();
        for (int frame = 0; frame < 12; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            expected.add(sample(sonic, world));
        }
        registry.restore(before);
        assertEquals(HomingAttackRuntime.Phase.HOMING, runtime(world).phase(), "The attack phase is captured history");
        for (int frame = 0; frame < 12; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            assertEquals(expected.get(frame), sample(sonic, world), "Replay frame " + frame);
        }
    }

    private static List<Integer> sample(com.openggf.sprites.playable.AbstractPlayableSprite sonic, WorldSession world) {
        return List.of((int) sonic.getCentreX(), (int) sonic.getCentreY(), (int) sonic.getXSpeed(),
                (int) sonic.getYSpeed(), runtime(world).phase().ordinal());
    }

    /** A bare eight-hit ENEMY-category boss body: native touch rebounds while hits remain. */
    static final class EightHitBoss extends com.openggf.level.objects.AbstractObjectInstance
            implements com.openggf.level.objects.TouchResponseProvider, com.openggf.level.objects.TouchResponseAttackable,
            com.openggf.level.objects.boss.InstagibBossTarget {
        int hits = 8, struck;
        EightHitBoss(ObjectSpawn spawn) { super(spawn, "EightHitBoss"); }
        @Override public void appendRenderCommands(List<com.openggf.graphics.GLCommand> commands) { }
        @Override public int getCollisionFlags() { return hits == 0 ? 0 : 0x0F; }
        @Override public int getCollisionProperty() { return hits; }
        @Override public void onPlayerAttack(com.openggf.game.PlayableEntity player, com.openggf.level.objects.TouchResponseResult result) {
            struck++;
            if (hits > 0) hits--;
        }
        @Override public void collapseToFinalHit() { if (hits > 1) hits = 1; }
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void instagibEndsABossInOneNativeHitAndBouncesOff() {
        var world = open(MutatorPolicy.HomingMode.INSTAGIB);
        var fixture = settledEhz1();
        var sonic = fixture.sprite();
        var objects = GameServices.level().getObjectManager();
        var boss = objects.createDynamicObject(() -> new EightHitBoss(new ObjectSpawn(
                sonic.getCentreX() + 80, sonic.getCentreY() - 64, 0, 0, 0, false, 0)));
        fixture.stepIdleFrames(2);
        jumpAndRepress(fixture);
        assertEquals(HomingAttackRuntime.Phase.HOMING, runtime(world).phase(), "A boss body is a homing target");
        assertTrue(runtime(world).flameActive(), "Instagib lights the friction-fire trail");
        for (int frame = 0; frame < 40 && boss.struck == 0; frame++) fixture.stepFrame(false, false, false, false, false);
        assertEquals(1, boss.struck, "Exactly one native attack lands");
        assertEquals(0, boss.hits, "Instagib makes that one hit the defeating one");
        assertTrue(sonic.getYSpeed() <= -0x200, "The player bounces off the boss");
    }

    @Test
    @RequiresRom(SonicGame.SONIC_2)
    void ordinaryHomingLeavesBossHitCountsNative() {
        open(MutatorPolicy.HomingMode.HOMING);
        var fixture = settledEhz1();
        var sonic = fixture.sprite();
        var boss = GameServices.level().getObjectManager().createDynamicObject(() -> new EightHitBoss(new ObjectSpawn(
                sonic.getCentreX() + 80, sonic.getCentreY() - 64, 0, 0, 0, false, 0)));
        fixture.stepIdleFrames(2);
        jumpAndRepress(fixture);
        for (int frame = 0; frame < 40 && boss.struck == 0; frame++) fixture.stepFrame(false, false, false, false, false);
        assertEquals(1, boss.struck);
        assertEquals(7, boss.hits, "Homing mode strikes like a jump: one native hit");
    }

    @Test
    @RequiresRom(SonicGame.SONIC_3K)
    void s3kSonicKeepsTheInstaShieldWithoutATargetAndHomesWithOne() {
        // The wrapped module must be the session's native root: S3K's fresh-level clear checks it.
        var s3k = new com.openggf.game.sonic3k.Sonic3kGameModule();
        var world = open(MutatorPolicy.HomingMode.HOMING, s3k, s3k);
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 0).withSkippedZoneIntro().build();
        fixture.stepIdleFrames(60);
        var sonic = fixture.sprite();
        assertFalse(sonic.getAir());
        jumpAndRepress(fixture);
        assertEquals(1, sonic.getDoubleJumpFlag(), "No target: the native insta-shield takes the press");
        assertEquals(HomingAttackRuntime.Phase.IDLE, runtime(world).phase());
        for (int frame = 0; frame < 120 && sonic.getAir(); frame++) fixture.stepFrame(false, false, false, false, false);
        assertFalse(sonic.getAir());
        fixture.stepIdleFrames(4);
        var boss = GameServices.level().getObjectManager().createDynamicObject(() -> new EightHitBoss(new ObjectSpawn(
                sonic.getCentreX() + 80, sonic.getCentreY() - 64, 0, 0, 0, false, 0)));
        fixture.stepIdleFrames(2);
        jumpAndRepress(fixture);
        assertEquals(HomingAttackRuntime.Phase.HOMING, runtime(world).phase(), "A target outranks the insta-shield");
        assertEquals(0, sonic.getDoubleJumpFlag(), "The homing launch leaves the native ability flag untouched");
        for (int frame = 0; frame < 40 && boss.struck == 0; frame++) fixture.stepFrame(false, false, false, false, false);
        assertEquals(1, boss.struck);
    }

    @Test
    void hiddenPriorityThenFacingThenKindThenDistanceSelectTheTarget() {
        var boss = new HomingTarget(HomingTargetKind.BOSS, 0, 0, 0);
        var badnik = new HomingTarget(HomingTargetKind.BADNIK, 0, 0, 0);
        var part = new HomingTarget(HomingTargetKind.BOSS, 2, 0, 0);
        assertTrue(HomingAttackRuntime.better(part, false, 150, boss, true, 10), "Hidden priority outranks everything");
        assertTrue(HomingAttackRuntime.better(badnik, true, 150, boss, false, 10), "A target ahead outranks one behind");
        assertTrue(HomingAttackRuntime.better(boss, true, 150, badnik, true, 10), "Then bosses outrank badniks");
        assertTrue(HomingAttackRuntime.better(badnik, true, 10, badnik, true, 150), "Then the nearer target wins");
    }

    @Test
    void policyCompositionUnitesCharactersAndPrefersInstagib() {
        var policies = Map.<String, List<MutatorPolicy>>of(
                "a", List.of(new MutatorPolicy.HomingAttack(MutatorPolicy.HomingMode.HOMING, Set.of(MutatorPolicy.HomingCharacter.SONIC))),
                "b", List.of(new MutatorPolicy.HomingAttack(MutatorPolicy.HomingMode.INSTAGIB, Set.of(MutatorPolicy.HomingCharacter.KNUCKLES))));
        var effective = new MutatorSessionState.Effective(1, policies);
        var composed = effective.homingPolicy(Set.of(MutatorCapability.HOMING_ATTACK));
        assertEquals(HomingAttackRuntime.Mode.INSTAGIB, composed.mode());
        assertEquals(Set.of("sonic", "knuckles"), composed.characters());
        assertSame(HomingAttackRuntime.Policy.STOCK, effective.homingPolicy(Set.of()), "Unavailable capability stays stock");
    }
}
