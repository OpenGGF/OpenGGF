package com.openggf.game.competition;

import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import com.openggf.game.session.GameplayModeContext;

/** Internal registration and post-roster camera reference repair for native humans. */
public final class NativeCompetitionCameras {
    private static final String SECOND_CAMERA = "native_competition_camera_2";
    private static final String REFERENCES = "native_competition_camera_references";
    private NativeCompetitionCameras() { }

    public static void register(GameplayModeContext context) {
        var registry = context.getRewindRegistry();
        registry.deregister(SECOND_CAMERA);
        registry.deregisterPostRestoreCallback(REFERENCES);
        var roster = NativeCompetitionRoster.from(context.getWorldSession());
        if (roster == null) return;
        if (roster.player(1) != null) {
            registry.register(new RewindSnapshottable<CameraSnapshot>() {
                @Override public String key() { return SECOND_CAMERA; }
                @Override public CameraSnapshot capture() { return roster.camera(1).capture(); }
                @Override public void restore(CameraSnapshot state) {
                    roster.camera(1).restore(state, roster.player(1));
                }
            });
        }
        registry.registerPostRestoreCallback(REFERENCES, () -> {
            // Sprite restore has now relinked the native session. Preserve every
            // captured camera value while replacing only its retired actor reference.
            for (int slot = 0; slot < 2; slot++) {
                var actor = roster.player(slot);
                if (actor != null) roster.camera(slot).restore(roster.camera(slot).capture(), actor);
            }
        });
    }
}
