package com.openggf.mods.run;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-owned native capture shared by live runs and verifier replay. */
public final class NativePlayerPose {
    private NativePlayerPose() { }

    public static PlayerPose fromArena(com.openggf.game.arena.ArenaPose p) {
        return new PlayerPose(p.centreX(), p.centreY(), p.mappingFrame(), p.hFlip(), p.vFlip(),
                p.priorityBucket(), p.highPriority(), p.hurt(), p.secondaryPath(), p.shoeKind());
    }

    public static PlayerPose capture(AbstractPlayableSprite sprite) {
        return new PlayerPose(sprite.getCentreX(), sprite.getCentreY(), sprite.getMappingFrame(),
                sprite.getRenderHFlip(), sprite.getRenderVFlip(), sprite.getPriorityBucket(),
                sprite.isHighPriority(), sprite.isHurt(),sprite.getTopSolidBit()==0xE,0);
    }
}
