package com.openggf.level.objects;

/** Verified registered-factory derivatives; construction and admission retain the content owner's boundary. */
final class RegisteredObjectDerivatives {
    private RegisteredObjectDerivatives() { }

    static String registeredContentOwner(ObjectManager manager, ObjectInstance source) {
        if (source == null || manager.objectQuery().identityOf(source).isEmpty()
                || manager.objectCallbacks().call(source, source::isDestroyed)) return null;
        ObjectSpawn placement = manager.managedSpawn(source);
        String owner = manager.objectCallbacks().owner(source);
        return placement != null && owner != null && owner.equals(placement.ownerModId())
                && manager.objectRegistry() != null && manager.objectRegistry().hasObjectKey(placement.objectKey()) ? owner : null;
    }

    static AbstractObjectInstance createRegisteredDerivative(ObjectManager manager, ObjectInstance source, int x, int y) {
        String owner = registeredContentOwner(manager, source);
        if (owner == null || !manager.hasFreeDynamicSlot() || x < 0 || x > 0xFFFF || y < 0 || y > 0x0FFF) return null;
        ObjectSpawn placement = manager.managedSpawn(source);
        ObjectSpawn derivative = new ObjectSpawn(x, y, placement.objectId(), placement.subtype(), placement.renderFlags(),
                false, placement.rawFlags() | y, -1, owner, placement.objectKey());
        // Construction AND admission run as the registered content owner, even
        // when a different creator's mutator callback caused this allocation.
        return manager.objectCallbacks().construct(owner, () -> ObjectConstructionContext.construct(manager.services(), () -> {
            ObjectInstance created = manager.objectRegistry().create(derivative);
            if (!(created instanceof AbstractObjectInstance object) || !(created instanceof RewindRecreatable)
                    || created.getClass() != source.getClass() || object.isDestroyed() || object.getSlotIndex() >= 0
                    || !derivative.equals(object.managedSpawn())
                    || manager.objectQuery().identityOf(created).isPresent()
                    || !(created instanceof com.openggf.game.mutators.NativeObjectFieldTarget target)
                    || target.fieldKind() != com.openggf.game.mutators.NativeObjectFieldTarget.Kind.BADNIK
                    || !target.supportsGroundedFieldMotion()) return null;
            // Validate before admission; the normal factory path owns slot allocation and rejection.
            return manager.createDynamicObject(() -> object);
        }));
    }

}
