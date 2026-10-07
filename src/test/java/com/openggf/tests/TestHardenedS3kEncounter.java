package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.ShieldType;
import com.openggf.mods.code.EffectiveCatalogPatchEnablement;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplaySessionFactory;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.io.ModInputLimits;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.mods.DefaultModRepositoryScanner;
import com.openggf.mods.EffectiveCatalogBuilder;
import com.openggf.mods.ModCatalogValidator;
import com.openggf.mods.ModDescriptor;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModState;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModClassLoaderFactory;
import com.openggf.mods.code.ModClassResolver;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModRuntime;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual external encounter classes in a trusted owner/classloader, executing native
 * MHZ production updates. This deliberately short lane covers encounter contracts;
 * title/load/placement transforms and whole-act traversal have separate coverage.
 */
@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestHardenedS3kEncounter {
    private static final String OWNER = "hardened-s3k";
    @TempDir static Path temp;
    private static Path jar;

    @BeforeAll static void compileMaintainedEncounter() throws Exception {
        Path classes = Files.createDirectories(temp.resolve("classes"));
        var arguments = new ArrayList<>(List.of("--release", "21", "-classpath",
                TestSessionOutputPaths.compiledClasses().toAbsolutePath().toString(), "-d", classes.toString()));
        Path source = Path.of("examples/hardened-s3k/src/main/java/hardened");
        for (String name : List.of("EncounterPlan", "EncounterState", "Sentry", "Spore"))
            arguments.add(source.resolve(name + ".java").toString());
        Path fixture = Path.of("src/test/resources/mods/hardened-encounter-src");
        arguments.add(fixture.resolve("hardened/fixture/EncounterFixtureMod.java").toString());
        var errors = new ByteArrayOutputStream();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, errors, errors,
                arguments.toArray(String[]::new)), errors.toString(StandardCharsets.UTF_8));
        Files.createDirectories(classes.resolve("META-INF"));
        Files.copy(fixture.resolve("META-INF/openggf-mod.yaml"), classes.resolve("META-INF/openggf-mod.yaml"));
        Path repository = Files.createDirectories(temp.resolve("repository"));
        jar = repository.resolve("hardened-s3k.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar)); var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output); output.closeEntry();
            }
        }
    }

    @AfterAll static void releaseMode() { SessionManager.clear(); }

    @Test void nativePostContactAndProtectedWaitAllowNoDamageExit() throws Exception {
        try (var encounter = open()) {
            var f = encounter.fixture;
            assertInstanceOf(Sonic3kLevel.class, GameServices.level().getCurrentLevel());
            encounter.begin();
            // Short independent local route beside the physical ROM post. Never seed
            // checkpoint state or rewrite native physics to make this solution work.
            encounter.until(() -> GameServices.level().getCheckpointState().isActive(), 100, true, false);
            assertTrue(GameServices.level().getCheckpointState().isActive(), encounter.position());
            assertEquals(2, GameServices.level().getCheckpointState().getLastCheckpointIndex());
            encounter.until(() -> !encounter.value("phase").toString().equals("WAITING"), 100, true, false);
            assertNotEquals("WAITING", encounter.value("phase").toString(), encounter.position());
            encounter.until(() -> (f.sprite().getCentreX() & 0xffff) <= encounter.constant("POST_X"), 60, false, true);
            encounter.frames(12, false, false);
            assertTrue((f.sprite().getCentreX() & 0xffff) < encounter.constant("ATTACK_LEFT"), encounter.position());
            encounter.frames(180, false, false);
            assertFalse(f.sprite().getDead());
            assertFalse(f.sprite().getInvulnerable(), "safe wait cannot consume a damage boost");
            assertEquals(2, encounter.value("volleys"));
            encounter.until(() -> encounter.value("status").toString().equals("CLEARED"), 120, true, false);
            assertEquals("CLEARED", encounter.value("status").toString(), encounter.position());
        }
    }

    @Test void physicalCheckpointSurvivesTwoNativeDeathReloads() throws Exception {
        try (var encounter = open()) {
            encounter.begin();
            encounter.until(() -> GameServices.level().getCheckpointState().isActive(), 100, true, false);
            var checkpoint = GameServices.level().getCheckpointState();
            int savedX = checkpoint.getSavedX(), savedY = checkpoint.getSavedY();
            var input = new com.openggf.control.InputHandler();
            var neutral = new Bk2FrameInput(0, 0, 0, false, "");
            input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral, neutral));
            var loop = new com.openggf.GameLoop(input);
            loop.setGameplayMode(encounter.fixture.gameplayMode());
            loop.setGameMode(com.openggf.game.GameMode.LEVEL);
            try {
                for (int cycle = 0; cycle < 2; cycle++) {
                    var oldManager = GameServices.level().getObjectManager();
                    assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath(), "declared native death stimulus");
                    boolean loaded = false;
                    for (int n = 0; n < 1200; n++) {
                        encounter.fixture.gameplayMode().getFadeManager().update(); loop.step();
                        if (GameServices.level().getObjectManager() != oldManager) { loaded = true; break; }
                    }
                    assertTrue(loaded, "native GameLoop death reload cycle " + cycle);
                    assertInstanceOf(Sonic3kLevel.class, GameServices.level().getCurrentLevel());
                    assertEquals(2, GameServices.level().getCheckpointState().getLastCheckpointIndex());
                    assertEquals(savedX, GameServices.sprites().getMainPlayable().getCentreX() & 0xffff);
                    assertEquals(savedY, GameServices.sprites().getMainPlayable().getCentreY() & 0xffff);
                    assertNotSame(oldManager, GameServices.level().getObjectManager());
                    // The production controller observes this native load boundary.
                    // This core fixture models only its documented session-ledger call.
                    encounter.call("resetForLoad");
                    assertEquals("READY", encounter.value("status").toString());
                    assertEquals(0, encounter.value("volleys"));
                    boolean released = false;
                    for (int n = 0; n < 500; n++) {
                        encounter.fixture.gameplayMode().getFadeManager().update(); loop.step();
                        var title = GameServices.module().getTitleCardProvider();
                        if (loop.getCurrentGameMode() == com.openggf.game.GameMode.LEVEL
                                && (title == null || title.isComplete())
                                && !encounter.fixture.gameplayMode().getFadeManager().isActive()
                                && !GameServices.sprites().getMainPlayable().isControlLocked()) {
                            released = true; break;
                        }
                    }
                    assertTrue(released, "native card and fade release after cycle " + cycle);
                    assertFalse(GameServices.sprites().getMainPlayable().getDead());
                }
            } finally { loop.closePresence(); }
        }
    }

    @Test void committedAimTwoVolleysCapAndProjectilesRecreateAcrossTwoReplayCycles() throws Exception {
        try (var encounter = open()) {
            encounter.begin();
            encounter.until(() -> !encounter.value("phase").toString().equals("WAITING"), 100, true, false);
            encounter.until(() -> (encounter.fixture.sprite().getCentreX() & 0xffff) <= encounter.constant("POST_X"), 60, false, true);
            encounter.frames(12, false, false);
            encounter.until(() -> !encounter.spores().isEmpty(), 100, false, false);
            var objects = GameServices.level().getObjectManager();
            assertFalse(encounter.spores().isEmpty(), "first volley must be live");
            assertTrue(encounter.spores().size() <= encounter.constant("PROJECTILE_CAP"));
            var registry = encounter.fixture.gameplayMode().getRewindRegistry();
            var before = registry.capture();
            encounter.frames(60, false, false);
            var after = registry.capture();
            for (int cycle = 0; cycle < 2; cycle++) {
                for (ObjectInstance shot : encounter.spores()) objects.removeDynamicObject(shot);
                registry.restore(before);
                assertFalse(encounter.spores().isEmpty(), "destroyed mod shots must be recreated cycle " + cycle);
                sameEncounterWorld(before, registry.capture(), "restore " + cycle);
                encounter.fixture.runner().primeInputState(new Bk2FrameInput(0, 0, 0, false, ""));
                encounter.frames(60, false, false);
                sameEncounterWorld(after, registry.capture(), "replay " + cycle);
            }
        }
    }

    @Test void nativeRingLossAndZeroRingDeathRemainNative() throws Exception {
        try (var encounter = open()) {
            encounter.begin();
            encounter.fixture.sprite().setRingCount(3); // declared damage setup, not a route reward.
            encounter.stationarySporeAtPlayer();
            encounter.frames(4, false, false);
            assertEquals(0, encounter.fixture.sprite().getRingCount());
            assertFalse(encounter.fixture.sprite().getDead());
            assertTrue(encounter.fixture.sprite().getInvulnerable());
        }
        try (var encounter = open()) {
            encounter.begin();
            encounter.fixture.sprite().setRingCount(0);
            encounter.stationarySporeAtPlayer();
            encounter.frames(4, false, false);
            assertTrue(encounter.fixture.sprite().getDead());
            assertEquals("FAILED", encounter.value("status").toString());
        }
    }

    @Test void elementalShieldDeflectsThroughNativeTouchWithoutRingToll() throws Exception {
        for (var shield : List.of(ShieldType.FIRE, ShieldType.LIGHTNING, ShieldType.BUBBLE)) {
            try (var encounter = open()) {
                encounter.begin();
                encounter.fixture.sprite().setRingCount(1);
                encounter.fixture.sprite().giveShield(shield);
                var shot = encounter.stationarySporeAtPlayer();
                encounter.frames(4, false, false);
                assertFalse(encounter.fixture.sprite().getDead());
                assertTrue(encounter.fixture.sprite().hasShield(), shield.toString());
                assertEquals(1, encounter.fixture.sprite().getRingCount());
                assertEquals(0, ((com.openggf.level.objects.TouchResponseProvider) shot).getCollisionFlags());
            }
        }
    }

    @Test void offscreenReentryRepeatsTellAndAbortDisarmsExistingShots() throws Exception {
        try (var encounter = open()) {
            encounter.begin();
            encounter.until(() -> !encounter.value("phase").toString().equals("WAITING"), 100, true, false);
            encounter.frames(5, false, false);
            var sentry = encounter.sentry();
            int savedCameraX = encounter.fixture.camera().getX();
            encounter.fixture.camera().setX((short) (encounter.constant("SENTRY_X") + 200));
            sentry.update(0, encounter.fixture.sprite());
            assertEquals("WAITING", encounter.read(sentry, "phase").toString());
            assertEquals(0, ((com.openggf.level.objects.TouchResponseProvider) sentry).getCollisionFlags());
            encounter.fixture.camera().setX((short) savedCameraX);
            sentry.update(0, encounter.fixture.sprite());
            assertEquals("TELL", encounter.read(sentry, "phase").toString());
            assertEquals(0, encounter.read(sentry, "phaseTick"));
            encounter.call("abort", "fault-injection");
            for (var object : GameServices.level().getObjectManager().getActiveObjects()) {
                if (object.getClass().getName().startsWith("hardened.")
                        && object instanceof com.openggf.level.objects.TouchResponseProvider touch)
                    assertEquals(0, touch.getCollisionFlags(), "fault abort must disarm before another native tick");
            }
            encounter.frames(2, false, false);
            assertTrue(encounter.spores().isEmpty());
        }
    }

    @Test void earlyExitWaitsForBothVolleysAndFullRecovery() throws Exception {
        try (var encounter = open()) {
            encounter.begin();
            encounter.until(() -> !encounter.value("phase").toString().equals("WAITING"), 100, true, false);
            encounter.until(() -> (encounter.fixture.sprite().getCentreX() & 0xffff) <= encounter.constant("POST_X"), 60, false, true);
            encounter.frames(12, false, false);
            encounter.until(() -> encounter.value("volleys").equals(2), 140, false, false);
            assertFalse(encounter.spores().isEmpty(), "second volley must still exist at its emission boundary");
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(encounter.fixture.sprite(), encounter.constant("EXIT_X"));
            encounter.call("afterGameplayTick", encounter.fixture.sprite(), true);
            assertEquals("ACTIVE", encounter.value("status").toString(), "exit cannot erase the second volley on emission");
            encounter.frames(20, false, false);
            assertEquals("ACTIVE", encounter.value("status").toString(), "volley travel remains playable before recovery");
            encounter.until(() -> encounter.value("status").toString().equals("CLEARED"), 140, false, false);
            assertEquals("RESTING", encounter.value("phase").toString());
        }
    }

    @Test void boundedAuthoringRejectsUnsafePatternValuesAndExpiredSporeDisarms() throws Exception {
        try (var encounter = open()) {
            var validate = encounter.plan.getMethod("validatePattern", int.class, int.class, int.class);
            assertDoesNotThrow(() -> validate.invoke(null, 36, 4, 180));
            for (int[] invalid : List.of(new int[]{0, 4, 180}, new int[]{36, 5, 180}, new int[]{36, 4, 181})) {
                var failure = assertThrows(InvocationTargetException.class,
                        () -> validate.invoke(null, invalid[0], invalid[1], invalid[2]));
                assertInstanceOf(IllegalArgumentException.class, failure.getCause());
            }
            encounter.begin();
            // Isolate the lifetime boundary with an actual service-injected mod
            // object in the danger band, while Sonic stays at the protected post.
            var shot = encounter.stationarySpore(0x1d90, 0x1ac);
            for (int age = 0; age < encounter.constant("PROJECTILE_LIFE_TICKS"); age++)
                shot.update(0, encounter.fixture.sprite());
            assertFalse(shot.isDestroyed());
            shot.update(0, encounter.fixture.sprite());
            assertTrue(shot.isDestroyed());
            assertEquals(0, ((com.openggf.level.objects.TouchResponseProvider) shot).getCollisionFlags());
        }
    }

    @Test void nativeAllocationPressureAbortsWithoutPublishingHalfAVolley() throws Exception {
        try (var encounter = open()) {
            encounter.begin();
            encounter.until(() -> !encounter.value("phase").toString().equals("WAITING"), 100, true, false);
            encounter.until(() -> (encounter.fixture.sprite().getCentreX() & 0xffff) <= encounter.constant("POST_X"), 60, false, true);
            encounter.frames(12, false, false);
            encounter.until(() -> encounter.value("phase").toString().equals("LOCKED"), 100, false, false);
            var objects = GameServices.level().getObjectManager();
            objects.reserveAllButNFreeSlots(1);
            encounter.frames(14, false, false);
            assertEquals("ABORTED", encounter.value("status").toString());
            assertTrue(encounter.spores().isEmpty(), "failed pair allocation must leave no harmful partial volley");
        }
    }

    private static void sameEncounterWorld(CompositeSnapshot expected, CompositeSnapshot actual, String where) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), where);
        assertNotNull(expected.get("object-manager"), "object graph is a required replay oracle");
        for (String key : expected.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), key + " " + where + ": " + differences.stream().limit(8).toList());
        }
    }

    private Encounter open() throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        var scanned = new DefaultModRepositoryScanner().scan(jar.getParent().toAbsolutePath());
        var validated = new ModCatalogValidator(jar.getParent().toAbsolutePath(), ModInputLimits.production(),
                (game, id) -> true).validate(scanned);
        var descriptor = (ModDescriptor) validated.entries().getFirst();
        assertFalse(descriptor.hasErrors(), descriptor.findings()::toString);
        var catalog = new EffectiveCatalogBuilder().build(validated.entries(), new ModState(1,
                List.of(new ModState.Entry(OWNER, true, 0, true, descriptor.sha256()))));
        var runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(catalog.effective(), Set.of(OWNER));
        runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), owners -> { }));
        var plan = runtime.newRegistrationPlan();
        assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures()::toString);
        GameModule root = GameServices.module();
        // Closing the previous test mode never mutates the ROM or selected stock providers.
        if (root instanceof com.openggf.game.patch.DelegatingGameModule)
            root = new com.openggf.game.sonic3k.Sonic3kGameModule();
        root.createGame(TestEnvironment.currentRom());
        var resolver = new ModuleResolutionService(List.of(), new EffectiveCatalogPatchEnablement(catalog.effective()),
                new LogicalRomResolver(() -> null), config, ignored -> plan);
        GameModule resolved = resolver.resolveForLaunch(root, new GameplayLaunchRequest("s3k", "sonic", List.of()),
                ModuleResolutionService.LaunchPolicy.STANDARD);
        var mode = SessionManager.openGameplaySession(root, resolved, null);
        GameplaySessionFactory.attachManagers(mode, EngineServices.current());
        GameModuleRegistry.setCurrent(resolved);
        Class<?> stateType = runtime.loadOwned(OWNER, "hardened.EncounterState");
        Object state = resolved.getGameService(stateType);
        assertNotNull(state, "trusted fixture patch must expose the actual example's session state");
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(7, 0)
                .startPosition((short) 0x1d30, (short) 0x1a8).startPositionIsCentre()
                .withFreshLevelStartLifecycle().build();
        var encounter = new Encounter(runtime, fixture, state, runtime.loadOwned(OWNER, "hardened.EncounterPlan"));
        var manager = GameServices.level().getObjectManager();
        manager.setRewindClassResolver(new ModClassResolver(runtime, getClass().getClassLoader()));
        assertNotNull(manager.createDynamicObject(() -> resolved.createObjectRegistry().create(
                new ObjectSpawn(encounter.constant("SENTRY_X"), encounter.constant("SENTRY_Y"), 0, 0, 0, false,
                        encounter.constant("SENTRY_Y"), -1, OWNER, OWNER + ":spore-sentry"))));
        fixture.stepIdleFrames(1);
        return encounter;
    }

    private static final class Encounter implements AutoCloseable {
        final ModRuntime runtime;
        final HeadlessTestFixture fixture;
        final Object state;
        final Class<?> plan;
        Encounter(ModRuntime runtime, HeadlessTestFixture fixture, Object state, Class<?> plan) {
            this.runtime = runtime; this.fixture = fixture; this.state = state; this.plan = plan;
        }
        int constant(String key) throws Exception { return plan.getField(key).getInt(null); }
        Object read(Object object, String name) throws Exception { return object.getClass().getMethod(name).invoke(object); }
        Object value(String name) throws Exception { return read(state, name); }
        void call(String name, Object... arguments) throws Exception {
            Method method = java.util.Arrays.stream(state.getClass().getMethods())
                    .filter(m -> m.getName().equals(name) && m.getParameterCount() == arguments.length).findFirst().orElseThrow();
            try { method.invoke(state, arguments); }
            catch (InvocationTargetException failed) { throw new AssertionError("Encounter callback " + name, failed.getCause()); }
        }
        void begin() throws Exception { call("begin"); }
        void frames(int frames, boolean right, boolean left) throws Exception {
            for (int n = 0; n < frames; n++) {
                fixture.stepFrame(false, false, left, right, false);
                call("afterGameplayTick", fixture.sprite(), GameServices.level().getCheckpointState().isActive());
            }
        }
        @FunctionalInterface interface Condition { boolean reached() throws Exception; }
        void until(Condition condition, int limit, boolean right, boolean left) throws Exception {
            for (int n = 0; n < limit && !condition.reached(); n++) frames(1, right, left);
            assertTrue(condition.reached(), "Input route did not reach its milestone: " + position());
        }
        String position() { return "player=" + fixture.sprite().getCentreX() + "," + fixture.sprite().getCentreY(); }
        List<ObjectInstance> spores() {
            return GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getName().equals("hardened.Spore") && !o.isDestroyed()).toList();
        }
        AbstractObjectInstance sentry() {
            return (AbstractObjectInstance) GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(o -> o.getClass().getName().equals("hardened.Sentry") && !o.isDestroyed()).findFirst().orElseThrow();
        }
        AbstractObjectInstance stationarySporeAtPlayer() throws Exception {
            // Declared collision setup inside the danger band; the protected post
            // side intentionally cannot host harmful spores.
            com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(fixture.sprite(), constant("TRIGGER_X"));
            com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(fixture.sprite(), 0x1ab);
            fixture.sprite().setXSpeed((short)0); fixture.sprite().setYSpeed((short)0); fixture.sprite().setGSpeed((short)0);
            return stationarySpore(fixture.sprite().getCentreX() & 0xffff, fixture.sprite().getCentreY() & 0xffff);
        }
        AbstractObjectInstance stationarySpore(int x, int y) throws Exception {
            Class<?> type = runtime.loadOwned(OWNER, "hardened.Spore");
            var spawn = new ObjectSpawn(x, y, 0, 0, 0, false, y, -1, OWNER, OWNER + ":spore-shot");
            return GameServices.level().getObjectManager().createDynamicObject(() -> {
                try { return (AbstractObjectInstance) type.getConstructor(ObjectSpawn.class).newInstance(spawn); }
                catch (ReflectiveOperationException failed) { throw new AssertionError(failed); }
            });
        }
        @Override public void close() throws Exception { SessionManager.clear(); runtime.close(); }
    }
}
