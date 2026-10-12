package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneKeys;
import eggsky.Game;
import eggsky.core.VoiceLine;
import eggsky.Mode;
import eggsky.core.Sound;
import eggsky.world.Planet;

/**
 * Renaming a discovered planet, as every explorer must: type letters, digits and spaces,
 * Backspace deletes, Enter keeps the name, Escape-free cancel with an empty name restores the
 * procedural one. Up/down cycles the letter under the cursor for pad players.
 */
public final class NameEntryMode implements Mode {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 -'";
    private static final int MAX = 18;
    private final Mode back;
    private final Planet planet;
    private final StringBuilder text = new StringBuilder();
    private int age;

    public NameEntryMode(Mode back, Planet planet) {
        this.back = back;
        this.planet = planet;
    }

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void enter(Game g) {
        text.append(g.displayName(planet.spec).toUpperCase());
    }

    @Override
    public void update(Game g) {
        age++;
        for (int i = 0; i < 26; i++) {
            if (g.ctx.keyPressed(SceneKeys.A + i) && text.length() < MAX) {
                text.append((char) ('A' + i));
                g.sound.sfx(Sound.PLINK, 1);
            }
        }
        for (int i = 0; i < 10; i++) {
            if (g.ctx.keyPressed(SceneKeys.DIGIT_0 + i) && text.length() < MAX) {
                text.append((char) ('0' + i));
                g.sound.sfx(Sound.PLINK, 1);
            }
        }
        if (g.ctx.keyPressed(SceneKeys.SPACE) && text.length() < MAX) {
            text.append(' ');
        }
        if (g.ctx.keyPressed(SceneKeys.MINUS) && text.length() < MAX) {
            text.append('-');
        }
        if (g.ctx.keyPressed(SceneKeys.BACKSPACE) && text.length() > 0) {
            text.setLength(text.length() - 1);
            g.sound.sfx(Sound.SWITCH, 1);
        }
        // Pad: up/down change the last letter, right adds a letter.
        boolean up = g.ctx.buttonRepeated(com.openggf.mods.scene.SceneButtons.UP) && !g.ctx.keyDown(SceneKeys.W);
        boolean down = g.ctx.buttonRepeated(com.openggf.mods.scene.SceneButtons.DOWN) && !g.ctx.keyDown(SceneKeys.S);
        if (up || down) {
            if (text.length() == 0) {
                text.append('A');
            }
            char last = text.charAt(text.length() - 1);
            int idx = Math.max(0, ALPHABET.indexOf(last));
            idx = Math.floorMod(idx + (up ? 1 : -1), ALPHABET.length());
            text.setCharAt(text.length() - 1, ALPHABET.charAt(idx));
        }
        if (g.ctx.buttonPressed(com.openggf.mods.scene.SceneButtons.RIGHT) && !g.ctx.keyDown(SceneKeys.D)
                && text.length() < MAX) {
            text.append('A');
        }
        boolean enter = g.ctx.keyPressed(SceneKeys.ENTER) || g.ctx.keyPressed(SceneKeys.KP_ENTER)
                || g.ctx.buttonPressed(com.openggf.mods.scene.SceneButtons.START);
        if (enter && age > 2) {
            String name = text.toString().trim();
            String key = planet.key;
            if (name.isEmpty()) {
                g.player.names.remove(key);
            } else {
                g.player.names.put(key, name.replace(';', ' ').replace('>', ' '));
            }
            g.sound.sfx(Sound.REGISTER);
            g.toast(VoiceLine.PLANET_NAMED, "PLANET NAMED " + g.displayName(planet.spec).toUpperCase(), Ui.GOLD);
            g.save();
            g.setMode(back);
        }
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        back.draw(g, c);
        Ui.dim(c, 0xA0);
        int w = 260;
        int x = (g.width - w) / 2;
        int y = 70;
        Ui.panel(c, x, y, w, 70);
        g.font.centre(c, "NAME THIS PLANET", g.width / 2, y + 8, Ui.GOLD);
        String shown = text + ((g.ticks / 15) % 2 == 0 ? "_" : " ");
        g.font.drawBig(c, shown, g.width / 2f, y + 24, 2, 0xFFFFFFFF, Ui.CYAN, 255);
        g.font.centre(c, "TYPE A NAME, ENTER TO KEEP (EMPTY RESTORES)", g.width / 2, y + 52, Ui.DIM);
    }
}
