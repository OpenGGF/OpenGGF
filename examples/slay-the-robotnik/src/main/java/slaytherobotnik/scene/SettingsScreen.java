package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;

/** Options kept in the profile: combat speed, screen shake, music and sound effects. */
final class SettingsScreen implements Screen {
    /** Profile keys; each is 0 (the default) or 1. */
    static final String FAST_COMBAT = "setting.fast";
    static final String NO_SHAKE = "setting.noshake";
    static final String NO_MUSIC = "setting.nomusic";
    static final String NO_SFX = "setting.nosfx";

    private final Hotspots spots = new Hotspots();

    private void layout(Shell shell) {
        spots.clear();
        int x = shell.width() / 2 - 100;
        int y = 60;
        for (String id : new String[] {FAST_COMBAT, NO_SHAKE, NO_MUSIC, NO_SFX}) {
            spots.add(id, x, y, 200, 16);
            y += 22;
        }
        spots.add("back", shell.width() / 2 - 40, 190, 80, 16);
    }

    @Override
    public void update(Shell shell) {
        layout(shell);
        String before = spots.focused();
        String picked = spots.update(shell.in);
        if (before != null && !before.equals(spots.focused())) {
            shell.sfx(Sounds.SFX_CURSOR);
        }
        if (shell.in.back || shell.in.mouse.rightPressed() || "back".equals(picked)) {
            shell.profile.save(shell.ctx.storage());
            shell.sfx(Sounds.SFX_SWITCH);
            shell.go(new TitleScreen());
            return;
        }
        if (picked != null) {
            shell.profile.set(picked, 1 - shell.profile.get(picked));
            shell.profile.save(shell.ctx.storage());
            if (picked.equals(NO_MUSIC)) {
                if (shell.profile.get(NO_MUSIC) == 1) {
                    shell.ctx.audio().stopMusic();
                } else {
                    shell.resumeMusic();
                }
            }
            shell.sfx(Sounds.SFX_STARPOST);
        }
    }

    private static String label(Shell shell, String id) {
        boolean on = shell.profile.get(id) == 1;
        return switch (id) {
            case FAST_COMBAT -> "COMBAT SPEED: " + (on ? "FAST" : "NORMAL");
            case NO_SHAKE -> "SCREEN SHAKE: " + (on ? "OFF" : "ON");
            case NO_MUSIC -> "MUSIC: " + (on ? "OFF" : "ON");
            default -> "SOUND EFFECTS: " + (on ? "OFF" : "ON");
        };
    }

    @Override
    public void draw(Shell shell, SceneCanvas c) {
        Backdrops.menu(c, shell.width(), shell.height(), shell.ticks);
        var f = shell.font;
        f.drawOutlined(c, "SETTINGS", (shell.width() - f.width("SETTINGS") * 2) / 2, 24, Colors.GOLD, 2);
        layout(shell);
        for (Hotspots.Spot s : spots.spots()) {
            String text = s.id().equals("back") ? "BACK" : label(shell, s.id());
            Gfx.button(c, f, text, s.x(), s.y(), s.w(), s.h(), spots.isFocused(s.id()), true, shell.ticks);
        }
        String hint = "HOLD A BUTTON IN A FIGHT TO SPEED IT UP";
        f.drawShadowed(c, hint, (shell.width() - f.width(hint)) / 2, 160, Colors.TEXT_DIM);
    }
}
