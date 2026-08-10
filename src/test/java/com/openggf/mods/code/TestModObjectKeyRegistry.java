package com.openggf.mods.code;

import com.openggf.game.PlayableEntity;
import com.openggf.game.GameModule;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectFactory;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TestModObjectKeyRegistry {

    @Test
    void creatorPreviewMappingKeepsObjectAndArtIdentitiesIndependent() {
        ModContext context=new ModContext("owner","s2",com.openggf.io.ModAssetRoot.forTests("preview"));
        context.registerObject("enemy",named("enemy"));
        context.registerObjectArt("cards/enemy",new BakedSheetRef("art/enemy.gsheet"));
        context.registerObjectPreview("enemy","cards/enemy");
        ModRegistrationPlan plan=context.freeze();
        assertEquals("owner:cards/enemy",plan.objectPreviewArtKeys().get("owner:enemy"));
        assertNotEquals("owner:enemy",plan.objectPreviewArtKeys().get("owner:enemy"));
    }

    @Test
    void untaggedStockSpawnDelegatesWithoutConsultingModFactories() {
        AtomicInteger baseCreates = new AtomicInteger();
        ObjectRegistry base = baseRegistry(baseCreates);
        ModObjectKeyRegistry keys = new ModObjectKeyRegistry(List.of(
                new ModObjectKeyRegistry.Registration("example", "example:objects/buzzer", named("mod"))));
        ObjectRegistry decorated = new ModDecoratedObjectRegistry(base, keys);

        ObjectInstance created = decorated.create(new ObjectSpawn(12, 34, 0xFE, 0, 0, false, 34));

        assertEquals("base", created.getName());
        assertEquals(1, baseCreates.get());
    }

    @Test
    void taggedSpawnUsesOnlyItsNamespacedFactoryEvenForStockPlaceholderByte() {
        AtomicInteger baseCreates = new AtomicInteger();
        ObjectRegistry decorated = new ModDecoratedObjectRegistry(baseRegistry(baseCreates),
                new ModObjectKeyRegistry(List.of(new ModObjectKeyRegistry.Registration(
                        "example", "example:objects/buzzer", named("mod")))));
        ObjectSpawn tagged = new ObjectSpawn(12, 34, 0xFE, 2, 1, false, 34, -1,
                "example", "example:objects/buzzer");

        ObjectInstance created = decorated.create(tagged);

        assertEquals("mod", created.getName());
        assertSame(tagged, created.getSpawn());
        assertEquals(0, baseCreates.get(), "a tagged spawn must never fall through to a stock placeholder id");
    }

    @Test
    void rejectsNonCanonicalKeysOwnerMismatchDuplicatesAndAbsentOwner() {
        ObjectFactory factory = named("mod");
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new ModObjectKeyRegistry(List.of(
                        new ModObjectKeyRegistry.Registration("Example", "Example:thing", factory)))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ModObjectKeyRegistry(List.of(
                        new ModObjectKeyRegistry.Registration("example", "other:thing", factory)))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ModObjectKeyRegistry(List.of(
                        new ModObjectKeyRegistry.Registration(null, "example:thing", factory)))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ModObjectKeyRegistry(List.of(
                        new ModObjectKeyRegistry.Registration("example", "example:Thing", factory)))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ModObjectKeyRegistry(List.of(
                        new ModObjectKeyRegistry.Registration("example", "example:thing", factory),
                        new ModObjectKeyRegistry.Registration("example", "example:thing", factory)))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ObjectSpawn(
                        0, 0, 0, 0, 0, false, 0, -1, null, "example:thing"))
        );
    }

    @Test
    void absentTaggedFactoryFailsClosedAndDynamicTaggedSpawnKeepsOwnerIdentity() {
        ObjectRegistry decorated = new ModDecoratedObjectRegistry(baseRegistry(new AtomicInteger()),
                new ModObjectKeyRegistry(List.of()));
        ObjectSpawn dynamicChild = new ObjectSpawn(1, 2, 0, 0, 0, false, 2, -1,
                "example", "example:children/projectile");

        assertEquals(-1, dynamicChild.layoutIndex());
        assertEquals("example", dynamicChild.ownerModId());
        assertEquals("example:children/projectile", dynamicChild.objectKey());
        assertThrows(IllegalArgumentException.class, () -> decorated.create(dynamicChild));
    }

    @Test
    void twoOwnerPatchCompositionFlattensRegistriesSoBothOwnersResolve() {
        AtomicInteger baseCreates = new AtomicInteger();
        GameModule base = moduleWithRegistry(baseRegistry(baseCreates));
        ModBackedGamePatch ownerA = patch("owner-a", "owner-a:objects/a", named("a"));
        ModBackedGamePatch ownerB = patch("owner-b", "owner-b:objects/b", named("b"));

        GameModule composed = ownerB.apply(ownerA.apply(base, null), null);
        ObjectRegistry registry = composed.createObjectRegistry();

        assertEquals("a", registry.create(tagged("owner-a", "owner-a:objects/a")).getName());
        assertEquals("b", registry.create(tagged("owner-b", "owner-b:objects/b")).getName());
        assertEquals("base", registry.create(new ObjectSpawn(1, 2, 0xFE, 0, 0, false, 2)).getName());
        assertEquals(1, baseCreates.get());
        assertThrows(IllegalArgumentException.class,
                () -> registry.create(tagged("owner-a", "owner-a:objects/missing")));
    }

    @Test
    void duplicateKeysAcrossPatchCompositionFailDeterministically() {
        GameModule base = moduleWithRegistry(baseRegistry(new AtomicInteger()));
        ModBackedGamePatch first = patch("same-owner", "same-owner:objects/dup", named("first"));
        ModBackedGamePatch duplicate = patch("same-owner", "same-owner:objects/dup", named("second"));
        GameModule composed = duplicate.apply(first.apply(base, null), null);

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, composed::createObjectRegistry);
        assertEquals("Duplicate object key: same-owner:objects/dup", failure.getMessage());
    }

    @Test
    void registeredDynamicChildUsesItsExplicitOwnerAndDifferentChildKey() {
        ObjectRegistry registry = new ModDecoratedObjectRegistry(baseRegistry(new AtomicInteger()),
                new ModObjectKeyRegistry(List.of(
                        new ModObjectKeyRegistry.Registration(
                                "example", "example:objects/parent", named("parent")),
                        new ModObjectKeyRegistry.Registration(
                                "example", "example:children/projectile", named("child")))));
        ObjectSpawn child = tagged("example", "example:children/projectile");

        ObjectInstance created = registry.create(child);

        assertEquals("child", created.getName());
        assertSame(child, created.getSpawn());
        assertEquals(-1, created.getSpawn().layoutIndex());
        assertEquals("example", created.getSpawn().ownerModId());
        assertEquals(List.of("example:objects/parent", "example:children/projectile"),
                registry.browsableObjectKeys());
    }


    private static ObjectFactory named(String name) {
        return (spawn, registry) -> new StubObject(spawn, name);
    }

    private static ModBackedGamePatch patch(String owner, String key, ObjectFactory factory) {
        return new ModBackedGamePatch(new ModRegistrationPlan(
                owner, "s2", Map.of(key, factory), Map.of(), List.of()));
    }

    private static ObjectSpawn tagged(String owner, String key) {
        return new ObjectSpawn(1, 2, 0xFE, 0, 0, false, 2, -1, owner, key);
    }

    private static GameModule moduleWithRegistry(ObjectRegistry registry) {
        return (GameModule) Proxy.newProxyInstance(
                TestModObjectKeyRegistry.class.getClassLoader(), new Class<?>[] { GameModule.class },
                (proxy, method, args) -> method.getName().equals("createObjectRegistry")
                        ? registry : defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        throw new IllegalArgumentException("Unsupported primitive " + type);
    }

    private static ObjectRegistry baseRegistry(AtomicInteger creates) {
        return new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) {
                creates.incrementAndGet();
                return new StubObject(spawn, "base");
            }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) {}
            @Override public String getPrimaryName(int objectId) { return "stock"; }
        };
    }

    private record StubObject(ObjectSpawn getSpawn, String getName) implements ObjectInstance {
        @Override public void update(int vIntRunCount, PlayableEntity player) {}
        @Override public void appendRenderCommands(List<GLCommand> commands) {}
        @Override public boolean isHighPriority() { return false; }
        @Override public boolean isDestroyed() { return false; }
    }

}
