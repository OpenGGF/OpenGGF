package hello;

import com.openggf.mods.scene.ModScene;
import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.SceneBackdrop;
import com.openggf.mods.scene.SceneButtons;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneContext;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;

/**
 * A whole mod scene in one class: Sonic runs and jumps over Angel Island's parallax background,
 * collecting rings. It shows each part of the scene API once:
 * <ul>
 *   <li>{@link #enter}: ROM art (a zone background, a playable character, a sprite from its
 *       art and mapping addresses), saved data, and music;</li>
 *   <li>{@link #update}: the controller ({@code buttonDown}/{@code buttonPressed}), the mouse,
 *       sound effects, saving, and leaving for the stock game;</li>
 *   <li>{@link #draw}: the background, sprites (flipped, animated), plain fills and text.</li>
 * </ul>
 * The engine calls {@code update} then {@code draw} once a frame, 60 times a second. Keep all
 * state in fields and change it only in {@code update}: {@code draw} may be skipped or repeated.
 */
public final class HelloScene implements ModScene {
    /** Sonic 3 &amp; Knuckles' sound driver IDs: Angel Island act 1's music, and two sounds. */
    private static final int MUSIC_AIZ1 = 0x01;
    private static final int SFX_RING = 0x33;
    private static final int SFX_JUMP = 0x62;
    /** Sonic's animation numbers in the ROM's animation table (AniSonic). */
    private static final int ANIM_WALK = 0x00;
    private static final int ANIM_ROLL = 0x02;
    private static final int ANIM_WAIT = 0x05;
    /** The screen row the ground starts on, and the motion in pixels per frame. */
    private static final int GROUND = 176;
    private static final float RUN_SPEED = 2.5f;
    private static final float JUMP_SPEED = 6.5f;
    private static final float GRAVITY = 0.21875f; // Sonic's own gravity, $38 / 256
    private static final int RING_COUNT = 6;
    private static final String SAVE_FILE = "best.txt";

    // Art, loaded once in enter(). Any of it may be null when the ROM art is not available.
    private SceneBackdrop background;
    private SceneSpriteSet sonic;
    private SceneSpriteSet ring;

    // State.
    private float x = 60;
    private float height;          // pixels above the ground
    private float fallSpeed;
    private boolean facingLeft;
    private boolean running;
    private float target = Float.NaN; // where a mouse click sent Sonic
    private final int[] ringX = new int[RING_COUNT];
    private final int[] ringY = new int[RING_COUNT];
    private final int[] collectedAt = new int[RING_COUNT]; // tick each ring was taken, or -1
    private int rings;
    private int best;
    private int ticks;

    @Override
    public void enter(SceneContext ctx) {
        SceneRomArt rom = ctx.art().rom(); // null only when the ROM's art could not be prepared
        if (rom != null) {
            background = rom.zoneBackdrop(0, 0); // zone 0 act 1: Angel Island (null if unsupported)
            sonic = rom.character("sonic");
            // A sprite from the ROM by address: the ring's art (Nemesis-compressed), its mappings,
            // and the palette lines it draws in. The addresses come from the disassembly's listing
            // (ArtNem_RingHUDText and Map_Ring in sonic3k.lst; `ggfmod sprites` shows the frames).
            int[] palette = new int[64];
            System.arraycopy(rom.palette(0x0A8A3C, 16), 0, palette, 0, 16); // Pal_SonicTails, line 0
            System.arraycopy(rom.palette(0x0A8B7C, 48), 0, palette, 16, 48); // Pal_AIZ, lines 1-3
            ring = rom.sprites(RomSpriteRequest.of(0x192AEE, RomSpriteRequest.Compression.NEMESIS, 0x01A99A, 1),
                    palette);
        }
        best = ctx.storage().read(SAVE_FILE).map(HelloScene::parse).orElse(0);
        placeRings(ctx.width());
        ctx.audio().playMusic(MUSIC_AIZ1);
    }

    private void placeRings(int width) {
        for (int i = 0; i < RING_COUNT; i++) {
            ringX[i] = 40 + i * (width - 80) / (RING_COUNT - 1);
            ringY[i] = GROUND - 16 - (i % 2 == 0 ? 0 : 44);
            collectedAt[i] = -1;
        }
    }

    @Override
    public void update(SceneContext ctx) {
        ticks++;
        // Start returns to the stock game's title screen (holding Escape always leaves for the master title).
        if (ctx.buttonPressed(SceneButtons.START)) {
            ctx.exitToGameTitle();
            return;
        }
        // Run with the d-pad, or towards where the mouse clicked.
        if (ctx.mouse().leftPressed()) {
            target = ctx.mouse().x();
        }
        float move = 0;
        if (ctx.buttonDown(SceneButtons.LEFT)) {
            move = -RUN_SPEED;
            target = Float.NaN;
        } else if (ctx.buttonDown(SceneButtons.RIGHT)) {
            move = RUN_SPEED;
            target = Float.NaN;
        } else if (!Float.isNaN(target)) {
            move = Math.max(-RUN_SPEED, Math.min(RUN_SPEED, target - x));
            if (Math.abs(target - x) < 1) {
                target = Float.NaN;
            }
        }
        running = move != 0;
        if (running) {
            facingLeft = move < 0;
        }
        x = Math.max(16, Math.min(ctx.width() - 16, x + move));
        // Jump with A, B or C, then fall back under gravity.
        if (height == 0 && ctx.buttonPressed(SceneButtons.ACTIONS)) {
            fallSpeed = -JUMP_SPEED;
            ctx.audio().playSfx(SFX_JUMP);
        }
        if (height > 0 || fallSpeed < 0) {
            fallSpeed += GRAVITY;
            height = Math.max(0, height - fallSpeed);
            if (height == 0) {
                fallSpeed = 0;
            }
        }
        collectRings(ctx);
    }

    private void collectRings(SceneContext ctx) {
        int sonicY = Math.round(GROUND - height - 16);
        boolean anyLeft = false;
        for (int i = 0; i < RING_COUNT; i++) {
            if (collectedAt[i] >= 0) {
                continue;
            }
            if (Math.abs(ringX[i] - x) < 16 && Math.abs(ringY[i] - sonicY) < 24) {
                collectedAt[i] = ticks;
                rings++;
                ctx.audio().playSfx(SFX_RING);
                if (rings > best) {
                    best = rings;
                    ctx.storage().write(SAVE_FILE, Integer.toString(best));
                }
            } else {
                anyLeft = true;
            }
        }
        // Once every ring is gone (and has finished sparkling), put them all back.
        if (!anyLeft && ticks - lastCollected() > 60) {
            placeRings(ctx.width());
        }
    }

    private int lastCollected() {
        int last = 0;
        for (int at : collectedAt) {
            last = Math.max(last, at);
        }
        return last;
    }

    @Override
    public void draw(SceneContext ctx, SceneCanvas canvas) {
        int width = ctx.width();
        // The zone's own parallax, scrolling with Sonic; a plain sky when the ROM art is missing.
        if (background != null) {
            canvas.drawBackdrop(background, 0x100, x * 2, ticks);
        } else {
            canvas.clear(0x2468B0);
        }
        canvas.fill(0, GROUND, width, ctx.height() - GROUND, 0xFF3C8C24);
        canvas.fill(0, GROUND, width, 3, 0xFF8CDA48);

        for (int i = 0; i < RING_COUNT; i++) {
            drawRing(canvas, i);
        }
        drawSonic(canvas);

        canvas.text("RINGS " + rings, 8, 8, 0xFFFFDA24);
        canvas.text("BEST " + best, width - 8 - canvas.textWidth("BEST " + best), 8, 0xFFFFFFFF);
        centred(canvas, "ARROWS OR CLICK: RUN   A: JUMP", width, ctx.height() - 30);
        centred(canvas, "START: THE STOCK TITLE SCREEN", width, ctx.height() - 16);
    }

    private static void centred(SceneCanvas canvas, String text, int width, int y) {
        canvas.text(text, (width - canvas.textWidth(text)) / 2, y, 0xFFFFFFFF);
    }

    private void drawRing(SceneCanvas canvas, int i) {
        if (ring == null) {
            return;
        }
        int frame;
        if (collectedAt[i] < 0) {
            frame = (ticks / 8) % 4;                    // frames 0-3: the ring turning
        } else {
            int age = ticks - collectedAt[i];
            if (age >= 24) {
                return;
            }
            frame = 4 + age / 6;                        // frames 4-7: the sparkle when taken
        }
        canvas.draw(ring.frame(frame), ringX[i], ringY[i], SceneDraw.plain());
    }

    private void drawSonic(SceneCanvas canvas) {
        if (sonic == null) {
            canvas.fill(Math.round(x) - 8, Math.round(GROUND - height) - 32, 16, 32, 0xFF2448DA);
            return;
        }
        int anim = height > 0 ? ANIM_ROLL : running ? ANIM_WALK : ANIM_WAIT;
        SceneSprite pose = frame(sonic, anim, ticks);
        if (pose == null) {
            return;
        }
        // A sprite is drawn with its origin at (x, y); the ROM's character origins are their
        // centres, so lift him by the distance from the origin to his feet.
        float feet = GROUND - height;
        canvas.draw(pose, x, feet - (pose.height() - pose.originY()), SceneDraw.plain().withFlipX(facingLeft));
    }

    /** The frame of a ROM animation script at a time, looping. */
    private static SceneSprite frame(SceneSpriteSet set, int anim, int ticks) {
        int[] frames = set.animationFrames(anim);
        if (frames.length == 0) {
            return null;
        }
        // Walking and running scripts take their speed from the player's ground speed ($FF);
        // a scene has none, so show them at a brisk fixed pace.
        int delay = set.animationDelay(anim) > 30 ? 4 : set.animationDelay(anim) + 1;
        int frame = frames[(ticks / delay) % frames.length];
        return set.frame(frame >= 0xF0 ? frames[0] : frame); // $F0 and up are script commands
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
