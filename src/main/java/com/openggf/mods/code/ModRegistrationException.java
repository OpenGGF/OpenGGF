package com.openggf.mods.code;

/** Structured failure that aborts one owner's private registration transaction. */
public final class ModRegistrationException extends RuntimeException {
    private final String ownerModId;
    private final String findingCode;
    private final String path;

    public ModRegistrationException(String ownerModId, String message) {
        this(ownerModId, "MOD_REGISTRATION_FAILED", message, null, null);
    }

    public ModRegistrationException(String ownerModId, String message, Throwable cause) {
        this(ownerModId, "MOD_REGISTRATION_FAILED", message, null, cause);
    }

    public ModRegistrationException(String ownerModId, String findingCode, String message,
                                    String path, Throwable cause) {
        super(message, cause);
        this.ownerModId = java.util.Objects.requireNonNull(ownerModId, "ownerModId");
        this.findingCode = java.util.Objects.requireNonNull(findingCode, "findingCode");
        this.path = path;
    }

    public String ownerModId() {
        return ownerModId;
    }

    public String findingCode() { return findingCode; }

    public String path() { return path; }
}
