package com.openggf.game.soniccd.fmv;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.program.CdProgramHost;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;

import java.util.Set;

/** Runs the opening attract video and the unlocked Visual Mode browser. */
public final class CdFmvHost implements CdProgramHost {
    @Override public Set<CdNativeProgram> programs() { return Set.of(CdNativeProgram.OPENING, CdNativeProgram.VISMODE); }

    @Override
    public NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator) {
        return switch (step.program()) {
            case OPENING -> new CdFmvScreen(coordinator);
            case VISMODE -> new CdVisualModeScreen(coordinator);
            default -> throw new IllegalArgumentException("No video host for " + step.program());
        };
    }
}
