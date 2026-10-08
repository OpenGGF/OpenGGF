package survivors;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectServices;
import java.util.function.Supplier;

/** Mod-owned arena entities are rewindable extensions, not native ROM SST occupants. */
final class ArenaObjects {
    private ArenaObjects() { }

    static <T extends AbstractObjectInstance> T spawn(ObjectServices services, Supplier<T> factory) {
        return ObjectConstructionContext.construct(services, () -> {
            T object = factory.get();
            if (!ObjectConstructionContext.isProbeConstruction()) {
                services.objectManager().addRewindableAuxiliaryDynamicObject(object);
            }
            return object;
        });
    }
}
