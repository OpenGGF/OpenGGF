package slaytherobotnik.scene;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import slaytherobotnik.art.Art;
import slaytherobotnik.content.Content;
import slaytherobotnik.run.Run;
import slaytherobotnik.ui.SmallFont;

/**
 * The startup scene: owns the {@link Shell} and forwards the engine's calls to it. The
 * assets arrive as bytes read during registration (mod files are only open then).
 */
public final class SlayScene implements ModScene {
    private final byte[] fontText;
    private final byte[] iconText;
    private final byte[] cardText;
    private final byte[] relicText;
    private Shell shell;

    public SlayScene(byte[] fontText, byte[] iconText, byte[] cardText, byte[] relicText) {
        this.fontText = fontText;
        this.iconText = iconText;
        this.cardText = cardText;
        this.relicText = relicText;
    }

    @Override
    public void enter(SceneContext ctx) {
        SmallFont font = new SmallFont(ctx.art(), fontText);
        Art art = new Art(ctx, iconText, cardText, relicText);
        shell = new Shell(ctx, font, art, Content.build());
        shell.goNow(new TitleScreen());
    }

    @Override
    public void update(SceneContext ctx) {
        shell.update();
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        shell.draw(canvas);
    }

    @Override
    public void exit(SceneContext ctx) {
        if (shell != null) {
            shell.profile.save(ctx.storage());
        }
    }

    /**
     * Starts a run and jumps straight into one room, for screenshot tools and debugging:
     * {@code "sonic:42:fight:hcz:big_shaker"} or {@code "tails:7:event:event:slot_machine"}
     * (character, seed, then {@code fight} or {@code event} and the id, or {@code room} and a
     * room type such as {@code Shop:2} for a shop in act 2), {@code "compendium:2"},
     * {@code "kill"} to defeat every enemy in the current fight, or {@code "die"} to lose it.
     */
    public void debugJump(String command) {
        if (command.equals("kill") && shell.run != null
                && shell.run.room() instanceof slaytherobotnik.run.CombatRoom room) {
            // Defeat every enemy in the current fight (to see deaths and the victory flow).
            for (slaytherobotnik.core.Enemy e : room.combat().activeEnemies()) {
                room.combat().loseHp(e, e.hp());
            }
            room.combat().checkVictory();
            return;
        }
        if (command.equals("die") && shell.run != null
                && shell.run.room() instanceof slaytherobotnik.run.CombatRoom room) {
            // Lose the current fight (to see the game over screen).
            room.combat().loseHp(room.combat().player(), room.combat().player().hp());
            return;
        }
        if (command.startsWith("compendium")) {
            // "compendium:N" opens the compendium on tab N.
            String[] c = command.split(":");
            shell.goNow(new CompendiumScreen(c.length > 1 ? Integer.parseInt(c[1]) : 0));
            return;
        }
        String[] parts = command.split(":", 4);
        Run run = Run.start(shell.catalog, parts[0], Long.parseLong(parts[1]));
        shell.attach(run);
        if (parts[2].equals("fight")) {
            run.enterFight(parts[3]);
        } else if (parts[2].equals("room")) {
            // "sonic:7:room:Shop:2" enters a Shop / Rest / Treasure room, optionally in act 2.
            String[] room = parts[3].split(":");
            if (room.length > 1) {
                run.state().setPosition(Integer.parseInt(room[1]), run.state().floor(), run.state().actFloor(),
                        run.state().nodeX());
            }
            run.state().gainRings(300);
            run.enterRoom(room[0]);
        } else {
            run.state().setPosition(shell.catalog.event(parts[3]).acts().stream().min(Integer::compare).orElse(1),
                    run.state().floor(), run.state().actFloor(), run.state().nodeX());
            run.enterEvent(parts[3]);
        }
        shell.goNow(new RunScreen());
    }

    /** The shell, for tests that drive the scene directly. */
    public Shell shell() {
        return shell;
    }
}
