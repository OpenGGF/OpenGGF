package typinghog.hub;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;

/**
 * The title: the logo drops onto a real act from the player's ROMs where Sonic waits with
 * his keyboard backpack, letters fluttering out of it. A word plate asks for START: typing it
 * (or Enter, or Start on a pad) sends Sonic spinning off to the main menu.
 */
final class TitleScreen implements Screen {
    private static final String WORD = "start";
    private static final int DROP = 36;
    private static final int EXIT = 40;
    private static final int SONIC_X = 92;

    private long opened;
    private int typed;
    private long shakeTick = -100;
    private long exitTick = -1;
    private boolean skipIntro;

    @Override public String name() { return "TITLE"; }

    void skipIntro() { skipIntro = true; }

    @Override public void open(HubScene hub) {
        opened = skipIntro ? hub.ticks - DROP - 30 : hub.ticks;
        skipIntro = false;
        typed = 0;
        exitTick = -1;
        hub.audio.theme(HubAudio.Theme.TITLE);
    }

    @Override public void update(HubScene hub) {
        long age = hub.ticks - opened;
        if (exitTick >= 0) {
            if (hub.ticks - exitTick >= EXIT) hub.go(hub.menu);
            return;
        }
        if (age < DROP / 2) return;
        Controls in = hub.controls;
        for (char c : in.typed().toCharArray()) {
            char lower = Character.toLowerCase(c);
            if (!Character.isLetterOrDigit(lower)) continue;
            if (lower == WORD.charAt(typed)) {
                typed++;
                hub.audio.cue(HubAudio.Cue.KEY);
                if (typed == WORD.length()) {
                    begin(hub);
                    return;
                }
            } else {
                typed = lower == WORD.charAt(0) ? 1 : 0;
                shakeTick = hub.ticks;
                hub.audio.cue(HubAudio.Cue.ERROR);
            }
        }
        if (in.backspace() && typed > 0) typed--;
        if (in.accept()) begin(hub);
    }

    private void begin(HubScene hub) {
        typed = WORD.length();
        exitTick = hub.ticks;
        hub.audio.cue(HubAudio.Cue.CONFIRM);
    }

    @Override public void draw(HubScene hub, SceneCanvas c) {
        long t = hub.ticks;
        long age = t - opened;
        hub.stage.draw(c, t);
        int w = c.width();
        // Sonic, waiting; on START he curls up and rockets off.
        int floor = hub.stage.floorRow(SONIC_X, t);
        if (exitTick < 0) {
            Hero.sonic(c, hub.art, hub.game, HubArt.ANIM_WAIT, t, SONIC_X, floor, false, 1f);
            letters(hub, c, t, floor);
        } else {
            long e = t - exitTick;
            int anim = e < 10 ? HubArt.ANIM_ROLL : HubArt.ANIM_RUN;
            int x = SONIC_X + (int) (e < 10 ? 0 : (e - 10) * (e - 10) / 2);
            Hero.sonic(c, hub.art, hub.game, anim, t, x, hub.stage.floorRow(Math.min(w - 1, x), t), false, 1f);
        }
        // The logo drops in with a bounce.
        SceneImage logo = hub.art.image("logo");
        float d = Math.min(1, age / (float) DROP);
        float bounce = d < 1 ? (float) (1 - Math.pow(1 - d, 3)) : 1;
        int settle = age > DROP && age < DROP + 12 ? (int) Math.round(Math.sin((age - DROP) / 12.0 * Math.PI) * -4) : 0;
        if (logo != null) {
            int ly = (int) (-logo.height() + (logo.height() + 10) * bounce) + settle;
            c.draw(logo, (w - logo.width()) / 2f, ly);
        } else {
            hub.font.outline(c, "THE TYPING OF THE HEDGEHOG", w / 2 - hub.font.width("THE TYPING OF THE HEDGEHOG", 2) / 2,
                    30, Gfx.WHITE, 0xFF000000, 2);
        }
        if (age < DROP / 2) return;
        // The prompt plate.
        int shake = t - shakeTick < 10 ? (int) Math.round(Math.sin((t - shakeTick) * 2.2) * 3) : 0;
        int px = w / 2 + 64, py = 128;
        float pop = Math.min(1, (age - DROP / 2f) / 10f);
        if (pop > 0.2f) {
            Gfx.plate(c, hub.font, "START", typed, px, py, typed > 0 ? Gfx.GOLD : 0xFFE0E0E0, 2, shake);
            hub.ring(c, px - 44 + shake, py + 11, t);
            if ((t / 24) % 2 == 0 || exitTick >= 0) {
                hub.font.centred(c, "TYPE START  OR PRESS ENTER", px, py + 32, Gfx.WHITE, 1);
            }
        }
        // Which cartridges are plugged in.
        int bx = w - 8;
        for (String g : java.util.List.of("s3k", "s2", "s1")) {
            String label = HubScene.gameShort(g);
            boolean on = hub.available(g);
            int lw = hub.font.width(label, 1) + 8;
            bx -= lw + 3;
            Gfx.panel(c, bx, c.height() - 18, lw, 14, on ? 0xE0183C9C : 0x90101020, on ? Gfx.GOLD : 0x80505868);
            hub.font.draw(c, label, bx + 4, c.height() - 15, on ? Gfx.WHITE : 0x80808898, 1);
        }
        hub.font.shadow(c, "AN OPENGGF EXAMPLE MOD", 8, c.height() - 14, 0xC0FFFFFF, 1);
    }

    /** Letters drifting up out of Sonic's keyboard. */
    private void letters(HubScene hub, SceneCanvas c, long t, int floor) {
        String pool = "TYPINGHEDGEHOG";
        for (int i = 0; i < 5; i++) {
            long life = (t + i * 37) % 110;
            if (life > 80) continue;
            int seed = (int) ((t + i * 37) / 110) * 7 + i * 13;
            char ch = pool.charAt(Math.floorMod(seed, pool.length()));
            float rise = life / 80f;
            int x = SONIC_X - 14 + (int) Math.round(Math.sin(life * 0.12 + i) * 6) - (int) (rise * 10);
            int y = floor - 36 - (int) (rise * 46);
            int a = (int) (255 * (1 - rise));
            hub.font.draw(c, String.valueOf(ch), x + 1, y + 1, (a / 2) << 24, 1);
            hub.font.draw(c, String.valueOf(ch), x, y, (a << 24) | 0xFFE060, 1);
        }
    }
}
