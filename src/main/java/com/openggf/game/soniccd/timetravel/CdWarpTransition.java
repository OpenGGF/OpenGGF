package com.openggf.game.soniccd.timetravel;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.level.LevelManager;

import java.io.IOException;

/**
 * The stage-to-stage part of a warp: once {@link CdTimeTravel#readyWarp()} is set the source
 * stage exits (Level/Main.asm:407-408 tests {@code levelRestart} after the object pass, then
 * LevelStart's {@code .SkipFade} return, Level/Main.asm:61-80), IPX runs the warp cutscene
 * ({@link CdWarpRequest.Step#WARP_CUTSCENE_NOT_HOSTED}) and loads the destination file
 * ({@code RunLevel .LevelLoop}, CD Initial Program/IPX.asm:551-580). The destination goes
 * through the ordinary level load; the level-init profile applies the carry as
 * {@code LoadTimeWarpData}. The program-flow owner calls this after the cutscene step.
 */
public final class CdWarpTransition {
    private CdWarpTransition() { }

    /** Loads the ready warp's destination and returns it. */
    public static CdStagePayload loadDestination(LevelManager levels, CdTimeTravel tt) throws IOException {
        CdWarpRequest request = tt.readyWarp();
        if (request == null) throw new IllegalStateException("No time warp is ready");
        CdStagePayload destination = request.destination(tt.goodFuture());
        tt.prepareEntry(CdTimeTravel.Entry.WARP_ARRIVAL);
        levels.loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(destination), SonicCdZoneRegistry.actIndex(destination));
        return destination;
    }
}
