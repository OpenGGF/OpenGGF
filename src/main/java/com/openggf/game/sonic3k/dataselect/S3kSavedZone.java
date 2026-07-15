package com.openggf.game.sonic3k.dataselect;

import com.openggf.game.ZoneKey;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Strict tagged S3K saved-zone codec; historical numeric stock payloads remain unchanged. */
public record S3kSavedZone(ZoneKey zoneKey, boolean legacyNumeric) {
    public static final String FIELD = "savedZone";

    public S3kSavedZone {
        Objects.requireNonNull(zoneKey, "zoneKey");
    }

    public static S3kSavedZone read(Map<String, Object> payload) {
        Objects.requireNonNull(payload, "payload");
        Object tagged = payload.get(FIELD);
        if (tagged != null && payload.containsKey("zone")) {
            throw new IllegalArgumentException(
                    "S3K save cannot contain both zone and savedZone identities");
        }
        if (tagged == null) {
            int value = exactInt(payload.get("zone"), "zone");
            if (value < 0) {
                throw new IllegalArgumentException("S3K save has no valid saved-zone identity");
            }
            return new S3kSavedZone(ZoneKey.stock(value), true);
        }
        if (!(tagged instanceof Map<?, ?> union) || union.size() != 1) {
            throw new IllegalArgumentException("savedZone must contain exactly one tagged arm");
        }
        if (union.containsKey("stock")) {
            int value = exactInt(union.get("stock"), "savedZone.stock");
            if (value < 0) {
                throw new IllegalArgumentException("savedZone.stock must be non-negative");
            }
            return new S3kSavedZone(ZoneKey.stock(value), false);
        }
        Object modValue = union.get("mod");
        if (!(modValue instanceof Map<?, ?> mod) || mod.size() != 2
                || !(mod.get("owner") instanceof String owner)
                || !(mod.get("local") instanceof String local)) {
            throw new IllegalArgumentException("savedZone.mod requires exactly owner and local");
        }
        return new S3kSavedZone(ZoneKey.mod(owner, local), false);
    }

    public static void write(Map<String, Object> payload, ZoneKey key) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(key, "key");
        if (key instanceof ZoneKey.Stock stock) {
            payload.remove(FIELD);
            payload.put("zone", stock.zoneIndex());
            return;
        }
        payload.remove("zone");
        ZoneKey.Mod mod = (ZoneKey.Mod) key;
        LinkedHashMap<String, Object> identity = new LinkedHashMap<>();
        identity.put("owner", mod.ownerModId());
        identity.put("local", mod.localName());
        payload.put(FIELD, Map.of("mod", identity));
    }

    private static int exactInt(Object raw, String label) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(label + " must be an integer");
        }
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(number.toString());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " must be finite", invalid);
        }
        try {
            return decimal.intValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException(
                    label + " must be an exact 32-bit integer", invalid);
        }
    }
}
