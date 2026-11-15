package com.openggf.game.soniccd.warp;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.program.CdProgramHost;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Set;

/** The time-warp cutscene ({@code WARP}, {@code RunLevel}'s {@code SCMD_WARP}, IPX.asm:555-561) for the native-program host. */
public final class CdWarpCutsceneHost implements CdProgramHost {
    @Override public Set<CdNativeProgram> programs() { return Set.of(CdNativeProgram.WARP); }

    @Override
    public NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator) {
        return new CdWarpCutsceneScreen(coordinator);
    }
}
