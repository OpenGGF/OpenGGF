package com.openggf.io;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical syntax shared by mod manifests and namespaced registries. */
public final class ModKeySyntax {
    private static final Pattern MANIFEST_ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
    private static final Pattern LOCAL_NAME = Pattern.compile("[a-z0-9][a-z0-9._/-]{0,127}");
    private static final int MAX_DISPLAY_BYTES = 192;

    private ModKeySyntax() {}

    public static String requireManifestId(String id) {
        Objects.requireNonNull(id, "id");
        if (!MANIFEST_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid mod id: " + id);
        }
        return id;
    }

    public static String requireLocalName(String localName) {
        Objects.requireNonNull(localName, "localName");
        if (!LOCAL_NAME.matcher(localName).matches()) {
            throw new IllegalArgumentException("Invalid mod-local name: " + localName);
        }
        for (String segment : localName.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid mod-local path segment: " + localName);
            }
        }
        return localName;
    }

    public static String requireOwnedKey(String modId, String localName) {
        String display = requireManifestId(modId) + ":" + requireLocalName(localName);
        if (display.getBytes(StandardCharsets.UTF_8).length > MAX_DISPLAY_BYTES) {
            throw new IllegalArgumentException("Mod key exceeds 192 UTF-8 bytes");
        }
        return display;
    }

    public static String requireOwnedKey(String declaringManifestId, String ownerModId, String localName) {
        requireManifestId(declaringManifestId);
        requireManifestId(ownerModId);
        if (!declaringManifestId.equals(ownerModId)) {
            throw new IllegalArgumentException("Key owner does not match declaring mod id");
        }
        return requireOwnedKey(ownerModId, localName);
    }

    public static String requireDisplayKey(String displayKey) {
        Objects.requireNonNull(displayKey, "displayKey");
        int colon = displayKey.indexOf(':');
        if (colon <= 0 || colon != displayKey.lastIndexOf(':') || colon == displayKey.length() - 1) {
            throw new IllegalArgumentException("Mod key must contain exactly one colon");
        }
        String canonical = requireOwnedKey(displayKey.substring(0, colon), displayKey.substring(colon + 1));
        if (!canonical.equals(displayKey)) {
            throw new IllegalArgumentException("Non-canonical mod key");
        }
        return displayKey;
    }
}
