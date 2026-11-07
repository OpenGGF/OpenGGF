package com.openggf.game.sonic2.competition.view;

import com.openggf.game.rewind.RewindSnapshottable;
import java.util.List;

/** Native block-crossing latches and VInt camera/flag copies; never tracks a player. */
public final class Sonic2CompetitionPlaneScroll implements RewindSnapshottable<Sonic2CompetitionPlaneScroll.State> {
    public record Player(int x, int y, int horizontalLatch, int verticalLatch, int flags) { }
    public record State(List<Player> live, List<Player> copied, boolean redraw, boolean postLoadScrollClear) {
        public State { live = List.copyOf(live); copied = List.copyOf(copied); }
    }
    private final Player[] live = {new Player(0,0,0,0,0), new Player(0,0,0,0,0)};
    private final Player[] copied = live.clone();
    private boolean redraw;
    private boolean postLoadScrollClear = true;
    public boolean postLoadScrollClear() { return postLoadScrollClear; }

    /** DeformBgLayer clears flags before Scroll_lock, retaining camera/latches when frozen. */
    public void completeCameraPass(int slot, int oldX, int oldY, int x, int y, boolean frozen) {
        var old = live[slot];
        int horizontal = old.horizontalLatch(), vertical = old.verticalLatch(), flags = 0;
        if (frozen) {
            live[slot] = new Player(old.x(), old.y(), horizontal, vertical, 0);
            return;
        }
        // SetHorizScrollFlags (s2.asm:18015-18034), SetVertiScrollFlags (s2.asm:18301-18320).
        // The latch is compared for equality, not inferred from camera distance.
        if ((x & 16) == horizontal) {
            horizontal ^= 16;
            flags |= (short) (x - oldX) < 0 ? 4 : 8;
        }
        if ((y & 16) == vertical) {
            vertical ^= 16;
            flags |= (short) (y - oldY) < 0 ? 1 : 2;
        }
        live[slot] = new Player(x & 65535, y & 65535, horizontal, vertical, flags);
        if (slot == 1) postLoadScrollClear = false;
    }
    /** Cold Camera_RAM, then LevelSizeLoad. The host supplies retained state on later loads. */
    public void initialize(int firstX, int firstY, int secondX, int secondY) {
        var zero = new Player(0, 0, 0, 0, 0);
        initializeLevel(new State(List.of(zero, zero), List.of(zero, zero), false, true),
                firstX, firstY, secondX, secondY);
    }
    /**
     * LevelSizeLoad (s2.asm:14665-14683) clears live/copied scroll flags,
     * preserves copy positions, and seeds only P1 crossing latches to $1010
     * (s2.asm:14707). The caller owns the actual retained camera-state lifetime.
     * Level (s2.asm:4930-4941) subsequently clears the display scroll page.
     */
    public void initializeLevel(State retained, int firstX, int firstY, int secondX, int secondY) {
        java.util.Objects.requireNonNull(retained);
        if (retained.live().size() != 2 || retained.copied().size() != 2)
            throw new IllegalArgumentException("Two native camera banks required");
        var second = retained.live().get(1);
        live[0] = new Player(firstX & 65535, firstY & 65535, 16, 16, 0);
        live[1] = new Player(secondX & 65535, secondY & 65535,
                second.horizontalLatch(), second.verticalLatch(), 0);
        for (int slot = 0; slot < 2; slot++) {
            var old = retained.copied().get(slot);
            copied[slot] = new Player(old.x(), old.y(), old.horizontalLatch(), old.verticalLatch(), 0);
        }
        redraw = false;
        postLoadScrollClear = true;
    }
    /** Camera_RAM clear on the native menu entry path: TitleScreen (s2.asm:4269-4273).
     * Camera_Positions_Copy / Camera_Positions_Copy_P2 (s2.constants.asm:1268-1288).
     * The host, rather than this owner, detects that menu event.
     */
    public void menuClear() {
        var zero = new Player(0, 0, 0, 0, 0);
        java.util.Arrays.fill(live, zero); java.util.Arrays.fill(copied, zero);
        redraw = false;
        postLoadScrollClear = true;
    }
    /** Title-card VInt copies only P1; P2 must retain its last ordinary copy. */
    public void copyAtVInt(boolean titleCard) {
        copied[0] = live[0];
        if (!titleCard) copied[1] = live[1];
    }
    public Player copy(int slot) { return copied[slot]; }
    public void requestRedraw() { redraw = true; }
    public void drawForeground(Sonic2CompetitionPlanes planes, Sonic2CompetitionPlaneDraw draw) {
        for (int slot = 0; slot < 2; slot++) {
            var p = copied[slot];
            planes.apply(draw.foregroundUpdates(slot, new Sonic2CompetitionPlaneDraw.CameraCopy(p.x(), p.y()),
                    p.flags(), slot == 0 && redraw));
        }
        redraw = false;
    }
    /** teleport_swap_table (s2.asm:26185-26188) swaps latches/live flags/copies, never plane images. */
    public void swapPlayers() {
        var a = live[0]; live[0] = live[1]; live[1] = a;
        a = copied[0]; copied[0] = copied[1]; copied[1] = a;
    }
    @Override public String key() { return "sonic2.competition.plane-scroll"; }
    @Override public State capture() { return new State(List.of(live), List.of(copied), redraw, postLoadScrollClear); }
    @Override public void restore(State state) {
        for (int i = 0; i < 2; i++) { live[i] = state.live().get(i); copied[i] = state.copied().get(i); }
        redraw = state.redraw();
        postLoadScrollClear = state.postLoadScrollClear();
    }
}
