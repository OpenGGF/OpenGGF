package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneSprite;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import slaytherobotnik.core.Card;
import slaytherobotnik.core.RestOption;
import slaytherobotnik.run.RestRoom;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Ease;
import slaytherobotnik.ui.Gfx;
import slaytherobotnik.ui.Hotspots;
import slaytherobotnik.ui.SmallFont;

/**
 * A Starpost: rest, tune up a card, or use a relic's option. Resting crouches the hero for a
 * breather while the healing rises off them; a tuned-up card appears beside the post and flashes
 * into its upgrade as the post's ball spins.
 */
final class RestView implements RunScreen.RoomView {
    private final RestRoom room;
    private final Hotspots spots = new Hotspots();
    /** Ticks left of the Starpost ball's orbit. */
    private int spin;
    /** The row the stage's floor sits on: the hero and the Starpost stand above the options. */
    private static final int GROUND = 130;
    /**
     * Obj_StarPost (sonic3k.asm loc_2D12E): the touched post's ball orbits for $20 frames, its
     * angle stepping -$10 a frame from -$40, 12 pixels ($C00 * sine >> 16) around a point $14
     * above the post; then the post flashes frames 0 and 4 every 4 frames (Ani_Starpost_Spinning).
     */
    private static final int ORBIT_FRAMES = 0x20;
    /** Mod timing for the tuned-up card: rising in, then the flash into its upgrade. */
    private static final int CARD_RISE = 14;
    private static final int CARD_FLASH = 30;
    /** Frames since resting (the healing popup and sparkles), or -1. */
    private int restAge = -1;
    private int healed;
    /** The card the smith option upgraded, once its pick is made, and frames since. */
    private Card smithed;
    private int smithAge;
    /** Cards already upgraded before the smith pick, to tell which one it upgraded. */
    private final Set<Card> upgradedBefore = Collections.newSetFromMap(new IdentityHashMap<>());

    RestView(RestRoom room) {
        this.room = room;
    }

    private void layout(Shell shell) {
        spots.clear();
        int w = shell.width();
        if (room.chosen() != null) {
            spots.add("go", w / 2 - 50, 196, 100, 16);
            return;
        }
        List<RestOption> options = room.options();
        int bw = 96;
        int total = options.size() * bw + (options.size() - 1) * 10;
        for (int i = 0; i < options.size(); i++) {
            spots.add("o" + i, (w - total) / 2 + i * (bw + 10), 140, bw, 44, options.get(i).enabled());
        }
    }

    @Override
    public void update(Shell shell, RunScreen screen) {
        if (spin > 0) {
            spin--;
        }
        if (restAge >= 0) {
            restAge++;
        }
        if (smithed != null) {
            smithAge++;
        } else if ("smith".equals(room.chosen()) && shell.run.deckChoice() == null) {
            // The pick is made: the card it upgraded appears, and only now does the post spin.
            for (Card card : shell.run.state().deck()) {
                if (card.upgraded() && !upgradedBefore.contains(card)) {
                    smithed = card;
                    smithAge = 0;
                    spin = ORBIT_FRAMES;
                    shell.sfx(Sounds.SFX_STARPOST);
                    break;
                }
            }
        }
        layout(shell);
        String picked = spots.update(shell.in);
        if (picked == null) {
            return;
        }
        if (picked.equals("go")) {
            shell.sfx(Sounds.SFX_SPRING);
            room.proceed();
            return;
        }
        RestOption option = room.options().get(Integer.parseInt(picked.substring(1)));
        int hpBefore = shell.run.state().hp();
        upgradedBefore.clear();
        for (Card card : shell.run.state().deck()) {
            if (card.upgraded()) {
                upgradedBefore.add(card);
            }
        }
        if (room.choose(option.id()) && !option.id().equals("smith")) {
            spin = ORBIT_FRAMES;
            shell.sfx(Sounds.SFX_STARPOST);
            healed = shell.run.state().hp() - hpBefore;
            restAge = 0;
        }
    }

    /** The ROM's Starpost standing at {@code x} on {@code ground}: idle, its ball orbiting, then flashing. */
    private void drawStarpost(Shell shell, SceneCanvas c, int x, int ground) {
        SceneSprite idle = shell.art.romFrame("starpost", 0);
        if (idle == null) {
            var icon = shell.art.icon("node_rest");
            c.draw(icon, x - icon.width() * 1.5f, ground - icon.height() * 3, SceneDraw.plain().withScale(3));
            return;
        }
        // Every frame shares the object's origin; the idle post's bottom stands on the ground.
        float originY = ground - (idle.height() - idle.originY());
        int frame = spin > 0 ? 1 : room.chosen() != null && (shell.ticks / 4) % 2 == 1 ? 4 : 0;
        c.draw(shell.art.romFrame("starpost", frame), x, originY, SceneDraw.plain());
        if (spin > 0) {
            int angle = (-0x10 * (ORBIT_FRAMES - spin) - 0x40) & 0xFF;
            double radians = angle * Math.PI * 2 / 256;
            c.draw(shell.art.romFrame("starpost", 2), Math.round(x + Math.cos(radians) * 12),
                    Math.round(originY - 0x14 + Math.sin(radians) * 12), SceneDraw.plain());
        }
    }

    /** The healing rising off the resting hero, with sparkles about them. */
    private void drawRest(Shell shell, SceneCanvas c, int heroX, int feet) {
        if (restAge < 0 || restAge >= 80) {
            return;
        }
        if (restAge < 60) {
            EventArt.sparkles(shell, c, heroX, feet - 18, 16 + restAge / 6, shell.ticks);
        }
        if (healed > 0) {
            String text = "+" + healed + " HP";
            float alpha = restAge < 60 ? 1f : 1f - (restAge - 60) / 20f;
            shell.font.drawOutlined(c, text, heroX - shell.font.width(text), Math.round(feet - 44 - restAge * 0.4f),
                    Colors.alpha(Colors.TEXT_GOOD, alpha), 2);
        }
    }

    /** The tuned-up card beside the post: rising in, then flashing into its upgrade. */
    private void drawSmithed(Shell shell, RunScreen screen, SceneCanvas c, int cx, int feet) {
        if (smithed == null) {
            return;
        }
        int x = cx - CardRenderer.SMALL_W / 2;
        int top = feet - CardRenderer.SMALL_H - 6;
        if (smithAge < CARD_RISE) {
            top += Math.round((1f - Ease.outCubic(smithAge / (float) CARD_RISE)) * 30);
        }
        boolean upgraded = smithAge >= CARD_FLASH + 4;
        screen.cards.drawSmall(c, upgraded ? smithed : new Card(smithed.def(), false), null, x, top, true, false);
        if (smithAge >= CARD_FLASH && smithAge < CARD_FLASH + 10) {
            float k = 1f - Math.abs(smithAge - CARD_FLASH - 4) / 6f;
            c.fill(x, top, CardRenderer.SMALL_W, CardRenderer.SMALL_H, Colors.alpha(Colors.WHITE, Math.max(0f, k)));
        }
        if (upgraded) {
            EventArt.sparkles(shell, c, cx, top + CardRenderer.SMALL_H / 2, 30, shell.ticks);
        }
    }

    @Override
    public void draw(Shell shell, RunScreen screen, SceneCanvas c) {
        int w = shell.width();
        LevelStages.Placement stage = LevelStages.draw(shell, c, GROUND);
        c.fill(0, 0, w, shell.height(), 0x30000020);
        SmallFont f = shell.font;
        f.drawOutlined(c, "STARPOST", (w - f.width("STARPOST") * 2) / 2, 36, Colors.GOLD, 2);
        drawStarpost(shell, c, w / 2 + 16, LevelStages.feet(stage, w / 2 + 16, GROUND));
        int heroX = w / 2 - 40;
        int heroFeet = LevelStages.feet(stage, heroX, GROUND);
        Poses.hero(shell, c, shell.run.state().character().id(),
                room.chosen() != null && room.chosen().equals("rest") ? Poses.DUCK : Poses.WAIT, shell.ticks,
                heroX, heroFeet, SceneDraw.plain());
        drawRest(shell, c, heroX, heroFeet);
        drawSmithed(shell, screen, c, w / 2 + 70, LevelStages.feet(stage, w / 2 + 16, GROUND));
        layout(shell);
        if (room.chosen() == null) {
            for (int i = 0; i < room.options().size(); i++) {
                RestOption o = room.options().get(i);
                Hotspots.Spot s = spots.spot("o" + i);
                boolean focus = spots.isFocused(s.id());
                Gfx.button(c, f, "", s.x(), s.y(), s.w(), s.h(), focus, o.enabled(), shell.ticks);
                f.drawCentered(c, o.label().toUpperCase(), s.x() + s.w() / 2, s.y() + 6,
                        o.enabled() ? Colors.GOLD : Colors.TEXT_DIM);
                int ly = s.y() + 16;
                for (String line : f.wrap(o.detail(), s.w() - 8)) {
                    f.drawCentered(c, line, s.x() + s.w() / 2, ly, o.enabled() ? Colors.TEXT : Colors.TEXT_DIM);
                    ly += SmallFont.LINE;
                }
            }
        } else {
            String done = switch (room.chosen()) {
                case "rest" -> "YOU CATCH YOUR BREATH.";
                case "smith" -> "GOOD AS NEW - BETTER, EVEN.";
                default -> "DONE.";
            };
            f.drawCentered(c, done, w / 2, 150, Colors.TEXT);
            Hotspots.Spot go = spots.spot("go");
            Gfx.button(c, f, "PROCEED", go.x(), go.y(), go.w(), go.h(), spots.isFocused("go"), true, shell.ticks);
        }
    }
}
