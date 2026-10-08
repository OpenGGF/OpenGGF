package com.openggf.mods.code;

import com.openggf.io.*;
import com.openggf.mods.*;
import com.openggf.mods.mutators.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Maintained source builds against this candidate and passes the actual package boundary. */
class TestExampleMutatorsPackage {
    @TempDir Path temp;
    @Test void sourceBuildsPackagesAndRegistersRomOnlyBoundedPolicies() throws Exception {
        var project=Path.of("examples/example-mutators");var classes=temp.resolve("classes");Files.createDirectories(classes);
        var args=new ArrayList<String>(List.of("--release","21","-classpath",System.getProperty("java.class.path"),"-d",classes.toString()));
        try(var sources=Files.walk(project.resolve("src/main/java"))) { sources.filter(p->p.toString().endsWith(".java")).sorted().map(Path::toString).forEach(args::add); }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        var manifest=classes.resolve("META-INF/openggf-mod.yaml");Files.createDirectories(manifest.getParent());
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"),manifest);
        assertEquals(0,GgfModCli.run(new String[]{"package","--input",classes.toString(),"--out",temp.resolve("example-mutators.jar").toString()},System.out));
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},getClass().getClassLoader());
            var assets=ModAssetRoot.snapshotDirectory(classes,classes,ModInputLimits.production(),DirectoryAccess.TEST)) {
            var mod=Class.forName("mutators.MutatorsMod",true,loader).asSubclass(GgfMod.class).getConstructor().newInstance();
            var context=new ModContext("example-mutators","any",assets);mod.register(context);var plan=context.freeze();
            assertEquals(Set.of("example-mutators:gravity","example-mutators:ringfall","example-mutators:big-head",
                    "example-mutators:stealth","example-mutators:no-powerups","example-mutators:no-checkpoints",
                    "example-mutators:no-rings","example-mutators:game-speed","example-mutators:no-special-stages",
                    "example-mutators:no-bonus-stages","example-mutators:violent-explosions"),plan.mutators().keySet());
            assertEquals(3,plan.explicitPatches().size());assertTrue(plan.objectFactories().isEmpty());
            assertEquals(List.of("s1","s2","s3k"),plan.stockScenePlans().stream().map(ModRegistrationPlan::baseGameId).toList());
            for(var game:plan.stockScenePlans()) {
                assertEquals(1,game.explicitPatches().size());
                assertEquals(game.baseGameId(),game.explicitPatches().getFirst().baseGameId());
                assertEquals(plan.mutators(),game.mutators());
                com.openggf.game.GameModule nativeModule=switch(game.baseGameId()) {
                    case "s1" -> new com.openggf.game.sonic1.Sonic1GameModule();
                    case "s2" -> new com.openggf.game.sonic2.Sonic2GameModule();
                    default -> new com.openggf.game.sonic3k.Sonic3kGameModule();
                };
                var decorated=game.explicitPatches().getFirst().apply(nativeModule,null);
                assertEquals(nativeModule.getGameId(),decorated.getGameId());
                assertNull(decorated.getDataSelectProvider(),"Lab Start cannot route into a stock save-slot selection");
                assertEquals(nativeModule.supportsSidekick(),decorated.supportsSidekick(),"Native team support stays with the base module");
            }
            for (String game : List.of("s1","s2","s3k")) {
                var profile=(MutatorSupportProfile)Class.forName("mutators.NativeLabProfile",true,loader)
                        .getConstructor(String.class).newInstance(game);
                assertTrue(profile.startLabel().startsWith("Start "));
                assertTrue(profile.locationLabel(0,0).contains(switch(game) {
                    case "s1" -> "Green Hill"; case "s2" -> "Emerald Hill"; default -> "Angel Island";
                }));
                assertFalse(profile.locationLabel(1,0).contains(switch(game) {
                    case "s1" -> "Green Hill"; case "s2" -> "Emerald Hill"; default -> "Angel Island";
                }),"Later acts must not keep the opening-zone caption");
                assertTrue(profile.supportsPlayer(MutatorCapability.PLAYER_STEALTH,"tails",false));
                assertFalse(profile.supportsPlayer(MutatorCapability.BIG_HEAD,"tails",false),"Unreviewed art retains its native pose");
                assertTrue(profile.optionUnavailableReason("no-powerups","rings").isBlank());
                assertEquals(game.equals("s3k"),profile.optionUnavailableReason("no-powerups","bubble_shield").isBlank());
            }
            for(var owned:plan.mutators().values()) {
                assertEquals("example-mutators",owned.ownerModId());
                var scope=Set.of("no-powerups","no-checkpoints","no-rings").contains(owned.definition().localId())
                        ? MutatorScope.LOAD : MutatorScope.LIVE;
                assertEquals(scope,owned.definition().enableScope());assertEquals(scope,owned.definition().disableScope());
                assertTrue(owned.definition().options().stream().allMatch(o->o.editScope()==scope));
                assertEquals(owned.definition().capabilities(),owned.definition().factory()
                        .prepare(new MutatorOptions(owned.definition().defaults())).stream()
                        .map(MutatorPolicy::capability).collect(java.util.stream.Collectors.toSet()));
            }
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
        }
        try(var jar=new java.util.zip.ZipFile(temp.resolve("example-mutators.jar").toFile())) {
            assertTrue(jar.stream().noneMatch(e->e.getName().endsWith(".gen")||e.getName().endsWith(".png")||e.getName().endsWith(".wav")),"gameplay assets come only from supplied ROMs");
        }
    }
}
