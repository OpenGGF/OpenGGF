package com.openggf.level.objects;

/** Engine-internal authority for host-provided named factories. */
public interface HostObjectKeyRegistry {
    String RESERVED_OWNER = "openggf";

    boolean hasHostObjectKey(String key);

    boolean ownsHostObject(ObjectSpawn spawn, ObjectInstance instance);

    /** Creator class loaders cannot acquire host authority by implementing this interface. */
    static HostObjectKeyRegistry trusted(ObjectRegistry registry) {
        return registry instanceof HostObjectKeyRegistry authority
                && registry.getClass().getClassLoader() == HostObjectKeyRegistry.class.getClassLoader()
                ? authority : null;
    }
}
