package com.openggf.game.soniccd.fmv;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.game.GameServices;
import com.openggf.game.MenuChrome;
import com.openggf.game.MenuFeedback;
import com.openggf.game.MenuStyle;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdLoadTiming;
import com.openggf.game.soniccd.audio.CdSubCpuAudio;
import com.openggf.game.soniccd.ending.CdEndings;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.title.CdTitleScreen;
import com.openggf.graphics.PixelFont;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.i18n.UiMessages;

import java.util.List;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glIsEnabled;

/** OpenGGF's browser for hosted Sonic CD clips, with its own playback lifetime. */
final class CdVisualModeScreen implements NativeMenuScreen {
    private static final int TOP = 45, MARGIN = 12, ROW_GAP = 5;

    private enum Choice {
        OPENING, GOOD_ENDING, BAD_ENDING, PENCIL_TEST, BACK;

        String label() {
            return switch (this) {
                case OPENING -> UiMessages.text("menu.soniccd.visual_mode.opening");
                case GOOD_ENDING -> UiMessages.text("menu.soniccd.visual_mode.good_ending");
                case BAD_ENDING -> UiMessages.text("menu.soniccd.visual_mode.bad_ending");
                case PENCIL_TEST -> UiMessages.text("menu.soniccd.visual_mode.pencil_test");
                case BACK -> UiMessages.text("menu.soniccd.visual_mode.back_to_title");
            };
        }
    }

    private static final Choice[] CHOICES = Choice.values();
    private final CdSceneCoordinator coordinator;
    private final CdProgramScreenRenderer pictureRenderer = new CdProgramScreenRenderer();
    private final PixelFontTextRenderer menuText = PixelFontTextRenderer.menu();
    private CdVideoProgram video;
    private CdSubCpuAudio videoAudio;
    private CdProgramScreenRenderer.Frame thankYouPreview;
    private InputHandler input;
    private int selected;
    private int frame;
    private boolean exiting;

    CdVisualModeScreen(CdSceneCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override public void initialize() {
        selected = 0;
        frame = 0;
        exiting = false;
        GameServices.audio().playMusic(CdSubCpuAudio.commandId("SCMD_STOPCDDA"));
    }

    @Override public void update(InputHandler input) {
        if (exiting) return;
        this.input = input;
        var pointer = MenuInput.pointer(input, width(), presentationHeight());
        boolean back = MenuInput.back(input) || pointer.inside() && pointer.rightPressed();
        if (video != null) {
            if (back) stopVideo();
            else {
                video.frame(CdTitleScreen.pad(input.logical().player1()));
                if (video.done()) {
                    stopVideo();
                    // VisualMode_GoodEnd (CD Initial Program/IPX.asm:1177-1187) runs Thank You
                    // after the ending returns. Back above cancels playback and restores the row.
                    if (CHOICES[selected] == Choice.GOOD_ENDING) {
                        thankYouPreview = CdThankYouPreview.load(coordinator.disc().assets());
                        pictureRenderer.reset();
                    }
                }
            }
            return;
        }
        if (thankYouPreview != null) {
            if (back) {
                MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
                thankYouPreview = null;
            }
            return;
        }
        if (back) {
            MenuFeedback.emit(MenuFeedback.Cue.CANCEL);
            exiting = true;
            return;
        }
        int previous = selected;
        if (MenuInput.up(input)) selected = Math.floorMod(selected - 1, CHOICES.length);
        else if (MenuInput.down(input)) selected = (selected + 1) % CHOICES.length;
        if (previous != selected) MenuFeedback.emit(MenuFeedback.Cue.NAVIGATE);
        boolean picked = false;
        if (pointer.leftPressed()) {
            for (int i = 0; i < CHOICES.length; i++) {
                if (pointer.over(MARGIN, rowY(i), width() - 2 * MARGIN, rowHeight())) {
                    selected = i;
                    picked = true;
                    break;
                }
            }
        }
        if (!picked && !MenuInput.accept(input)) {
            frame++;
            return;
        }
        MenuFeedback.emit(MenuFeedback.Cue.CONFIRM);
        if (CHOICES[selected] == Choice.BACK) exiting = true;
        else startVideo(CHOICES[selected]);
    }

    private void startVideo(Choice choice) {
        var disc = coordinator.disc().assets();
        var timing = CdLoadTiming.programTiming();
        if (choice == Choice.PENCIL_TEST) {
            video = new CdPencilTestProgram(disc, timing);
            pictureRenderer.reset();
            return;
        }
        var assets = new CdFmvAssets(coordinator.data());
        videoAudio = CdFmvScreen.subCpuAudio();
        var pcm = videoAudio != null ? videoAudio.beginProgramPcm() : CdSubCpuAudio.ProgramPcm.detached();
        try {
            video = switch (choice) {
                case OPENING -> CdFmvScreen.opening(assets, disc, pcm, timing);
                case GOOD_ENDING -> CdEndings.program(assets, disc, true, pcm, timing);
                case BAD_ENDING -> CdEndings.program(assets, disc, false, pcm, timing);
                default -> throw new IllegalArgumentException("No sound video for " + choice);
            };
        } catch (RuntimeException | Error failure) {
            stopVideo();
            throw failure;
        }
        pictureRenderer.reset();
    }

    private void stopVideo() {
        if (videoAudio != null) videoAudio.endProgramPcm();
        videoAudio = null;
        video = null;
    }

    @Override public void draw() {
        var graphics = GameServices.graphics();
        if (graphics.isHeadlessMode()) return;
        if (video != null) {
            pictureRenderer.draw(video.screen().frame());
            return;
        }

        int width = width();
        menuText.setProjectionMatrix(graphics.getProjectionMatrixBuffer());
        var font = menuText.font();
        // Native picture batches can leave blending off when they return to this menu.
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        glEnable(GL_BLEND);
        try {
            menuText.beginBatch();
            if (thankYouPreview == null) drawBrowser(font, width);
            else drawPreviewChrome(font, width);
        } finally {
            try {
                menuText.endBatch();
            } finally {
                if (!blendEnabled) glDisable(GL_BLEND);
            }
        }
        if (thankYouPreview != null) pictureRenderer.draw(thankYouPreview);
    }

    private void drawBrowser(PixelFont font, int width) {
        MenuChrome.page(font, width, UiMessages.text("menu.soniccd.visual_mode.title"), null, null);
        for (int i = 0; i < CHOICES.length; i++) {
            var label = MenuStyle.pageText(CHOICES[i].label(), width - 2 * MARGIN - 8,
                    MenuStyle.COMPACT, frame);
            MenuChrome.button(font, MARGIN, rowY(i), width - 2 * MARGIN, rowHeight(), label,
                    selected == i, false, frame);
        }
        int hintY = rowY(CHOICES.length) + 5;
        for (String line : MenuStyle.wrap(UiMessages.text("menu.soniccd.visual_mode.help"), width - 2 * MARGIN, MenuStyle.COMPACT)) {
            MenuChrome.compact(font, line, MARGIN, hintY, MenuChrome.MUTED);
            hintY += Math.max(MenuChrome.DESCRIPTION_PITCH, MenuChrome.compactLineHeight());
        }
        MenuChrome.footer(font, width, null, List.of(
                MenuChrome.upDown(input, UiMessages.text("menu.details.move")),
                MenuChrome.hint(MenuChrome.confirmKey(input), UiMessages.text("menu.details.choose")),
                MenuChrome.hint(MenuChrome.backKey(input), UiMessages.text("menu.details.back_caps"))),
                MenuChrome.controller(input), frame);
    }

    private void drawPreviewChrome(PixelFont font, int width) {
        MenuChrome.page(font, width, UiMessages.text("menu.soniccd.thank_you.title"), null,
                UiMessages.text("menu.soniccd.thank_you.description"));
        CdThankYouPreview.drawPanel(font, width);
        MenuChrome.footer(font, width, null,
                List.of(MenuChrome.hint(MenuChrome.backKey(input), UiMessages.text("menu.details.back_caps"))),
                MenuChrome.controller(input), frame);
    }

    @Override public void setClearColor() {
        if (GameServices.graphics().isHeadlessMode()) return;
        if (video != null) {
            var screen = video.screen();
            pictureRenderer.setClearColor(screen.frame(), screen.backdrop());
        } else glClearColor(0, 0, 0, 1);
    }

    @Override public void reset() {
        stopVideo();
        thankYouPreview = null;
        menuText.cleanup();
        input = null;
        exiting = false;
    }

    @Override public Exit consumeExit() {
        if (!exiting) return Exit.NONE;
        exiting = false;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }

    private static int width() { return Math.max(320, GameServices.graphics().getProjectionWidth()); }
    private static int rowHeight() { return Math.max(20, MenuChrome.compactLineHeight() + 6); }
    private static int rowY(int row) { return TOP + row * (rowHeight() + ROW_GAP); }
}
