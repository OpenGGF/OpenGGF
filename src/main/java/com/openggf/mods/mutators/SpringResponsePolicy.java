package com.openggf.mods.mutators;

/** Changes future qualified spring contacts; admission, direction and nudges stay native. */
@com.openggf.game.ModApi
public record SpringResponsePolicy(int percent) implements MutatorPolicy {
    public SpringResponsePolicy {
        if (percent < 75 || percent > 135) throw new IllegalArgumentException("Spring response must be 75..135 percent");
    }
    public int scale(int nativeStrength, int requiredMinimum, int directionalCap) {
        int magnitude = Math.abs(nativeStrength);
        if (requiredMinimum < 0 || directionalCap < requiredMinimum || directionalCap > 0x2000)
            throw new IllegalArgumentException("Invalid host spring bounds");
        int scaled = Math.max(requiredMinimum, Math.min(directionalCap, magnitude * percent / 100));
        return nativeStrength < 0 ? -scaled : scaled;
    }
    @Override public MutatorCapability capability() { return MutatorCapability.SPRING_RESPONSE; }
}
