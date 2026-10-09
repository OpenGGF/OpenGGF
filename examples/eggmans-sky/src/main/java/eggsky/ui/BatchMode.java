package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import eggsky.Game;
import eggsky.core.VoiceLine;
import eggsky.Mode;
import eggsky.core.Sound;
import eggsky.game.Production;
import java.util.ArrayList;
import java.util.List;

/** Explicit batch selection with a preview before any inventory mutation. */
public final class BatchMode implements Mode {
    private final Mode back;
    private final int output;
    private final int outCount;
    private final int[] inputs;
    private final int[] counts;
    private final boolean craft;
    private int row;
    private int maximum;
    private int age;

    public BatchMode(Mode back, int output, int outCount, int[] inputs, int[] counts, boolean craft) {
        this.back = back;
        this.output = output;
        this.outCount = outCount;
        this.inputs = inputs.clone();
        this.counts = counts.clone();
        this.craft = craft;
    }

    @Override public boolean live() { return false; }
    @Override public void enter(Game g) {
        maximum = Production.maximum(g.player, output, outCount, inputs, counts);
    }
    private int quantity() { return row == 0 ? 1 : row == 1 ? 5 : maximum; }

    @Override public void update(Game g) {
        age++;
        if (g.in.backPressed || g.in.menuPressed && !g.in.confirmPressed) {
            g.setMode(back);
            return;
        }
        int rows = craft ? 4 : 3;
        if (g.in.upRepeat || g.in.downRepeat) {
            row = Math.floorMod(row + (g.in.downRepeat ? 1 : -1), rows);
        }
        if (g.in.confirmPressed && age > 2) {
            if (row == 3) {
                g.player.pinned = g.player.pinned == output ? 0 : output;
                g.toast(g.player.pinned == 0 ? VoiceLine.OBJECTIVE_UNPINNED : VoiceLine.OBJECTIVE_PINNED, g.player.pinned == 0 ? "OBJECTIVE UNPINNED" : "RECIPE PINNED", Ui.CYAN);
                g.setMode(back);
            } else if (Production.make(g.player, output, outCount, inputs, counts, quantity())) {
                g.toast(craft ? VoiceLine.CRAFTED : VoiceLine.REFINED, (craft ? "CRAFTED " : "REFINED ") + quantity() * outCount + " "
                        + g.catalog.name(output), Ui.CYAN);
                g.sound.sfx(Sound.CLANK);
                g.setMode(back);
            } else {
                g.toast(VoiceLine.MATERIALS_MISSING, "CHECK MATERIALS, RESERVES AND CARGO SPACE", Ui.RED);
                g.sound.sfx(Sound.ERROR);
            }
        }
    }

    @Override public void draw(Game g, SceneCanvas c) {
        back.draw(g, c);
        Ui.dim(c, 0xC0);
        Ui.panel(c, 28, 22, g.width - 56, 180);
        Font f = g.font;
        f.draw(c, (craft ? "CRAFT " : "REFINE ") + g.catalog.name(output), 38, 32, Ui.GOLD);
        int n = row == 3 ? 1 : quantity();
        int y = 49;
        for (int i = 0; i < inputs.length; i++) {
            String line = g.catalog.name(inputs[i]) + " " + g.player.available(inputs[i]) + "/" + counts[i] * n;
            f.draw(c, line, 38, y, g.player.available(inputs[i]) >= counts[i] * n ? Ui.WHITE : Ui.RED);
            y += 10;
        }
        f.draw(c, "OUTPUT " + n * outCount + "   MAX BATCHES " + maximum, 38, 83, Ui.CYAN);
        f.draw(c, "Reserved cargo is excluded from inputs.", 38, 95, Ui.DIM);
        List<String> options = new ArrayList<>(List.of("MAKE 1 BATCH", "MAKE 5 BATCHES", "MAKE MAXIMUM (" + maximum + ")"));
        if (craft) {
            options.add(g.player.pinned == output ? "UNPIN RECIPE" : "PIN RECIPE AS OBJECTIVE");
        }
        for (int i = 0; i < options.size(); i++) {
            if (i == row) Ui.focus(c, 34, 112 + i * 15, g.width - 68, 12, g.ticks);
            f.draw(c, options.get(i), 40, 114 + i * 15, i == row ? Ui.WHITE : Ui.DIM);
        }
        f.draw(c, "A/ENTER: SELECT   B/X: BACK", 38, 186, Ui.GREY);
    }
}
