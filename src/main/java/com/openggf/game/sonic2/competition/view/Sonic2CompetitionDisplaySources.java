package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic2.competition.Sonic2CompetitionSession;
import com.openggf.game.sonic2.competition.Sonic2CompetitionLevelProfile;
import com.openggf.level.rings.RingNativeDisplayInternalAccess;
import java.util.ArrayList;
import java.util.List;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionDisplayInputs.*;

/** Live semantic inputs. Object admissions are supplied by A2-S's shared ordered display bridge. */
public final class Sonic2CompetitionDisplaySources {
    private final RomByteReader rom;
    public Sonic2CompetitionDisplaySources(RomByteReader rom) { this.rom = rom; }
    public Build current(GameplayModeContext context, Sonic2CompetitionSession session,
                         Sonic2CompetitionLevelProfile profile, List<Admission> objects, Sonic2CompetitionDisplayState displayState) {
        int frame = context.getLevelManager().getFrameCounter();
        var rings = context.getLevelManager().getRingManager();
        var rows = context.getLevelManager().getCurrentLevel().getRings();
        var first = RingNativeDisplayInternalAccess.window(rings, rows, 0, frame);
        var second = RingNativeDisplayInternalAccess.window(rings, rows, 1, frame);
        var admissions = new ArrayList<Admission>();
        int ordinal = 0;
        for (int bucket = 0; bucket < 8; bucket++) {
            for (int slot = 0; slot < 2; slot++) if (session.player(slot).getPriorityBucket() == bucket) {
                var body = Sonic2CompetitionPlayerDisplay.body(rom, session.player(slot), slot);
                if (body != null) admissions.add(new Admission(slot, bucket, ordinal++, body));
            }
            for (var object : objects) if (object.bucket() == bucket)
                admissions.add(new Admission(object.slot(), bucket, ordinal++, object.occupant()));
            // LevelOnly_Object_RAM follows the 128 ordinary SSTs. Shields/stars are
            // actual ObjectManager owners and belong to the supplied bridge, not a second list.
            if (bucket == 2) {
                var tail = Sonic2CompetitionPlayerDisplay.tail(rom, session.player(1));
                if (tail != null) admissions.add(new Admission(128, bucket, ordinal++, tail));
            }
        }
        return new Build(camera(session, 0), camera(session, 1),
                new Hud(context.getCamera().isLevelStarted(), frame & 65535, hud(session, 0), hud(session, 1), session.loserTimeLeft()),
                ringWindow(first, frame), ringWindow(second, frame), first.animationFrame(), admissions,
                displayState.teleportFlag());
    }
    private static Camera camera(Sonic2CompetitionSession session, int slot) {
        var camera = session.camera(slot); return new Camera(camera.getX() & 65535, camera.getY() & 65535);
    }
    private static PlayerHud hud(Sonic2CompetitionSession session, int slot) {
        var state = session.levelState(slot);
        int seconds = state.getElapsedSeconds();
        return new PlayerHud(state.getRings() & 65535, seconds / 60, seconds % 60, state.isTimerPaused() ? 0 : 1);
    }
    private static RingWindow ringWindow(RingNativeDisplayInternalAccess.Window window, int frame) {
        var rows = new ArrayList<Ring>();
        for (var row : window.rows()) {
            int status = 0;
            if (row.collected()) {
                int elapsed = frame - row.sparkleStartFrame();
                int offset = Math.max(0, elapsed) / Math.max(1, window.sparkleFrameDelay());
                status = row.sparkleStartFrame() < 0 || offset >= window.sparkleFrameCount()
                        ? 0x8000 : window.sparkleStartIndex() + offset;
            }
            rows.add(new Ring(row.index(), status, row.x(), row.y()));
        }
        return new RingWindow(rows);
    }
}
