package com.openggf.game.sonic3k.ending;

import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.NativeSstStateSource;

/** Only the native parent3 fields read by the ending flight children. */
interface EndingFlightChildHost extends ObjectInstance, NativeSstStateSource {
    int flightPlayerMode();
    boolean flightFlipX();
    boolean flightChildRetired();
    boolean flightChildSmall();
    boolean flightChildHidden();
    int flightFrame();
    boolean flightPowered();
    /** Leader of the group this sprite crosses a wider picture with; null keeps its native place ({@link EndingWideStage}). */
    EndingFlightChildHost stageLeader();
    /** A leader's own 16.16 native screen X, zero at the native left edge. */
    int stageScreenX();
}
