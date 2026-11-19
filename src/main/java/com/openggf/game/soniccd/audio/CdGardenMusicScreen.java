package com.openggf.game.soniccd.audio;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.GameServices;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuStyle;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.SonicCdZoneRegistry;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.i18n.UiMessages;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glIsEnabled;

/** OpenGGF track selection for the unlocked D.A. Garden title entry. */
final class CdGardenMusicScreen implements NativeMenuScreen {
    private static final int FIRST_STAGE_TRACK = 3, FIRST_CUE_TRACK = 24;
    private static final int STOP = CdSubCpuAudio.commandId("SCMD_STOPCDDA");
    private static final int TOP = 43, LEFT = 12, SCROLL_MARGIN = 16, ROW_GAP = 4;
    private static final int ART_WIDTH = 176, ART_TOP = 46, ART_PADDING = 4;
    private static final int PLANET_HINT = 2, TRACKS_HINT = 0;
    private static final List<CdSubCpuAudio.Command> TRACKS = trackCommands();

    private final PixelFontTextRenderer text = PixelFontTextRenderer.menu();
    private final SonicCdZoneRegistry zones = new SonicCdZoneRegistry();
    private final CdGardenPlanetArt planet;
    private List<MenuChrome.FooterTarget> footerTargets = List.of();
    private InputHandler input;
    private CdSubCpuAudio audio;
    private int selected;
    private int first;
    private int frame;
    private boolean exiting;
    private boolean viewingPlanet;

    CdGardenMusicScreen(CdGardenPlanetArt planet) {
        this.planet = planet;
    }

    private static List<CdSubCpuAudio.Command> trackCommands() {
        var byTrack = new TreeMap<Integer, CdSubCpuAudio.Command>();
        for (int commandId = 0; commandId <= 0xff; commandId++) {
            var command = CdSubCpuAudio.command(commandId);
            if (command != null && command.action() == CdSubCpuAudio.Action.CDDA_ONCE
                    && command.argument() >= FIRST_STAGE_TRACK) {
                byTrack.putIfAbsent(command.argument(), command);
            }
        }
        return List.copyOf(byTrack.values());
    }

    @Override public void initialize() {
        selected = 0;
        first = 0;
        frame = 0;
        exiting = false;
        viewingPlanet = false;
        footerTargets = List.of();
        var manager = GameServices.audio();
        audio = manager.getAudioProfile() instanceof SonicCdAudioProfile cd ? cd.subCpuAudio() : null;
        manager.playMusic(STOP);
    }

    @Override public void update(InputHandler input) {
        if (exiting) return;
        this.input = input;
        frame++;
        int width = width();
        var pointer = MenuInput.pointer(input, width, presentationHeight());
        boolean back = MenuInput.back(input) || pointer.inside() && pointer.rightPressed();
        if (viewingPlanet) {
            if (back || clickedHint(pointer, TRACKS_HINT)) {
                viewingPlanet = false;
                footerTargets = List.of();
                MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            }
            return;
        }
        if (back) {
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            exiting = true;
            return;
        }
        if (planet != null && (MenuInput.details(input) || clickedHint(pointer, PLANET_HINT))) {
            viewingPlanet = true;
            footerTargets = List.of();
            MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
            return;
        }

        int previous = selected;
        int rows = visibleRows();
        if (MenuInput.up(input)) selected = Math.floorMod(selected - 1, TRACKS.size());
        else if (MenuInput.down(input)) selected = (selected + 1) % TRACKS.size();
        else if (pointer.wheel() != 0) selected = Math.clamp(selected - pointer.wheel(), 0, TRACKS.size() - 1);
        boolean picked = false;
        if (pointer.leftPressed()) {
            for (int row = 0; row < rows && first + row < TRACKS.size(); row++) {
                if (pointer.over(LEFT, rowY(row), width - LEFT - SCROLL_MARGIN, rowHeight())) {
                    selected = first + row;
                    picked = true;
                    break;
                }
            }
        }
        first = Math.clamp(first, Math.max(0, selected - rows + 1), Math.min(selected, TRACKS.size() - rows));
        if (previous != selected) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        if (!picked && !MenuInput.accept(input)) return;
        var command = TRACKS.get(selected);
        // Use the same disc playback path as Sound Test, including the shared output mixer.
        if (audio != null) audio.submit(command.argument() == playingTrack() ? STOP : command.id());
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
    }

    @Override public void draw() {
        var graphics = GameServices.graphics();
        if (graphics.isHeadlessMode()) return;
        int width = width();
        float[] projection = graphics.getProjectionMatrixBuffer();
        text.setProjectionMatrix(projection);
        var font = text.font();
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        glEnable(GL_BLEND);
        try {
            boolean hasArt = viewingPlanet && planet.prepare();
            text.beginBatch();
            try {
                int playing = playingTrack();
                String subtitle = playing == 0 ? null
                        : UiMessages.text("menu.soniccd.garden.playing_track", Map.of("track", number(playing)));
                if (viewingPlanet) {
                    MenuChrome.page(font, width, UiMessages.text("menu.soniccd.garden.planet_title"), null, subtitle);
                    drawPlanetPanel(font, width, hasArt);
                } else {
                    if (subtitle == null) subtitle = UiMessages.text("menu.soniccd.garden.select");
                    MenuChrome.page(font, width, UiMessages.text("menu.soniccd.garden.title"), null, subtitle);
                    drawTrackRows(font, width, playing);
                }
                var hints = footerHints();
                footerTargets = MenuChrome.footerLayout(width, null, hints, frame).targets();
                MenuChrome.footer(font, width, null, hints, MenuChrome.controller(input), frame);
            } finally {
                text.endBatch();
            }
            if (hasArt) {
                planet.draw(projection, (width - ART_WIDTH) / 2,
                        MenuChrome.SCREEN_H - ART_TOP - CdGardenPlanetArt.heightAtWidth(ART_WIDTH), ART_WIDTH);
            }
        } finally {
            if (!blendEnabled) glDisable(GL_BLEND);
        }
    }

    private void drawTrackRows(PixelFont font, int width, int playing) {
        int rows = visibleRows();
        for (int row = 0; row < rows && first + row < TRACKS.size(); row++) {
            int index = first + row;
            int track = TRACKS.get(index).argument();
            int y = rowY(row);
            int rowWidth = width - LEFT - SCROLL_MARGIN;
            if (index == selected) MenuChrome.focus(font, LEFT, y, rowWidth, rowHeight(), frame);
            else MenuChrome.panel(font, LEFT, y, rowWidth, rowHeight());
            if (track == playing) MenuChrome.icon(font, MenuChrome.Icon.PLAY, LEFT + 6, y + (rowHeight() - 7) / 2,
                    MenuChrome.CYAN);
            int textY = y + (rowHeight() - MenuChrome.compactLineHeight()) / 2 + MenuChrome.compactMetrics().above();
            MenuChrome.compact(font, number(track), LEFT + 20, textY, MenuChrome.DIM);
            MenuChrome.compact(font, MenuStyle.pageText(trackLabel(track), rowWidth - 49, MenuStyle.COMPACT, frame),
                    LEFT + 42, textY, index == selected ? MenuChrome.WHITE : MenuChrome.TEXT);
        }
        MenuChrome.scrollbar(font, width - 9, TOP, rows * (rowHeight() + ROW_GAP) - ROW_GAP,
                first, rows, TRACKS.size());
    }

    private void drawPlanetPanel(PixelFont font, int width, boolean hasArt) {
        int height = CdGardenPlanetArt.heightAtWidth(ART_WIDTH);
        MenuChrome.panel(font, (width - ART_WIDTH) / 2 - ART_PADDING, ART_TOP - ART_PADDING,
                ART_WIDTH + 2 * ART_PADDING, height + 2 * ART_PADDING);
        if (!hasArt) {
            String unavailable = MenuStyle.pageText(UiMessages.text("menu.soniccd.garden.planet_unavailable"),
                    ART_WIDTH, MenuStyle.COMPACT, frame);
            MenuChrome.compact(font, unavailable, (width - MenuChrome.compactWidth(unavailable)) / 2,
                    ART_TOP + (height - MenuChrome.compactLineHeight()) / 2 + MenuChrome.compactMetrics().above(),
                    MenuChrome.MUTED);
        }
    }

    private List<MenuChrome.Hint> footerHints() {
        var back = MenuChrome.backKey(input);
        if (viewingPlanet) return List.of(MenuChrome.hint(back, UiMessages.text("menu.soniccd.garden.tracks")));
        var move = MenuChrome.upDown(input, UiMessages.text("menu.details.move"));
        var play = MenuChrome.hint(MenuChrome.confirmKey(input), UiMessages.text("menu.soniccd.garden.play_stop"));
        var exit = MenuChrome.hint(back, UiMessages.text("menu.details.back_caps"));
        if (planet == null) return List.of(move, play, exit);
        var art = MenuChrome.hint(MenuChrome.detailsKey(input), UiMessages.text("menu.soniccd.garden.planet"));
        return List.of(move, play, art, exit);
    }

    private boolean clickedHint(MenuInput.Pointer pointer, int index) {
        return pointer.leftPressed() && footerTargets.stream().anyMatch(target -> target.index() == index
                        && pointer.over(target.x(), target.y(), target.width(), target.height()));
    }

    private String trackLabel(int track) {
        if (track < FIRST_CUE_TRACK) {
            int relative = track - FIRST_STAGE_TRACK;
            String period = switch (relative % 3) {
                case 0 -> UiMessages.text("menu.soniccd.garden.present");
                case 1 -> UiMessages.text("menu.soniccd.garden.good_future");
                default -> UiMessages.text("menu.soniccd.garden.bad_future");
            };
            return UiMessages.text("menu.soniccd.garden.zone_track", Map.of("zone", zones.getZoneName(relative / 3),
                    "period", period));
        }
        return switch (track) {
            case 24 -> UiMessages.text("menu.soniccd.garden.boss");
            case 25 -> UiMessages.text("menu.soniccd.garden.final_boss");
            case 26 -> UiMessages.text("menu.soniccd.garden.title_screen");
            case 27 -> UiMessages.text("menu.soniccd.garden.time_attack");
            case 28 -> UiMessages.text("menu.soniccd.garden.results");
            case 29 -> UiMessages.text("menu.soniccd.garden.speed_shoes");
            case 30 -> UiMessages.text("menu.soniccd.garden.invincibility");
            case 31 -> UiMessages.text("menu.soniccd.garden.game_over");
            case 32 -> UiMessages.text("menu.soniccd.garden.special_stage");
            case 33 -> UiMessages.text("menu.soniccd.garden.title");
            case 34 -> UiMessages.text("menu.soniccd.visual_mode.opening");
            case 35 -> UiMessages.text("menu.soniccd.garden.ending");
            default -> UiMessages.text("menu.soniccd.garden.unknown_track");
        };
    }

    @Override public void setClearColor() {
        if (!GameServices.graphics().isHeadlessMode()) glClearColor(0, 0, 0, 1);
    }

    @Override public void reset() {
        if (audio != null) audio.submit(STOP);
        audio = null;
        input = null;
        footerTargets = List.of();
        if (planet != null) planet.reset();
        text.cleanup();
    }

    @Override public Exit consumeExit() {
        if (!exiting) return Exit.NONE;
        exiting = false;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }

    private int playingTrack() { return audio == null ? 0 : audio.state().cdda().track(); }
    private static String number(int track) { return String.format(Locale.ROOT, "%02d", track); }
    private static int width() { return Math.max(320, GameServices.graphics().getProjectionWidth()); }
    private static int rowHeight() { return Math.max(20, MenuChrome.compactLineHeight() + 6); }
    private static int rowY(int row) { return TOP + row * (rowHeight() + ROW_GAP); }
    private static int visibleRows() { return Math.min(TRACKS.size(), (MenuChrome.FOOTER_Y - TOP - 4) / (rowHeight() + ROW_GAP)); }
}
