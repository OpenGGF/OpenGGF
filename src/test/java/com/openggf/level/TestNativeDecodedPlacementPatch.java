package com.openggf.level;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RingObjectPlacementMapping;
import com.openggf.level.rings.RingSpawn;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class TestNativeDecodedPlacementPatch {
    @TempDir Path temporary;
    static ObjectSpawn object(int index,String owner) {
        return new ObjectSpawn(100+index,200,0x8d,3,2,true,0xa0c8,index,owner,owner==null?null:owner+":guard");
    }
    public static class NativeFixture extends AbstractLevel {
        public NativeFixture() { this(0); }
        NativeFixture(int zone) {
            super(zone);
            objects=List.of(object(1,null),object(2,"other"));
            rings=List.of(new RingSpawn(120,180,1),new RingSpawn(140,180,2),new RingSpawn(160,180,3));
            patterns=new Pattern[]{new Pattern()}; patternCount=1;
            chunks=new Chunk[]{new Chunk()}; chunkCount=1;
            blocks=new Block[]{new Block()}; blockCount=1;
            solidTiles=new SolidTile[]{new SolidTile(0,new byte[16],new byte[16],(byte)0)}; solidTileCount=1;
            map=new Map(2,1,1);
            palettes=new Palette[]{new Palette(),new Palette(),new Palette(),new Palette()};
        }
    }
    public static final class CreatorFixture extends NativeFixture { public CreatorFixture() { } }
    public interface Applier { Level apply(LevelPatch patch,Level source); }
    public static final class CreatorProbe {
        public static Level apply(LevelPatch patch,Level source) { return LevelPatchOwnership.applyDecoded(patch,source); }
        public static Applier factory() { return LevelPatchOwnership::applyDecoded; }
    }
    private static LevelPatch admitted(LevelPatch patch) {
        return LevelPatchOwnership.bind("sample",patch,Set.of("sample:guard"));
    }
    private static LevelPatch nativeBinding() {
        return LevelPatch.empty().nativePlacements().select(s->s.ownerModId()==null).bind("guard");
    }

    @Test void nativeAdmissionPreservesTheSameConcreteSourceAndEveryGeometryOwner() {
        var source=new NativeFixture(); var foreign=source.getObjects().getLast();
        var patch=admitted(nativeBinding().retainRings((rings,objects)->List.of(rings.get(0),rings.get(2))));
        var map=source.getMap(); var pattern=source.getPattern(0); var chunk=source.getChunk(0);
        var block=source.getBlock(0); var solid=source.getSolidTile(0); var palette=source.getPalette(0);
        assertSame(source,LevelPatchOwnership.applyDecoded(patch,source));
        assertSame(map,source.getMap()); assertSame(pattern,source.getPattern(0)); assertSame(chunk,source.getChunk(0));
        assertSame(block,source.getBlock(0)); assertSame(solid,source.getSolidTile(0)); assertSame(palette,source.getPalette(0));
        assertEquals("sample:guard",source.getObjects().getFirst().objectKey());
        assertEquals(1,source.getObjects().getFirst().layoutIndex());
        assertEquals(0xa0c8,source.getObjects().getFirst().rawYWord());
        assertSame(foreign,source.getObjects().getLast());
        assertEquals(List.of(1,3),source.getRings().stream().map(RingSpawn::placementId).toList());
        assertThrows(UnsupportedOperationException.class,()->source.getObjects().clear());
        assertThrows(UnsupportedOperationException.class,()->source.getRings().clear());
    }

    @Test void retentionSeesIsolatedOriginalTablesBeforeBindingAndPublishesOnlyAfterValidation() {
        var source=new NativeFixture(); var originals=source.getObjects(); var rings=source.getRings();
        var patch=admitted(nativeBinding().retainRings((input,objects)->{
            assertThrows(UnsupportedOperationException.class,()->input.clear());
            assertThrows(UnsupportedOperationException.class,()->objects.clear());
            assertNull(objects.getFirst().ownerModId());
            assertSame(originals,source.getObjects()); assertSame(rings,source.getRings());
            return List.of(input.getFirst());
        }));
        LevelPatchOwnership.applyDecoded(patch,source);
        assertEquals(1,source.getRings().size());
    }

    @Test void badRetentionCannotPartiallyPublishValidObjectBindings() {
        List<BiFunction<List<RingSpawn>,List<ObjectSpawn>,List<RingSpawn>>> invalid=List.of(
                (rings,objects)->List.of(rings.get(0),rings.get(0)),
                (rings,objects)->List.of(rings.get(2),rings.get(0)),
                (rings,objects)->List.of(new RingSpawn(120,180,1)),
                (rings,objects)->List.of(new RingSpawn(121,180,1)),
                (rings,objects)->List.of(new RingSpawn(200,180,8)),
                (rings,objects)->null);
        for (var retention:invalid) {
            var source=new NativeFixture(); var objects=source.getObjects(); var rings=source.getRings();
            assertThrows(RuntimeException.class,()->LevelPatchOwnership.applyDecoded(
                    admitted(nativeBinding().retainRings(retention)),source));
            assertSame(objects,source.getObjects()); assertSame(rings,source.getRings());
        }
    }

    @Test void bindingFailureAndPostAdmissionFactoryForgeryLeaveBothTablesUntouched() {
        for (var patch:List.of(
                admitted(LevelPatch.empty().nativePlacements().select(s->true).bind("guard")),
                admitted(nativeBinding()).select(s->s.ownerModId()==null).bind("other","other:guard"),
                admitted(nativeBinding()).select(s->s.ownerModId()==null).bind("sample","sample:unregistered"))) {
            var source=new NativeFixture(); var objects=source.getObjects(); var rings=source.getRings();
            assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.applyDecoded(patch,source));
            assertSame(objects,source.getObjects()); assertSame(rings,source.getRings());
        }
        assertThrows(IllegalStateException.class,()->LevelPatchOwnership.applyDecoded(nativeBinding(),new NativeFixture()));
    }

    @Test void unsupportedNativeSourcesAndGeometryEditsFailBeforeAnyCreatorCallback() {
        var callbacks=new AtomicInteger();
        var patch=admitted(nativeBinding().whenInventory(objects->{callbacks.incrementAndGet();return true;}));
        assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.applyDecoded(patch,MutableLevel.snapshot(new NativeFixture())));
        var overridden=new NativeFixture(){@Override public List<ObjectSpawn> getObjects(){callbacks.incrementAndGet();return super.getObjects();}};
        assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.applyDecoded(patch,overridden));
        assertEquals(0,callbacks.get());
        assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.applyDecoded(
                admitted(LevelPatch.empty().nativePlacements().select(s->true).move(1,0)),new NativeFixture()));
        class ObjectBacked extends NativeFixture implements RingObjectPlacementMapping {
            public java.util.Map<ObjectSpawn,List<RingSpawn>> ringObjectPlacementMapping(){return java.util.Map.of();}
        }
        assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.applyDecoded(
                admitted(nativeBinding().retainRings((rings,objects)->rings)),new ObjectBacked()));
    }

    @Test void immutableBuilderAndAdmissionCopiesRetainZoneInventoryModeAndRetention() {
        var calls=new AtomicInteger();
        var patch=LevelPatch.empty().forZone(0).whenInventory(objects->{calls.incrementAndGet();return true;})
                .nativePlacements().retainRings((rings,objects)->List.of(rings.getFirst()))
                .select(s->s.ownerModId()==null).bind("guard");
        var bound=admitted(patch);
        var otherZone=new NativeFixture(1);var objects=otherZone.getObjects();var rings=otherZone.getRings();
        assertSame(otherZone,LevelPatchOwnership.applyDecoded(bound,otherZone));
        assertSame(objects,otherZone.getObjects());assertSame(rings,otherZone.getRings());assertEquals(0,calls.get());
        var source=new NativeFixture();assertSame(source,LevelPatchOwnership.applyDecoded(bound,source));
        assertEquals(1,calls.get());assertEquals(1,source.getRings().size());
        assertThrows(IllegalStateException.class,()->LevelPatchOwnership.applyDecoded(patch,new NativeFixture()));
    }

    @Test void inventoryMarkerSkipsReapplicationButFreshLoadsApplyIndependently() {
        var calls=new AtomicInteger();
        var patch=admitted(nativeBinding().forZone(0).whenInventory(objects->objects.stream().noneMatch(s->"sample".equals(s.ownerModId())))
                .retainRings((rings,objects)->{calls.incrementAndGet();return List.of(rings.getFirst());}));
        var source=new NativeFixture();LevelPatchOwnership.applyDecoded(patch,source);
        var objects=source.getObjects();var rings=source.getRings();
        LevelPatchOwnership.applyDecoded(patch,source);
        assertSame(objects,source.getObjects());assertSame(rings,source.getRings());assertEquals(1,calls.get());
        LevelPatchOwnership.applyDecoded(patch,new NativeFixture());assertEquals(2,calls.get());
        var never=admitted(nativeBinding().whenInventory(inventory->false).retainRings((r,o)->{fail("false inventory must skip retention");return r;}));
        var untouched=new NativeFixture();var original=untouched.getObjects();
        assertSame(untouched,LevelPatchOwnership.applyDecoded(never,untouched));assertSame(original,untouched.getObjects());
    }

    @Test void legacySnapshotPathAndPublicApplyReturnTypeStayUnchanged() {
        var source=new NativeFixture();var original=source.getObjects();
        var patch=LevelPatch.empty().select(s->s.ownerModId()==null).move(5,0);
        var result=LevelPatchOwnership.applyDecoded(patch,source);
        assertInstanceOf(MutableLevel.class,result);assertNotSame(source,result);
        assertEquals(original.getFirst().x()+5,result.getObjects().getFirst().x());
        assertSame(original,source.getObjects());assertNotSame(source.getPattern(0),result.getPattern(0));
        MutableLevel direct=admitted(nativeBinding()).apply(source);
        assertNotSame(source,direct);assertNull(source.getObjects().getFirst().ownerModId());
    }

    @Test void creatorLoaderCannotApplyThroughDirectOrHiddenBridgeNorSupplyNativeSubclass() throws Exception {
        var names=List.of(CreatorProbe.class.getName(),CreatorFixture.class.getName());
        var jar=temporary.resolve("creator-native-patch.jar");
        try(var output=new JarOutputStream(Files.newOutputStream(jar))) {
            for(String name:names)try(var input=getClass().getResourceAsStream("/"+name.replace('.','/')+".class")) {
                output.putNextEntry(new JarEntry(name.replace('.','/')+".class"));output.write(input.readAllBytes());output.closeEntry();
            }
        }
        var filtered=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(names.contains(name))throw new ClassNotFoundException(name);return super.loadClass(name,resolve);
            }
        };
        try(var loader=new com.openggf.mods.code.ModDependencyClassLoader("probe",new URL[]{jar.toUri().toURL()},filtered,List.of())) {
            var probe=loader.loadClass(names.getFirst());
            var exception=assertThrows(InvocationTargetException.class,()->probe.getMethod("apply",LevelPatch.class,Level.class)
                    .invoke(null,admitted(nativeBinding()),new NativeFixture()));
            assertInstanceOf(SecurityException.class,exception.getCause());
            var reference=(Applier)probe.getMethod("factory").invoke(null);
            assertThrows(SecurityException.class,()->reference.apply(admitted(nativeBinding()),new NativeFixture()));
            var creator=(Level)loader.loadClass(names.getLast()).getConstructor().newInstance();
            assertThrows(IllegalArgumentException.class,()->LevelPatchOwnership.applyDecoded(admitted(nativeBinding()),creator));
        }
    }
}
