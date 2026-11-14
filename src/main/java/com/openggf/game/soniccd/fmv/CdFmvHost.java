package com.openggf.game.soniccd.fmv;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.program.CdProgramHost;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Set;

/** The opening video ({@code OPENING}, IPX {@code Demo}'s first entry) for the native-program host. */
public final class CdFmvHost implements CdProgramHost {
    @Override public Set<CdNativeProgram> programs() { return Set.of(CdNativeProgram.OPENING); }

    @Override
    public NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator) {
        return new CdFmvScreen(coordinator);
    }
}
