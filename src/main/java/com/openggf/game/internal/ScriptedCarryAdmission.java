package com.openggf.game.internal;

import com.openggf.sprites.playable.AbstractPlayableSprite;

/** Engine-owned participant authority for a native scripted carry; outside the creator API. */
public interface ScriptedCarryAdmission {
    boolean admitsScriptedCarrier(AbstractPlayableSprite carrier, AbstractPlayableSprite leader);
}
