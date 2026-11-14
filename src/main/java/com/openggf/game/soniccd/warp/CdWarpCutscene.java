package com.openggf.game.soniccd.warp;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.media.CdTimeWarpPrograms;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import com.openggf.game.soniccd.scene.CdWarpAssets;
import com.openggf.game.soniccd.scene.CdWarpScene;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/**
 * The time-warp cutscene program ({@code WARP}, {@code SCMD_WARP}, Time Warp Cutscene/Main.asm) as the
 * program-flow lane runs it between two stages ({@code RunLevel}, IPX.asm:555-561). The game logic is
 * the carried {@link CdWarpScene} (90-frame delay, fade from white, 240-frame main loop that Start
 * ends, fade to white; Main.asm:125-199); this owner steps it once per frame and builds the
 * picture the hardware shows from its state: the art at VRAM 0 ({@code #$40000000}), the background
 * map as plane B at $E000 with the per-screen vertical scroll ({@code vscrollBG} into VSRAM 2,
 * Main.asm:270-272), the displayed palette and sprite table, and the display bit.
 */
public final class CdWarpCutscene {
    /** The 4bpp tile store the renderer reads: VRAM $0000-$FFFF as 0x800 tiles. */
    private static final int TILES = 0x800;

    private final CdWarpScene scene;
    private final CdWarpAssets assets;
    private final byte[] tiles = new byte[TILES * 32];
    private final BitSet dirty = new BitSet(TILES);
    private final int[] planeA = new int[64 * 32];
    private final int[] planeB = new int[64 * 32];
    private CdWarpScene.State state;
    private final List<Integer> sounds = new ArrayList<>();
    private int frames;

    public CdWarpCutscene(CdWarpAssets assets, int initialVIntCounter) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.scene = new CdWarpScene(assets);
        this.state = scene.initial(initialVIntCounter);
        byte[] art = assets.art().copy();
        System.arraycopy(art, 0, tiles, 0, Math.min(art.length, tiles.length));
        dirty.set(0, TILES);
        byte[] plane = assets.backgroundPlane().copy();
        for (int i = 0; i < planeB.length; i++) planeB[i] = ((plane[2 * i] & 0xFF) << 8) | (plane[2 * i + 1] & 0xFF);
    }

    /** The disc's authenticated USA WARP program. */
    public static CdWarpAssets assets(CdDiscDataSource disc) {
        try {
            return CdWarpAssets.fromPrograms(CdTimeWarpPrograms.fromPrograms(disc.assets()));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** One V-blank pulse with the native pad byte; true once the program has returned ({@code rts}). */
    public boolean frame(int pad) {
        if (done()) return true;
        CdWarpScene.Result result = scene.step(state, pad & 0xFF);
        state = result.state();
        for (CdWarpScene.SoundRequest sound : result.sounds()) sounds.add(sound.nativeId());
        frames++;
        return done();
    }

    public boolean done() { return state.phase() == CdWarpScene.Phase.RETURNED; }

    /** Frames run since the program started. */
    public int frames() { return frames; }

    public CdWarpScene.State state() { return state; }

    /** FM sound IDs queued since the last call ({@code FM_D8} into {@code FMDrvQueue2}, Main.asm:166-168). */
    public List<Integer> drainSounds() {
        List<Integer> out = List.copyOf(sounds);
        sounds.clear();
        return out;
    }

    /** The picture of the last pulse. */
    public CdProgramScreenRenderer.Frame screen() {
        int[] palette = new int[64];
        state.displayedPalette().ifPresent(bytes -> {
            for (int i = 0; i < 64; i++) palette[i] = bytes.unsignedByte(2 * i) << 8 | bytes.unsignedByte(2 * i + 1);
        });
        List<CdProgramScreenRenderer.Sprite> sprites = new ArrayList<>();
        var sat = state.displayedSprites();
        for (int i = 0; i < state.displayedSpriteCount(); i++) {
            int at = i * 8;
            int y = (sat.unsignedByte(at) << 8 | sat.unsignedByte(at + 1)) & 0x3FF;
            int size = sat.unsignedByte(at + 2);
            int tile = sat.unsignedByte(at + 4) << 8 | sat.unsignedByte(at + 5);
            int x = (sat.unsignedByte(at + 6) << 8 | sat.unsignedByte(at + 7)) & 0x1FF;
            sprites.add(new CdProgramScreenRenderer.Sprite(x - 128, y - 128, size, tile));
        }
        return new CdProgramScreenRenderer.Frame(tiles, dirty, planeA, planeB, palette, line -> 0, line -> 0,
                sprites, 320, state.displayEnabled(), 0, state.displayedVscrollBgWord());
    }

    /** The decoded art, for tests. */
    CdWarpAssets assets() { return assets; }
}
