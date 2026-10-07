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
    }
    @Test void oldOwnerSaveLoadsWithoutMovingAndNewWritesAreSharedWithScenes() throws Exception {
        Path legacy=root.resolve("survivors/profile.txt");
        Files.createDirectories(legacy.getParent()); Files.writeString(legacy,"bank=42\nshop.0=2\n");
        var module=ModStorageFactory.forOwner(root,"survivors");
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
        }
    }
}
