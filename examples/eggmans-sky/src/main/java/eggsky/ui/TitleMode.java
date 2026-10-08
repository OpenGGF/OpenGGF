package eggsky.ui;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneLevelKit;
import com.openggf.mods.scene.SceneSprite;
import eggsky.Game;
import eggsky.Mode;
import eggsky.art.Art;
import eggsky.core.Controls;
import eggsky.core.Sound;
import eggsky.space.Body;
import eggsky.space.Cam;
import eggsky.space.PlanetTexture;
import eggsky.space.SpaceRenderer;
import eggsky.world.PlanetSpec;
import eggsky.world.StarSystem;
import java.util.ArrayList;
import java.util.List;

/**
 * The title: a planet made from Angel Island's own blocks turning in space, the Egg Mobile
 * cruising past, the logo, and Continue / New Expedition / How to Play / the stock game.
 */
public final class TitleMode implements Mode {
    private final List<String> options = new ArrayList<>();
    private int choice;
    private int age;
    private boolean confirmNew;
    private boolean help;
    private int helpPage;
    private SpaceRenderer renderer;
    private final Cam cam = new Cam();
    private final List<Body> bodies = new ArrayList<>();
    private float shipX = -60;

    @Override
    public boolean live() {
        return false;
    }

    @Override
    public void enter(Game g) {
        options.clear();
        if (g.hasSave()) {
            options.add("CONTINUE");
        }
        options.add("NEW EXPEDITION");
        options.add("HOW TO PLAY");
        options.add("PLAY SONIC 3 & KNUCKLES");
        choice = 0;
        g.sound.resetMusic();
        g.sound.music(Sound.M_DATA_SELECT);
        cam.width = g.width;
        cam.height = g.height;
        renderer = new SpaceRenderer(0x5EEDL, 0xFFFFE070);
        renderer.sunSize = 2600;
        // A planet remixed from the first usable biome, recoloured as a lush world.
        PlanetSpec spec = null;
        SceneLevelKit kit = null;
        if (!g.usableBiomes.isEmpty()) {
            spec = new PlanetSpec(0x7171L, 0, StarSystem.YELLOW, g.usableBiomes.subList(0, 1));
            try {
                kit = g.art.rom(spec.biome.game()).levelKit(spec.biome.zone(), spec.biome.act());
            } catch (RuntimeException failed) {
                kit = null;
            }
        }
        Body planet = new Body(0, 2600, -1300, 9000, 3200, 0.35, 0.0012, spec, false);
        planet.texture = spec == null ? null : PlanetTexture.build(spec, kit);
        bodies.add(planet);
        cam.x = 0;
        cam.y = 0;
        cam.z = 0;
        cam.yaw = 0.05;
        cam.pitch = -0.05;
        cam.update();
    }

    @Override
    public void update(Game g) {
        age++;
        Controls in = g.in;
        for (Body b : bodies) {
            b.spin += b.spinSpeed;
        }
        cam.yaw = 0.05 + Math.sin(age * 0.002) * 0.04;
        cam.update();
        renderer.render(cam, bodies, g.ticks);
        shipX += 0.9f;
        if (shipX > g.width + 120) {
            shipX = -120;
        }
        if (help) {
            if (in.confirmPressed || in.rightPressed) {
                helpPage++;
                g.sound.sfx(Sound.SWITCH, 3);
                if (helpPage > 2) {
                    help = false;
                }
            } else if (in.backPressed) {
                help = false;
            } else if (in.leftPressed && helpPage > 0) {
                helpPage--;
            }
            return;
        }
        if (in.upRepeat) {
            choice = Math.floorMod(choice - 1, options.size());
            confirmNew = false;
            g.sound.sfx(Sound.SWITCH, 3);
        }
        if (in.downRepeat) {
            choice = Math.floorMod(choice + 1, options.size());
            confirmNew = false;
            g.sound.sfx(Sound.SWITCH, 3);
        }
        if (in.mouseMoved) {
            for (int i = 0; i < options.size(); i++) {
                int y = 150 + i * 14;
                if (in.mouseY >= y - 3 && in.mouseY < y + 10 && Math.abs(in.mouseX - g.width / 2) < 90) {
                    choice = i;
                }
            }
        }
        if (in.confirmPressed && age > 10) {
            String option = options.get(choice);
            switch (option) {
                case "CONTINUE" -> {
                    if (g.load()) {
                        g.sound.sfx(Sound.STARPOST);
                        if (g.player.planet >= 0) {
                            g.land(g.player.planet, false);
                        } else {
                            g.setMode(new eggsky.space.SpaceMode(eggsky.space.SpaceMode.ARRIVE_RESUME, -1));
                        }
                    }
                }
                case "NEW EXPEDITION" -> {
                    if (g.hasSave() && !confirmNew) {
                        confirmNew = true;
                        g.sound.sfx(Sound.ERROR);
                        return;
                    }
                    g.sound.sfx(Sound.LAUNCH_GO);
                    g.newExpedition(g.newSeed(), 1, null);
                    g.setMode(new IntroMode());
                }
                case "HOW TO PLAY" -> {
                    help = true;
                    helpPage = 0;
                }
                default -> g.ctx.exitToGameTitle();
            }
        }
    }

    @Override
    public void draw(Game g, SceneCanvas c) {
        renderer.draw(c, g.width, g.height);
        renderer.drawStars(c, cam, g.ticks);
        Font f = g.font;
        // The Egg Mobile cruising past with a laugh.
        SceneSprite body = g.art.frame("ship", Art.SHIP_BODY);
        SceneSprite head = g.art.frame("ship", (age / 40) % 5 == 0 ? Art.SHIP_HEAD_LAUGH
                : (age / 10) % 2 == 0 ? Art.SHIP_HEAD_IDLE0 : Art.SHIP_HEAD_IDLE1);
        SceneSprite flame = g.art.frame("ship", Art.SHIP_FLAME);
        float sy = 112 + (float) Math.sin(age * 0.04) * 6;
        if (flame != null && (age / 2) % 2 == 0) {
            c.draw(flame, shipX - 30, sy + 2, SceneDraw.plain().withFlipX(true).withScale(1.3f));
        }
        if (head != null) {
            c.draw(head, shipX, sy - 0x1C, SceneDraw.plain().withFlipX(true));
        }
        if (body != null) {
            c.draw(body, shipX, sy, SceneDraw.plain().withFlipX(true));
        }
        // Logo.
        float drop = Math.min(1, age / 40f);
        float logoY = -50 + 66 * (1 - (1 - drop) * (1 - drop));
        f.drawBig(c, "EGGMAN'S", g.width / 2f, logoY, 3, 0xFFFFF0A0, 0xFFE08020, 255);
        f.drawBig(c, "SKY", g.width / 2f, logoY + 26, 7, 0xFFFFFFFF, 0xFF40A0FF, 255);
        if (age > 40) {
            f.centre(c, "A GALAXY OF WORLDS REMIXED FROM EVERY ZONE", g.width / 2, (int) logoY + 82, 0xFFC0D8FF);
        }
        if (help) {
            drawHelp(g, c);
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            int y = 150 + i * 14;
            String text = options.get(i);
            if (text.equals("NEW EXPEDITION") && confirmNew && i == choice) {
                text = "OVERWRITE SAVE? CONFIRM AGAIN";
            }
            int w = Font.width(text) + 16;
            if (i == choice) {
                Ui.focus(c, g.width / 2 - w / 2, y - 3, w, 13, g.ticks);
            }
            f.centre(c, text, g.width / 2, y, i == choice ? 0xFFFFFFFF : 0xFF90A0C8);
        }
        f.draw(c, g.art.games().size() + " ROM" + (g.art.games().size() > 1 ? "S" : "") + ": "
                + String.join(" ", g.art.games()).toUpperCase() + "   " + g.usableBiomes.size() + " BIOMES", 4,
                g.height - 10, 0xFF606890);
        f.right(c, "OPENGGF MOD", g.width - 4, g.height - 10, 0xFF606890);
    }

    private void drawHelp(Game g, SceneCanvas c) {
        Font f = g.font;
        int x = 30;
        int y = 104;
        int w = g.width - 60;
        Ui.panel(c, x, y, w, 112);
        String[] lines = switch (helpPage) {
            case 0 -> new String[] {
                    "ON A PLANET",
                    "ARROWS/WASD FLY THE EGG MOBILE. IT HOVERS OVER THE GROUND.",
                    "SPACE/Z (OR MOUSE) FIRES THE MINING LASER. MIND THE HEAT.",
                    "X TAPS A SCAN PULSE. HOLD X FOR THE ANALYSIS VISOR:",
                    "  CATALOGUE CREATURES, PLANTS AND ROCKS FOR RINGS.",
                    "C BOOSTS. HOLD UP + C TO LAUNCH (NEEDS LAUNCH FUEL).",
                    "KEYS 1-4 RECHARGE LIFE, HAZARD, FUEL AND HULL.",
                    "ENTER OPENS THE MENU: REFINE, CRAFT, RECHARGE, LOG."};
            case 1 -> new String[] {
                    "SURVIVAL AND SENTINELS",
                    "LIFE SUPPORT DRAINS EVERYWHERE; HOT, COLD, TOXIC AND",
                    "IRRADIATED WORLDS DRAIN HAZARD PROTECTION. CAVES SHELTER.",
                    "MINING TOO GREEDILY OR HARMING ANIMALS RAISES YOUR",
                    "WANTED LEVEL: FLICKIES, TAILS, SONIC, KNUCKLES... THEN",
                    "SUPER SONIC. LASER THEM TO KNOCK THEIR RINGS LOOSE,",
                    "OR HIDE UNDERGROUND UNTIL THEY GIVE UP.",
                    "MONITORS, EGG CAPSULES AND STARPOSTS HELP YOU."};
            default -> new String[] {
                    "IN SPACE AND BEYOND",
                    "STEER WITH ARROWS OR MOUSE. SPACE FIRES. HOLD C TO",
                    "BOOST, KEEP HOLDING TO ENGAGE THE PULSE DRIVE.",
                    "DIVE INTO A PLANET TO LAND. FLY INTO THE EGG STATION",
                    "TO TRADE, UPGRADE AND TAKE MISSIONS.",
                    "CRAFT WARP CELLS AND WARP FROM THE GALAXY MAP (MENU).",
                    "ECHIDNA RUINS REVEAL CHAOS EMERALD SHRINES. BRING ALL",
                    "SEVEN TO THE GALACTIC CORE. GLORY TO THE EGGMAN EMPIRE!"};
        };
        f.drawBig(c, lines[0], g.width / 2f, y + 6, 2, 0xFFFFFFFF, Ui.GOLD, 255);
        for (int i = 1; i < lines.length; i++) {
            f.draw(c, lines[i], x + 10, y + 16 + i * 11, 0xFFE0E8FF);
        }
        f.right(c, (helpPage + 1) + "/3  CONFIRM: NEXT", x + w - 6, y + 102, Ui.DIM);
    }
}
