package com.openggf.audio.rewind;

import com.openggf.audio.smps.AbstractSmpsData;

import java.util.Arrays;
import java.util.Objects;

public record SmpsSourceDescriptor(
        Kind kind,
        int id,
        String name,
        String donorGameId,
        int z80StartAddress,
        int dataLength,
        int dataHash,
        boolean palSpeedupDisabled) {

    public enum Kind {
        UNKNOWN,
        BASE_MUSIC,
        BASE_SFX_ID,
        BASE_SFX_NAME,
        DONOR_MUSIC,
        DONOR_SFX_ID
    }

    public SmpsSourceDescriptor {
        Objects.requireNonNull(kind, "kind");
    }

    public static SmpsSourceDescriptor from(AbstractSmpsData data) {
        return describe(Kind.UNKNOWN, null, null, data);
    }

    public static SmpsSourceDescriptor baseMusic(AbstractSmpsData data) {
        return describe(Kind.BASE_MUSIC, null, null, data);
    }

    public static SmpsSourceDescriptor baseSfx(AbstractSmpsData data) {
        return describe(Kind.BASE_SFX_ID, null, null, data);
    }

    public static SmpsSourceDescriptor baseNamedSfx(String name, AbstractSmpsData data) {
        return describe(Kind.BASE_SFX_NAME, Objects.requireNonNull(name, "name"), null, data);
    }

    public static SmpsSourceDescriptor donorMusic(String donorGameId, AbstractSmpsData data) {
        return describe(Kind.DONOR_MUSIC, null, Objects.requireNonNull(donorGameId, "donorGameId"), data);
    }

    public static SmpsSourceDescriptor donorSfx(String donorGameId, AbstractSmpsData data) {
        return describe(Kind.DONOR_SFX_ID, null, Objects.requireNonNull(donorGameId, "donorGameId"), data);
    }

    private static SmpsSourceDescriptor describe(
            Kind kind,
            String name,
            String donorGameId,
            AbstractSmpsData data) {
        Objects.requireNonNull(data, "data");
        byte[] bytes = data.getData();
        return new SmpsSourceDescriptor(
                kind,
                data.getId(),
                name,
                donorGameId,
                data.getZ80StartAddress(),
                bytes != null ? bytes.length : 0,
                Arrays.hashCode(bytes),
                data.isPalSpeedupDisabled());
    }

    public boolean matchesData(AbstractSmpsData data) {
        return matches(from(data));
    }

    public boolean matches(SmpsSourceDescriptor other) {
        Objects.requireNonNull(other, "other");
        return id == other.id
                && z80StartAddress == other.z80StartAddress
                && dataLength == other.dataLength
                && dataHash == other.dataHash
                && palSpeedupDisabled == other.palSpeedupDisabled;
    }
}
