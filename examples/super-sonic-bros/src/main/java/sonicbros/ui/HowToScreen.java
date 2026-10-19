package sonicbros.ui;

import com.openggf.mods.scene.SceneCanvas;

/** Controls and the basics: damage, knockback, blast lines, specials and monitors. */
public final class HowToScreen implements Screen {
    private final Game g;
    private int page;
    private long t;

    public HowToScreen(Game g) {
        this.g = g;
    }

    @Override public void update() {
        t++;
        if (g.controls.anyRepeated(sonicbros.sim.Pad.LEFT)) { page = (page + 2) % 3; g.sfx(Game.SFX_MOVE); }
        if (g.controls.anyRepeated(sonicbros.sim.Pad.RIGHT)) { page = (page + 1) % 3; g.sfx(Game.SFX_MOVE); }
        if (t > 10 && (g.controls.anyAccept() || g.controls.anyBack())) {
            g.sfx(Game.SFX_BACK);
            g.go(new MainMenu(g));
        }
    }

    private void lines(SceneCanvas c, String... lines) {
        int y = 40;
        for (String l : lines) {
            boolean head = l.startsWith("#");
            g.font.draw(c, head ? l.substring(1) : l, 16, y, head ? 0xFFFFE040 : 0xFFE8E8F0);
            y += head ? 13 : 12;
        }
    }

    @Override public void draw(SceneCanvas c) {
        int w = g.width();
        g.menuBackground(c, 0);
        g.bigCentered(c, "HOW TO PLAY " + (page + 1) + "/3", 2, w / 2, 8, 0xFFFFFFFF, 0xFF80B0FF);
        switch (page) {
            case 0 -> lines(c,
                    "#THE GOAL",
                    "EVERYONE STARTS AT 0%. HITS ADD DAMAGE, AND THE HIGHER IT",
                    "GOES THE FURTHER YOU FLY. KNOCK RIVALS PAST THE EDGES OF",
                    "THE SCREEN (THE BLAST LINES) TO SCORE A KO.",
                    "#MODES",
                    "STOCK: LAST ONE WITH LIVES LEFT WINS.",
                    "TIME: MOST KOS WINS. EACH FALL COSTS A POINT.",
                    "STAMINA: HIT POINTS INSTEAD OF DAMAGE.",
                    "#RECOVERY",
                    "JUMP AGAIN IN THE AIR, THEN UP + SPECIAL TO GET BACK.",
                    "GRAB THE LEDGE, THEN CLIMB, JUMP, ROLL OR ATTACK.");
            case 1 -> lines(c,
                    "#KEYBOARD (ARROWS)        KEYBOARD (WASD)",
                    "ARROWS  MOVE              WASD  MOVE",
                    "Z  ATTACK  X  SPECIAL     F  ATTACK  G  SPECIAL",
                    "C/SPACE  JUMP  V  SHIELD  H  JUMP    R  SHIELD",
                    "ENTER  PAUSE              Q  PAUSE",
                    "#GAMEPADS",
                    "X/SQUARE ATTACK  B/CIRCLE SPECIAL  A/CROSS JUMP",
                    "SHOULDERS/TRIGGERS SHIELD  START PAUSE",
                    "#TECHNIQUE",
                    "TAP A DIRECTION WITH ATTACK FOR A SMASH (HOLD TO CHARGE)",
                    "SHIELD + ATTACK GRABS. SHIELD + DIRECTION ROLLS/DODGES.");
            default -> lines(c,
                    "#MONITORS",
                    "BREAK A MONITOR WITH ANY ATTACK TO TAKE WHAT'S INSIDE:",
                    "SUPER RING HEALS, SHIELDS BLOCK A HIT, SHOES ADD SPEED,",
                    "STARS MAKE YOU INVINCIBLE, THE 'S' TURNS YOU SUPER...",
                    "...AND EGGMAN'S MONITOR EXPLODES. CAREFUL!",
                    "#DEFENCE",
                    "SHIELDS SHRINK AS THEY TAKE HITS. LET ONE BREAK AND",
                    "YOU'RE DIZZY. PRESS SHIELD AS YOU HIT THE GROUND TO",
                    "TECH, AND HOLD AWAY FROM DANGER WHEN LAUNCHED.");
        }
        g.textCentered(c, "LEFT/RIGHT PAGE   ATTACK BACK", w / 2, g.height() - 12, 0xA0FFFFFF);
    }

    @Override public String name() { return "howto"; }
}
