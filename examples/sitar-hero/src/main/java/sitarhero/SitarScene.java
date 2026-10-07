package sitarhero;

import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import com.openggf.mods.scene.*;
import sitarhero.chart.ChartCurator;
import sitarhero.controls.*;
import sitarhero.model.*;
import sitarhero.ui.SitarUi;
import static sitarhero.ui.SitarUi.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Arcade character → role → song → finite performance → results/retry.
 * Debug capture commands: perform:SONG:ROLE:PERFORMER, autoplay, pause, resume,
 * retry, settings, menu. Autoplay is a labelled tool-only performance, never a menu mode.
 */
public final class SitarScene implements ModScene, DebuggableScene {
    public enum Screen { CHARACTERS, ROLES, SONGS, LOADING, PLAY, PAUSED, RESULTS, SETTINGS, CALIBRATION, ERROR }
    private SceneContext context;
    private List<String> games = List.of();
    private List<Roster> roster = List.of();
    private List<SongSpec> songs = List.of();
    private final Map<String, PerformerArt> actors = new HashMap<>();
    private final ControlSettings settings = new ControlSettings();
    private final MappedControls controls = new MappedControls(settings);
    private Screen screen = Screen.CHARACTERS;
    private Screen settingsReturn = Screen.CHARACTERS;
    private int selected;
    private Roster performer = Roster.SONIC;
    private Role role = Role.SITAR;
    private SongSpec song;
    private ScenePreparedMusic prepared;
    private SceneMusicPlayer player;
    private RhythmSession session;
    private SceneBackdrop backdrop;
    private SceneImage foreground;
    private long loadingTick;
    private long underruns;
    private long best;
    private boolean autoplay;
    private boolean settingsDrums;
    private boolean settingsPad;
    private boolean capturingBinding;
    private boolean calibratePending;
    private String notice = "";
    private String error = "";
    private final List<Long> calibration = new ArrayList<>();
    private long lastCalibrationNote = -1;
    private long eofNanos = Long.MIN_VALUE;
    private long pauseNanos;

    @Override public void enter(SceneContext ctx) {
        context = ctx;
        games = ctx.art().availableGames();
        roster = Roster.availablePerformers(games);
        songs = SongCatalog.available(games);
        settings.read(ctx.storage().read("settings.txt").orElse(""));
        if (roster.isEmpty() || songs.isEmpty()) { error = "Supply a supported Sonic ROM"; screen = Screen.ERROR; return; }
        performer = roster.get(0); song = songs.get(0);
        actor(performer);
        stage(song);
        ctx.audio().stopMusic();
    }

    private PerformerArt actor(Roster who) {
        return actors.computeIfAbsent(who.id(), ignored -> new PerformerArt(context.art().rom(who.game(games)), who.id()));
    }
    private void stage(SongSpec source) {
        SceneRomArt rom = context.art().rom(source.game());
        backdrop = rom.zoneBackdrop(source.zone(), source.act());
        foreground = null;
        List<SceneLevelStage> stages = rom.levelStages(source.zone(), source.act(), context.width(), 90, 24);
        if (!stages.isEmpty()) {
            SceneLevelStage stage = stages.get(0);
            foreground = rom.levelForeground(source.zone(), source.act(), stage.x(), stage.floorY() - 180,
                    context.width(), context.height());
        }
    }
    private void load(boolean calibrationMode) {
        stop(); calibratePending = calibrationMode;
        screen = Screen.LOADING; loadingTick = context.ticks(); selected = 0; notice = "";
    }
    private void prepare() {
        try {
            stage(song); actor(performer);
            prepared = context.music().prepare(song.game(), song.musicId(), song.durationFrames());
            Chart chart = ChartCurator.curate(song, role, prepared);
            if (chart.notes().isEmpty()) throw new IllegalStateException("Selected musical part has no chart");
            session = new RhythmSession(chart, role.drums(), prepared.sampleRate());
            player = context.music().start(prepared, ChartCurator.audioParts(song, role, prepared),
                    prepared.sampleRate() * 3);
            controls.reset(context.physicalInput(), role.drums());
            underruns = player.underrunCount();
            best = parseLong(context.storage().read(recordName()).orElse("0"));
            calibration.clear(); lastCalibrationNote = -1;
            eofNanos = Long.MIN_VALUE;
            screen = calibratePending ? Screen.CALIBRATION : Screen.PLAY;
        } catch (RuntimeException failure) {
            stop(); error = "Song preparation failed: " + failure.getMessage(); screen = Screen.ERROR;
        }
    }

    @Override public void update(SceneContext ctx) {
        if (screen == Screen.LOADING) { if (ctx.ticks() > loadingTick + 1) prepare(); return; }
        if (screen == Screen.PLAY) { performance(); return; }
        if (screen == Screen.CALIBRATION) { calibrate(); return; }
        if (screen == Screen.SETTINGS) { settings(); return; }
        if (screen == Screen.PAUSED) {
            menuMove(3);
            if (accept()) {
                if (selected == 0) resume();
                else if (selected == 1) load(false);
                else { stop(); screen = Screen.SONGS; selected = songs.indexOf(song); }
            } else if (context.keyPressed(SceneKeys.ESCAPE)) resume();
            return;
        }
        if (screen == Screen.ERROR) {
            if (accept() || back()) {
                if (roster.isEmpty() || songs.isEmpty()) ctx.exitToMasterTitle();
                else { screen = Screen.CHARACTERS; selected = 0; }
            }
            return;
        }
        if (context.keyPressed(SceneKeys.TAB)) { settingsReturn = screen; screen = Screen.SETTINGS; selected = 0; return; }
        if (screen == Screen.RESULTS) {
            menuMove(3);
            if (accept()) {
                if (selected == 0) load(false);
                else if (selected == 1) { screen = Screen.SONGS; selected = songs.indexOf(song); }
                else { screen = Screen.CHARACTERS; selected = roster.indexOf(performer); }
            } else if (back()) { screen = Screen.SONGS; selected = songs.indexOf(song); }
            return;
        }
        int count = screen == Screen.CHARACTERS ? roster.size() : screen == Screen.ROLES ? Role.values().length : songs.size();
        menuMove(count);
        if (accept()) {
            if (screen == Screen.CHARACTERS) { performer = roster.get(selected); actor(performer); screen = Screen.ROLES; selected = role.ordinal(); }
            else if (screen == Screen.ROLES) { role = Role.values()[selected]; screen = Screen.SONGS; selected = songs.indexOf(song); }
            else { song = songs.get(selected); load(false); }
        } else if (back()) {
            if (screen == Screen.SONGS) { screen = Screen.ROLES; selected = role.ordinal(); }
            else if (screen == Screen.ROLES) { screen = Screen.CHARACTERS; selected = roster.indexOf(performer); }
            else ctx.exitToMasterTitle();
        }
    }

    private void performance() {
        PhysicalInput input = context.physicalInput();
        long now = player.samplePosition();
        if (player.finished() && eofNanos == Long.MIN_VALUE) eofNanos = input.timestampNanos();
        if (eofNanos != Long.MIN_VALUE) now = postrollPosition(input.timestampNanos());
        if (input.droppedEvents() != 0 || player.paused() || player.underrunCount() != underruns) {
            notice = input.droppedEvents() != 0 ? "Input interrupted" : "Audio interrupted";
            pause(); underruns = player.underrunCount(); return;
        }
        if (autoplay) {
            while (session.nextNote() < session.chart().notes().size()) {
                ChartNote note = session.chart().notes().get(session.nextNote());
                if (note.onset() > now) break;
                session.input(note.onset(), note.lanes(), role.drums() ? note.lanes() : 0,
                        !role.drums(), session.starCharge() >= .5, false);
            }
            if (context.keyPressed(SceneKeys.ESCAPE)) { pause(); return; }
        } else {
            for (MappedControls.Frame frame : controls.accept(input)) {
                if (frame.pause()) { pause(); return; }
                long at = (eofNanos != Long.MIN_VALUE && frame.timestamp() >= eofNanos
                        ? postrollPosition(frame.timestamp()) : player.samplePositionAt(frame.timestamp()))
                        - (long) settings.inputOffsetMs() * prepared.sampleRate() / 1000;
                session.input(at, frame.frets(), frame.directPressed(), frame.strum(), frame.power(), frame.whammy() > .2f);
                player.setWhammy(role.drums() ? 0 : frame.whammy());
            }
        }
        session.advance(now - (long) settings.inputOffsetMs() * prepared.sampleRate() / 1000);
        player.setPartAudible(session.partAudible());
        if (session.failed() || player.finished() && session.finished()) finish();
    }

    // A bounded silent judgment tail preserves calibrated late strikes. The ROM audio
    // remains finite; only this final input window uses the same monotonic input clock.
    private long postrollPosition(long timestamp) {
        return prepared.lengthSamples() + Math.max(0, timestamp - eofNanos) * prepared.sampleRate() / 1_000_000_000L;
    }

    private void pause() {
        pauseNanos = context.physicalInput().timestampNanos();
        player.pause(); screen = Screen.PAUSED; selected = 0;
    }
    private void resume() {
        player.resume();
        if (player.paused()) { notice = "Speaker unavailable; restore audio and retry"; return; }
        if (eofNanos != Long.MIN_VALUE) eofNanos += Math.max(0, context.physicalInput().timestampNanos() - pauseNanos);
        controls.reset(context.physicalInput(), role.drums());
        underruns = player.underrunCount(); screen = Screen.PLAY; notice = "";
    }
    private void finish() {
        stop();
        if (!autoplay && !session.failed() && session.score() > best) {
            best = session.score(); context.storage().write(recordName(), Long.toString(best));
        }
        screen = Screen.RESULTS; selected = 0;
    }
    private String recordName() { return song.id() + "." + role.name().toLowerCase(java.util.Locale.ROOT) + ".txt"; }
    private static long parseLong(String text) { try { return Math.max(0, Long.parseLong(text.trim())); } catch (NumberFormatException ignored) { return 0; } }
    private void stop() { if (player != null) { player.stop(); player = null; } }

    private void calibrate() {
        for (PhysicalInputEvent event : context.physicalInput().events()) {
            if (!event.pressed() || !(event.kind() == PhysicalInputEvent.Kind.KEY && event.code() == SceneKeys.SPACE
                    || event.kind() == PhysicalInputEvent.Kind.BUTTON && event.code() == PhysicalGamepad.BUTTON_A)) continue;
            long at = player.samplePositionAt(event.timestampNanos());
            SceneNoteEvent nearest = null;
            for (SceneNoteEvent note : prepared.notes()) {
                if (note.kind() != SceneNoteEvent.Kind.DAC) continue;
                if (nearest == null || Math.abs(note.onsetSamples() - at) < Math.abs(nearest.onsetSamples() - at)) nearest = note;
            }
            if (nearest != null && nearest.onsetSamples() != lastCalibrationNote
                    && Math.abs(at - nearest.onsetSamples()) < prepared.sampleRate() / 4) {
                calibration.add(at - nearest.onsetSamples()); lastCalibrationNote = nearest.onsetSamples();
                if (calibration.size() > 24) calibration.remove(0);
            }
        }
        if (context.keyPressed(SceneKeys.ENTER) && calibration.size() >= 8) {
            List<Long> sorted = calibration.stream().sorted().toList();
            long median = sorted.get(sorted.size() / 2);
            settings.inputOffsetMs((int) (median * 1000 / prepared.sampleRate()));
            context.storage().write("settings.txt", settings.encode());
            stop(); screen = Screen.SETTINGS; selected = 10; notice = "Calibration saved";
        } else if (back() || player.finished()) { stop(); screen = Screen.SETTINGS; selected = 13; }
    }

    private void settings() {
        if (capturingBinding) {
            if (context.keyPressed(SceneKeys.ESCAPE)) { capturingBinding = false; return; }
            for (PhysicalInputEvent event : context.physicalInput().events()) {
                PhysicalBinding binding = PhysicalBinding.capture(event, settingsPad);
                if (binding != null) {
                    settings.bind(settingsDrums, settingsPad, selected, binding); capturingBinding = false;
                    context.storage().write("settings.txt", settings.encode()); return;
                }
            }
            return;
        }
        int previous = selected;
        menuMove(15);
        if (settingsDrums && selected >= 5 && selected <= 7) selected = selected < previous ? 4 : 8;
        if (context.keyPressed(SceneKeys.TAB)) settingsPad = !settingsPad;
        int direction = left() ? -1 : right() ? 1 : 0;
        if (selected == 10 && direction != 0) settings.inputOffsetMs(settings.inputOffsetMs() + direction * 5);
        if (selected == 11 && direction != 0) settings.displayOffsetMs(settings.displayOffsetMs() + direction * 5);
        if (selected == 12 && direction != 0) settings.lefty(!settings.lefty());
        if (accept()) {
            if (selected < 10) capturingBinding = true;
            else if (selected == 12) settings.lefty(!settings.lefty());
            else if (selected == 13) { load(true); return; }
            else if (selected == 14) { settingsDrums = !settingsDrums; }
        }
        if (context.keyPressed(SceneKeys.DELETE) && selected < 10)
            settings.bind(settingsDrums, settingsPad, selected, new PhysicalBinding(settingsPad ? 'B' : 'K', 0, -1, 1));
        if (back()) {
            context.storage().write("settings.txt", settings.encode());
            screen = settingsReturn; selected = 0;
        }
    }

    private boolean padPressed(int button) {
        return context.physicalInput().events().stream().anyMatch(event -> event.kind() == PhysicalInputEvent.Kind.BUTTON
                && event.code() == button && event.pressed());
    }
    private boolean accept() { return context.keyPressed(SceneKeys.ENTER) || padPressed(PhysicalGamepad.BUTTON_A)
            || padPressed(PhysicalGamepad.BUTTON_START); }
    private boolean back() { return context.keyPressed(SceneKeys.ESCAPE) || padPressed(PhysicalGamepad.BUTTON_B); }
    private boolean left() { return context.buttonRepeated(SceneButtons.LEFT) || padPressed(PhysicalGamepad.BUTTON_DPAD_LEFT) || context.keyPressed(SceneKeys.LEFT); }
    private boolean right() { return context.buttonRepeated(SceneButtons.RIGHT) || padPressed(PhysicalGamepad.BUTTON_DPAD_RIGHT) || context.keyPressed(SceneKeys.RIGHT); }
    private void menuMove(int count) {
        if (context.buttonRepeated(SceneButtons.UP) || padPressed(PhysicalGamepad.BUTTON_DPAD_UP) || context.keyPressed(SceneKeys.UP)) selected = Math.floorMod(selected - 1, count);
        if (context.buttonRepeated(SceneButtons.DOWN) || padPressed(PhysicalGamepad.BUTTON_DPAD_DOWN) || context.keyPressed(SceneKeys.DOWN)) selected = (selected + 1) % count;
    }

    @Override public void draw(SceneContext ctx, SceneCanvas c) {
        c.clear(0x143C74);
        if (backdrop != null) c.drawBackdrop(backdrop, Math.max(0, Math.min(backdrop.image().height() - c.height(), 128)), 0, ctx.ticks());
        if (foreground != null) c.draw(foreground, 0, 0);
        if (screen == Screen.PLAY || screen == Screen.PAUSED) { drawPerformance(c); if (screen == Screen.PAUSED) drawPause(c); return; }
        if (screen == Screen.CALIBRATION) { drawCalibration(c); return; }
        int step = screen == Screen.CHARACTERS ? 1 : screen == Screen.ROLES ? 2 : screen == Screen.SONGS ? 3 : 0;
        SitarUi.page(c, step);
        if (screen == Screen.SETTINGS) { drawSettings(c); return; }
        if (screen == Screen.LOADING) {
            SitarUi.panel(c, 24, 52, c.width() - 48, 136);
            center(c, "GET READY", 63, GOLD);
            SitarUi.center(c, song.label().toUpperCase(java.util.Locale.ROOT), 24, 88, c.width() - 48, CREAM);
            SitarUi.center(c, performer.label() + " / " + shortRole(), 24, 103, c.width() - 48, CYAN);
            actor(performer).draw(c, c.width() / 2, 147, ctx.ticks(), shortRole(), true);
            SitarUi.footer(c, "Preparing your ROM music and arcade chart", "A three-second count-in starts the performance");
            return;
        }
        if (screen == Screen.ERROR) {
            SitarUi.panel(c, 12, 52, c.width() - 24, 136);
            SitarUi.label(c, "UNABLE TO START", 24, 62, c.width() - 48, GOLD);
            SitarUi.wrapped(c, error, 24, 86, c.width() - 48, CREAM);
            SitarUi.footer(c, "Your selection is preserved", "ENTER / A: RETURN"); return;
        }
        if (screen == Screen.RESULTS) { drawResults(c); return; }
        drawChoices(c);
    }

    private void drawChoices(SceneCanvas c) {
        String heading = screen == Screen.CHARACTERS ? "CHOOSE YOUR PERFORMER" : screen == Screen.ROLES ? "CHOOSE YOUR INSTRUMENT" : "CHOOSE YOUR SONG";
        SitarUi.text(c, heading, 12, 42, GOLD);
        int paneWidth = c.width() - 156;
        SitarUi.panel(c, 12, 55, paneWidth, 136);
        int x = 18, width = paneWidth - 12;
        if (screen == Screen.CHARACTERS) {
            for (int i = 0; i < roster.size(); i++) SitarUi.choice(c, roster.get(i).label(), x, 60 + i * 18, width, 17, selected == i);
            SitarUi.footer(c, "ARROWS / DPAD: CHOOSE    TAB: CONTROLS", "ENTER / A: SELECT    ESC / B: MASTER TITLE");
        } else if (screen == Screen.ROLES) {
            for (int i = 0; i < Role.values().length; i++) SitarUi.choice(c, Role.values()[i].label(), x, 64 + i * 30, width, 25, selected == i);
            SitarUi.footer(c, "ARROWS / DPAD: CHOOSE    TAB: CONTROLS", "ENTER / A: SELECT    ESC / B: PERFORMER");
        } else {
            for (int i = 0; i < songs.size(); i++) {
                SongSpec choice = songs.get(i); int y = 60 + i * 42;
                SitarUi.choice(c, choice.label(), x, y, width, 35, selected == i);
                SitarUi.text(c, choice.game().toUpperCase(java.util.Locale.ROOT) + "  /  " + choice.durationFrames() / 60 + " SEC  /  ARCADE", x + 21, y + 26, selected == i ? CYAN : DIM);
            }
            SitarUi.footer(c, performer.label() + " / " + shortRole() + "    TAB: CONTROLS", "ENTER / A: PLAY    ESC / B: INSTRUMENT");
        }
        Roster preview = screen == Screen.CHARACTERS ? roster.get(selected) : performer;
        Role previewRole = screen == Screen.ROLES ? Role.values()[selected] : role;
        int previewX = c.width() - 140;
        SitarUi.panel(c, previewX, 55, 128, 136);
        SitarUi.center(c, "ON STAGE", previewX, 63, 128, CYAN);
        SitarUi.center(c, preview.label(), previewX, 77, 128, CREAM);
        c.fill(previewX + 16, 148, 96, 3, GOLD);
        c.fill(previewX + 16, 151, 96, 4, 0xFF941B2F);
        actor(preview).draw(c, previewX + 64, 121, context.ticks(), previewRole.label().split(" / ")[0], screen != Screen.CHARACTERS);
        if (screen == Screen.CHARACTERS) {
            SitarUi.center(c, preview.game(games).equals("s3k") ? "SONIC 3 & KNUCKLES" : "SONIC " + preview.game(games).substring(1), previewX, 164, 128, DIM);
            SitarUi.center(c, "CHOOSE YOUR STYLE", previewX, 178, 128, CREAM);
        } else {
            SitarUi.center(c, previewRole.drums() ? "4 PADS + KICK" : "5 FRETS + STRUM", previewX, 163, 128, GOLD);
            SitarUi.center(c, previewRole.drums() ? "DIRECT HITS" : "SUSTAINS / HOPO", previewX, 178, 128, DIM);
        }
    }

    private void drawPerformance(SceneCanvas c) {
        int mid = c.width() / 2, top = 50, bottom = 190;
        int laneCount = role.drums() ? 4 : 5;
        for (int y = top; y <= bottom; y++) {
            int width = 64 + (y - top) * 144 / (bottom - top);
            c.fill(mid - width / 2 - 2, y, width + 4, 1, session.starActive() ? 0xFF75E9FF : 0xFFBCD7DC);
            c.fill(mid - width / 2, y, width, 1, session.starActive() ? 0xED153F6C : 0xEE111B32);
            for (int lane = 1; lane < laneCount; lane++) c.fill(mid - width / 2 + lane * width / laneCount, y, 1, 1, 0x664B719A);
        }
        long position = player != null ? player.samplePosition() : session.position();
        long display = position + (long) settings.displayOffsetMs() * prepared.sampleRate() / 1000;
        long lookahead = prepared.sampleRate() * 2L;
        long beat = session.chart().samplesPerBeat();
        for (long line = Math.max(0, display / beat) * beat; line <= display + lookahead; line += beat) {
            int y = noteY(line - display, lookahead); if (y < top || y > bottom) continue;
            int width = highwayWidth(y); c.fill(mid - width / 2, y, width, 1, 0x5548729A);
        }
        List<ChartNote> notes = session.chart().notes();
        int start = Math.max(0, session.nextNote() - 3);
        for (int index = start; index < notes.size(); index++) {
            ChartNote note = notes.get(index); if (note.onset() > display + lookahead) break;
            if (note.end() < display - prepared.sampleRate() / 5 || session.status(index) == 2) continue;
            int y = noteY(note.onset() - display, lookahead);
            if (note.end() > note.onset()) {
                int endY = noteY(note.end() - display, lookahead);
                for (int tailY = Math.max(top, endY); tailY < Math.min(bottom, y); tailY += 2)
                    for (int lane = 0; lane < laneCount; lane++) if ((note.lanes() & 1 << lane) != 0)
                        c.fill(laneX(mid, tailY, lane, laneCount) - 1, tailY, 3, 2, laneColor(lane));
            }
            if (session.status(index) == 1 || y < top || y > bottom + 12) continue;
            if (role.drums() && (note.lanes() & 16) != 0) {
                int width = highwayWidth(y); c.fill(mid - width / 2 + 2, y - 2, width - 4, 5, 0xFFFFC648);
                c.fill(mid - width / 2 + 4, y - 1, width - 8, 1, 0xFFFFF0AE);
            }
            for (int lane = 0; lane < laneCount; lane++) if ((note.lanes() & 1 << lane) != 0)
                gem(c, laneX(mid, y, lane, laneCount), y, 3 + (y - top) * 4 / (bottom - top), laneColor(lane), note.hopo(), note.phrase() >= 0);
        }
        for (int lane = 0; lane < laneCount; lane++) gem(c, laneX(mid, bottom, lane, laneCount), bottom, 7,
                (session.held() & 1 << lane) != 0 ? 0xFFFFFFFF : laneColor(lane), false, false);
        c.fill(0, 0, c.width(), 35, 0xF00B2457); c.fill(0, 34, c.width(), 1, GOLD);
        SitarUi.label(c, song.label(), 8, 5, c.width() - 120, GOLD);
        SitarUi.text(c, shortRole().toUpperCase(java.util.Locale.ROOT) + " / " + song.game().toUpperCase(java.util.Locale.ROOT), 8, 23, CREAM);
        String score = Long.toString(session.score());
        c.text(score, c.width() - 8 - c.textWidth(score), 5, CREAM);
        SitarUi.text(c, "X" + session.multiplier() + "  STREAK " + session.streak(), c.width() - 105, 23, CYAN);
        SitarUi.panel(c, 7, 46, 73, 103);
        SitarUi.text(c, "ROCK", 14, 55, CREAM);
        meter(c, 13, 69, 60, session.rock(), session.rock() < .25 ? 0xFFFF5969 : 0xFF6CDA66);
        SitarUi.text(c, "STAR POWER", 14, 91, CREAM);
        meter(c, 13, 105, 60, session.starCharge(), CYAN);
        SitarUi.text(c, session.starActive() ? "ACTIVE!" : session.starCharge() >= .5 ? "READY!" : "BUILD IT", 14, 128, session.starCharge() >= .5 ? GOLD : DIM);
        int actorX = c.width() - 83;
        SitarUi.panel(c, actorX, 57, 76, 134);
        SitarUi.center(c, "ON STAGE", actorX, 66, 76, CYAN);
        c.fill(actorX + 7, 157, 62, 2, GOLD);
        actor(performer).draw(c, actorX + 38, 133, context.ticks(), shortRole(), true);
        SitarUi.wrapped(c, performer.label(), actorX + 6, 169, 65, CREAM);
        if (position < 0) center(c, Integer.toString((int) ((-position + prepared.sampleRate() - 1) / prepared.sampleRate())), 111, GOLD);
        if (autoplay) SitarUi.text(c, "DEMO", 14, 160, CYAN);
        c.fill(0, 201, c.width(), 23, 0xE5030B22);
        if (role.drums()) {
            c.fill(mid - 95, 203, 190, 6, (session.held() & 16) != 0 ? CREAM : 0xFFCE9430);
            SitarUi.center(c, "KICK", 0, 214, c.width(), GOLD);
        } else {
            String frets = ""; for (int i = 0; i < 5; i++) frets += settings.binding(false, false, i).label() + " ";
            SitarUi.center(c, frets.trim(), 0, 212, c.width(), DIM);
        }
    }
    private static void meter(SceneCanvas c, int x, int y, int width, double value, int color) {
        c.fill(x, y, width, 9, INK); SitarUi.frame(c, x, y, width, 9, DIM);
        c.fill(x + 2, y + 2, (int) ((width - 4) * value), 5, color);
    }

    private int highwayWidth(int y) { return 64 + (y - 50) * 144 / 140; }
    private int laneX(int mid, int y, int lane, int count) {
        if (settings.lefty()) lane = count - 1 - lane;
        int width = highwayWidth(y); return mid - width / 2 + (lane * 2 + 1) * width / (count * 2);
    }
    private static int noteY(long distance, long ahead) {
        double t = 1 - distance / (double) ahead;
        return (int) Math.round(50 + 140 * t * Math.abs(t));
    }
    private static int laneColor(int lane) {
        return switch (lane) { case 0 -> 0xFF51DC61; case 1 -> 0xFFF85765; case 2 -> 0xFFFFD544; case 3 -> 0xFF609CFF; default -> 0xFFFF934B; };
    }
    private static void gem(SceneCanvas c, int x, int y, int r, int color, boolean hopo, boolean star) {
        c.fill(x - r, y - r + 2, r * 2 + 1, r * 2 - 2, 0xFF061226);
        c.fill(x - r + 1, y - r + 1, r * 2 - 1, r * 2 - 1, color);
        c.fill(x - r + 2, y - r, r * 2 - 3, 2, 0xFFFFF3BE);
        c.fill(x - r + 2, y + r - 2, r * 2 - 3, 2, 0xFF242C51);
        if (hopo) c.fill(x - 1, y - 2, 3, 3, 0xFFFFFFFF);
        if (star) { c.fill(x - 3, y - 1, 7, 1, 0xFFFFFFFF); c.fill(x, y - 4, 1, 7, 0xFFFFFFFF); }
    }
    private void drawPause(SceneCanvas c) {
        c.fill(0, 0, c.width(), 224, 0xB8000B28);
        int x = (c.width() - 248) / 2;
        SitarUi.panel(c, x, 48, 248, 144);
        title(c, "PAUSED", 59);
        for (int i = 0; i < 3; i++) SitarUi.choice(c, List.of("Resume", "Retry", "Song select").get(i), x + 10, 83 + i * 26, 228, 22, selected == i);
        SitarUi.wrapped(c, notice, x + 12, 166, 224, GOLD);
        SitarUi.footer(c, "Performance clock and score are paused", "ENTER / A: SELECT    ESC: RESUME");
    }
    private void drawResults(SceneCanvas c) {
        int width = c.width() - 156;
        SitarUi.text(c, session.failed() ? "PERFORMANCE FAILED" : "STAGE CLEAR!", 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, width, 136);
        SitarUi.text(c, SitarUi.fit(song.label().toUpperCase(java.util.Locale.ROOT) + " / " + shortRole().toUpperCase(java.util.Locale.ROOT), width - 20), 22, 63, CREAM);
        SitarUi.label(c, "SCORE " + session.score(), 22, 78, width - 20, GOLD);
        SitarUi.text(c, "BEST " + best + "    HIT " + session.hits() + "/" + session.chart().notes().size(), 22, 98, CYAN);
        SitarUi.text(c, "BEST STREAK " + session.bestStreak(), 22, 111, DIM);
        for (int i = 0; i < 3; i++) SitarUi.choice(c, List.of("Retry", "Choose song", "Choose performer").get(i), 18, 126 + i * 20, width - 12, 19, selected == i);
        int x = c.width() - 140;
        SitarUi.panel(c, x, 55, 128, 136);
        SitarUi.center(c, performer.label(), x, 66, 128, CREAM);
        actor(performer).draw(c, x + 64, 129, context.ticks(), shortRole(), false);
        SitarUi.center(c, session.failed() ? "TRY AGAIN!" : "GREAT SHOW!", x, 164, 128, GOLD);
        SitarUi.footer(c, autoplay ? "DEMO / RECORD NOT SAVED" : "Best scores are kept for each song and instrument", "ENTER / A: SELECT    ESC / B: SONG SELECT");
    }
    private void drawSettings(SceneCanvas c) {
        SitarUi.text(c, "CONTROLS / " + (settingsDrums ? "BONGOS" : "FRETS") + " / " + (settingsPad ? "GAMEPAD" : "KEYBOARD"), 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < 15; i++) if (!settingsDrums || i < 5 || i > 7) rows.add(i);
        int first = Math.max(0, Math.min(rows.size() - 8, rows.indexOf(selected) - 3));
        for (int row = first; row < Math.min(rows.size(), first + 8); row++) {
            int i = rows.get(row), y = 60 + (row - first) * 16;
            if (selected == i) {
                c.fill(18, y, c.width() - 36, 15, 0xFF173B6C);
                SitarUi.frame(c, 18, y, c.width() - 36, 15, CYAN);
                c.fill(19, y + 1, 3, 13, GOLD);
            }
            String name = i < 10 ? settings.actionName(i, settingsDrums)
                    : switch (i) { case 10 -> "INPUT OFFSET"; case 11 -> "DISPLAY OFFSET";
                        case 12 -> "LEFTY FLIP"; case 13 -> "TAP CALIBRATION"; default -> "SWITCH INSTRUMENT PROFILE"; };
            String value = i < 10 ? settings.binding(settingsDrums, settingsPad, i).label()
                    : switch (i) { case 10 -> settings.inputOffsetMs() + " MS"; case 11 -> settings.displayOffsetMs() + " MS";
                        case 12 -> settings.lefty() ? "ON" : "OFF"; default -> ">"; };
            SitarUi.text(c, name, 29, y + 4, selected == i ? GOLD : CREAM);
            SitarUi.text(c, value, c.width() - 28 - SitarUi.width(value, 1), y + 4, CYAN);
        }
        SitarUi.footer(c, capturingBinding ? "Press a key, button or move an axis / ESC cancels" : "ENTER / A: REMAP    TAB: DEVICE    LEFT / RIGHT: ADJUST",
                capturingBinding ? "Waiting for your new binding..." : "DELETE: UNBIND    ESC / B: SAVE AND RETURN");
    }
    private void drawCalibration(SceneCanvas c) {
        SitarUi.page(c, 0);
        SitarUi.text(c, "TAP CALIBRATION", 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        SitarUi.wrapped(c, "Listen to the ROM drum hits. Tap SPACE / PAD A on the beat. Use at least 8 taps. The median sets your input offset.", 24, 66, c.width() - 48, CREAM);
        center(c, calibration.size() + " TAPS", 111, GOLD);
        int fill = Math.min(8, calibration.size());
        for (int i = 0; i < 8; i++) {
            int x = c.width() / 2 - 77 + i * 20;
            c.fill(x, 137, 14, 8, i < fill ? CYAN : INK);
            SitarUi.frame(c, x, 137, 14, 8, DIM);
        }
        SitarUi.center(c, calibration.size() >= 8 ? "READY TO SAVE" : "KEEP TAPPING", 0, 165, c.width(), CYAN);
        SitarUi.footer(c, "SPACE / PAD A: TAP WITH THE DRUMS", "ENTER: SAVE OFFSET AFTER 8 TAPS    ESC / B: CANCEL");
    }
    private String shortRole() { return role.label().split(" / ")[0]; }
    private static void title(SceneCanvas c, String text, int y) {
        center(c, text, y + 2, INK); center(c, text, y, GOLD);
    }
    private static void center(SceneCanvas c, String text, int y, int color) { c.text(text, (c.width() - c.textWidth(text)) / 2, y, color); }

    @Override public void exit(SceneContext ctx) { stop(); ctx.storage().write("settings.txt", settings.encode()); }
    public String screen() { return screen.name(); }
    public long score() { return session == null ? 0 : session.score(); }
    public int hits() { return session == null ? 0 : session.hits(); }
    public int chartNotes() { return session == null ? 0 : session.chart().notes().size(); }
    public String performerId() { return performer.id(); }
    public List<String> availableSongs() { return songs.stream().map(SongSpec::id).toList(); }
    public List<String> availablePerformers() { return roster.stream().map(Roster::id).toList(); }
    public String error() { return error; }

    @Override public boolean debugJump(String command) {
        if (context == null) return false;
        if (command.startsWith("perform:")) {
            String[] fields = command.split(":"); if (fields.length != 4) return false;
            SongSpec source = songs.stream().filter(s -> s.id().equals(fields[1])).findFirst().orElse(null);
            Roster who = roster.stream().filter(p -> p.id().equals(fields[3])).findFirst().orElse(null);
            Role chosen;
            try { chosen = Role.valueOf(fields[2].toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException ignored) { return false; }
            if (source == null || who == null) return false;
            song = source; role = chosen; performer = who; load(false); return true;
        }
        return switch (command) {
            case "autoplay" -> { autoplay = true; yield true; }
            case "pause" -> { if (player == null) yield false; pause(); yield true; }
            case "resume" -> { if (screen != Screen.PAUSED) yield false; resume(); yield true; }
            case "retry" -> { load(false); yield true; }
            case "settings" -> { stop(); settingsReturn = Screen.CHARACTERS; screen = Screen.SETTINGS; selected = 0; yield true; }
            case "menu" -> { stop(); screen = Screen.CHARACTERS; selected = 0; autoplay = false; yield true; }
            default -> false;
        };
    }
}
