package com.openggf.game.soniccd;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The first row at which each routes-B long route (native {@code rehost-long}) diverges from the engine, a green stage
 * pinned at its row count. {@link TestSonicCdRoutesBLongNative} asserts the pins; the object checks that replay the same routes
 * ({@link TestSonicCdSharedObjectRows}) compare only the rows before them, where the player and camera equal native.
 */
final class CdLongRoutePins {
    static final Map<CdStagePayload, Integer> FIRST_DIVERGENCE = new LinkedHashMap<>();

    static {
        FIRST_DIVERGENCE.put(CdStagePayload.R51A, 4756);
        FIRST_DIVERGENCE.put(CdStagePayload.R51B, 5494);
        FIRST_DIVERGENCE.put(CdStagePayload.R51C, 7151);
        FIRST_DIVERGENCE.put(CdStagePayload.R52A, 4772);
        FIRST_DIVERGENCE.put(CdStagePayload.R61A, 4892);
        FIRST_DIVERGENCE.put(CdStagePayload.R61B, 5449);
        FIRST_DIVERGENCE.put(CdStagePayload.R61C, 6767);
        FIRST_DIVERGENCE.put(CdStagePayload.R62A, 4787);
        FIRST_DIVERGENCE.put(CdStagePayload.R71A, 3977);
        FIRST_DIVERGENCE.put(CdStagePayload.R71B, 4918);
        FIRST_DIVERGENCE.put(CdStagePayload.R71C, 6096);
        FIRST_DIVERGENCE.put(CdStagePayload.R72A, 4399);
        FIRST_DIVERGENCE.put(CdStagePayload.R81A, 4736);
        FIRST_DIVERGENCE.put(CdStagePayload.R81B, 3991);
        FIRST_DIVERGENCE.put(CdStagePayload.R81C, 4524);
        FIRST_DIVERGENCE.put(CdStagePayload.R82A, 4577);
    }

    private CdLongRoutePins() { }
}
