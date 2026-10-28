package com.openggf.mods.code;

import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Checkout adapter; authored tests remain in the independent mod's own test tree. */
@RequiresRom(SonicGame.SONIC_2)
class TestSonicEchoesFoundation {
    @TempDir static Path temp;
    static URLClassLoader loader;
    static Class<?> tests;
    @BeforeAll static void compileMod() throws Exception {
        Path project=Path.of("examples/sonic-echoes"), classes=Files.createDirectory(temp.resolve("classes"));
        compile(project.resolve("src/main/java"),classes,System.getProperty("java.class.path"));
        try(var walk=Files.walk(project.resolve("src/main/resources"))) {
            for(var source:walk.filter(Files::isRegularFile).toList()) {
                var dest=classes.resolve(project.resolve("src/main/resources").relativize(source));
                Files.createDirectories(dest.getParent()); Files.copy(source,dest);
            }
        }
        Path jar=temp.resolve("sonic-echoes.jar");
        assertEquals(0,GgfModCli.run(new String[]{"package","--input",classes.toString(),"--out",jar.toString()},System.out));
        Path testClasses=Files.createDirectory(temp.resolve("tests"));
        compile(project.resolve("src/test/java"),testClasses,classes+java.io.File.pathSeparator+System.getProperty("java.class.path"));
        loader=new ModDependencyClassLoader("sonic-echoes", new URL[]{jar.toUri().toURL(),testClasses.toUri().toURL()},TestSonicEchoesFoundation.class.getClassLoader(), List.of());
        tests=loader.loadClass("echoes.FoundationTest");
        java.util.function.BiFunction<GgfMod,Path,com.openggf.game.GameModule> factory=(mod,artifact)-> {
            try(var assets=com.openggf.io.ModAssetRoot.jar(artifact.getParent(),artifact,com.openggf.io.ModInputLimits.production())) {
                var context=new ModContext("sonic-echoes","s2",assets,null,false,temp.resolve("save-"+UUID.randomUUID()));
                mod.register(context); var plan=context.freeze();
                var boundary=new ModFaultBoundary(Map.of(),new com.openggf.mods.ModRuntimeFindingStore(),
                        owners->new com.openggf.mods.ModStateSaveResult.Saved(),owners->{});
                var effective=new ModBackedGamePatch(plan,boundary).apply(com.openggf.game.GameServices.module(),null);
                for(var patch:plan.explicitPatches()) effective=patch.apply(effective,null);
                return effective;
            } catch(Exception e) { throw new IllegalStateException(e); }
        };
        tests.getField("moduleFactory").set(null,factory);
    }
    static void compile(Path sources,Path output,String cp) throws Exception {
        var args=new ArrayList<>(List.of("--release","25","-cp",cp,"-d",output.toString()));
        try(var walk=Files.walk(sources)) { walk.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->args.add(p.toString())); }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
    }
    static void run(String method) throws Exception {
        try { tests.getMethod(method).invoke(tests.getConstructor().newInstance()); }
        catch(java.lang.reflect.InvocationTargetException e) {
            if(e.getCause() instanceof Exception x) throw x;
            if(e.getCause() instanceof Error x) throw x;
            throw e;
        }
    }
    @AfterAll static void closeLoader() throws Exception { if(loader!=null) loader.close(); }
    @Test void installedModLoadsEveryRoomThroughProductionRuntime() throws Exception {
        var descriptor = new com.openggf.mods.DefaultModRepositoryScanner().scan(temp.toAbsolutePath()).stream()
                .filter(entry -> entry instanceof com.openggf.mods.ModDescriptor)
                .map(entry -> (com.openggf.mods.ModDescriptor) entry).findFirst().orElseThrow();
        var base = com.openggf.tests.SharedLevel.load(SonicGame.SONIC_2, 0, 0);
        try (var runtime = new ModClassLoaderFactory(getClass().getClassLoader()).create(
                     new com.openggf.mods.EffectiveModCatalog(List.of(descriptor)), Set.of("sonic-echoes"))) {
            runtime.installStorageRoot(temp.resolve("installed-save"));
            runtime.installFaultBoundary(new ModFaultBoundary(Map.of(), new com.openggf.mods.ModRuntimeFindingStore(),
                    owners -> new com.openggf.mods.ModStateSaveResult.Saved(), runtime::disableOwnersForProcess));
            assertTrue(runtime.rejectedOwners().isEmpty(), runtime.rejectedOwners().toString());
            var plan = runtime.newRegistrationPlan();
            assertTrue(runtime.registrationFailures().isEmpty(), runtime.registrationFailures().toString());
            assertEquals(2, plan.registrations().size());
            var effective = com.openggf.game.GameServices.module();
            for (var registration : plan.registrations()) effective = registration.patch().apply(effective, null);
            var sessionType = runtime.loadOwned("sonic-echoes", "echoes.RoomSession");
            // Production deliberately hides classpath resources. This used to throw Unknown room: 1-1.
            assertNull(sessionType.getResource("/rooms/1-1.properties"));
            var session = effective.getGameService(sessionType);
            assertNotNull(session);
            assertNotNull(effective.getTitleScreenProvider());
            assertTrue(effective.suppressesLevelSelect());
            var launcherType = runtime.loadOwned("sonic-echoes", "echoes.RoomLauncher");
            var launcher = effective.getGameService(launcherType);
            for (int world = 1; world <= 3; world++) for (int card = 1; card <= 4; card++) {
                String id = world + "-" + card;
                var room = launcherType.getMethod("launch", String.class).invoke(launcher, id);
                assertEquals(id, room.getClass().getMethod("id").invoke(room));
                assertFalse(((String) room.getClass().getMethod("hint").invoke(room)).isBlank(), id);
                assertFalse(((List<?>) room.getClass().getMethod("solution").invoke(room)).isEmpty(), id);
            }
        } finally { base.dispose(); }
    }
    @Test void zeroDriftAcrossThreeLoops() throws Exception { run("zeroDriftAcrossThreeLoops"); }
    @Test void tutorialReferenceSolution() throws Exception { run("tutorialReferenceSolution"); }
    @Test void disturbedEchoFractures() throws Exception { run("disturbedEchoFractures"); }
    @Test void historySurvivesWorldRestore() throws Exception { run("historySurvivesWorldRestore"); }
    @Test void longSessionKeepsPerFrameWrappersStable() throws Exception { run("longSessionKeepsPerFrameWrappersStable"); }
}
