package com.openggf.game.sonic2.competition.view;

import com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.ObjectSlot;

/** Explicit native display fields for owners whose ordinary draw is not one ROM mapping frame. */
public interface Sonic2NativeDisplaySource {
    /** Null means this admitted owner emits no mapping this frame. */
    ObjectSlot nativeDisplaySlot();
}
