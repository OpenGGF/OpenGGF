package starpost.people;

import com.openggf.mods.scene.RomSpriteRequest;
import com.openggf.mods.scene.RomSpriteRequest.Compression;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.scene.SceneDraw;
import com.openggf.mods.scene.SceneImage;
import com.openggf.mods.scene.SceneSprite;
import com.openggf.mods.scene.SceneSpriteSet;
import com.openggf.mods.scene.art.AnimationSampling;
import java.util.HashMap;
import java.util.Map;
import starpost.art.Art;
import starpost.core.Catalog;

/**
 * The neighbours' pictures. Bodies are ROM sprites: the S3K heroes and Egg Robo from
 * {@link Art}, Sonic 1's on-foot Eggman (the Scrap Brain 2 cutscene's, in Pal_Sonic plus
 * Pal_SBZ2), Sonic 1's freed animals, the Motobug, and Green Hill's totem pole. The picture
 * speech glyphs (hearts, weather, notes...) are original simple shapes, as the art rule allows
 * for UI; faces in picture speech are always ROM art (Robotnik is the signpost's frame 0).
 */
public final class PeopleArt {
    // Sonic 1 (sonic.lst): ArtNem_SBZ2_Eggman / Map_SEgg, Pal_Sonic, Pal_SBZ2.
    private static final int NEM_SEGG = 0x5E4CE;
    private static final int MAP_SEGG = 0x1A1E4;
    private static final int PAL_SONIC = 0x2380;
    private static final int PAL_SBZ2 = 0x2660;

    /** Map_Sign: frame 0 is Eggman's face, 4 Sonic's; the panel is the top 32 rows (the post below). */
    static final int SIGN_EGGMAN = 0;
    static final int SIGN_SONIC = 4;
    static final int SIGN_PANEL_ROWS = 32;

    /** Map_SEgg frames (Ani_SEgg: running is 7, 4, 8, 4; laughing 1, 2). */
    static final int EGG_STAND = 0;
    static final int EGG_LAUGH1 = 1;
    static final int EGG_LAUGH2 = 2;
    static final int EGG_JUMP = 3;
    static final int EGG_STEP = 4;
    static final int EGG_SURPRISE = 5;
    static final int EGG_RUN1 = 7;
    static final int EGG_RUN2 = 8;

    /**
     * Map_EggRobo (the S3K badnik): frame 0 is empty, 1 the body (head, torso, one glove), 2 the
     * gun arm, 4-6 the jet flame. ObjDat3_919A6 / ChildObjDat_919D0 put the flame child at
     * (-$C, +$1C) from the body. Rusty has retired the gun arm.
     */
    static final int EGGROBO_BODY = 1;
    static final int EGGROBO_JET = 4;
    static final int EGGROBO_JET_X = -0x0C;
    static final int EGGROBO_JET_Y = 0x1C;

    // S3K player animation ids (AniSonic / AniTails / AniKnuckles).
    static final int ANI_WALK = 0x00;
    static final int ANI_WAIT = 0x05;
    static final int ANI_LOOK_UP = 0x07;
    static final int ANI_SPRING = 0x10;
    static final int ANI_VICTORY = 0x13;
    static final int ANI_HURT = 0x1A;

    public final Art art;
    public final SceneSpriteSet robotnik;
    /** The season the world is drawn in (set each tick by the system; the totem follows it). */
    int season;
    private final Map<String, SceneImage> glyphs = new HashMap<>();
    private final Map<String, SceneImage> heads = new HashMap<>();

    public PeopleArt(Art art) {
        this.art = art;
        int[] palette = new int[64];
        System.arraycopy(art.s1.palette(PAL_SONIC, 16), 0, palette, 0, 16);
        System.arraycopy(art.s1.palette(PAL_SBZ2, 48), 0, palette, 16, 48);
        robotnik = art.s1.sprites(RomSpriteRequest.of(NEM_SEGG, Compression.NEMESIS, MAP_SEGG, 0), palette);
    }

    // ------------------------------------------------------------------ bodies

    /** The idle picture of a body: the dialogue portrait. */
    public SceneSprite portrait(String body, long ticks) {
        return pose(body, Bodies.IDLE, ticks);
    }

    /** One pose of a body at a time (stateless, so draw can call it freely). */
    public SceneSprite pose(String body, int pose, long ticks) {
        String kind = body.contains(":") ? body.substring(0, body.indexOf(':')) : body;
        String name = body.contains(":") ? body.substring(body.indexOf(':') + 1) : "";
        switch (kind) {
            case "hero" -> {
                SceneSpriteSet set = art.farmer(name);
                int anim = switch (pose) {
                    case Bodies.WALK -> ANI_WALK;
                    case Bodies.HAPPY -> ANI_VICTORY;
                    case Bodies.LOOK_UP -> ANI_LOOK_UP;
                    case Bodies.HOP -> ANI_SPRING;
                    case Bodies.SURPRISE -> ANI_HURT;
                    default -> ANI_WAIT;
                };
                SceneSprite sprite = pose == Bodies.WALK
                        ? AnimationSampling.frame(set, anim, ticks, AnimationSampling.Timing.fixed(7))
                        : pose == Bodies.IDLE ? AnimationSampling.frame(set, anim, ticks, new AnimationSampling.Timing(0, 30, 4, 0))
                        : AnimationSampling.still(set, anim);
                return sprite != null ? sprite : set.frame(0);
            }
            case "robotnik" -> {
                int frame = switch (pose) {
                    case Bodies.WALK -> runFrame(ticks);
                    case Bodies.LAUGH, Bodies.HAPPY -> ticks / 7 % 2 == 0 ? EGG_LAUGH1 : EGG_LAUGH2;
                    case Bodies.SURPRISE -> EGG_SURPRISE;
                    case Bodies.HOP -> EGG_JUMP;
                    default -> EGG_STAND;
                };
                return frame(robotnik, frame);
            }
            case "eggrobo" -> {
                return frame(art.eggRobo, EGGROBO_BODY);
            }
            case "animal" -> {
                SceneSpriteSet set = art.animal(name);
                boolean flier = name.equals("flicky");
                int frame;
                if (pose == Bodies.WALK || pose == Bodies.HOP || flier) {
                    frame = (int) (ticks / (flier ? 4 : 8) % 2);
                } else {
                    frame = 2;   // Map_Animal's upright "drop" frame
                }
                return frame(set, frame);
            }
            case "motobug" -> {
                return frame(art.motobug, pose == Bodies.WALK ? (int) (ticks / 8 % 3) : 0);
            }
            case "totem" -> {
                SceneImage totem = art.season(season).totem;
                return new SceneSprite(totem, totem.width() / 2, totem.height());
            }
            default -> {
                return frame(art.flicky, 0);
            }
        }
    }

    private static int runFrame(long ticks) {
        // Ani_SEgg .running: 7, 4, 8, 4 at a delay of 6 (seven ticks each).
        return switch ((int) (ticks / 7 % 4)) {
            case 0 -> EGG_RUN1;
            case 2 -> EGG_RUN2;
            default -> EGG_STEP;
        };
    }

    private static SceneSprite frame(SceneSpriteSet set, int frame) {
        if (set == null || set.frameCount() == 0) {
            return null;
        }
        return set.frame(Math.min(frame, set.frameCount() - 1));
    }

    /** The Egg Robo's jet flame, flickering through its three frames. */
    SceneSprite eggRoboJet(long ticks) {
        return frame(art.eggRobo, EGGROBO_JET + (int) (ticks / 3 % 3));
    }

    /** Whether the ROM draws this body facing west when not mirrored (Sonic 1 objects, the Egg Robo). */
    public static boolean facesLeft(String body) {
        return !body.startsWith("hero:") && !body.equals("totem");
    }

    /** Tails's two tails: drawn behind him while he stands (Obj_Tails_Tail_AniSelection's swish). */
    public void tails(SceneCanvas canvas, float x, float originY, long ticks, SceneDraw style) {
        if (art.tailsTails != null) {
            canvas.draw(art.tailsTails.frame(0x22 + (int) (ticks / 8 % 5)), x, originY, style);
        }
    }

    // ------------------------------------------------------------------ faces and glyphs

    /**
     * A villager's face for picture speech and the social page: the top of their idle frame
     * (Sonic's and Robotnik's are the signpost's own faces).
     */
    public SceneImage head(String body) {
        int frame = body.equals("hero:sonic") ? SIGN_SONIC : body.equals("robotnik") ? SIGN_EGGMAN : -1;
        if (frame >= 0 && frame < art.signpost.frameCount()) {
            String key = "sign:" + frame;
            SceneImage cached = heads.get(key);
            if (cached == null) {
                SceneImage sign = art.signpost.frame(frame).image();
                cached = sign.crop(0, 0, sign.width(), Math.min(sign.height(), SIGN_PANEL_ROWS));
                heads.put(key, cached);
            }
            return cached;
        }
        return face(body);
    }

    /** A small face for lists: the top of the idle frame (the whole of a small animal). */
    public SceneImage face(String body) {
        SceneImage cached = heads.get(body);
        if (cached != null) {
            return cached;
        }
        SceneSprite idle = pose(body, Bodies.IDLE, 0);
        SceneImage full = idle == null ? new SceneImage(1, 1, new int[1]) : idle.image();
        int rows = body.startsWith("animal:") || body.equals("motobug") ? full.height()
                : body.equals("totem") ? Math.min(full.height(), 30) : Math.min(full.height(), 22);
        SceneImage image = full.crop(0, 0, full.width(), rows);
        heads.put(body, image);
        return image;
    }

    /**
     * Draws one picture-speech token with its top-left at (x, y), centred in a box of
     * {@code boxHeight}; returns its width. Small pictures (icons and glyphs, 18 pixels or less)
     * draw at {@link #scale} 2 in speech bubbles so they read at a glance; faces stay at 1x.
     */
    public int drawToken(SceneCanvas canvas, String token, float x, float y, int boxHeight, Catalog catalog,
            Cast cast, String farmer, SceneDraw style, boolean big) {
        String key = token.equals("food") ? "item:chili_dog" : token;
        int scale = big ? scale(key, cast, farmer) : 1;
        if (key.startsWith("item:")) {
            String id = key.substring(5);
            if (!catalog.hasItem(id)) {
                return 0;
            }
            art.icons.draw(canvas, catalog.item(id), x, y + (boxHeight - 16 * scale) / 2f, style.withScale(scale));
            return 16 * scale;
        }
        SceneImage image = tokenImage(key, cast, farmer);
        if (image == null) {
            return 0;
        }
        canvas.draw(image, x, y + (boxHeight - image.height() * scale) / 2f, style.withScale(scale));
        return image.width() * scale;
    }

    public int drawToken(SceneCanvas canvas, String token, float x, float y, int boxHeight, Catalog catalog,
            Cast cast, String farmer, SceneDraw style) {
        return drawToken(canvas, token, x, y, boxHeight, catalog, cast, farmer, style, false);
    }

    /** 2 for small pictures, 1 for faces and signs. */
    public int scale(String token, Cast cast, String farmer) {
        return tokenHeight(token, cast, farmer) <= 18 ? 2 : 1;
    }

    /** A token's width, without drawing it. */
    public int tokenWidth(SceneCanvas canvas, String token, Catalog catalog, Cast cast, String farmer, boolean big) {
        String key = token.equals("food") ? "item:chili_dog" : token;
        int scale = big ? scale(key, cast, farmer) : 1;
        if (key.startsWith("item:")) {
            return catalog.hasItem(key.substring(5)) ? 16 * scale : 0;
        }
        SceneImage image = tokenImage(key, cast, farmer);
        return image == null ? 0 : image.width() * scale;
    }

    public int tokenWidth(SceneCanvas canvas, String token, Catalog catalog, Cast cast, String farmer) {
        return tokenWidth(canvas, token, catalog, cast, farmer, false);
    }

    /** A token's height at 1x (item icons are 16). */
    public int tokenHeight(String token, Cast cast, String farmer) {
        if (token.equals("food") || token.startsWith("item:")) {
            return 16;
        }
        SceneImage image = tokenImage(token, cast, farmer);
        return image == null ? 0 : image.height();
    }

    private SceneImage tokenImage(String token, Cast cast, String farmer) {
        if (token.startsWith("who:")) {
            VillagerDef v = cast.get(token.substring(4));
            return head(v == null ? "hero:sonic" : v.body());
        }
        return switch (token) {
            case "farmer" -> head("hero:" + farmer);
            case "robotnik" -> head("robotnik");
            case "ring" -> art.ring.frame(0).image();
            case "badnik" -> art.motobug.frame(0).image();
            case "flicky" -> art.flicky.frame(0).image();
            default -> glyph(token);
        };
    }

    /** The original glyphs: simple 13-16 pixel shapes in Mega Drive colours. */
    public SceneImage glyph(String token) {
        SceneImage cached = glyphs.get(token);
        if (cached != null || glyphs.containsKey(token)) {
            return cached;
        }
        String[] rows = Glyphs.rows(token);
        SceneImage image = rows == null ? null : Glyphs.image(rows);
        glyphs.put(token, image);
        return image;
    }
}
