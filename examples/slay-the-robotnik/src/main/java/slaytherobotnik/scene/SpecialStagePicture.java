package slaytherobotnik.scene;

import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import slaytherobotnik.core.Card;
import slaytherobotnik.ui.Colors;
import slaytherobotnik.ui.Gfx;

/**
 * The Special Stage Warp event: the Blue Sphere globe rolling away under a starry sky, blue
 * spheres and rings riding towards you (the special stage's own sphere and ring sprites at its
 * perspective sizes, Map_SStageSphere and Map_SStageRing), a Giant Ring hanging over the
 * horizon and turning as Obj_SSEntryRing does. The floor is drawn - the game builds it from a
 * plane map the mod cannot render - in the act's stage colours from Pal_SStage_3_1 onwards (its
 * two checker colours, words 0 and 4). Shown "old card>new card", the card given up drifts
 * into the ring with sfx_BigRing and the entry flash, sfx_EnterSS sounds $20 frames later and
 * every blue sphere turns red (sfx_BlueSphere); then the ring forms again from its forming
 * frames and the new card drops out of it. The drift and the re-forming are the mod's staging.
 */
final class SpecialStagePicture extends EventPicture {
    private static final int RING_X = 63;
    private static final int RING_Y = 40;
    private static final int HORIZON = 74;
    /** The drawn floor's projection: depth over rows below the horizon, focal length, cell size, speed. */
    private static final float DEPTH = 2200f;
    private static final float FOCAL = 110f;
    private static final float CELL = 12f;
    private static final float ROLL = 0.75f;
    /** Pal_SStage_3_1: the first stage's palette; each act takes the next one ($26 bytes apart). */
    private static final int STAGE_PALETTES = 0x89FE;
    private static final int STAGE_PALETTE_SIZE = 0x26;
    /** How long the old card takes to reach the ring, and the new one to come down (the mod's choice). */
    private static final int DRIFT = 30;
    private static final int DROP = 24;
    private static final int SETTLE = 12;
    /** Frames after sfx_EnterSS when the spheres turn red, and when the ring starts to form again. */
    private static final int TURN_RED = 16;
    private static final int REFORM = 28;

    private final byte[] ringScript;
    private final byte[] flashScript;
    private GiantRingEntry entry;
    private int checkerA = 0xFFEE8800;
    private int checkerB = 0xFF662200;
    private CardRenderer cards;
    private Card oldCard;
    private Card newCard;
    private int sinceEntered = -1;
    private boolean reformed;
    private int sinceFormed = -1;
    private long scroll;

    SpecialStagePicture(Shell shell) {
        ringScript = RomTables.read(shell, RomTables.ANIRAW_SS_ENTRY_RING, 18);
        flashScript = RomTables.read(shell, RomTables.ANIRAW_SS_ENTRY_FLASH, 11);
        if (ringScript != null && flashScript != null && shell.art.romFrame("big_ring", 8) != null) {
            entry = new GiantRingEntry(ringScript, flashScript, RING_X, RING_Y);
        }
        if (shell.art.hasRom()) {
            int act = shell.run != null ? Math.max(1, Math.min(8, shell.run.state().act())) : 1;
            int[] stage = shell.art.rom().palette(STAGE_PALETTES + (act - 1) * STAGE_PALETTE_SIZE, 8);
            checkerA = stage[0];
            checkerB = stage[4];
        }
    }

    @Override
    void show(Shell shell, String detail) {
        if (entry == null || oldCard != null) {
            return;
        }
        String body = detail.startsWith("enter:") ? detail.substring(6) : detail;
        int arrow = body.indexOf('>');
        if (arrow < 0) {
            return;
        }
        oldCard = card(shell, body.substring(0, arrow));
        newCard = card(shell, body.substring(arrow + 1));
        if (oldCard == null || newCard == null) {
            oldCard = null;
            return;
        }
        cards = new CardRenderer(shell);
        entry.drift(RING_X, HEIGHT - 30, DRIFT);
    }

    private static Card card(Shell shell, String key) {
        boolean upgraded = key.endsWith("+");
        String id = upgraded ? key.substring(0, key.length() - 1) : key;
        return shell.catalog.hasCard(id) ? new Card(shell.catalog.card(id), upgraded) : null;
    }

    @Override
    boolean busy() {
        return oldCard != null && sinceFormed < DROP + SETTLE;
    }

    @Override
    void tick(Shell shell) {
        scroll++;
        if (entry == null) {
            return;
        }
        entry.tick();
        if (entry.touchedNow()) {
            shell.sfx(Sounds.SFX_BIG_RING);
        }
        if (entry.enteredNow()) {
            shell.sfx(Sounds.SFX_ENTER_SPECIAL);
            sinceEntered = 0;
        } else if (sinceEntered >= 0) {
            sinceEntered++;
        }
        if (sinceEntered == TURN_RED) {
            shell.sfx(Sounds.SFX_BLUE_SPHERE);
        }
        if (sinceEntered == REFORM && !reformed) {
            entry.reform();
            reformed = true;
        }
        // The new card comes out as the ring finishes forming (its frame 6 of 0-7).
        if (reformed && sinceFormed < 0 && entry.ringFrame() >= 6) {
            sinceFormed = 0;
        } else if (sinceFormed >= 0) {
            sinceFormed++;
        }
    }

    @Override
    void draw(Shell shell, SceneCanvas c, int x, int y, int w, int h) {
        long t = shell.ticks;
        Gfx.gradient(c, x, y, w, h, 0xFF000024, 0xFF24246C);
        EventArt.stars(c, x, y, w, HORIZON + 20, t);
        drawFloor(c, x, y, w, h);
        drawSpheres(shell, c, x, y);
        if (entry == null) {
            return;
        }
        int ringFrame = entry.ringFrame();
        if (ringFrame >= 0) {
            c.draw(shell.art.romFrame("big_ring", ringFrame), x + RING_X, y + RING_Y, SceneDraw.plain());
            if (ringFrame >= 8) {
                EventArt.sparkles(shell, c, x + RING_X, y + RING_Y, 40, t);
            }
        }
        Motion drifting = entry.jumper();
        if (drifting != null && oldCard != null) {
            drawCard(c, oldCard, x + drifting.x(), y + drifting.y());
        }
        int flash = entry.flashFrame();
        if (flash >= 0) {
            c.draw(shell.art.romFrame("ss_entry_flash", flash), x + RING_X, y + RING_Y,
                    SceneDraw.plain().withFlipX(entry.flashFlipped()));
        }
        if (sinceFormed >= 0 && newCard != null) {
            float f = Math.min(1f, sinceFormed / (float) DROP);
            float cy = RING_Y + (HEIGHT - 34 - RING_Y) * f;
            drawCard(c, newCard, x + RING_X, y + cy);
            if (sinceFormed < DROP + SETTLE) {
                EventArt.sparkles(shell, c, x + RING_X, Math.round(y + cy), 26, t);
            }
        }
    }

    /** A small card centred on a point. */
    private void drawCard(SceneCanvas c, Card card, float cx, float cy) {
        cards.drawSmall(c, card, null, Math.round(cx - CardRenderer.SMALL_W / 2f),
                Math.round(cy - CardRenderer.SMALL_H / 2f), false, false);
    }

    /** The horizon row at window column {@code col}: the globe curves away at the sides. */
    private static int horizon(int col) {
        int dx = col - WIDTH / 2;
        return HORIZON + dx * dx / 200;
    }

    /** The checkered floor in perspective, filled a run of one colour at a time. */
    private void drawFloor(SceneCanvas c, int x, int y, int w, int h) {
        float roll = scroll * ROLL;
        int haze = Colors.mix(checkerA, checkerB, 0.5f);
        for (int row = HORIZON + 1; row < h; row++) {
            int runStart = -1;
            int runColour = 0;
            for (int col = 0; col <= w; col += 2) {
                int colour = 0;
                if (col < w) {
                    int dy = row - horizon(col);
                    if (dy > 0) {
                        float z = DEPTH / dy;
                        float u = (col - w / 2f) * z / FOCAL;
                        float v = z + roll;
                        boolean even = (Math.floorMod((int) Math.floor(u / CELL), 2)
                                + Math.floorMod((int) Math.floor(v / CELL), 2)) % 2 == 0;
                        // Far off the squares are smaller than a pixel: the colours blur together.
                        colour = z > 260 ? haze : even ? checkerA : checkerB;
                    }
                }
                if (colour != runColour) {
                    if (runColour != 0) {
                        c.fill(x + runStart, y + row, col - runStart, 1, runColour);
                    }
                    runStart = col;
                    runColour = colour;
                }
            }
        }
        // Haze where the globe meets the sky.
        for (int col = 0; col < w; col += 2) {
            c.fill(x + col, y + horizon(col) + 1, 2, 3, Colors.alpha(Colors.WHITE, 0.2f));
        }
    }

    /**
     * Blue spheres and a ring riding the floor towards the viewer, each drawn at the
     * Map_SStageSphere / Map_SStageRing size for its distance (frame 0 nearest, 15-18 far off).
     */
    private void drawSpheres(Shell shell, SceneCanvas c, int x, int y) {
        float roll = scroll * ROLL;
        boolean red = sinceEntered >= TURN_RED;
        int passed = (int) (roll / (2 * CELL));
        for (int lane = 24; lane >= 0; lane--) {
            float z = lane * CELL + CELL / 2 - (roll % (2 * CELL));
            if (z <= 18) {
                continue;
            }
            for (int across = -2; across <= 1; across++) {
                if (((across + lane) & 1) != 0) {
                    continue;
                }
                float u = across * CELL + CELL / 2;
                int col = Math.round(WIDTH / 2f + u * FOCAL / z);
                int row = Math.round(horizon(col) + DEPTH / z);
                if (row >= HEIGHT + 8 || col < -10 || col > WIDTH + 10) {
                    continue;
                }
                // A cell is CELL * FOCAL / z pixels across here; the sphere fills most of it.
                int frame = Math.max(0, Math.min(18, Math.round((32 - 0.75f * CELL * FOCAL / z) / 1.7f)));
                boolean ring = across == 1 && (lane + passed * 2) % 6 == 1;
                String key = ring ? "ss_ring" : red ? "ss_sphere_red" : "ss_sphere";
                int f = ring ? Math.min(15, frame) : frame;
                c.draw(shell.art.romFrame(key, f), x + col, y + row - 4, SceneDraw.plain());
            }
        }
    }
}
