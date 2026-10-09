package com.openggf.mods.scene.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.openggf.game.GameId;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TestModStorage {
    @TempDir Path root;
    public static final class CreatorProbe {
        public static void claim(Path root) { ModStorageFactory.forOwner(root, "other-owner"); }
        public static java.util.function.BiFunction<Path,String,com.openggf.mods.scene.SceneStorage> factory() {
            return ModStorageFactory::forOwner;
        }
    }
    @Test void oldOwnerSaveLoadsWithoutMovingAndNewWritesAreSharedWithScenes() throws Exception {
        Path legacy=root.resolve("survivors/profile.txt");
        Files.createDirectories(legacy.getParent()); Files.writeString(legacy,"bank=42\nshop.0=2\n");
        java.util.function.BiFunction<Path,String,com.openggf.mods.scene.SceneStorage> engineFactory=ModStorageFactory::forOwner;
        var module=engineFactory.apply(root,"survivors");
        var scene=ModStorageFactory.forOwner(root,"survivors");
        assertEquals("bank=42\nshop.0=2\n",module.read("profile.txt").orElseThrow());
        assertTrue(Files.exists(legacy));
        assertTrue(module.write("profile.txt","bank=45\n"));
        assertEquals("bank=45\n",scene.read("profile.txt").orElseThrow());
        assertEquals("bank=42\nshop.0=2\n",Files.readString(legacy));
        assertTrue(ModStorageFactory.forOwner(root,"other").read("profile.txt").isEmpty());
        assertThrows(IllegalArgumentException.class,()->module.read("../survivors/profile.txt"));
        assertFalse(module.write("huge.txt","x".repeat((1<<20)+1)));
    }
    @Test void linksCannotEscapeOwnerStorageOrLegacyFallback() throws Exception {
        Path stock=root.resolve("stock.txt"); Files.writeString(stock,"stock-save");
        Path own=root.resolve("mods/sample"); Files.createDirectories(own);
        Files.createSymbolicLink(own.resolve("profile.txt"),stock);
        var storage=ModStorageFactory.forOwner(root,"sample");
        assertTrue(storage.read("profile.txt").isEmpty()); assertFalse(storage.write("profile.txt","bad"));
        assertFalse(storage.delete("profile.txt")); assertTrue(storage.list().isEmpty());
        assertEquals("stock-save",Files.readString(stock));
        Files.createSymbolicLink(root.resolve("other"),root);
        assertTrue(ModStorageFactory.forOwner(root,"other").read("stock.txt").isEmpty());
    }

    @Test void binaryFilesRoundTripArbitraryBytesAndShareTheTextNamespace() throws Exception {
        var storage=ModStorageFactory.forOwner(root,"ghosts");
        byte[] payload=new byte[256];
        for(int i=0;i<payload.length;i++) payload[i]=(byte)i;
        assertTrue(storage.writeBytes("best.ggfghost",payload));
        byte[] read=storage.readBytes("best.ggfghost").orElseThrow();
        assertArrayEquals(payload,read);
        read[0]=42;
        assertArrayEquals(payload,storage.readBytes("best.ggfghost").orElseThrow(),"reads return a fresh copy");
        payload[1]=99;
        assertEquals(1,storage.readBytes("best.ggfghost").orElseThrow()[1],"writes do not retain the caller array");
        assertTrue(storage.read("best.ggfghost").isEmpty(),"non-UTF-8 bytes are not text");
        assertTrue(storage.write("notes.txt","hi"));
        assertArrayEquals("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                storage.readBytes("notes.txt").orElseThrow());
        assertTrue(storage.writeBytes("empty.bin",new byte[0]));
        assertArrayEquals(new byte[0],storage.readBytes("empty.bin").orElseThrow());
        assertEquals(java.util.List.of("best.ggfghost","empty.bin","notes.txt"),storage.list());
        assertTrue(storage.delete("best.ggfghost"));
        assertTrue(storage.readBytes("best.ggfghost").isEmpty());
        assertTrue(ModStorageFactory.forOwner(root,"other").readBytes("notes.txt").isEmpty());
        assertThrows(NullPointerException.class,()->storage.writeBytes("null.bin",null));
    }

    @Test void binaryCapIsEnforcedOnWriteAndReadWithoutTouchingTheExistingFile() throws Exception {
        var storage=ModStorageFactory.forOwner(root,"capped");
        int cap=com.openggf.mods.ModStorage.MAX_BINARY_BYTES;
        assertEquals(4<<20,cap);
        assertEquals(1<<20,com.openggf.mods.ModStorage.MAX_TEXT_BYTES);
        byte[] full=new byte[cap];
        full[cap-1]=7;
        assertTrue(storage.writeBytes("full.bin",full));
        assertEquals(cap,storage.readBytes("full.bin").orElseThrow().length);
        assertTrue(storage.writeBytes("keep.bin",new byte[]{1,2,3}));
        assertFalse(storage.writeBytes("keep.bin",new byte[cap+1]));
        assertArrayEquals(new byte[]{1,2,3},storage.readBytes("keep.bin").orElseThrow());
        Path oversize=root.resolve("mods/capped/oversize.bin");
        Files.write(oversize,new byte[cap+1]);
        assertTrue(storage.readBytes("oversize.bin").isEmpty(),"a file grown past the cap outside the API is not read");
        assertTrue(storage.write("text.txt","x".repeat(1<<20)));
        assertTrue(storage.read("text.txt").isPresent());
        assertTrue(storage.readBytes("text.txt").isPresent(),"text files under the binary cap read as bytes");
        assertNoStagingResidue(root.resolve("mods/capped"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"","../escape.bin","a/b","UPPER.bin",".hidden",
            "space name","toolong-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void binaryNamesFollowTheTextNameRules(String name) {
        var storage=ModStorageFactory.forOwner(root,"named");
        assertThrows(IllegalArgumentException.class,()->storage.readBytes(name));
        assertThrows(IllegalArgumentException.class,()->storage.writeBytes(name,new byte[]{1}));
        assertThrows(IllegalArgumentException.class,()->storage.read(name));
        assertThrows(IllegalArgumentException.class,()->storage.write(name,"x"));
    }

    @Test void binaryWritesRefuseLinksAndLeaveNoStagingFileWhenReplacementFails() throws Exception {
        Path stock=root.resolve("stock.bin"); Files.write(stock,new byte[]{9});
        Path own=root.resolve("mods/linked"); Files.createDirectories(own);
        Files.createSymbolicLink(own.resolve("profile.bin"),stock);
        var storage=ModStorageFactory.forOwner(root,"linked");
        assertTrue(storage.readBytes("profile.bin").isEmpty());
        assertFalse(storage.writeBytes("profile.bin",new byte[]{1}));
        assertArrayEquals(new byte[]{9},Files.readAllBytes(stock));
        // A non-empty directory in the target's place makes the final rename fail.
        Files.createDirectories(own.resolve("blocked.bin/inner"));
        assertFalse(storage.writeBytes("blocked.bin",new byte[]{1,2}));
        assertTrue(Files.isDirectory(own.resolve("blocked.bin/inner")));
        assertNoStagingResidue(own);
    }

    // POSIX rename replaces the target atomically even while a reader holds it open; Windows
    // refuses to replace an open file, so the write would report failure rather than tear.
    @org.junit.jupiter.api.condition.DisabledOnOs(org.junit.jupiter.api.condition.OS.WINDOWS)
    @Test void concurrentReadersOnlyEverSeeOneWholeBinaryFile() throws Exception {
        var storage=ModStorageFactory.forOwner(root,"atomic");
        byte[] first=new byte[64*1024]; java.util.Arrays.fill(first,(byte)'A');
        byte[] second=new byte[96*1024]; java.util.Arrays.fill(second,(byte)'B');
        assertTrue(storage.writeBytes("ghost.bin",first));
        var torn=new java.util.concurrent.atomic.AtomicReference<String>();
        var stop=new java.util.concurrent.atomic.AtomicBoolean();
        Thread reader=new Thread(()->{
            while(!stop.get()&&torn.get()==null){
                byte[] seen=storage.readBytes("ghost.bin").orElse(null);
                if(seen==null){torn.set("file missing during replacement");break;}
                if(!java.util.Arrays.equals(seen,first)&&!java.util.Arrays.equals(seen,second))
                    torn.set("torn read of "+seen.length+" bytes");
            }
        });
        reader.start();
        try{
            for(int i=0;i<200;i++) assertTrue(storage.writeBytes("ghost.bin",(i&1)==0?second:first));
        }finally{stop.set(true);reader.join(10_000);}
        assertNull(torn.get());
        assertNoStagingResidue(root.resolve("mods/atomic"));
    }

    private static void assertNoStagingResidue(Path directory) throws Exception {
        try(var files=Files.list(directory)){
            assertEquals(java.util.List.of(),files.map(p->p.getFileName().toString())
                    .filter(n->n.endsWith(".tmp")).toList());
        }
    }

    @ParameterizedTest
    @EnumSource(GameId.class)
    void ownerNamedLikeBuiltInGameCannotAccessItsStockSaveSlots(GameId game) throws Exception {
        Path stock=root.resolve(game.code()+"/slot1.json");
        Files.createDirectories(stock.getParent()); Files.writeString(stock,"stock-slot");
        var storage=ModStorageFactory.forOwner(root,game.code());
        assertTrue(storage.read("slot1.json").isEmpty()); assertFalse(storage.delete("slot1.json"));
        assertTrue(storage.write("slot1.json","mod-owned"));
        assertEquals("stock-slot",Files.readString(stock));
        assertEquals("mod-owned",Files.readString(root.resolve("mods/"+game.code()+"/slot1.json")));
    }

    @Test void creatorClassloaderCannotInvokeEngineOwnerAdmissionBridge() throws Exception {
        String name=CreatorProbe.class.getName();
        byte[] bytecode;
        try (var input=getClass().getResourceAsStream("/"+name.replace('.','/')+".class")) {
            bytecode=input.readAllBytes();
        }
        Path jar=root.resolve("creator-probe.jar");
        try (var output=new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.jar.JarEntry(name.replace('.','/')+".class"));
            output.write(bytecode); output.closeEntry();
        }
        ClassLoader engineParent=getClass().getClassLoader();
        ClassLoader filteredParent=new ClassLoader(engineParent) {
            @Override protected Class<?> loadClass(String requested, boolean resolve) throws ClassNotFoundException {
                if (requested.equals(name)) throw new ClassNotFoundException(requested);
                return super.loadClass(requested,resolve);
            }
        };
        try (var loader=new com.openggf.mods.code.ModDependencyClassLoader("probe",
                new java.net.URL[]{jar.toUri().toURL()},filteredParent,java.util.List.of())) {
            var method=loader.loadClass(name).getMethod("claim",Path.class);
            var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,root));
            assertInstanceOf(SecurityException.class,failure.getCause());
            @SuppressWarnings("unchecked")
            var factory=(java.util.function.BiFunction<Path,String,com.openggf.mods.scene.SceneStorage>)
                    loader.loadClass(name).getMethod("factory").invoke(null);
            assertThrows(SecurityException.class,()->factory.apply(root,"other-owner"),
                    "A creator-defined method reference keeps its defining loader when the engine calls it");
        }
    }
}
