package com.openggf.game.modzone;

/** Host-side validation failure attributed to one mod owner. */
public final class ModZoneRegistrationException extends RuntimeException {
    private final String ownerModId;
    private final String findingCode;
    private final String path;

    public ModZoneRegistrationException(String ownerModId, String message) {
        this(ownerModId, "MOD_REGISTRATION_FAILED", message, null, null);
    }

    public ModZoneRegistrationException(String ownerModId, String findingCode, String message,
                                        String path, Throwable cause) {
        super(message, cause);
        this.ownerModId = java.util.Objects.requireNonNull(ownerModId, "ownerModId");
        this.findingCode = java.util.Objects.requireNonNull(findingCode, "findingCode");
        this.path = path;
    }

    public String ownerModId() { return ownerModId; }
    public String findingCode() { return findingCode; }
    public String path() { return path; }
}
