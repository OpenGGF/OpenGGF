package com.openggf.game.soniccd.title.secrets;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.program.CdProgramHost;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Set;

/** The sound test, the hidden pictures its codes open, and best staff times, for the native-program host. */
public final class CdSecretsHost implements CdProgramHost {
    @Override
    public Set<CdNativeProgram> programs() {
        return Set.of(CdNativeProgram.SNDTEST, CdNativeProgram.STAFFTIMES, CdNativeProgram.FUNISINF, CdNativeProgram.MCSONIC, CdNativeProgram.TAILS,
                CdNativeProgram.BATMAN, CdNativeProgram.CUTESONIC);
    }

    @Override
    public NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator) {
        return new CdSecretsScreen(coordinator, step.program());
    }
}
