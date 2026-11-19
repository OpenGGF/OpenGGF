package com.openggf.game.soniccd.title;

import com.openggf.control.InputHandler;
import com.openggf.control.MenuInput;
import com.openggf.data.compression.NemesisReader;
import com.openggf.game.GameServices;
import com.openggf.game.MenuChrome;
import com.openggf.game.nativemenu.NativeMenuScreen;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.fmv.CdProgramScreenState;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer.Frame;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer.Sprite;
import com.openggf.game.soniccd.release.CdRetailReleases;
import com.openggf.graphics.PixelFontTextRenderer;
import com.openggf.i18n.UiMessages;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/** OpenGGF contact sheet of tiles supplied by the authenticated USA disc. */
public final class CdCominSoonPreviewScreen implements NativeMenuScreen {
    private static final int PALETTE_START = 0x2B8;
    private static final int TILES_START = 0x2D8;
    private static final int TILES_END = 0x7DC;
    private static final int TILE_COUNT = 150;
    private static final int NATIVE_WIDTH = 320;
    private static final int COLUMNS = 15, PITCH = 12, SHEET_TOP = 54, SHEET_PADDING = 5;
    private static final int SHEET_WIDTH = (COLUMNS - 1) * PITCH + 8;
    private static final int SHEET_HEIGHT = (TILE_COUNT / COLUMNS - 1) * PITCH + 8;
    private static final int BACK_TOP = 181, BACK_WIDTH = 128, BACK_HEIGHT = 20;

    private final CdProgramAssets disc;
    private final CdProgramScreenRenderer renderer = new CdProgramScreenRenderer();
    private final PixelFontTextRenderer text = PixelFontTextRenderer.menu();
    private Frame picture;
    private InputHandler input;
    private int ticks;
    private boolean exiting;

    public CdCominSoonPreviewScreen(CdProgramAssets disc) {
        this.disc = disc;
    }

    @Override
    public void initialize() {
        try {
            picture = loadPicture(disc);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot open the Comin' Soon disc-art preview", failure);
        }
        renderer.reset();
        ticks = 0;
        exiting = false;
    }

    private static Frame loadPicture(CdProgramAssets disc) throws IOException {
        if (CdRetailReleases.find(disc.identity()).orElse(null) != CdRetailReleases.USA
                || disc.header().observedVariant().orElse(null) != CdReleaseVariant.USA) {
            throw new IOException("The Comin' Soon preview requires an authenticated USA disc");
        }
        CdNativeFile file = CdNativeFile.COMINSOON;
        var entry = disc.entry(file);
        if (!"/COME__.MMD;1".equals(entry.path()) || entry.directory() || entry.version() != 1
                || entry.size() != 8952 || entry.extentBlock() != 17103) {
            throw new IOException("Unexpected USA Comin' Soon file extent");
        }

        // Start (Comin Soon/Main.asm:49-95) and LoadGraphics (Comin Soon/Main.asm:495-506) identify the palette and
        // compressed tile declarations. Use only those file ranges for the OpenGGF contact sheet.
        byte[] art = disc.read(file, PALETTE_START, TILES_END - PALETTE_START);
        ByteBuffer words = ByteBuffer.wrap(art);
        var screen = new CdProgramScreenState();
        for (int i = 0; i < 16; i++) screen.setPalette(i, words.getShort());
        int tileOffset = TILES_START - PALETTE_START;
        if ((words.getShort(tileOffset) & 0x7FFF) != TILE_COUNT) {
            throw new IOException("Unexpected USA Comin' Soon Nemesis tile declaration");
        }
        byte[] decoded;
        try (var input = Channels.newChannel(new ByteArrayInputStream(art, tileOffset, TILES_END - TILES_START))) {
            decoded = NemesisReader.decompress(input);
        }
        if (decoded.length != TILE_COUNT * CdProgramScreenState.TILE_BYTES) {
            throw new IOException("Unexpected USA Comin' Soon decoded art size");
        }
        screen.writeTiles(0, decoded, 0, decoded.length);
        screen.setDisplay(true);
        Frame frame = screen.frame();
        List<Sprite> tiles = new ArrayList<>(TILE_COUNT);
        for (int i = 0; i < TILE_COUNT; i++) {
            int x = (NATIVE_WIDTH - SHEET_WIDTH) / 2 + (i % COLUMNS) * PITCH;
            int y = SHEET_TOP + (i / COLUMNS) * PITCH;
            tiles.add(new Sprite(x, y, 0, i));
        }
        return new Frame(frame.tiles(), frame.dirtyTiles(), frame.planeA(), frame.planeB(),
                frame.palette(), frame.scrollA(), frame.scrollB(), List.copyOf(tiles), NATIVE_WIDTH, true);
    }

    @Override
    public void update(InputHandler input) {
        this.input = input;
        int width = width();
        var pointer = MenuInput.pointer(input, width, presentationHeight());
        exiting |= MenuInput.back(input) || MenuInput.accept(input)
                || pointer.leftPressed() && pointer.over((width - BACK_WIDTH) / 2, BACK_TOP, BACK_WIDTH, BACK_HEIGHT);
        ticks++;
    }

    @Override
    public void draw() {
        var graphics = GameServices.graphics();
        if (picture == null || graphics.isHeadlessMode()) return;
        int width = width();
        text.setProjectionMatrix(graphics.getProjectionMatrixBuffer());
        var font = text.font();
        boolean blendEnabled = glIsEnabled(GL_BLEND);
        glEnable(GL_BLEND);
        try {
            text.beginBatch();
            try {
                MenuChrome.page(font, width, UiMessages.text("menu.soniccd.comin_soon.title"),
                        null, UiMessages.text("menu.soniccd.comin_soon.description"));
                MenuChrome.panel(font, (width - SHEET_WIDTH) / 2 - SHEET_PADDING, SHEET_TOP - SHEET_PADDING,
                        SHEET_WIDTH + 2 * SHEET_PADDING, SHEET_HEIGHT + 2 * SHEET_PADDING);
                MenuChrome.button(font, (width - BACK_WIDTH) / 2, BACK_TOP, BACK_WIDTH, BACK_HEIGHT,
                        UiMessages.text("menu.details.back"), true, false, ticks);
                MenuChrome.footer(font, width, null, List.of(MenuChrome.hint(MenuChrome.backKey(input),
                        UiMessages.text("menu.details.back_caps"))), MenuChrome.controller(input));
            } finally {
                text.endBatch();
            }
            renderer.draw(picture);
        } finally {
            if (blendEnabled) glEnable(GL_BLEND);
            else glDisable(GL_BLEND);
        }
    }

    @Override public void setClearColor() {
        if (!GameServices.graphics().isHeadlessMode()) glClearColor(0, 0, 0, 1);
    }

    @Override public void reset() {
        picture = null;
        text.cleanup();
        input = null;
        exiting = false;
    }

    @Override public Exit consumeExit() {
        if (!exiting) return Exit.NONE;
        exiting = false;
        return new Exit(Destination.TITLE_SCREEN, null, false);
    }

    private static int width() {
        return Math.max(NATIVE_WIDTH, GameServices.graphics().getProjectionWidth());
    }
}
