package com.openggf.level;

import com.openggf.level.objects.ObjectSpawn;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TestLevelPatch {
    @TempDir java.nio.file.Path temp;
    public interface Binder {
        LevelPatch bind(String owner,LevelPatch patch,java.util.Set<String> keys);
    }
    public static final class CreatorProbe {
        public static void claim(LevelPatch patch) { LevelPatchOwnership.bind("other",patch,java.util.Set.of("other:spring")); }
        public static Binder factory() { return LevelPatchOwnership::bind; }
    }
    private ObjectSpawn spawn(int slot, String owner) {
        return new ObjectSpawn(100,200,0x41,3,2,true,0xA0C8,slot,owner,owner==null ? null : owner+":spring");
    }
    @Test void decodedTransformEditsTheSingleCapturedPlacementTable() {
        var original=spawn(1,null);
        class ChangingLevel extends AbstractLevel {
            int reads;
            ChangingLevel() {
                super(0); map=new Map(2,1,1); rings=List.of();
                palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
            }
            @Override public List<ObjectSpawn> getObjects() {
                return ++reads==1 ? List.of(original) : List.of(spawn(2,"other"));
            }
        }
        var source=new ChangingLevel();
        var patched=LevelPatch.empty().select(s->true).move(20,0).apply(source);
        assertEquals(1,source.reads,"A decoded placement table is captured once before callbacks run");
        assertEquals(List.of(original.withPosition(120,200)),patched.getObjects());
    }
    @Test void transformationsPreserveOrderOwnerSlotAndFlagsAndRunOnce() {
        var owned=spawn(1,"another-mod"); var nativeSpawn=spawn(2,null); var removed=spawn(3,null);
        AtomicInteger callbacks=new AtomicInteger();
        var patch=LevelPatch.empty().select(s->s.layoutIndex()==3).remove()
                .select(s->s.layoutIndex()==1).replace(s->{callbacks.incrementAndGet(); return s.withPosition(120,220);})
                .select(s->s.layoutIndex()==2).bind("sample","sample:spring");
        var result=patch.applyToObjects(List.of(owned,nativeSpawn,removed));
        assertEquals(1,callbacks.get());
        assertEquals(List.of(1,2),result.stream().map(ObjectSpawn::layoutIndex).toList());
        assertEquals("another-mod:spring",result.get(0).objectKey());
        assertEquals(0xA0DC,result.get(0).rawYWord());
        assertEquals(owned.renderFlags(),result.get(0).renderFlags());
        assertTrue(result.get(0).respawnTracked());
        assertEquals("sample:spring",result.get(1).objectKey());
        assertEquals(100,owned.x(),"Source record remains immutable");
    }
    @Test void rejectsIdentityTheftDuplicateSlotsAndCoordinateTruncation() {
        var nativeSpawn=spawn(1,null); var owned=spawn(2,"other");
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().applyToObjects(List.of(nativeSpawn,nativeSpawn)));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().select(s->true).replace(s->spawn(8,null)).applyToObjects(List.of(nativeSpawn)));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().select(s->true).replace(s->spawn(1,"thief")).applyToObjects(List.of(nativeSpawn)));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().select(s->true).bind("thief","thief:spring").applyToObjects(List.of(owned)));
        assertThrows(IllegalArgumentException.class,()->LevelPatch.empty().select(s->true).move(-101,0).applyToObjects(List.of(nativeSpawn)));
    }

    @Test void localBindingsNeedVerifiedAdmissionAndRegisteredFactoryWithoutChangingOtherOwners() {
        var local=LevelPatch.empty().select(s->s.ownerModId()==null).bind("spring");
        var nativeSpawn=spawn(1,null); var foreign=spawn(2,"other");
        assertThrows(IllegalStateException.class,()->local.applyToObjects(List.of(nativeSpawn)));
        assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.bind("sample",local,java.util.Set.of()));
        Binder engineBinder=LevelPatchOwnership::bind;
        var bound=engineBinder.bind("sample",local,java.util.Set.of("sample:spring"));
        var transformed=bound.applyToObjects(List.of(nativeSpawn,foreign));
        assertEquals("sample:spring",transformed.getFirst().objectKey());
        assertSame(foreign,transformed.get(1));
        assertThrows(IllegalStateException.class,()->local.applyToObjects(List.of(nativeSpawn)),"Admission leaves the template immutable");
        var forged=LevelPatch.empty().select(s->true).bind("other","other:spring");
        assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.bind("sample",forged,java.util.Set.of("other:spring")));
        assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.bind("sample",
                LevelPatch.empty().select(s->true).bind("spring"),java.util.Set.of("sample:spring"))
                .applyToObjects(List.of(foreign)));
    }

    @Test void aCreatorLoaderCannotClaimTrustedOwnershipThroughTheAdmissionBridge() throws Exception {
        String name=CreatorProbe.class.getName();
        byte[] bytecode;
        try (var input=getClass().getResourceAsStream("/"+name.replace('.','/')+".class")) { bytecode=input.readAllBytes(); }
        var jar=temp.resolve("creator-patch-probe.jar");
        try (var output=new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.jar.JarEntry(name.replace('.','/')+".class"));
            output.write(bytecode); output.closeEntry();
        }
        ClassLoader engineParent=getClass().getClassLoader();
        ClassLoader filteredParent=new ClassLoader(engineParent) {
            @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                if (requested.equals(name)) throw new ClassNotFoundException(requested);
                return super.loadClass(requested,resolve);
            }
        };
        try (var loader=new com.openggf.mods.code.ModDependencyClassLoader("probe",new java.net.URL[]{jar.toUri().toURL()},filteredParent,List.of())) {
            var method=loader.loadClass(name).getMethod("claim",LevelPatch.class);
            var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,
                    ()->method.invoke(null,LevelPatch.empty().select(s->true).bind("spring")));
            assertInstanceOf(SecurityException.class,failure.getCause());
            var binder=(Binder)loader.loadClass(name).getMethod("factory").invoke(null);
            assertThrows(SecurityException.class,()->binder.bind("other",
                    LevelPatch.empty().select(s->true).bind("spring"),java.util.Set.of("other:spring")),
                    "A creator-defined method reference retains its own defining loader");
        }
    }
}
