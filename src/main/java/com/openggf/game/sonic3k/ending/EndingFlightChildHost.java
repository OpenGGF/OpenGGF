package com.openggf.game.sonic3k.ending;

import com.openggf.level.objects.ObjectInstance;

/** Only the native parent3 fields read by the ending flight children. */
interface EndingFlightChildHost extends ObjectInstance {
    boolean flightFlipX();
    boolean flightChildRetired();
    boolean flightChildSmall();
    boolean flightChildHidden();
    int flightFrame();
    boolean flightPowered();
}
