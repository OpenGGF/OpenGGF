package com.openggf.game.soniccd.audio;

import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeProgram;
import com.openggf.game.soniccd.program.CdProgramHost;
import com.openggf.game.soniccd.program.CdProgramRequest;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.release.CdRetailReleases;

import java.util.Set;

public final class CdGardenMusicHost implements CdProgramHost {
    @Override public Set<CdNativeProgram> programs() { return Set.of(CdNativeProgram.DAGARDEN); }

    @Override public NativeMenuScreen screen(CdProgramRequest.NotHosted step, CdSceneCoordinator coordinator) {
        var disc = coordinator.disc().assets();
        var planet = CdRetailReleases.find(disc.identity()).filter(CdRetailReleases.USA::equals)
                .map(release -> new CdGardenPlanetArt(disc)).orElse(null);
        return new CdGardenMusicScreen(planet);
    }
}
