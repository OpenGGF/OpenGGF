package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.net.protocol.TrackKey;

/** Engine-free catalogue policy supplied to room admission and timing validation. */
@com.openggf.game.ModApi
public interface TrackRulesProvider {
    boolean legal(TrackKey key, String mode);
    boolean monitorsLegal(ControlMessage.RoundConfig config);
    int clockStartFrame(TrackKey key);
}
