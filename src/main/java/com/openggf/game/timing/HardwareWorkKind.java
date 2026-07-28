package com.openggf.game.timing;

/** Hardware work classes whose final readiness may be recorded for replay. */
public enum HardwareWorkKind {
    KOS_MODULE_QUEUE;

    public static HardwareWorkKind fromWireName(String wireName) {
        if ("kos_module_queue".equals(wireName)) {
            return KOS_MODULE_QUEUE;
        }
        throw new IllegalArgumentException("unknown kind: " + wireName);
    }
}
