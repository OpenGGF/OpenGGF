package com.openggf.level.objects;

import com.openggf.graphics.MotionPacket;
import com.openggf.graphics.SpriteMotionInternalAccess;

final class ObjectMotionPresentation {
    private ObjectMotionPresentation() { }
    private static final ClassValue<MotionPacket.Anchoring> ANCHORING = new ClassValue<>() {
        @Override protected MotionPacket.Anchoring computeValue(Class<?> type) {
            if (type.getClassLoader() != ObjectMotionPresentation.class.getClassLoader())
                return MotionPacket.Anchoring.WORLD;
            var relative = type.getDeclaredAnnotation(CameraRelativeDraw.class);
            return relative != null && relative.value() == CameraRelativeDraw.Use.SCREEN_POSITION
                    ? MotionPacket.Anchoring.SCREEN : MotionPacket.Anchoring.FOLLOW;
        }
    };
    static void draw(ObjectInstance object, ObjectServices services, Runnable draw) {
        var graphics = services == null ? null : services.graphicsManager();
        if (!SpriteMotionInternalAccess.collecting(graphics) || services.objectManager() == null) {
            draw.run();
            return;
        }
        var id = services.objectManager().objectQuery().identityOf(object);
        var anchoring = ANCHORING.get(object.getClass());
        if (id.isEmpty() || anchoring == MotionPacket.Anchoring.WORLD) { draw.run(); return; }
        SpriteMotionInternalAccess.withOwner(graphics, new MotionPacket.Owner(new MotionPacket.ObjectKey(id.get()),
                object.getX(), object.getY(), anchoring), draw);
    }
}
