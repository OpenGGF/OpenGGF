package com.openggf.net.hub;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Opaque room-scoped session tokens. */
public final class SessionTokenIssuer {
    private final SecureRandom random = new SecureRandom();
    private final Set<String> active = ConcurrentHashMap.newKeySet();

    public String issue() {
        String token;
        do {
            byte[] bytes = new byte[16];
            random.nextBytes(bytes);
            token = HexFormat.of().formatHex(bytes);
        } while (!active.add(token));
        return token;
    }

    public boolean isValid(String token) {
        return token != null && active.contains(token);
    }

    public void revoke(String token) {
        if (token != null) {
            active.remove(token);
        }
    }
}
