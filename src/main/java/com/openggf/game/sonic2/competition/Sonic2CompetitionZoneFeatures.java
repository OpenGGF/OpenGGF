package com.openggf.game.sonic2.competition;

import com.openggf.data.Rom;
import com.openggf.audio.GameSound;
import com.openggf.game.PlayableEntity;
import com.openggf.game.competition.NativeTouchResponseOrder;
import com.openggf.game.sonic2.Sonic2ZoneFeatureInternalAccess;
import com.openggf.game.sonic2.Sonic2ZoneFeatureProvider;
import com.openggf.game.sonic2.bumpers.CNZBumperDataLoader;
import com.openggf.game.sonic2.bumpers.Sonic2NativeCNZBumpers;

import java.io.IOException;

/** Native dual-cursor bumper consumer retaining the single existing world slot machine. */
final class Sonic2CompetitionZoneFeatures extends Sonic2ZoneFeatureProvider implements NativeTouchResponseOrder {
    private final Sonic2NativeCompetitionHost host;
    private Sonic2NativeCNZBumpers bumpers;

    Sonic2CompetitionZoneFeatures(Sonic2NativeCompetitionHost host) { this.host = host; }

    @Override public void initZoneFeatures(Rom rom, int zone, int act, int cameraX) throws IOException {
        Sonic2ZoneFeatureInternalAccess.initializeNativeCompetitionFeatures(
                this, host.context(), host.session(), rom, zone, act, cameraX);
        bumpers = zone == 12 ? new Sonic2NativeCNZBumpers(
                new CNZBumperDataLoader().loadNativeCompetition(rom, act), host.session(),
                slot -> host.context().getLevelManager().getObjectManager().getObjectServices()
                        .playSfx(GameSound.LARGE_BUMPER)) : null;
        var registry = host.context().getRewindRegistry();
        registry.deregister("sonic2_native_cnz_bumpers");
        if (bumpers != null) registry.register(bumpers.rewindAdapter());
    }

    @Override public boolean owns(PlayableEntity player) {
        return player != null && (player == host.session().player(0) || player == host.session().player(1));
    }

    @Override public void afterRings(PlayableEntity player) {
        // TouchResponse (s2.asm:85174-85185): native rings precede bumpers,
        // which precede object/boss touch. Bumper eligibility is its own gate.
        if (bumpers != null && owns(player))
            bumpers.checkPlayerCollision(player == host.session().player(0) ? 0 : 1);
    }

    void updateNativeCursors() { if (bumpers != null) bumpers.updateCursors(); }
    Sonic2NativeCNZBumpers nativeBumpers() { return bumpers; }
}
