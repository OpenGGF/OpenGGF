package com.openggf.game.soniccd.program;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;

import java.util.Set;

/**
 * A program lane's host for native programs that run in the {@code OPTIONS} native-program mode
 * (Time Attack, D.A. Garden, Visual Mode and FMVs, sound test and secrets, backup RAM manager).
 * {@link CdProgramHosts} lists the hosts; a program with no host runs {@link CdNotHostedScreen}.
 * The screen exits {@code TITLE_SCREEN} where IPX returns to {@code .GameLoop}, or {@code LEVEL}
 * where the program starts a stage.
 */
public interface CdProgramHost {
    /** The programs this host runs. */
    Set<CdNativeProgram> programs();

    /** A fresh screen for one visit; {@code step} names the program and its IPX caller. */
    NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator);
}
