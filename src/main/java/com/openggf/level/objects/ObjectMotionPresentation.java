package com.openggf.level.objects;

import com.openggf.level.LevelMotionOwners;

import com.openggf.game.PowerUpObject;
import com.openggf.graphics.MotionPacket;
import com.openggf.graphics.SpriteMotionInternalAccess;

final class ObjectMotionPresentation {
    private ObjectMotionPresentation() { }
    private record Policy(boolean engine, boolean screen, boolean spatial) { }
    private static final ClassValue<Policy> POLICY = new ClassValue<>() {
        @Override protected Policy computeValue(Class<?> type) {
            var relative = type.getDeclaredAnnotation(CameraRelativeDraw.class);
            try {
                return new Policy(type.getClassLoader() == ObjectMotionPresentation.class.getClassLoader(),
                        relative != null && relative.value() == CameraRelativeDraw.Use.SCREEN_POSITION,
                        type.getMethod("getX").getDeclaringClass() != ObjectInstance.class
                                && type.getMethod("getY").getDeclaringClass() != ObjectInstance.class);
            } catch (NoSuchMethodException exception) {
                throw new AssertionError("ObjectInstance position contract missing", exception);
            }
        }
    };
    static MotionPacket.Binding resolve(ObjectInstance object, ObjectServices services) {
        var policy = POLICY.get(object.getClass());
        if (!policy.engine()) return null;
        if (object instanceof PowerUpObject effect && effect.boundPlayer() != null) {
            return LevelMotionOwners.playerOwner(services.spriteManager(), effect.boundPlayer());
        }
        if (policy.screen()) return MotionPacket.Screen.INSTANCE;
        if (object.getSpawn() == null && !policy.spatial()) return null;
        var manager = services.objectManager();
        if (manager == null) return null;
        return manager.objectQuery().identityOf(object)
                .map(id -> new MotionPacket.Owner(new LevelMotionOwners.ObjectKey(id), object.getX(), object.getY(),
                        MotionPacket.Anchoring.FOLLOW)).orElse(null);
    }
    static void draw(ObjectInstance object, ObjectServices services, Runnable draw) {
        var graphics = services == null ? null : services.graphicsManager();
        if (!SpriteMotionInternalAccess.collecting(graphics)) {
            draw.run();
            return;
        }
        SpriteMotionInternalAccess.withOwner(graphics, resolve(object, services), draw);
    }
}
