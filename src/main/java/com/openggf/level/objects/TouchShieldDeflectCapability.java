package com.openggf.level.objects;

@com.openggf.game.ModApi
public enum TouchShieldDeflectCapability {
    NONE,
    SHIELD_DEFLECT;

    public com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability toCanonical() {
        return com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability.valueOf(name());
    }

    public static TouchShieldDeflectCapability fromCanonical(
            com.openggf.game.profiles.touchresponse.TouchShieldDeflectCapability canonical) {
        return TouchShieldDeflectCapability.valueOf(canonical.name());
    }
}
