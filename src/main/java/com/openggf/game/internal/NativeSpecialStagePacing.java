package com.openggf.game.internal;

/** Native engine SPI: observes the control owner without supplying gameplay values or creator authority. */
public interface NativeSpecialStagePacing {
    State pacingState();
    NativeSpecialStagePacingOwner pacingOwner();

    record State(boolean interactive, long entryEpoch, long acceptedSampleOrdinal,
                 int player1Held, int player2Held, boolean player2Supported) { }
}
