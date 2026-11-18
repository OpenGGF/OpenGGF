package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.program.CdProgramHost;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Set;

public final class CdStage8ClearHost implements CdProgramHost {
    @Override public Set<CdNativeProgram> programs() { return Set.of(CdNativeProgram.SS8CREDS); }

    @Override public NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator) {
        return new CdStage8ClearScreen();
    }
}
