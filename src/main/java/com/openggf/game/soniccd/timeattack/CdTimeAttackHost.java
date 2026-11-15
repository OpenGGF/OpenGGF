package com.openggf.game.soniccd.timeattack;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.program.CdProgramHost;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Set;

/** The Time Attack menu (TIMEATK, {@code TimeAttack}, IPX.asm:1214-1238) for the native-program host. */
public final class CdTimeAttackHost implements CdProgramHost {
    @Override public Set<CdNativeProgram> programs() { return Set.of(CdNativeProgram.TIMEATK); }

    @Override
    public NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator) {
        return new CdTimeAttackScreen(coordinator);
    }
}
