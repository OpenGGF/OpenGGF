package com.openggf.level.objects;

/** Engine-internal access to a manager-owned visual direction controller. */
public final class ObjectDirectionAccess {
    private ObjectDirectionAccess() {}

    public static StageplayObjectDirection forManager(ObjectManager manager) {
        java.util.Objects.requireNonNull(manager, "object manager");
        if (manager.stageplayDirection == null) {
            manager.stageplayDirection = new StageplayObjectDirection(manager);
        }
        return manager.stageplayDirection;
    }

    public static ObjectRegistry registry(ObjectManager manager) {
        return manager.objectRegistry();
    }

    /** Internal scene director input; ordinary gameplay has no activation policy. */
    public static void activationWindows(ObjectManager manager, java.util.List<StageplayActivationPolicy.Window> windows) {
        if (manager.stageplayActivation != null) manager.stageplayActivation.setWindows(windows);
    }

    static boolean activationContains(ObjectServices services, int x, int y, int xMargin, int yMargin) {
        var manager = services == null ? null : services.objectManager();
        return manager != null && manager.stageplayActivation != null
                && manager.stageplayActivation.contains(x, y, xMargin, yMargin);
    }


    public static ObjectInstance createNative(ObjectManager manager, ObjectSpawn spawn) {
        return forManager(manager)
                .explicitNativeAdmission(
                        () ->
                                manager.createDynamicObject(
                                        () -> manager.objectRegistry().create(spawn)));
    }

    public static ObjectInstance createAuxiliary(
            ObjectManager manager, ObjectSpawn spawn, DestructionEffects.AnimalFactory factory) {
        return forManager(manager)
                .explicitNativeAdmission(
                        () ->
                                manager.createDynamicObject(
                                        () -> factory.create(spawn, manager.services())));
    }

    static boolean suspended(ObjectManager manager, ObjectInstance object) {
        return manager != null
                && manager.stageplayDirection != null
                && manager.stageplayDirection.suspended(object);
    }

    static void childSpawned(ObjectServices services, ObjectInstance parent, ObjectInstance child) {
        if (services != null && services.objectManager() != null && child != null) {
            StageplayObjectDirection direction = services.objectManager().stageplayDirection;
            if (direction != null) {
                direction.childSpawned(parent, child);
            }
        }
    }
}
