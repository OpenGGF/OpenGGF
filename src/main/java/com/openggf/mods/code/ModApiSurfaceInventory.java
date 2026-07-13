package com.openggf.mods.code;

import com.openggf.game.ModApi;

import java.util.Comparator;
import java.util.List;

/** Canonical annotated-type inventory shared by signature and Javadoc tooling. */
public final class ModApiSurfaceInventory {
    private static final List<String> TYPE_NAMES = List.of(
            "com.openggf.mods.code.GgfMod",
            "com.openggf.mods.code.ModContext",
            "com.openggf.mods.code.BakedSheetRef",
            "com.openggf.mods.code.BakedLevelRef",
            "com.openggf.mods.code.ModLevelDefinition",
            "com.openggf.level.objects.BakedSheetReader",
            "com.openggf.game.patch.GamePatch",
            "com.openggf.game.patch.PatchContext",
            "com.openggf.game.patch.PatchContext$LogicalRomSource",
            "com.openggf.game.patch.GameplayLaunchRequest",
            "com.openggf.level.objects.ObjectServices",
            "com.openggf.level.objects.AbstractObjectInstance",
            "com.openggf.level.objects.AbstractBadnikInstance",
            "com.openggf.level.objects.ObjectSpawn",
            "com.openggf.sprites.playable.ObjectControlState",
            "com.openggf.level.objects.ObjectLifetimeOps",
            "com.openggf.level.objects.ObjectPlayerQuery",
            "com.openggf.game.PhysicsProfile",
            "com.openggf.level.objects.SubpixelMotion",
            "com.openggf.level.objects.PatrolMovementHelper",
            "com.openggf.level.objects.PlatformBobHelper",
            "com.openggf.level.objects.SpringBounceHelper",
            "com.openggf.level.objects.DestructionEffects",
            // Explicitly required transitive creator contracts.
            "com.openggf.game.GameModule",
            "com.openggf.game.AbstractStandaloneGameModule",
            "com.openggf.game.ModGame",
            "com.openggf.mods.code.StandaloneLevelLoader",
            "com.openggf.game.RomDataSource",
            "com.openggf.level.objects.PlayableSheetReader",
            "com.openggf.level.objects.PlayableSheetMaterializer",
            "com.openggf.level.objects.ObjectFactory",
            "com.openggf.level.objects.RewindRecreatable",
            "com.openggf.level.spawn.SpawnPoint");

    private ModApiSurfaceInventory() { }

    /** The intentionally curated roots from which the public API closure is derived. */
    public static List<Class<?>> rootTypes() {
        return TYPE_NAMES.stream().map(ModApiSurfaceInventory::load).peek(type -> {
            if (!type.isAnnotationPresent(ModApi.class)) {
                throw new IllegalStateException("Inventory type lacks @ModApi: " + type.getName());
            }
        }).sorted(Comparator.comparing(Class::getName)).toList();
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, ModApiSurfaceInventory.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Missing curated Mod API root: " + name, exception);
        }
    }

    /** Every engine type recursively exposed by a public/protected root signature. */
    public static List<Class<?>> annotatedTypes() {
        return ModApiSignatureSurface.recursiveTypes().stream().peek(type -> {
            if (!type.isAnnotationPresent(ModApi.class)) {
                throw new IllegalStateException("Recursive API type lacks @ModApi: " + type.getName());
            }
        }).toList();
    }

    public static List<String> annotatedTypeNames() {
        return annotatedTypes().stream().map(Class::getName).toList();
    }
}
