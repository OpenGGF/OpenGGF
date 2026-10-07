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
            var context=new ModContext("example-mutators","s2",assets);mod.register(context);var plan=context.freeze();
            assertEquals(Set.of("example-mutators:gravity","example-mutators:stealth"),plan.mutators().keySet());
            assertEquals(1,plan.explicitPatches().size());assertTrue(plan.objectFactories().isEmpty());
            for(var owned:plan.mutators().values()) {
                assertEquals("example-mutators",owned.ownerModId());assertEquals(MutatorScope.LIVE,owned.definition().enableScope());
                assertEquals(MutatorScope.LIVE,owned.definition().disableScope());
                assertTrue(owned.definition().options().stream().allMatch(o->o.editScope()==MutatorScope.LIVE));
            }
            var gravity=plan.mutators().get("example-mutators:gravity").definition();
            assertEquals(List.of(new MutatorPolicy.DrySonicGravity(100)),gravity.factory().prepare(new MutatorOptions(gravity.defaults())));
        }
        try(var jar=new java.util.zip.ZipFile(temp.resolve("example-mutators.jar").toFile())) {
            assertTrue(jar.stream().noneMatch(e->e.getName().endsWith(".gen")||e.getName().endsWith(".png")||e.getName().endsWith(".wav")),"gameplay assets come only from supplied ROMs");
        }
    }
}
