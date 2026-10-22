package com.openggf.level.objects;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.SpriteSlotEmission;
import java.util.List;

/** Native render-slot composition kept outside the creator ObjectManager contract. */
public final class ObjectDrawInternalAccess {
    private ObjectDrawInternalAccess() { }
    public static void drawBucket(ObjectManager manager, int bucket, GraphicsManager graphics,
                                  List<SpriteSlotEmission> external) {
        Class<?> caller = com.openggf.util.EngineCallerAccess.callerOutside(ObjectDrawInternalAccess.class);
        if (caller.getClassLoader() != ObjectDrawInternalAccess.class.getClassLoader()) {
            throw new SecurityException("Native slot composition belongs to the engine renderer");
        }
        manager.drawBucketWithSlotEmissions(bucket, graphics, external);
    }
}
