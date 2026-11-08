package com.openggf.game.sonic2.competition.view;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.CameraSnapshot;
import java.util.List;
import java.util.Objects;
import static com.openggf.game.sonic2.competition.view.Sonic2CompetitionPlanes.PlaneId;

/** Semantic publication at the S2 VInt and split boundaries; no interrupt timing state. */
public final class Sonic2CompetitionViewRegisters implements RewindSnapshottable<Sonic2CompetitionViewRegisters.State> {
    public record State(boolean published, int foreground1, int background1, int foreground2, int background2,
                        List<Integer> horizontal, PlaneId upperPlane, PlaneId nextUpperPlane,
                        CameraSnapshot firstCamera, CameraSnapshot secondCamera,
                        Sonic2CompetitionSpritePages.Pair pages, List<Integer> paletteWords) {
        public State { horizontal = List.copyOf(horizontal); paletteWords = List.copyOf(paletteWords); }
    }
    private State state;
    public Sonic2CompetitionViewRegisters(CameraSnapshot first, CameraSnapshot second) {
        // Level (s2.asm:4930-4941): after initial DeformBgLayer, clear P1 FG,
        // write -screen_height to P2 FG, and clear the entire H-scroll page.
        state = new State(false, 0, 0, -224, 0, java.util.Collections.nCopies(224, 0),
                PlaneId.FOREGROUND_P1, PlaneId.FOREGROUND_P1, first, second,
                new Sonic2CompetitionSpritePages.Pair(Sonic2CompetitionSatBuilder.Page.empty(0),
                        Sonic2CompetitionSatBuilder.Page.empty(1)), java.util.Collections.nCopies(64, 0));
    }
    /**
     * Vint_Level (s2.asm:698-808), Vint_TitleCard (s2.asm:1005-1058),
     * Vint0_noWater (s2.asm:586-640), Do_ControllerPal (s2.asm:1153-1175).
     * The subsequent split selects P2 for the next interrupt's retained upper identity.
     */
    public void publish(PlcLifecyclePhase phase, int firstFactors, int secondFactors, int[] horizontal,
                        CameraSnapshot first, CameraSnapshot second, Sonic2CompetitionSpritePages.Pair pages,
                        List<Integer> paletteWords, int teleportTimer, boolean paused) {
        Objects.requireNonNull(phase);
        boolean ordinary = phase == PlcLifecyclePhase.ORDINARY_LEVEL || phase == PlcLifecyclePhase.NORMAL_PAUSE;
        boolean title = phase == PlcLifecyclePhase.LEVEL_TITLE_CARD;
        boolean lag = phase == PlcLifecyclePhase.LAG;
        boolean fade = phase == PlcLifecyclePhase.PALETTE_FADE;
        if (!ordinary && !title && !lag && !fade) throw new IllegalArgumentException("Not a level display phase: " + phase);
        if (horizontal.length != 224 || paletteWords.size() != 64) throw new IllegalArgumentException("Display page size");
        var palette = state.paletteWords();
        if (!lag && !(ordinary && teleportTimer != 0 && paused)) {
            palette = List.copyOf(paletteWords);
            // Vint_Level (s2.asm:707-734) decrements first and spares backdrop colour32.
            if (ordinary && teleportTimer != 0 && teleportTimer - 1 >= 16) {
                var flash = new java.util.ArrayList<>(java.util.Collections.nCopies(64, 0xEEE));
                flash.set(32, palette.get(32)); palette = List.copyOf(flash);
            }
        }
        state = new State(true, (short) (firstFactors >>> 16), (short) firstFactors,
                fade ? state.foreground2() : (short) (secondFactors >>> 16),
                fade ? state.background2() : (short) secondFactors,
                lag ? state.horizontal() : java.util.Arrays.stream(horizontal).boxed().toList(),
                ordinary || lag ? PlaneId.FOREGROUND_P1 : state.nextUpperPlane(), PlaneId.FOREGROUND_P2,
                first, second, Objects.requireNonNull(pages), palette);
    }
    @Override public String key() { return "sonic2.competition.view-registers"; }
    @Override public State capture() { return state; }
    @Override public void restore(State state) { this.state = Objects.requireNonNull(state); }
}
