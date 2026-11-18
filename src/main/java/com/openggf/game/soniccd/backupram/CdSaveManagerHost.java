package com.openggf.game.soniccd.backupram;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.program.CdProgramHost;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Set;

public final class CdSaveManagerHost implements CdProgramHost {
    @Override public Set<CdNativeProgram> programs() { return Set.of(CdNativeProgram.BURAMMGR); }

    @Override public NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator) {
        return new CdSaveManagerScreen(coordinator);
    }
}
