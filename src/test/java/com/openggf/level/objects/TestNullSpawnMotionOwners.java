package com.openggf.level.objects;

import com.openggf.game.PowerUpObject;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.sonic3k.objects.InstaShieldObjectInstance;
import com.openggf.graphics.MotionPacket;
import com.openggf.graphics.SpriteMotionInternalAccess;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestSessionOutputPaths;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestNullSpawnMotionOwners {
    private static final Pattern NULL_SPAWN = Pattern.compile(
            "super\\s*\\(\\s*null\\s*[,)]|getSpawn\\s*\\(\\s*\\)\\s*\\{\\s*return\\s+null\\s*;");

    @Test void everyConcreteNullSpawnProducerResolvesWithoutReadingADefaultPosition() throws Exception {
        Set<Class<?>> roots = new HashSet<>();
        Path sources = Path.of("src/main/java");
        try (var files = Files.walk(sources)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (!NULL_SPAWN.matcher(Files.readString(file)).find()) continue;
                String name = sources.relativize(file).toString().replace('/', '.').replace(".java", "");
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                if (ObjectInstance.class.isAssignableFrom(type)) roots.add(type);
            }
        }
        assertTrue(roots.size() >= 18, "Scan must include the production null-spawn constructors");
        Set<Class<?>> inventory = new HashSet<>();
        var compiled = TestSessionOutputPaths.compiledClasses();
        try (var files = Files.walk(compiled)) {
            for (var file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = compiled.relativize(file).toString().replace('/', '.').replace(".class", "");
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                if (type.isInterface() || Modifier.isAbstract(type.getModifiers())
                        || roots.stream().noneMatch(root -> root.isAssignableFrom(type))) continue;
                inventory.add(type);
                check(type.asSubclass(ObjectInstance.class));
            }
        }
        assertTrue(inventory.contains(InstaShieldObjectInstance.class), "Inherited null spawn must be discovered");
        assertTrue(inventory.size() > roots.size(), "Include concrete descendants, not just direct constructor sites");
    }

    private static void check(Class<? extends ObjectInstance> type) throws Exception {
        ObjectInstance object = mock(type, CALLS_REAL_METHODS);
        doReturn(null).when(object).getSpawn();
        var services = mock(ObjectServices.class);
        var manager = mock(ObjectManager.class);
        var query = mock(ObjectQuery.class);
        var sprites = mock(SpriteManager.class);
        var id = ObjectRefId.dynamic(3, 0, 7);
        when(services.objectManager()).thenReturn(manager);
        when(services.spriteManager()).thenReturn(sprites);
        when(manager.objectQuery()).thenReturn(query);
        when(query.identityOf(object)).thenReturn(Optional.of(id));
        when(sprites.getRegisteredSidekicks()).thenReturn(List.of());
        boolean spatial = type.getMethod("getX").getDeclaringClass() != ObjectInstance.class
                && type.getMethod("getY").getDeclaringClass() != ObjectInstance.class;
        if (spatial) {
            doReturn(321).when(object).getX(); doReturn(654).when(object).getY();
        }
        var relative = type.getDeclaredAnnotation(CameraRelativeDraw.class);
        var resolved = assertDoesNotThrow(() -> ObjectMotionPresentation.resolve(object, services), type.getName());
        if (relative != null && relative.value() == CameraRelativeDraw.Use.SCREEN_POSITION) {
            assertSame(MotionPacket.Screen.INSTANCE, resolved, type.getName());
        } else if (spatial) {
            assertEquals(new MotionPacket.Owner(new MotionPacket.ObjectKey(id), 321, 654,
                    MotionPacket.Anchoring.FOLLOW), resolved, type.getName());
        } else {
            assertNull(resolved, type.getName());
            verify(object, never()).getX(); verify(object, never()).getY();
        }
        if (object instanceof PowerUpObject effect) {
            var player = mock(AbstractPlayableSprite.class);
            when(player.getCentreX()).thenReturn((short) 123); when(player.getCentreY()).thenReturn((short) 456);
            doReturn(player).when(effect).boundPlayer();
            var expected = SpriteMotionInternalAccess.playerOwner(sprites, player);
            assertEquals(new MotionPacket.PlayerKey(PlayerRefId.mainPlayer(), player), expected.key());
            assertEquals(expected, ObjectMotionPresentation.resolve(object, services), type.getName());
            when(sprites.getRegisteredSidekicks()).thenReturn(List.of(player));
            assertEquals(new MotionPacket.PlayerKey(PlayerRefId.sidekick(0), player),
                    ((MotionPacket.Owner) ObjectMotionPresentation.resolve(object, services)).key(), type.getName());
        }
    }

    @Test void instaShieldSharesThePlayerAnchorWithoutTouchingItsNullSpawnGetters() throws Exception {
        check(InstaShieldObjectInstance.class);
    }
}
