package com.openggf.mods.run;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-owned native capture shared by live runs and verifier replay. */
public final class NativePlayerPose {
    private NativePlayerPose() { }

    public static PlayerPose capture(AbstractPlayableSprite sprite) {
        return new PlayerPose(sprite.getCentreX(), sprite.getCentreY(), sprite.getMappingFrame(),
                sprite.getRenderHFlip(), sprite.getRenderVFlip(), sprite.getPriorityBucket(),
                sprite.isHighPriority(), sprite.isHurt());
    }
}
