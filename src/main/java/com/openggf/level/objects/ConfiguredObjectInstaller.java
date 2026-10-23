package com.openggf.level.objects;

import com.openggf.level.objects.authored.AuthoredGizmos;

import java.util.function.Consumer;

/** Installs configured native occupants before authored regions during population reset. */
final class ConfiguredObjectInstaller {
    private ConfiguredObjectInstaller() { }

    static void install(FixedSstObjectInstaller fixed, ObjectPlacementController placement,
                        Consumer<ObjectInstance> admitAuthored) {
        fixed.installConfiguredObjects();
        AuthoredGizmos.install(placement.getAllSpawns(), admitAuthored);
    }
}
