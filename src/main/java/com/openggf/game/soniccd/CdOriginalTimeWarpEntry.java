package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.CdReservedObjectBank;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerTimeWarp;

/** Cross-package view of the private World-issued native occurrence.
 * The sole permitted implementation retains its private constructor and all
 * original owner/thread/generation checks; this type creates no invocation.
 */
public abstract sealed class CdOriginalTimeWarpEntry
        permits CdWorld.OriginalR11TimeWarpEntry {
    CdOriginalTimeWarpEntry() {}
    public abstract void requireFrame(CdPlayerFrameRuntime frame);
    public abstract void beforeBodyPrefix(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call);
    public abstract void beginNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call);
    public abstract void beforeNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,
                                      CdPlayerTimeWarp.OriginalCall primitive);
    public abstract CdReservedObjectBank.Snapshot reserved(
            CdPlayerFrameRuntime.OriginalR11TimeWarpCall call, CdPlayerTimeWarp.OriginalCall primitive);
    public abstract void store(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,
                               CdPlayerTimeWarp.OriginalCall primitive);
    public abstract int scalar(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive);
    public abstract com.openggf.game.soniccd.objects.CdTimeWarpSaveData.State saved(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive);
    public abstract int source(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive);
    public abstract void activeStore(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,CdPlayerTimeWarp.OriginalCall primitive);
    public abstract void afterNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,
                                     CdPlayerTimeWarp.OriginalCall primitive);
    public abstract void failedNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,
                                      CdPlayerTimeWarp.OriginalCall primitive, Throwable first);
    public abstract void completedNative(CdPlayerFrameRuntime.OriginalR11TimeWarpCall call,
                                         CdPlayerTimeWarp.Result result);
}
