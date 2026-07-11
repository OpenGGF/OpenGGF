package com.openggf.mods.code;

/** Structured failure that aborts one owner's private registration transaction. */
public final class ModRegistrationException extends RuntimeException {
    private final String ownerModId;

    public ModRegistrationException(String ownerModId, String message) {
        super(message);
        this.ownerModId = java.util.Objects.requireNonNull(ownerModId, "ownerModId");
    }

    public ModRegistrationException(String ownerModId, String message, Throwable cause) {
        super(message, cause);
        this.ownerModId = java.util.Objects.requireNonNull(ownerModId, "ownerModId");
    }

    public String ownerModId() {
        return ownerModId;
    }
}
