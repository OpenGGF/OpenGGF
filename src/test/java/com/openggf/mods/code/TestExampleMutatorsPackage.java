package com.openggf.mods.code;

import com.openggf.io.*;
import com.openggf.mods.*;
import com.openggf.mods.mutators.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Isolated;
import com.openggf.game.*;
import com.openggf.game.session.*;
import com.openggf.game.patch.*;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Maintained source builds against this candidate and passes the actual package boundary. */
@Isolated
class TestExampleMutatorsPackage {
    @TempDir Path temp;
    private String oldRoot;
    @BeforeEach void setup() {
        TestEnvironment.resetAll();
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        oldRoot = System.getProperty("openggf.saveRoot");
        System.setProperty("openggf.saveRoot", temp.resolve("saves").toString());
        GameServices.configuration().setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        GameServices.configuration().setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "none");
    }
    @AfterEach void cleanup() {
        TestEnvironment.resetAll();
        if (oldRoot == null) System.clearProperty("openggf.saveRoot");
        else System.setProperty("openggf.saveRoot", oldRoot);
    }
    private void assertNativeHosts(Path jar, Set<String> keys) throws Exception {
        ModManifest manifest;
        try (var zip = new java.util.zip.ZipFile(jar.toFile());
             var input = zip.getInputStream(zip.getEntry(DefaultModRepositoryScanner.MANIFEST_PATH))) {
            manifest = new ModManifestParser().parse(input.readAllBytes());
        }
        assertEquals("mutators.MutatorsMod", manifest.entrypoint());
        String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(jar)));
        var descriptor = new ModDescriptor(jar, manifest, hash, true, List.of());
        var catalog = new EffectiveModCatalog(List.of(descriptor));
        try (var runtime = new ModClassLoaderFactory(getClass().getClassLoader())
                .create(catalog, Set.of(manifest.id()));
             var capture = DevelopmentPatchLoader.openJar(jar)) {
            runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                    disabled -> new ModStateSaveResult.Saved(), disabled -> {}));
            var config = com.openggf.configuration.SonicConfigurationService.createStandalone();
            config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            GameServices.configuration().setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            var resolver = new ModuleResolutionService(List.of(), new EffectiveCatalogPatchEnablement(catalog),
                    LogicalRomResolver.fromRomManager(GameServices.rom()), config,
                    ignored -> runtime.newRegistrationPlan());
            for (GameModule base : List.of(new com.openggf.game.sonic1.Sonic1GameModule(),
                    new com.openggf.game.sonic2.Sonic2GameModule(),
                    new com.openggf.game.sonic3k.Sonic3kGameModule())) {
                String game = base.getGameId().code();
                var launch = resolver.prepareLaunch(ModuleResolutionService.LaunchPolicy.STANDARD);
                var request = new GameplayLaunchRequest(game, "sonic", List.of());
                var result = assertInstanceOf(ResolutionResult.Resolved.class,
                        resolver.resolveForLaunchResult(launch, base, request));
                assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures()::toString);
                var module = result.module();
                assertNull(module.getDataSelectProvider(), "Lab Start bypasses stock save slots");
                assertEquals(base.supportsSidekick(), module.supportsSidekick());
                var support = module.getGameService(MutatorSupportProfile.class);
                assertNotNull(support);
                assertFalse(support.capabilities(0, 0).isEmpty());
                config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, true);
                assertTrue(support.capabilities(0, 0).isEmpty());
                config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
                assertTrue(support.startLabel().startsWith("Start "));
                assertTrue(support.supportsPlayer(MutatorCapability.PLAYER_STEALTH, "tails", false));
                assertEquals(!game.equals("s1"), support.supportsPlayer(MutatorCapability.BIG_HEAD, "tails", false));
                assertEquals(game.equals("s3k"), support.optionUnavailableReason("no-powerups", "bubble_shield").isBlank());
                assertScreen(base, module, keys);
                assertScreen(base, capture.apply(base, request), keys);
                var tails = capture.apply(base, new GameplayLaunchRequest(game, "tails", List.of()));
                if (game.equals("s1")) assertNull(tails.getGameService(MutatorSupportProfile.class));
                else assertScreen(base, tails, keys);
            }
        }
    }

    private void assertScreen(GameModule base, GameModule module, Set<String> keys) throws Exception {
        GameModuleRegistry.setCurrent(module);
        SessionManager.openGameplaySession(base, module, null);
        TestEnvironment.activeGameplayMode();
        try {
            var world = SessionManager.getCurrentWorldSession();
            var title = assertInstanceOf(MutatorConfigurationScreen.class, unwrapOwned(module.getTitleScreenProvider()));
            assertSame(title, module.getGameService(LevelInputOverlay.class));
            assertEquals(keys, MutatorWorldAccess.state(world).definitions().stream()
                    .map(OwnedMutator::key).collect(java.util.stream.Collectors.toSet()));
            var backdrop = MutatorConfigurationScreen.class.getDeclaredField("backdrop");
            backdrop.setAccessible(true);
            assertSame(base.getTitleScreenProvider(), unwrapOwned(backdrop.get(title)),
                    "Exactly one screen wraps the native title");
        } finally {
            SessionManager.clear();
        }
    }

    private static Object unwrapOwned(Object value) throws Exception {
        if (!java.lang.reflect.Proxy.isProxyClass(value.getClass())) return value;
        var handler=java.lang.reflect.Proxy.getInvocationHandler(value);
        var trusted = Class.forName(
                com.openggf.game.internal.NativeProviderDispatch.class.getName() + "$PublishedHandler",
                false, com.openggf.game.internal.NativeProviderDispatch.class.getClassLoader());
        assertSame(trusted, handler.getClass(), "Expected an authenticated engine provider envelope");
        var source = trusted.getDeclaredField("source");
        source.setAccessible(true);
        return unwrapOwned(source.get(handler));
    }
    @Test void sourceBuildsPackagesAndRegistersRomOnlyBoundedPolicies() throws Exception {
        var project=Path.of("examples/example-mutators");var classes=temp.resolve("classes");Files.createDirectories(classes);
        var args=new ArrayList<String>(List.of("--release","25","-classpath",System.getProperty("java.class.path"),"-d",classes.toString()));
        try(var sources=Files.walk(project.resolve("src/main/java"))) { sources.filter(p->p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(args::add); }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        var manifest=classes.resolve("META-INF/openggf-mod.yaml");Files.createDirectories(manifest.getParent());
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"),manifest);
        assertEquals(0,GgfModCli.run(new String[]{"package","--input",classes.toString(),"--out",temp.resolve("example-mutators.jar").toString(),"--warnings","error"},System.out));
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},getClass().getClassLoader());
            var assets=ModAssetRoot.snapshotDirectory(classes,classes,ModInputLimits.production(),DirectoryAccess.TEST)) {
            var mod=Class.forName("mutators.MutatorsMod",true,loader).asSubclass(GgfMod.class).getConstructor().newInstance();
            var context=new ModContext("example-mutators","any",assets);mod.register(context);var plan=context.freeze();
            assertEquals(Set.of("example-mutators:gravity","example-mutators:ringfall","example-mutators:big-head",
                    "example-mutators:stealth","example-mutators:no-powerups","example-mutators:no-checkpoints",
                    "example-mutators:no-rings","example-mutators:game-speed","example-mutators:no-special-stages",
                    "example-mutators:no-bonus-stages","example-mutators:violent-explosions",
                    "example-mutators:homing-attack","example-mutators:beta-flight","example-mutators:start-rings","example-mutators:start-time",
                    "example-mutators:start-score","example-mutators:start-lives",
                    "example-mutators:hungry-for-rings"),plan.mutators().keySet());
            assertTrue(plan.explicitPatches().isEmpty());
            assertTrue(plan.objectFactories().isEmpty());
            assertEquals(List.of("s1","s2","s3k","scd"),
                    plan.stockScenePlans().stream().map(ModRegistrationPlan::baseGameId).toList());
            assertNativeHosts(temp.resolve("example-mutators.jar"), plan.mutators().keySet());
            for(var owned:plan.mutators().values()) {
                assertEquals("example-mutators",owned.ownerModId());
                String local=owned.definition().localId();
                var scope=Set.of("no-powerups","no-checkpoints","no-rings").contains(local) ? MutatorScope.LOAD
                        : Set.of("start-score","start-lives").contains(local) ? MutatorScope.LAUNCH : MutatorScope.LIVE;
                assertEquals(scope,owned.definition().enableScope());assertEquals(scope,owned.definition().disableScope());
                assertTrue(owned.definition().options().stream().allMatch(o->o.editScope()==scope));
                assertEquals(owned.definition().capabilities(),owned.definition().factory()
                        .prepare(new MutatorOptions(owned.definition().defaults())).stream()
                        .map(MutatorPolicy::capability).collect(java.util.stream.Collectors.toSet()));
            }
            var startRings=plan.mutators().get("example-mutators:start-rings").definition();
            assertEquals(List.of(new StartingStatePolicy.Rings(50,0)),startRings.factory().prepare(new MutatorOptions(startRings.defaults())));
            var startTime=plan.mutators().get("example-mutators:start-time").definition();
            assertEquals(List.of(new StartingStatePolicy.Time(300)),startTime.factory().prepare(new MutatorOptions(startTime.defaults())));
            var hunger=plan.mutators().get("example-mutators:hungry-for-rings").definition();
            assertEquals(List.of(new RingHungerPolicy(30,1,1,0,5)),hunger.factory().prepare(new MutatorOptions(hunger.defaults())));
            var gravity=plan.mutators().get("example-mutators:gravity").definition();
            assertEquals(List.of(new MutatorPolicy.DrySonicGravity(100)),gravity.factory().prepare(new MutatorOptions(gravity.defaults())));
            assertEquals(1,gravity.schemaVersion(),"Existing saved Gravity options remain valid");
            var stealth=plan.mutators().get("example-mutators:stealth").definition();
            assertEquals(1,stealth.schemaVersion());assertEquals(Set.of("target","effects"),stealth.defaults().keySet());
            var source=new MutatorPreferences("s2",Map.of("example-mutators:gravity",
                    new MutatorPreferences.Entry(1,true,Map.of("percent",50)),"example-mutators:stealth",
                    new MutatorPreferences.Entry(1,true,Map.of("target","leader","effects",false))));
            var retained=source.forSession(List.copyOf(plan.mutators().values()));
            assertEquals(50,retained.get("example-mutators:gravity").options().get("percent"));
            assertTrue(retained.get("example-mutators:stealth").enabled());
            var head=plan.mutators().get("example-mutators:big-head").definition();
            assertEquals(1,head.schemaVersion());assertEquals(false,head.defaults().get("rings"),"ring scaling defaults off");
            var savedHead=new MutatorPreferences("s2",Map.of("example-mutators:big-head",
                    new MutatorPreferences.Entry(1,true,Map.of("percent",180,"target","leader"))))
                    .forSession(List.copyOf(plan.mutators().values())).get("example-mutators:big-head");
            assertEquals(List.of(new MutatorPolicy.BigHead(180,MutatorPolicy.Target.LEADER)),
                    head.factory().prepare(new MutatorOptions(savedHead.options())),"a saved fixed head stays fixed");
        }
        try(var jar=new java.util.zip.ZipFile(temp.resolve("example-mutators.jar").toFile())) {
            assertTrue(jar.stream().noneMatch(e->e.getName().endsWith(".gen")||e.getName().endsWith(".png")||e.getName().endsWith(".wav")),"gameplay assets come only from supplied ROMs");
        }
    }
}
