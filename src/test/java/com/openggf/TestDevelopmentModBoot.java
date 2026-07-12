package com.openggf;

import com.openggf.io.ModInputLimits;
import com.openggf.mods.DevelopmentModSource;
import com.openggf.tools.modsdk.ProjectScaffolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TestDevelopmentModBoot {
    @TempDir Path temp;

    @Test void freshDevDescriptorIsSoleEffectiveEnabledTrustedOwnerWithoutPersistedState() throws Exception {
        Path project=temp.resolve("fresh-project");new ProjectScaffolder().scaffold(project,"fresh-dev","example.fresh");
        Path build=temp.resolve("classes");Files.createDirectories(build);
        java.util.List<String> sources;try(var paths=Files.walk(project.resolve("src/main/java"))){
            sources=paths.filter(p->p.toString().endsWith(".java")).map(Path::toString).toList();}
        var args=new java.util.ArrayList<>(java.util.List.of("--release","21","-cp",
                Path.of("target/classes").toAbsolutePath().toString(),"-d",build.toString()));args.addAll(sources);
        assertEquals(0,javax.tools.ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        Path manifest=build.resolve("META-INF/openggf-mod.yaml");Files.createDirectories(manifest.getParent());
        Files.copy(project.resolve("src/main/resources/META-INF/openggf-mod.yaml"),manifest);
        Path normalRoot=temp.resolve("mods").toAbsolutePath().normalize();Files.createDirectories(normalRoot);
        String previous=System.getProperty(DevelopmentModSource.PROPERTY);
        System.setProperty(DevelopmentModSource.PROPERTY,build.toString());
        ModSubsystem subsystem=null;
        try {
            subsystem=ModSubsystem.normalBootLoader(()->normalRoot,ModInputLimits.production(),
                    (game,id)->true,new Boundary()).get();
            var effective=subsystem.processCatalog().effective().orderedEnabled();
            assertEquals(1,effective.size());assertEquals("fresh-dev",effective.getFirst().manifest().id());
            assertEquals(java.util.Set.of("fresh-dev"),subsystem.trustedCodeOwners());
            assertFalse(Files.exists(normalRoot.resolve("modstate.json")));
        } finally {
            if(subsystem!=null)subsystem.close();
            if(previous==null)System.clearProperty(DevelopmentModSource.PROPERTY);
            else System.setProperty(DevelopmentModSource.PROPERTY,previous);
        }
    }

    @Test void blankDevPropertyDoesNotExemptTestModeOrInvokeBootScanner() {
        String previous=System.getProperty(DevelopmentModSource.PROPERTY);
        try{
            System.setProperty(DevelopmentModSource.PROPERTY," \t ");
            assertEquals(ExternalContentMode.STARTUP_DETERMINISTIC,Engine.externalContentBootMode(true));
            java.util.concurrent.atomic.AtomicBoolean invoked=new java.util.concurrent.atomic.AtomicBoolean();
            ModSubsystem.installAtBoot(new ExternalContentPolicy(Engine.externalContentBootMode(true)),()->{
                invoked.set(true);throw new AssertionError("scanner invoked");});
            assertFalse(invoked.get());
        }finally{ModSubsystem.clearProcess();if(previous==null)System.clearProperty(DevelopmentModSource.PROPERTY);
            else System.setProperty(DevelopmentModSource.PROPERTY,previous);}
    }

    private static final class Boundary implements ModSubsystem.SessionAudioBoundary {
        public void install(com.openggf.audio.StreamedMusicPort port){}
        public void clear(){}
    }
}
