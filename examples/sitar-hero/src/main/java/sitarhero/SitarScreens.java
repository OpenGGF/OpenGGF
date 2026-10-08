package sitarhero;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSpriteSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import sitarhero.SitarScene.Mode;
import sitarhero.SitarScene.Screen;
import sitarhero.controls.ControlSettings;
import sitarhero.model.*;
import sitarhero.net.OnlineMatch;
import sitarhero.stage.ConcertStage;
import sitarhero.stage.PerformerArt;
import sitarhero.story.CareerStory;
import sitarhero.ui.Motion;
import sitarhero.ui.SitarUi;
import static sitarhero.ui.SitarUi.*;

/**
 * The pictures half of the scene: draws {@link SitarScene}'s state and never changes it.
 *
 * <p>Every screen stands on the current venue ({@link ConcertStage}): the act the song or
 * world comes from. A screen's content eases in through a {@link Motion.ShiftedCanvas} for
 * {@link Motion#ENTER_TICKS} after it opens, sliding from the side the player came from;
 * footers with the controls are drawn outside it, so they are readable from the first frame.
 */
final class SitarScreens {
    /** The story dialogue box, shared with the scene's mouse hit-testing. */
    static final int STORY_TOP = 40, STORY_HEIGHT = 62;
    private final SitarScene s;

    SitarScreens(SitarScene scene) { s = scene; }

    void draw(SceneCanvas base, long now) {
        if (s.screen == Screen.TITLE && s.title != null) { drawTitle(base, now); return; }
        drawVenue(base, now);
        if (s.screen == Screen.PLAY || s.screen == Screen.PAUSED) {
            drawPerformance(base, now);
            if (s.screen == Screen.PAUSED) drawPause(base, now);
            else if (s.previousScreen == Screen.LOADING) titleCard(base, now, true);
            return;
        }
        if (s.screen == Screen.STORY) { drawStory(base, now); return; }
        if (s.screen == Screen.LOADING) { drawLoading(base, now); return; }
        if (s.screen == Screen.CALIBRATION) { SitarUi.page(base, 0); drawCalibration(base, base); return; }
        int step = s.screen == Screen.CHARACTERS ? 1 : s.screen == Screen.ROLES ? 2 : s.screen == Screen.DIFFICULTY ? 3
                : s.screen == Screen.SONGS ? 4 : 0;
        SitarUi.page(base, step, s.mode == Mode.CAREER ? "TOUR" : "ARCADE");
        SceneCanvas c = entering(base, now);
        switch (s.screen) {
            case TITLE, ERROR -> drawError(c, base);
            case RECORDS -> drawRecords(c, base);
            case HELP -> drawHelp(c, base);
            case TOURS, WORLDS -> drawCareerBoard(c, base, now);
            case ONLINE, LOBBY -> drawOnline(c, base);
            case SETTINGS -> drawSettings(c, base);
            case RESULTS -> drawResults(c, base, now);
            default -> drawChoices(c, base, now);
        }
    }

    /** Ticks for one venue to dissolve into the next. */
    private static final int VENUE_FADE = 16;

    /** The current venue, dissolving in over the previous one after a change. */
    private void drawVenue(SceneCanvas base, long now) {
        ConcertStage current = s.backdropStage();
        if (current == null) { base.clear(0x143C74); return; }
        double t = current == s.shownStage && s.fadingStage != null
                ? Motion.progress(now, s.stageTick, VENUE_FADE) : 1;
        if (t < 1) {
            s.fadingStage.draw(base, now, sway(s.fadingStage, now));
            current.draw(new Motion.ShiftedCanvas(base, 0, t), now, sway(current, now));
        } else current.draw(base, now, sway(current, now));
    }
    private int sway(ConcertStage stage, long now) {
        return s.title != null && stage == s.title.stage() ? s.title.sway(now) : 0;
    }

    /** The screen's content canvas: shifted and faded in for its first few ticks. */
    private SceneCanvas entering(SceneCanvas base, long now) {
        double t = Motion.easeOut(Motion.progress(now, s.screenTick, Motion.ENTER_TICKS));
        if (t >= 1) return base;
        int direction = s.enteredBack ? -1 : 1;
        return new Motion.ShiftedCanvas(base, (int) Math.round(direction * 14 * (1 - t)), 0.25 + 0.75 * t);
    }

    // ---------------------------------------------------------------- title

    private void drawTitle(SceneCanvas base, long now) {
        drawVenue(base, now);
        s.title.drawCast(base, now, s.reducedFlashes());
        // One card on the left holds the wordmark and the menu; the stage keeps the right.
        List<String> items = s.titleItems();
        int x = SitarScene.TITLE_X, top = SitarScene.TITLE_TOP, row = SitarScene.TITLE_ROW, width = SitarScene.TITLE_WIDTH;
        int cardBottom = top + items.size() * row + 4;
        base.fill(x - 6, 4, width + 18, cardBottom - 4, 0xC80A1A3C);
        SitarUi.frame(base, x - 6, 4, width + 18, cardBottom - 4, 0xFF365E95);
        // The first visit drops the wordmark in; later visits fade the menu back in.
        double drop = s.titleVisits == 0 ? Motion.easeOut(Motion.progress(now, 0, 24)) : 1;
        SitarUi.logo(base, x, Motion.lerp(-30, 11, drop), 3);
        SceneCanvas c = s.titleVisits == 0 ? base : entering(base, now);
        SitarUi.text(c, "WORLD TOUR", x + 2, 38, CYAN);
        SitarUi.text(c, s.songs.size() + " SONGS FROM YOUR ROMS", x + 68, 38, DIM);
        c.fill(x, top - 6, width, 1, 0xFF365E95);
        SitarUi.highlight(c, x, top + glide(now) * row / 16, width, row - 1);
        for (int i = 0; i < items.size(); i++)
            SitarUi.choiceLabel(c, items.get(i), x - 6, top + i * row, width, row - 1, s.selected == i);
        SitarUi.footer(base, s.titleHint(s.selected), "ARROWS / MOUSE: CHOOSE   ENTER / A: SELECT   ESC / B: EXIT");
    }

    /**
     * The highlight's position in sixteenths of a row, gliding towards the selection. A
     * selection changed outside {@code update} (a debug jump) shows in place immediately.
     */
    private int glide(long now) {
        return s.cursor.target() == s.selected * 16 ? s.cursor.at(now) : s.selected * 16;
    }

    /** A gliding bar's offset, held inside the visible rows while a long list scrolls. */
    private static int bar(int offset, int rows, int rowHeight) {
        return Math.max(0, Math.min((rows - 1) * rowHeight, offset));
    }

    // ---------------------------------------------------------------- selection lists

    private void drawList(SceneCanvas c, List<String> labels, int visible, long now) {
        int first = s.listFirst(labels.size(), visible), rowHeight = 126 / visible;
        int width = c.width() - 168;
        int bar = bar(glide(now) * rowHeight / 16 - first * rowHeight, Math.min(visible, labels.size()), rowHeight);
        SitarUi.highlight(c, 18, 60 + bar, width, rowHeight - 1);
        for (int row = 0; row < Math.min(visible, labels.size()); row++)
            SitarUi.choiceLabel(c, labels.get(first + row), 18, 60 + row * rowHeight, width, rowHeight - 1, s.selected == first + row);
        if (labels.size() > visible) SitarUi.text(c, (s.selected + 1) + "/" + labels.size(), c.width() - 188, 181, DIM);
    }

    private void drawChoices(SceneCanvas c, SceneCanvas base, long now) {
        Screen screen = s.screen;
        String heading = screen == Screen.CHARACTERS ? "CHOOSE PLAYER " + (s.choosingSecond ? "2" : "1") + " PERFORMER"
                : screen == Screen.ROLES ? "CHOOSE YOUR INSTRUMENT" : screen == Screen.DIFFICULTY ? "CHOOSE YOUR DIFFICULTY" : "CHOOSE YOUR SONG";
        SitarUi.text(c, heading, 12, 42, GOLD);
        int paneWidth = c.width() - 156;
        SitarUi.panel(c, 12, 55, paneWidth, 136);
        if (screen == Screen.CHARACTERS) drawList(c, s.roster.stream().map(Roster::label).toList(), 7, now);
        else if (screen == Screen.ROLES) drawList(c, java.util.Arrays.stream(Role.values()).map(Role::label).toList(), 4, now);
        else if (screen == Screen.DIFFICULTY) drawList(c, java.util.Arrays.stream(Difficulty.values()).map(Difficulty::label).toList(), 4, now);
        else {
            List<SongSpec> choices = s.setlistSongs();
            int first = s.listFirst(choices.size(), 4);
            if (!choices.isEmpty())
                SitarUi.highlight(c, 18, 60 + bar(glide(now) * 31 / 16 - first * 31, Math.min(4, choices.size()), 31), paneWidth - 12, 29);
            for (int row = 0; row < Math.min(4, choices.size()); row++) {
                int i = first + row; SongSpec choice = choices.get(i); int y = 60 + row * 31;
                boolean supported = choice.availableRoles().contains(s.role);
                SitarUi.choiceLabel(c, !supported ? "CHANGE PART / " + choice.label() : choice.label(), 18, y, paneWidth - 12, 29, s.selected == i);
                String detail = choice.game().toUpperCase(Locale.ROOT) + " / " + choice.durationFrames() / 60 + " SEC";
                if (s.mode == Mode.CAREER) detail = (s.careerWorld.requiredSongIds().contains(choice.id()) ? "MAIN ACT" : "SIDE GIG")
                        + (s.career.cleared(choice.id()) ? " / CLEARED" : "") + " / " + choice.durationFrames() / 60 + " SEC";
                SitarUi.text(c, SitarUi.fit(detail, paneWidth - 36), 39, y + 21, DIM);
            }
            if (choices.size() > 4) SitarUi.text(c, (s.selected + 1) + "/" + choices.size(), 18, 184, CYAN);
        }
        Roster preview = screen == Screen.CHARACTERS ? s.roster.get(s.selected) : s.performer;
        Role previewRole = screen == Screen.ROLES ? Role.values()[s.selected] : s.role;
        int previewX = c.width() - 140;
        SitarUi.panel(c, previewX, 55, 128, 136);
        SitarUi.center(c, s.modeLabel(), previewX, 63, 128, CYAN);
        SitarUi.center(c, SitarUi.fit(preview.label(), 116), previewX, 78, 128, CREAM);
        // A newly highlighted performer or instrument settles onto the stage line.
        int drop = screen == Screen.CHARACTERS || screen == Screen.ROLES
                ? (int) Math.round(-6 * (1 - Motion.easeOut(s.cursor.settled(now, 8)))) : 0;
        standOnLine(c, s.actor(preview), previewX + 64, 142, now, previewRole, drop);
        SitarUi.center(c, previewRole.drums() ? "4 PADS + KICK" : s.difficulty.lanes() + " FRETS + STRUM", previewX, 160, 128, GOLD);
        if (screen == Screen.SONGS && s.mode == Mode.CAREER) {
            SitarUi.frame(c, previewX + 8, 171, 112, 18, CYAN);
            SitarUi.center(c, "PART & LEVEL", previewX, 177, 128, CREAM);
        } else SitarUi.center(c, s.difficulty.label(), previewX, 178, 128, DIM);
        SitarUi.footer(base, s.notice.isEmpty() ? s.performer.label() + " / " + s.shortRole() + " / " + s.difficulty.label() : s.notice,
                screen == Screen.SONGS && s.mode == Mode.CAREER ? "ENTER / A: PLAY / I / X: PART & LEVEL / ESC / B: WORLD BOARD" : "ENTER / A: SELECT / TAB: SETTINGS / ESC / B: BACK");
    }

    /** A preview performer standing on a gold stage line at row {@code lineY}. */
    private void standOnLine(SceneCanvas c, PerformerArt art, int x, int lineY, long now, Role role) {
        standOnLine(c, art, x, lineY, now, role, 0);
    }
    private void standOnLine(SceneCanvas c, PerformerArt art, int x, int lineY, long now, Role role, int lift) {
        c.fill(x - 48, lineY, 96, 3, GOLD);
        art.draw(c, x, lineY - art.footOffset() + (art.hovers() ? -6 : 0) + lift, now, role, false);
    }

    // ---------------------------------------------------------------- career

    private void drawCareerBoard(SceneCanvas c, SceneCanvas base, long now) {
        boolean tourMenu = s.screen == Screen.TOURS;
        SitarUi.text(c, tourMenu ? "CHOOSE YOUR JOURNEY" : SitarUi.fit(s.careerTour.title(), c.width() - 24), 12, 42, GOLD);
        int pane = c.width() - 156, x = c.width() - 140;
        SitarUi.panel(c, 12, 55, pane, 136); SitarUi.panel(c, x, 55, 128, 136);
        if (tourMenu) {
            SitarUi.highlight(c, 18, 60 + bar(glide(now) * 40 / 16, s.tours.size(), 40), pane - 12, 36);
            for (int i = 0; i < s.tours.size(); i++) {
                CareerTour tour = s.tours.get(i);
                SitarUi.choiceLabel(c, tour.title(), 18, 60 + i * 40, pane - 12, 36, s.selected == i);
                SitarUi.text(c, s.career.cleared(tour) + " MAIN ACTS / " + (s.career.complete(tour) ? "COMPLETE" : tour.worlds().size() + " WORLDS"), 39, 86 + i * 40, DIM);
            }
            CareerTour preview = s.tours.get(s.selected);
            SitarUi.center(c, preview.game().toUpperCase(Locale.ROOT), x, 65, 128, CYAN);
            standOnLine(c, s.actor(s.performer), x + 64, 132, now, s.role);
            SitarUi.wrapped(c, preview.subtitle(), x + 10, 158, 108, CREAM);
        } else {
            List<CareerWorld> worlds = s.careerTour.worlds();
            int first = s.listFirst(worlds.size(), 5);
            SitarUi.highlight(c, 18, 60 + bar(glide(now) * 24 / 16 - first * 24, Math.min(5, worlds.size()), 24), pane - 12, 14);
            for (int row = 0; row < Math.min(5, worlds.size()); row++) {
                int i = first + row; CareerWorld world = worlds.get(i); int y = 60 + row * 24;
                boolean unlocked = s.career.unlocked(s.careerTour, world);
                SitarUi.choiceLabel(c, unlocked ? world.title() : "LOCKED / " + world.title(), 18, y, pane - 12, 14, s.selected == i);
                SitarUi.text(c, (i + 1) + " / " + s.career.cleared(world) + "/" + world.requiredSongIds().size() + " MAIN ACTS" + (s.career.complete(world) ? " / CLEAR" : ""), 39, y + 15, DIM);
            }
            SitarUi.text(c, (s.selected + 1) + "/" + worlds.size() + " WORLDS", 18, 184, CYAN);
            CareerWorld preview = worlds.get(s.selected);
            SitarUi.center(c, "TOUR ROUTE", x, 65, 128, GOLD);
            SitarUi.center(c, preview.sideSongIds().size() + " SIDE GIGS", x, 80, 128, DIM);
            for (int i = 0; i < worlds.size(); i++) {
                int column = i % 3, row = i / 3, nx = x + 20 + column * 34, ny = 91 + row * 15;
                int color = s.career.complete(worlds.get(i)) ? GOLD : s.career.unlocked(s.careerTour, worlds.get(i)) ? CYAN : 0xFF365E95;
                if (column < 2 && i + 1 < worlds.size()) c.fill(nx + 10, ny + 4, 25, 1, DIM);
                if (i + 3 < worlds.size()) c.fill(nx + 4, ny + 8, 1, 8, DIM);
                c.fill(nx, ny, 9, 9, color);
                if (i == s.selected) {
                    // A slow breathing ring marks the selected stop; reduced flashes holds it still.
                    int grow = s.reducedFlashes() ? 0 : (int) (now / 20 % 2);
                    SitarUi.frame(c, nx - 2 - grow, ny - 2 - grow, 13 + grow * 2, 13 + grow * 2, CREAM);
                }
            }
            SitarUi.frame(c, x + 8, 171, 112, 12, CYAN);
            SitarUi.center(c, "REPLAY SCENE", x, 174, 128, CREAM);
            if (s.career.complete(s.careerTour)) {
                SitarUi.frame(c, x + 8, 184, 112, 12, GOLD); SitarUi.center(c, "TOUR FINALE", x, 187, 128, GOLD);
            }
        }
        SitarUi.footer(base, s.notice.isEmpty() ? s.performer.label() + " / " + s.shortRole() + " / " + s.difficulty.label() : s.notice,
                tourMenu ? "ENTER / A: TOUR / ESC / B: LEVEL" : s.career.complete(s.careerTour)
                ? "A: GIG / R Y: SCENE / F X: FINALE / B: TOURS" : "ENTER / A: GIG / R / Y: REPLAY SCENE / ESC / B: TOURS");
    }

    /**
     * An intermission staged on the world's own act: everyone who speaks stands on its floor
     * holding their instrument, the dialogue sits above them and points at the speaker.
     */
    private void drawStory(SceneCanvas base, long now) {
        CareerStory.Scene story = s.story;
        CareerStory.Line line = story.lines().get(Math.min(s.storyLine, story.lines().size() - 1));
        List<Roster> cast = new ArrayList<>();
        for (CareerStory.Line each : story.lines())
            if (each.speaker() != null && !cast.contains(each.speaker()) && cast.size() < 4) cast.add(each.speaker());
        if (cast.isEmpty()) cast.add(s.performer);
        int width = base.width(), speakerX = -1;
        for (int i = 0; i < cast.size(); i++) {
            Roster who = cast.get(i);
            int x = 48 + (width - 96) * (2 * i + 1) / (2 * cast.size());
            int floor = s.venue == null ? ConcertStage.FLOOR_ROW : s.venue.floorRow(x, 0);
            PerformerArt art = s.actor(who);
            if (who == line.speaker()) {
                speakerX = x;
                base.fill(x - 26, floor - 1, 52, 3, 0x30FFF2C0);
            }
            art.draw(base, x, floor - art.footOffset() + (art.hovers() ? -6 : 1), now, who == s.performer ? s.role : signature(who), false);
        }
        SitarUi.header(base, 0, "");
        String title = SitarUi.fit(story.title().toUpperCase(Locale.ROOT), width - 140);
        SitarUi.text(base, title, width - 12 - SitarUi.width(title, 1), 15, CYAN);
        SceneCanvas c = entering(base, now);
        SitarUi.panel(c, 12, STORY_TOP, width - 24, STORY_HEIGHT);
        if (speakerX >= 0) {
            // A tail from the box to whoever is speaking.
            for (int i = 0; i < 7; i++) {
                c.fill(speakerX - 6 + i, STORY_TOP + STORY_HEIGHT - 1 + i, 13 - i * 2, 1, 0xFF365E95);
                if (i < 6) c.fill(speakerX - 5 + i, STORY_TOP + STORY_HEIGHT - 1 + i, 11 - i * 2, 1, 0xF011244D);
            }
        }
        String who = line.speaker() == null ? "BACKSTAGE" : line.speaker().label().toUpperCase(Locale.ROOT) + (line.action() ? " / STAGE BUSINESS" : "");
        SitarUi.text(c, who, 22, STORY_TOP + 6, GOLD);
        String count = (s.storyLine + 1) + "/" + story.lines().size();
        SitarUi.text(c, count, width - 22 - SitarUi.width(count, 1), STORY_TOP + 6, DIM);
        // Each new line rises into place; the box itself stays put.
        double t = Motion.easeOut(Motion.progress(now, s.storyLineTick, 6));
        SceneCanvas words = t >= 1 ? c : new Motion.ShiftedCanvas(c, 0, 0.3 + 0.7 * t);
        SitarUi.wrapped(words, line.text(), 22, STORY_TOP + 20 + (int) Math.round(3 * (1 - t)), width - 44, line.action() ? CYAN : CREAM);
        SitarUi.footer(base, "Robotnik's world tour / " + s.performer.label() + " on " + s.shortRole(),
                "ENTER / A / CLICK: NEXT / ESC / B: SKIP / R ON BOARD: REPLAY");
    }

    /** The instrument a supporting character carries in story scenes. */
    private static Role signature(Roster who) {
        return switch (who) {
            case SONIC -> Role.SITAR;
            case TAILS, MECHA_SONIC -> Role.SYNTH;
            case KNUCKLES, SILVER_SONIC, EGG_ROBO -> Role.BONGOS;
            case ROBOTNIK -> Role.HARP;
        };
    }

    // ---------------------------------------------------------------- loading and count-in

    /**
     * Loading: the performer waits on the song's venue while the ROM music renders. S3K zone
     * themes slide in the ROM's own title card; other songs get the mod's card.
     */
    private void drawLoading(SceneCanvas base, long now) {
        int width = base.width();
        base.fill(0, 0, width, base.height(), 0x40000D28);
        PerformerArt art = s.actor(s.performer);
        int x = width - 44, floor = s.venue == null ? ConcertStage.FLOOR_ROW : s.venue.floorRow(x, 0);
        art.draw(base, x, floor - art.footOffset() + (art.hovers() ? -6 : 1), now, s.role, false);
        // A ROM title card owns the top of the screen as it does in the game; otherwise the mod's header and card.
        boolean card = titleCard(base, now, false);
        if (!card) SitarUi.header(base, 0, "LOADING");
        if (!card) {
            double t = Motion.easeOut(Motion.progress(now, s.loadingTick, 16));
            SceneCanvas c = new Motion.ShiftedCanvas(base, (int) Math.round(-40 * (1 - t)), t);
            SitarUi.panel(c, 16, 52, width - 128, 92);
            SitarUi.text(c, s.calibratePending ? "SOUNDCHECK" : "NEXT UP", 28, 60, CYAN);
            SitarUi.label(c, s.calibratePending ? "Tap calibration" : s.song.label(), 28, 74, width - 152, GOLD);
            SitarUi.text(c, s.song.game().toUpperCase(Locale.ROOT) + " / " + s.song.durationFrames() / 60 + " SEC / "
                    + s.modeLabel(), 28, 94, DIM);
            SitarUi.text(c, s.performer.label().toUpperCase(Locale.ROOT) + " ON " + s.shortRole().toUpperCase(Locale.ROOT)
                    + " / " + s.difficulty.label().toUpperCase(Locale.ROOT), 28, 108, CREAM);
            SitarUi.text(c, "GET READY", 28, 126, GOLD);
        } else {
            String who = s.performer.label().toUpperCase(Locale.ROOT) + " ON " + s.shortRole().toUpperCase(Locale.ROOT)
                    + " / " + s.difficulty.label().toUpperCase(Locale.ROOT);
            SitarUi.text(base, SitarUi.fit(who, width - 140), 16, 178, CREAM);
        }
        int progress = s.loadingBar.at(now);
        base.fill(16, 186, width - 128, 6, INK);
        base.fill(17, 187, (width - 130) * progress / 100, 4, CYAN);
        SitarUi.footer(base, (s.preparingPart ? "Isolating your part " : "Rendering ROM music ") + s.loadingProgress() + "%",
                "ESC / B: CANCEL    THREE-SECOND COUNT-IN WHEN READY");
    }

    /**
     * Sonic 3 &amp; Knuckles title cards, animated as {@code SceneRomArt.titleCard} documents:
     * the four pieces slide in at 16 pixels a frame while loading, and leave at 32 a frame
     * (banner up, the rest right) once the count-in starts. True when a card was drawn.
     */
    private boolean titleCard(SceneCanvas c, long now, boolean leaving) {
        SongSpec song = s.song;
        if (s.calibratePending || song == null || !song.game().equals("s3k") || !song.label().contains("Zone")) return false;
        SceneRomArt rom = s.context.art().rom(song.game());
        if (rom == null || !rom.hasTitleCard(song.zone(), song.act())) return false;
        SceneSpriteSet card = rom.titleCard(song.zone(), song.act());
        if (card == null) return false;
        long arriving = (leaving ? s.screenTick : now) - s.loadingTick;
        long exit = leaving ? now - s.screenTick + 1 : 0;
        if (leaving && exit > 40) return false;
        int[][] path = {{96, -112, 96, 64, 1}, {480, 96, 160, 96, 3}, {636, 128, 252, 128, 5}, {708, 160, 260, 160, 7}};
        // The ROM omits the act piece for Sky Sanctuary and Doomsday.
        int pieces = song.zone() == 10 || song.zone() == 12 ? 3 : 4;
        int offset = (c.width() - 320) / 2;
        for (int i = 0; i < pieces; i++) {
            int[] p = path[i];
            int x = approach(p[0], p[2], arriving * 16), y = approach(p[1], p[3], arriving * 16);
            long away = Math.max(0, exit - p[4] + 1) * 32;
            if (i == 0) y -= (int) away; else x += (int) away;
            c.draw(card.frame(i), x + offset, y, SceneDraw.plain());
        }
        return true;
    }
    private static int approach(int from, int to, long travelled) {
        return from + (int) Math.signum(to - from) * (int) Math.min(Math.abs(to - from), travelled);
    }

    private void drawPerformance(SceneCanvas c, long now) {
        RhythmSession session = s.session;
        long position = s.player != null ? s.player.samplePosition() : session.position();
        int rate = s.prepared.sampleRate();
        int lanes = s.role.drums() ? 4 : s.difficulty.lanes();
        double t = s.previousScreen == Screen.PAUSED ? 1 : Motion.easeOut(Motion.progress(now, s.screenTick, Motion.ENTER_TICKS));
        SceneCanvas hud = t >= 1 ? c : new Motion.ShiftedCanvas(c, 0, t);
        hud.fill(0, 0, c.width(), 35, 0xF00B2457); hud.fill(0, 34, c.width(), 1, GOLD);
        SitarUi.text(hud, SitarUi.fit(s.song.label(), c.width() - 130), 8, 6, GOLD);
        SitarUi.text(hud, s.shortRole() + " / " + s.difficulty.label(), 8, 22, CREAM);
        SitarUi.text(hud, s.modeLabel(), c.width() - 113, 22, CYAN);
        if (s.session2 == null) {
            s.highway.draw(c, session, position, rate, s.settings, s.role.drums(), lanes,
                    c.width() / 2 - 98, 53, 196, 130, now);
            SitarUi.panel(hud, 7, 47, 78, 134);
            SitarUi.text(hud, "SCORE", 14, 55, CREAM);
            SitarUi.text(hud, Long.toString(session.score()), 14, 69, GOLD);
            SitarUi.text(hud, "X" + session.multiplier(), 14, 85, session.starActive() ? CYAN : GOLD);
            SitarUi.text(hud, "ROCK", 14, 102, CREAM); meter(hud, 13, 114, 63, session.rock(), rockColor(session));
            SitarUi.text(hud, "STAR POWER", 14, 135, CREAM); meter(hud, 13, 147, 63, session.starCharge(), CYAN);
            SitarUi.text(hud, session.starActive() ? "STAR POWER!" : session.starCharge() >= .5 ? "READY!" : "BUILD IT", 14, 166, GOLD);
            int x = c.width() - 47, floor = s.venue == null ? ConcertStage.FLOOR_ROW : s.venue.floorRow(x, 0);
            PerformerArt art = s.actor(s.performer);
            art.draw(c, x, floor - art.footOffset() + (art.hovers() ? -6 : 1), now, s.role, s.screen == Screen.PLAY);
            SitarUi.text(hud, SitarUi.fit(s.performer.label().toUpperCase(Locale.ROOT), 80), c.width() - 84, 42, CREAM);
            if (s.mode == Mode.PRACTICE) SitarUi.text(hud, "NO FAIL", c.width() - 84, 54, CYAN);
            if (s.online != null) SitarUi.text(hud, "PEER " + s.online.remoteScore(), c.width() - 84, 54, CYAN);
        } else {
            int half = c.width() / 2;
            drawPlayerHud(hud, session, s.performer, 0, half);
            drawPlayerHud(hud, s.session2, s.performer2, half, half);
            drawLocalPerformer(c, session, 0, half, now);
            drawLocalPerformer(c, s.session2, half, half, now);
            s.highway.draw(c, session, position, rate, s.settings, s.role.drums(), lanes, 12, 77, half - 84, 107, now);
            s.highway2.draw(c, s.session2, position, rate, s.settings2, s.role.drums(), lanes, half + 12, 77, half - 84, 107, now);
            c.fill(half - 1, 37, 2, 158, 0xFF365E95);
        }
        countIn(c, position, rate);
        if (s.autoplay) SitarUi.text(c, "DEMO", 14, 185, CYAN);
        double progress = Math.max(0, Math.min(1, position / (double) s.prepared.lengthSamples()));
        c.fill(0, 197, c.width(), 2, INK); c.fill(0, 197, (int) (c.width() * progress), 2, GOLD);
        SitarUi.footer(c, s.session2 == null ? "STREAK " + session.streak() + " / BEST " + session.bestStreak() : s.role.drums() ? "P1: ASDF + SPACE / P2: JKL; + P" : "P1: ASDFG / P2: JKL;' / REMAP IN SETTINGS",
                s.mode == Mode.PRACTICE ? "PRACTICE / RECORDS DISABLED / ESC: PAUSE" : "ESC / START: PAUSE / STAR POWER DOUBLES SCORE");
    }

    /** 3, 2, 1 on the audible clock, then GO: each digit settles as its second passes. */
    private static void countIn(SceneCanvas c, long position, int rate) {
        if (position < 0) {
            long remaining = -position;
            int digit = (int) ((remaining + rate - 1) / rate);
            double through = 1 - (remaining - (digit - 1L) * rate) / (double) rate;
            SceneCanvas faded = new Motion.ShiftedCanvas(c, 0, 1 - 0.5 * through * through);
            SitarUi.big(faded, Integer.toString(digit), c.width() / 2, 92 - (int) Math.round(6 * through), 5, GOLD);
        } else if (position < rate / 2) {
            double through = position / (rate / 2.0);
            SitarUi.big(new Motion.ShiftedCanvas(c, 0, 1 - through), "GO!", c.width() / 2, 92, 4, CYAN);
        }
    }

    private static int rockColor(RhythmSession who) {
        return who.failed() ? 0xFFFF5969 : who.rock() < .25 ? 0xFFFFB04B : 0xFF6CDA66;
    }

    private void drawPlayerHud(SceneCanvas c, RhythmSession who, Roster actor, int x, int width) {
        SitarUi.text(c, SitarUi.fit(actor.label(), width - 20), x + 10, 41, CREAM);
        SitarUi.text(c, "SCORE " + who.score() + "  X" + who.multiplier(), x + 10, 53, who.starActive() ? CYAN : GOLD);
        meter(c, x + 10, 66, width / 2 - 15, who.rock(), rockColor(who));
        meter(c, x + width / 2 + 5, 66, width / 2 - 15, who.starCharge(), CYAN);
    }
    private void drawLocalPerformer(SceneCanvas c, RhythmSession active, int x, int width, long now) {
        int centre = x + width - 36, floor = s.venue == null ? ConcertStage.FLOOR_ROW : s.venue.floorRow(centre, 0);
        PerformerArt art = s.performanceArtist(active);
        c.clip(x + width - 72, 77, 72, 109);
        art.draw(c, centre, Math.min(floor, 185) - art.footOffset() + (art.hovers() ? -6 : 1), now, s.role, s.screen == Screen.PLAY);
        c.unclip();
    }
    private static void meter(SceneCanvas c, int x, int y, int width, double value, int color) {
        c.fill(x, y, width, 9, INK); SitarUi.frame(c, x, y, width, 9, DIM);
        c.fill(x + 2, y + 2, (int) ((width - 4) * value), 5, color);
    }

    private void drawPause(SceneCanvas base, long now) {
        double t = Motion.easeOut(Motion.progress(now, s.screenTick, 6));
        base.fill(0, 0, base.width(), 224, Motion.fade(0xB8000B28, t));
        SceneCanvas c = t >= 1 ? base : new Motion.ShiftedCanvas(base, 0, t);
        int x = (c.width() - 248) / 2;
        SitarUi.panel(c, x, 48, 248, 144);
        title(c, "PAUSED", 59);
        List<String> items = List.of("Resume", "Retry", "Song select");
        for (int i = 0; i < 3; i++) SitarUi.choice(c, items.get(i), x + 10, 83 + i * 26, 228, 22, s.selected == i);
        SitarUi.wrapped(c, s.notice, x + 12, 166, 224, GOLD);
        SitarUi.footer(base, "Performance clock and score are paused", "ENTER / A: SELECT    ESC: RESUME");
    }

    // ---------------------------------------------------------------- results

    private void drawResults(SceneCanvas c, SceneCanvas base, long now) {
        PerformanceResult result = s.result;
        RhythmSession session = s.session;
        int width = c.width() - 156;
        boolean failed = result.failed();
        String heading = failed ? "PERFORMANCE FAILED" : s.mode == Mode.PRACTICE ? "PRACTICE COMPLETE"
                : s.mode == Mode.CAREER && s.careerTour != null && s.career.complete(s.careerTour) ? "TOUR COMPLETE!" : "STAGE CLEAR!";
        if (s.mode == Mode.LOCAL_VERSUS) heading = session.score() == s.session2.score() ? "DRAW!" : session.score() > s.session2.score() ? "PLAYER 1 WINS!" : "PLAYER 2 WINS!";
        if (s.mode == Mode.ONLINE_VERSUS) heading = !s.online.remoteFinished() ? "WAITING FOR PEER RESULT"
                : session.score() == s.online.remoteScore() ? "DRAW!" : session.score() > s.online.remoteScore() ? "YOU WIN!" : "PEER WINS!";
        SitarUi.text(c, heading, 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, width, 136);
        SitarUi.text(c, SitarUi.fit(s.song.label().toUpperCase(Locale.ROOT) + " / " + s.shortRole().toUpperCase(Locale.ROOT), width - 20), 22, 63, CREAM);
        // A clear counts its score up like Sonic's act tally; the stars follow one by one.
        long tallyStart = s.screenTick + SitarScene.TALLY_DELAY;
        double counted = result.cleared() ? Motion.progress(now, tallyStart, SitarScene.TALLY_TICKS) : 1;
        SitarUi.label(c, "SCORE " + Math.round(session.score() * counted), 22, 78, width - 120, GOLD);
        if (s.newRecord && counted >= 1)
            SitarUi.text(c, "NEW BEST!", width - 92, 70 - (s.reducedFlashes() ? 0 : (int) (now / 16 % 2)), CYAN);
        long starsFrom = tallyStart + SitarScene.TALLY_TICKS;
        for (int i = 0; i < 5; i++) {
            boolean shown = !result.cleared() || now >= starsFrom + i * 6L;
            SitarUi.star(c, width - 92 + i * 12, 79, shown && i < result.stars());
        }
        if (counted >= 1) {
            SitarUi.text(c, Math.round(result.accuracy() * 100) + "% / " + (result.fullCombo() ? "FULL COMBO" : "GRADE " + result.grade()), 22, 96, CYAN);
            SitarUi.text(c, "BEST " + s.best + " / STREAK " + session.bestStreak(), 22, 109, DIM);
        }
        List<String> items = List.of("Retry", "Choose song", s.mode == Mode.CAREER ? "Continue tour" : "Choose performer", "Title");
        for (int i = 0; i < 4; i++) SitarUi.choice(c, items.get(i), 18, 123 + i * 16, width - 12, 15, s.selected == i);
        int x = c.width() - 140;
        SitarUi.panel(c, x, 55, 128, 136);
        SitarUi.center(c, s.performer.label(), x, 66, 128, CREAM);
        standOnLine(c, s.actor(s.performer), x + 64, 146, now, s.role);
        if (s.result2 != null) {
            SitarUi.center(c, "P2 SCORE " + s.result2.score(), x, 164, 128, GOLD);
            SitarUi.center(c, Math.round(s.result2.accuracy() * 100) + "% / " + s.result2.stars() + " STARS", x, 178, 128, CYAN);
        } else if (s.online != null) SitarUi.center(c, "PEER " + s.online.remoteScore(), x, 164, 128, GOLD);
        else SitarUi.center(c, failed ? "TRY AGAIN!" : "GREAT SHOW!", x, 164, 128, GOLD);
        String recordNotice = s.saveFailed ? "SAVE FAILED / RECORDS REMAIN IN MEMORY" : s.autoplay ? "DEMO / RECORD NOT SAVED"
                : s.saved ? "RECORD SAVED / " + s.profile.clears() + " CLEARS" : "PRACTICE / MULTIPLAYER / FAILED: NO SOLO RECORD";
        SitarUi.footer(base, recordNotice, s.mode == Mode.CAREER ? failed ? CareerStory.retryQuip(s.performer) : CareerStory.successQuip(s.performer)
                : "ENTER / A: SELECT / ESC / B: SONG SELECT");
    }

    // ---------------------------------------------------------------- other menus

    private void drawRecords(SceneCanvas c, SceneCanvas base) {
        SongSpec item = s.songs.get(Math.min(s.selected, s.songs.size() - 1));
        SitarUi.text(c, "RECORDS / " + s.shortRole() + " / " + s.difficulty.label(), 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        SitarUi.text(c, item.label(), 24, 68, CREAM);
        PerformanceResult record = s.profile.best(item.id(), s.role.name(), s.difficulty.name()).orElse(null);
        if (record == null) SitarUi.text(c, "NO CLEAR YET / PLAY CAREER OR QUICK PLAY", 24, 96, DIM);
        else {
            SitarUi.text(c, "BEST SCORE " + record.score(), 24, 94, GOLD);
            int stars = s.profile.bestStars(item.id(), s.role.name(), s.difficulty.name()).orElse(record).stars();
            for (int i = 0; i < 5; i++) SitarUi.star(c, 24 + i * 12, 112, i < stars);
            SitarUi.text(c, "SCORE " + Math.round(record.accuracy() * 100) + "%", 90, 114, CYAN);
            SitarUi.text(c, "BEST STREAK " + record.bestStreak() + (record.fullCombo() ? " / FULL COMBO" : ""), 24, 138, CREAM);
        }
        SitarUi.text(c, "MAIN ACT CLEARS " + s.tours.stream().mapToInt(s.career::cleared).sum() + "/"
                + s.tours.stream().mapToInt(t -> t.worlds().stream().mapToInt(w -> w.requiredSongIds().size()).sum()).sum(), 24, 166, GOLD);
        SitarUi.footer(base, "UP / DOWN: SONG / LEFT / RIGHT: DIFFICULTY", "TAB: INSTRUMENT / ESC / B: TITLE");
    }
    private void drawHelp(SceneCanvas c, SceneCanvas base) {
        SitarUi.text(c, "HOW TO PLAY", 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        SitarUi.wrapped(c, s.role.drums() ? "BONGOS: press each coloured pad when it reaches the line. The gold bar is the kick pedal. New presses strike; holding does not." :
                "MELODIC: hold the matching frets and strum at the line. Hold long tails. White centres can be hammered on after a successful chain.", 24, 65, c.width() - 48, CREAM);
        SitarUi.wrapped(c, "Complete bright phrases to charge Star Power. Activate at half a meter for double score. Misses lower ROCK and mute your part.", 24, 107, c.width() - 48, GOLD);
        SitarUi.wrapped(c, "Career: clear each world's main acts. Tour progress follows any instrument or level. Practice never saves. Co-op shares survival; duels compare scores.", 24, 149, c.width() - 48, CYAN);
        SitarUi.footer(base, "REMAP CONTROLS AND CALIBRATE BOTH PLAYERS IN SETTINGS", "ESC / B: TITLE");
    }
    private void drawError(SceneCanvas c, SceneCanvas base) {
        SitarUi.panel(c, 12, 52, c.width() - 24, 136);
        SitarUi.label(c, "UNABLE TO START", 24, 62, c.width() - 48, GOLD);
        SitarUi.wrapped(c, s.error, 24, 86, c.width() - 48, CREAM);
        SitarUi.footer(base, "Your selection is preserved", "ENTER / A: RETURN");
    }
    private void drawOnline(SceneCanvas c, SceneCanvas base) {
        SitarUi.text(c, "DIRECT-CONNECT", 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        if (s.screen == Screen.LOBBY) {
            OnlineMatch online = s.online;
            SitarUi.wrapped(c, online == null ? "No connection" : online.status(), 24, 69, c.width() - 48, CYAN);
            if (online != null && online.choice() != null) {
                OnlineMatch.Choice choice = online.choice();
                SitarUi.text(c, choice.song() + " / " + choice.difficulty().label(), 24, 104, GOLD);
                SitarUi.text(c, choice.role().label(), 24, 122, CREAM);
            }
            SitarUi.wrapped(c, online != null && online.host() ? "ENTER: choose song and performer after your peer connects" : "ENTER: choose performer, then confirm the host's offered song to ready up", 24, 152, c.width() - 48, CREAM);
            SitarUi.footer(base, "Each player needs their own matching ROM and mod build", "ENTER / A: READY / ESC / B: DISCONNECT");
        } else {
            List<String> items = List.of("Host co-op", "Host score duel", "Join host", "Address / " + s.onlineAddress, "Title");
            for (int i = 0; i < items.size(); i++) SitarUi.choice(c, SitarUi.fit(items.get(i), c.width() - 75), 18, 62 + i * 24, c.width() - 36, 22, s.selected == i);
            SitarUi.footer(base, s.notice.isEmpty() ? "IP ADDRESS:PORT / LAN OR FORWARDED TCP PORT" : s.notice,
                    s.editingAddress ? "TYPE / BACKSPACE / ; FOR COLON / ENTER: SAVE" : "ENTER / A: SELECT / ESC / B: TITLE");
        }
    }
    private void drawSettings(SceneCanvas c, SceneCanvas base) {
        ControlSettings settings = s.activeSettings();
        SitarUi.text(c, "P" + (s.settingsSecond ? "2" : "1") + " / " + (s.settingsDrums ? "BONGOS" : "FRETS") + " / " + (s.settingsPad ? "GAMEPAD" : "KEYBOARD"), 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        List<Integer> rows = s.settingsRows();
        int first = Math.max(0, Math.min(rows.size() - 8, rows.indexOf(s.selected) - 3));
        for (int row = first; row < Math.min(rows.size(), first + 8); row++) {
            int i = rows.get(row), y = 60 + (row - first) * 16;
            if (s.selected == i) {
                c.fill(18, y, c.width() - 36, 15, 0xFF173B6C);
                SitarUi.frame(c, 18, y, c.width() - 36, 15, CYAN);
                c.fill(19, y + 1, 3, 13, GOLD);
            }
            String name = i < 10 ? settings.actionName(i, s.settingsDrums)
                    : switch (i) { case 10 -> "INPUT OFFSET"; case 11 -> "DISPLAY OFFSET";
                        case 12 -> "LEFTY FLIP"; case 13 -> "TAP CALIBRATION"; case 14 -> "SWITCH INSTRUMENT PROFILE";
                        case 15 -> "EDIT PLAYER"; case 16 -> "REDUCED FLASHES"; case 17 -> "HIGHWAY SPEED"; default -> "RESTORE DEFAULTS"; };
            String value = i < 10 ? settings.binding(s.settingsDrums, s.settingsPad, i).label()
                    : switch (i) { case 10 -> settings.inputOffsetMs() + " MS"; case 11 -> settings.displayOffsetMs() + " MS";
                        case 12 -> settings.lefty() ? "ON" : "OFF"; case 15 -> s.settingsSecond ? "P2" : "P1";
                        case 16 -> settings.reducedFlashes() ? "ON" : "OFF"; case 17 -> settings.scrollSpeed() + "%"; default -> ">"; };
            SitarUi.text(c, name, 29, y + 4, s.selected == i ? GOLD : CREAM);
            SitarUi.text(c, value, c.width() - 28 - SitarUi.width(value, 1), y + 4, CYAN);
        }
        SitarUi.footer(base, s.capturingBinding ? "Press a key, button or move an axis / ESC cancels" : "ENTER / A: REMAP    TAB: DEVICE    LEFT / RIGHT: ADJUST",
                s.capturingBinding ? "Waiting for your new binding..." : "DELETE: UNBIND    ESC / B: SAVE AND RETURN");
    }
    private void drawCalibration(SceneCanvas c, SceneCanvas base) {
        ControlSettings settings = s.activeSettings();
        SitarUi.text(c, "TAP CALIBRATION", 12, 42, GOLD);
        SitarUi.panel(c, 12, 55, c.width() - 24, 136);
        SitarUi.wrapped(c, "Listen to the ROM drum hits. Tap " + settings.binding(true, false, 4).label() + " / your PAD A on the beat. Use at least 8 taps. The median sets your input offset.", 24, 66, c.width() - 48, CREAM);
        center(c, s.calibration.size() + " TAPS", 111, GOLD);
        int fill = Math.min(8, s.calibration.size());
        for (int i = 0; i < 8; i++) {
            int x = c.width() / 2 - 77 + i * 20;
            c.fill(x, 137, 14, 8, i < fill ? CYAN : INK);
            SitarUi.frame(c, x, 137, 14, 8, DIM);
        }
        SitarUi.center(c, s.calibration.size() >= 8 ? "READY TO SAVE" : "KEEP TAPPING", 0, 165, c.width(), CYAN);
        SitarUi.footer(base, settings.binding(true, false, 4).label() + " / YOUR PAD A: TAP WITH THE DRUMS", "ENTER: SAVE OFFSET AFTER 8 TAPS    ESC / B: CANCEL");
    }

    private static void title(SceneCanvas c, String text, int y) {
        center(c, text, y + 2, INK); center(c, text, y, GOLD);
    }
    private static void center(SceneCanvas c, String text, int y, int color) { c.text(text, (c.width() - c.textWidth(text)) / 2, y, color); }
}
