package com.openggf.mods.code;

import com.openggf.game.*;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.SecondaryAbility;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCharacterSupplierProvenance {
    @TempDir Path temp;
    public static final class CreatorCallbacks {
        public static CharacterDefinition.PaletteSupplier water() {
            return code -> {throw new IllegalStateException("earlier creator water failure");};
        }
        public static CharacterDefinition.SuperFormSupplier superForm() {
            return code -> {throw new IllegalStateException("earlier creator super failure");};
        }
        public static CharacterDefinition.PresentationSupplier presentation() {
            return code -> {throw new IllegalStateException("earlier creator presentation failure");};
        }
    }
    private static CharacterDefinition definition(String owner, CharacterDefinition.PaletteSupplier water,
            CharacterDefinition.SuperFormSupplier form, CharacterDefinition.PresentationSupplier presentation) {
        return new CharacterDefinition(CharacterKey.mod(owner,"hero"),"Hero",(c,x,y)->null,null,
                PlayerCharacter.SONIC_ALONE,SecondaryAbility.NONE,form!=null,c->SpriteArtSet.EMPTY,
                null,Set.of(),water,presentation,form);
    }
    private static ModFaultBoundary boundary(Set<String> disabled) {
        return new ModFaultBoundary(Map.of(),new ModRuntimeFindingStore(),
                owners->new ModStateSaveResult.Saved(),disabled::addAll);
    }
    @ParameterizedTest @ValueSource(strings={"water","superForm","presentation"})
    void foreignRawCallbacksMustNotBeRetagged(String kind) throws Exception {
        String name=CreatorCallbacks.class.getName(); Path jar=temp.resolve("callbacks.jar");
        try(var input=getClass().getResourceAsStream("/"+name.replace('.','/')+".class");
            var out=new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
            out.putNextEntry(new java.util.jar.JarEntry(name.replace('.','/')+".class"));
            out.write(input.readAllBytes());out.closeEntry();
        }
        var parent=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String requested,boolean resolve) throws ClassNotFoundException {
                if(requested.equals(name)) throw new ClassNotFoundException(requested);
                return super.loadClass(requested,resolve);
            }
        };
        try(var loader=new ModDependencyClassLoader("earlier",new java.net.URL[]{jar.toUri().toURL()},parent,List.of())) {
            Object raw=loader.loadClass(name).getMethod(kind).invoke(null);
            var water=raw instanceof CharacterDefinition.PaletteSupplier p?p:null;
            var form=raw instanceof CharacterDefinition.SuperFormSupplier f?f:null;
            var presentation=raw instanceof CharacterDefinition.PresentationSupplier p?p:null;
            var earlier=definition("earlier",water,form,presentation);
            OwnedCharacterRegistry.bind("earlier",PlayableCharacterRegistry.empty().register(earlier.key(),earlier),
                    null,boundary(new LinkedHashSet<>()));
            var later=definition("later",water,form,presentation);
            var disabled=new LinkedHashSet<String>(); var faults=boundary(disabled);
            var rejected=assertThrows(ModFaultBoundary.CallbackAborted.class,
                    ()->OwnedCharacterRegistry.bind("later",
                            PlayableCharacterRegistry.empty().register(later.key(),later),null,faults));
            assertEquals("later",rejected.owner());
            assertTrue(rejected.getCause().getMessage().contains("already owned"));
            assertEquals(Set.of("later"),disabled);
        }
    }
}
