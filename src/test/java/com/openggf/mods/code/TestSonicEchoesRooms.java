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
class TestSonicEchoesRooms {
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
        loader=new ModDependencyClassLoader("sonic-echoes", new URL[]{jar.toUri().toURL(),testClasses.toUri().toURL()},TestSonicEchoesRooms.class.getClassLoader(), List.of());
        tests=loader.loadClass("echoes.RoomSolutionsTest");
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
    @TestFactory java.util.stream.Stream<DynamicTest> rooms() {
        String selected=System.getProperty("echoes.room", "all");
        var ids=new ArrayList<String>();
        for(int w=1;w<=3;w++) for(int r=1;r<=4;r++) ids.add(w+"-"+r);
        if(!selected.equals("all")) ids=new ArrayList<>(List.of(selected));
        return ids.stream().map(id->DynamicTest.dynamicTest(id,()-> {
            try { tests.getMethod("verify",String.class).invoke(tests.getConstructor().newInstance(),id); }
            catch(java.lang.reflect.InvocationTargetException e) {
                if(e.getCause() instanceof Exception x) throw x;
                if(e.getCause() instanceof Error x) throw x;
                throw e;
            }
        }));
    }
}
