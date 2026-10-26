package com.openggf.tests.reel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.Engine;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.sonic2.Sonic2;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.game.sonic2.Sonic2PlayerArt;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.kis2.Kis2PlayerArt;
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.level.Palette;
import com.openggf.level.objects.*;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.joml.Matrix4f;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;

/**
 * Presentation-only attachments for exact synthetic S2 fixture models (2026-10-26 TAS reel).
 * Uses detached original-ROM art and production draw owners on a blank component backdrop.
 * No world/terrain is substituted; no input, camera, animation or simulation state is advanced.
 */
public final class S2ComponentTestReel implements AutoCloseable {
    private static final ThreadLocal<S2ComponentTestReel> ACTIVE = new ThreadLocal<>();
    private static final ObjectMapper JSON = new ObjectMapper()
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private final int zone;
    private PresentationOffscreenTestContext context;
    private GraphicsManager graphics;
    private Rom rom;
    private Rom skRom;
    private Sonic2ObjectArt art;
    private Sonic2PlayerArt playerArt;
    private Kis2PlayerArt knucklesArt;
    private final Map<String, PatternSpriteRenderer> renderers = new HashMap<>();
    private final Map<String, ObjectSpriteSheet> sheets = new HashMap<>();
    private final Map<String, PlayerSpriteRenderer> players = new HashMap<>();
    private final IdentityHashMap<AbstractPlayableSprite, int[]> componentOrigins = new IdentityHashMap<>();
    private ObjectRenderManager objects;
    private com.openggf.sprites.managers.SpriteManager spriteView;
    private int nextPattern = 0x10000;
    private List<AbstractPlayableSprite> extraPlayers = List.of();

    private S2ComponentTestReel(int zone) { this.zone = zone; ACTIVE.set(this); }
    public static S2ComponentTestReel open(int nativeZone) { return new S2ComponentTestReel(nativeZone); }

    /** Captures only after a pre-existing logical operation; held/pressed use fixture logical masks. */
    public static void frame(AbstractPlayableSprite player, Collection<? extends ObjectInstance> models,
                             int held, int pressed, String segment) {
        if (!TestReelCapture.isRecording()) return;
        var capture = ACTIVE.get();
        if (capture == null) throw new IllegalStateException("Missing S2 component presentation scope");
        capture.draw(player, models, held, pressed, segment, null);
    }
    public static void teamFrame(AbstractPlayableSprite leader, AbstractPlayableSprite follower,
                                 String segment, int cameraX, int cameraY) {
        if (!TestReelCapture.isRecording()) return;
        var capture = ACTIVE.get();
        capture.extraPlayers = List.of(follower);
        try { capture.draw(leader, List.of(), 0, 0, segment, new int[]{cameraX, cameraY}); }
        finally { capture.extraPlayers = List.of(); }
    }

    /** Existing camera state can define the view without modifying it. */
    public static void cameraFrame(AbstractPlayableSprite player, Collection<? extends ObjectInstance> models,
                                   int held, int pressed, String segment, int cameraX, int cameraY) {
        if (!TestReelCapture.isRecording()) return;
        var capture = ACTIVE.get();
        if (capture == null) throw new IllegalStateException("Missing S2 component presentation scope");
        capture.draw(player, models, held, pressed, segment, new int[]{cameraX, cameraY});
    }

    /** Mirrors the tested mock draw consumer's exact arguments through the ROM-backed renderer. */
    public static void pointsFrame(int mapping, int x, int y, boolean hFlip, boolean vFlip) {
        if (!TestReelCapture.isRecording()) return;
        var capture = ACTIVE.get();
        try {
            capture.prepare();
            clear(); capture.renderer(ObjectArtKeys.POINTS).drawFrameIndex(mapping, x, y, hFlip, vFlip);
            capture.finish(x - 160, y - 112, 0, 0, "COMPONENT_VIEW points mock consumer frame=" + mapping);
        } catch (Exception failure) { throw new IllegalStateException("S2 points presentation failed", failure); }
    }

    private void prepare() throws Exception {
        if (context != null) return;
        // A private graphics owner survives fixture resets without altering their global graphics mode.
        context = PresentationOffscreenTestContext.openConfigured(320, 224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
        graphics = new GraphicsManager(); graphics.setHeadlessMode(false);
        graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
        glViewport(0, 0, 320, 224); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        graphics.setProjectionWidth(320); graphics.applyResolvedDisplayWidth(320);
        graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0, 320, 0, 224).get(new float[16]));
        graphics.setViewport(0, 0, 320, 224);
        rom = new Rom(); rom.open(Objects.requireNonNull(System.getProperty("sonic2.rom.path"), "S2 ROM required for component art"));
        var reader = RomByteReader.fromRom(rom);
        art = new Sonic2ObjectArt(rom, reader); playerArt = new Sonic2PlayerArt(reader);
        palette(rom.readBytes(Sonic2Constants.SONIC_TAILS_PALETTE_ADDR, 32), 0);
        int index = rom.read32BitAddr(Sonic2Constants.LEVEL_DATA_DIR + zone * 12 + 8) >>> 24;
        int entry = Sonic2Constants.LEVEL_PALETTE_DIR + index * 8;
        int address = rom.read32BitAddr(entry), count = Math.min(3, (rom.read16BitAddr(entry + 6) + 1) * 4 / 32);
        for (int line = 0; line < count; line++) palette(rom.readBytes(address + line * 32, 32), line + 1);
        var provider = (ObjectArtProvider) Proxy.newProxyInstance(ObjectArtProvider.class.getClassLoader(),
                new Class<?>[]{ObjectArtProvider.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getRenderer")) return renderer((String)args[0]);
                    if (method.getName().equals("getSheet")) { renderer((String)args[0]); return sheets.get((String)args[0]); }
                    if (method.getName().equals("getHudDigitPatterns")) return art.loadForZone(-1).getHudDigitPatterns();
                    if (method.getName().equals("isReady")) return true;
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    throw new UnsupportedOperationException("Unreviewed component art request: " + method);
                });
        objects = new ObjectRenderManager(provider);
    }

    private void palette(byte[] bytes, int line) {
        var palette = new Palette(); palette.fromSegaFormat(bytes); graphics.cachePaletteTexture(palette, line);
    }
    private PatternSpriteRenderer renderer(String key) throws Exception {
        if (renderers.containsKey(key)) return renderers.get(key);
        var sheet = switch (key) {
            case ObjectArtKeys.MONITOR -> art.loadForZone(-1).monitorSheet();
            case ObjectArtKeys.ANIMAL -> art.loadForZone(-1).animalSheet(); // fixture constructors select Rabbit/Rabbit with no render manager
            case ObjectArtKeys.POINTS -> art.loadPointsSheet();
            case ObjectArtKeys.EXPLOSION -> art.loadExplosionSheet();
            case ObjectArtKeys.EGG_PRISON -> art.loadEggPrisonSheet();
            case Sonic2ObjectArtKeys.LAUNCH_BALL -> art.loadLaunchBallSheet();
            case Sonic2ObjectArtKeys.SEESAW -> art.loadSeesawSheet();
            case Sonic2ObjectArtKeys.SEESAW_BALL -> art.loadSeesawBallSheet();
            case Sonic2ObjectArtKeys.NEBULA -> art.loadNebulaSheet();
            case Sonic2ObjectArtKeys.EHZ_BOSS -> art.loadEHZBossSheet();
            case ObjectArtKeys.RESULTS -> art.loadForZone(-1).resultsSheet();
            case Sonic2ObjectArtKeys.ARZ_BOSS_MAIN -> art.loadARZBossMainSheet();
            case Sonic2ObjectArtKeys.ARZ_BOSS_PARTS -> art.loadARZBossPartsSheet();
            case Sonic2ObjectArtKeys.CPZ_STAIR_BLOCK -> art.loadCpzStairBlockSheet();
            default -> throw new IllegalStateException("No reviewed ROM sheet for " + key);
        };
        if (sheet == null) throw new IllegalStateException("Missing ROM component sheet " + key);
        var renderer = new PatternSpriteRenderer(sheet, graphics);
        renderer.ensurePatternsCached(graphics, nextPattern); nextPattern += sheet.getPatterns().length;
        sheets.put(key, sheet); renderers.put(key, renderer); return renderer;
    }
    private PlayerSpriteRenderer playerRenderer(AbstractPlayableSprite player) throws Exception {
        String code = player.getCode().toLowerCase(Locale.ROOT);
        String kind = code.contains("knuckles") ? "knuckles" : code.contains("tails") ? "tails" : "sonic";
        if (players.containsKey(kind)) return players.get(kind);
        SpriteArtSet set;
        if (kind.equals("knuckles")) {
            if (knucklesArt == null) {
                skRom = new Rom(); skRom.open(Objects.requireNonNull(System.getProperty("s3k.rom.path"), "S&K ROM required for KiS2 art"));
                var sk = RomByteReader.fromRom(skRom).window(0, 0x200000);
                var skArt = new Sonic3kPlayerArt(sk);
                knucklesArt = new Kis2PlayerArt(sk, skArt::loadForCharacter);
            }
            set = knucklesArt.loadKnuckles();
        } else set = playerArt.loadForCharacter(kind);
        // Isolated banks prevent two same-world player types from sharing art slots.
        set = new SpriteArtSet(set.artTiles(), set.mappingFrames(), set.dplcFrames(), set.paletteIndex(),
                nextPattern, set.frameDelay(), set.bankSize(), set.animationProfile(), set.animationSet());
        nextPattern += Math.max(512, set.bankSize());
        var renderer = new PlayerSpriteRenderer(set, graphics); players.put(kind, renderer); return renderer;
    }

    private void draw(AbstractPlayableSprite player, Collection<? extends ObjectInstance> models, int held,
                      int pressed, String segment, int[] view) {
        var modelList = List.copyOf(models);
        try {
            prepare();
            var before = JSON.valueToTree(player.captureRewindState());
            var extraBefore = extraPlayers.stream().map(p -> JSON.valueToTree(p.captureRewindState())).toList();
            var identities = new com.openggf.game.rewind.identity.RewindIdentityTable();
            identities.registerPlayer(player, com.openggf.game.rewind.identity.PlayerRefId.mainPlayer());
            for (int i = 0; i < extraPlayers.size(); i++)
                identities.registerPlayer(extraPlayers.get(i), com.openggf.game.rewind.identity.PlayerRefId.sidekick(i));
            for (int i = 0; i < modelList.size(); i++)
                identities.registerObject(modelList.get(i), com.openggf.game.rewind.identity.ObjectRefId.dynamic(i, 0, i));
            var rewind = com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(identities);
            var objectStates = new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            for (var model : models) objectStates.add(JSON.valueToTree(((AbstractObjectInstance)model).captureRewindState(rewind)));
            // Read-only presentation view of these exact fixture players, for native object-facing owners.
            var visiblePlayers = new ArrayList<com.openggf.sprites.Sprite>();
            visiblePlayers.add(player); visiblePlayers.addAll(extraPlayers);
            spriteView = new com.openggf.sprites.managers.SpriteManager() {
                @Override public Collection<com.openggf.sprites.Sprite> getAllSprites() { return List.copyOf(visiblePlayers); }
            };
            int[] origin = view != null ? view : componentOrigins.computeIfAbsent(player,
                    p -> new int[]{p.getCentreX() - 160, p.getCentreY() - 112});
            clear();
            for (var model : models) drawObject((AbstractObjectInstance)model);
            if (player.getCode().toLowerCase(Locale.ROOT).contains("knuckles"))
                graphics.cachePaletteTexture(knucklesPalette(player), 0);
            else palette(rom.readBytes(Sonic2Constants.SONIC_TAILS_PALETTE_ADDR, 32), 0);
            playerRenderer(player).drawFrame(player.getMappingFrame(), player.getCentreX(), player.getCentreY(),
                    player.getRenderHFlip(), player.getRenderVFlip());
            for (var extra : extraPlayers) playerRenderer(extra).drawFrame(extra.getMappingFrame(), extra.getCentreX(),
                    extra.getCentreY(), extra.getRenderHFlip(), extra.getRenderVFlip());
            finish(origin[0], origin[1], held, pressed, "COMPONENT_VIEW " + segment);
            for (int i = 0; i < extraPlayers.size(); i++)
                if (!extraBefore.get(i).equals(JSON.valueToTree(extraPlayers.get(i).captureRewindState())))
                    throw new IllegalStateException("Component draw mutated follower rewind state");
            if (!before.equals(JSON.valueToTree(player.captureRewindState())))
                throw new IllegalStateException("Component draw mutated player rewind state");
            for (int i = 0; i < models.size(); i++)
                if (!objectStates.get(i).equals(JSON.valueToTree(((AbstractObjectInstance)modelList.get(i)).captureRewindState(rewind))))
                    throw new IllegalStateException("Component draw mutated " + modelList.get(i).getClass().getName());
        } catch (Exception failure) { throw new IllegalStateException("S2 component capture failed: " + segment, failure); }
    }
    private Palette knucklesPalette(AbstractPlayableSprite player) throws Exception {
        playerRenderer(player); return knucklesArt.loadKnucklesPalette();
    }
    private void drawObject(AbstractObjectInstance model) throws Exception {
        var field = AbstractObjectInstance.class.getDeclaredField("services"); field.setAccessible(true);
        var original = (ObjectServices)field.get(model);
        var proxy = (ObjectServices)Proxy.newProxyInstance(ObjectServices.class.getClassLoader(),
                new Class<?>[]{ObjectServices.class}, (p, method, args) -> {
                    if (method.getName().equals("renderManager")) return objects;
                    if (method.getName().equals("graphicsManager")) return graphics;
                    if (method.getName().equals("spriteManager")) return spriteView;
                    if (original == null) throw new IllegalStateException("Fixture has no service for " + method);
                    return method.invoke(original, args);
                });
        model.setServices(proxy);
        var attachments = new LinkedHashMap<java.lang.reflect.Field, Object>();
        try {
            // These production owners cache presentation dependencies at construction.
            // Attach their exact detached ROM art only for draw, then restore original identities.
            if (model instanceof ExplosionObjectInstance) attach(model, ExplosionObjectInstance.class,
                    "renderManager", objects, attachments);
            if (model instanceof AnimalObjectInstance) attach(model, AnimalObjectInstance.class,
                    "renderer", renderer(ObjectArtKeys.ANIMAL), attachments);
            if (model instanceof com.openggf.game.sonic2.objects.ResultsScreenObjectInstance) {
                for (String name : List.of("lastTimeBonus", "lastRingBonus", "lastTotalBonus", "lastPerfectBonus")) {
                    var cache = com.openggf.game.sonic2.objects.ResultsScreenObjectInstance.class.getDeclaredField(name);
                    cache.setAccessible(true); attachments.put(cache, cache.get(model));
                }
            }
            // MTZ platforms read native level-art tiles, independent of their authored collision geometry.
            if (model instanceof com.openggf.game.sonic2.objects.MTZPlatformObjectInstance) {
                cacheMtzLevelArt();
                drawMtzWithDetachedMappings(model);
            } else model.appendRenderCommands(new ArrayList<>());
        } finally {
            for (var attachment : attachments.entrySet()) attachment.getKey().set(model, attachment.getValue());
            model.setServices(original);
        }
    }
    private static void attach(Object model, Class<?> owner, String name, Object value,
                               Map<java.lang.reflect.Field, Object> originals) throws Exception {
        var field = owner.getDeclaredField(name); field.setAccessible(true);
        originals.put(field, field.get(model)); field.set(model, value);
    }
    private void drawMtzWithDetachedMappings(AbstractObjectInstance model) throws Exception {
        // LazyMappingHolder normally reads the active level game. This collision-only
        // fixture intentionally has none; lend only the exact native mapping data.
        var holderField = com.openggf.game.sonic2.objects.MTZPlatformObjectInstance.class.getDeclaredField("MAPPINGS");
        holderField.setAccessible(true); Object holder = holderField.get(null);
        var mappings = holder.getClass().getDeclaredField("mappings");
        var attempted = holder.getClass().getDeclaredField("attempted");
        mappings.setAccessible(true); attempted.setAccessible(true);
        Object previous = mappings.get(holder); boolean wasAttempted = attempted.getBoolean(holder);
        try {
            mappings.set(holder, com.openggf.game.sonic2.S2SpriteDataLoader.loadMappingFrames(
                    RomByteReader.fromRom(rom), Sonic2Constants.MAP_UNC_MTZ_PLATFORM_LEVELART_ADDR));
            attempted.setBoolean(holder, true);
            model.appendRenderCommands(new ArrayList<>());
        } finally { mappings.set(holder, previous); attempted.setBoolean(holder, wasAttempted); }
    }
    private boolean mtzCached;
    private void cacheMtzLevelArt() throws Exception {
        if (mtzCached) return;
        var level = new Sonic2(rom).buildDetachedLevel(14);
        graphics.beginPatternAtlasBatch();
        try { for (int i = 0; i < level.getPatternCount(); i++) if (level.getPattern(i) != null) graphics.cachePatternTexture(level.getPattern(i), i); }
        finally { graphics.endPatternAtlasBatch(); }
        mtzCached = true;
    }
    private static void clear() { glClearColor(0, 0, 0, 1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); }
    private void finish(int x, int y, int held, int pressed, String mode) {
        graphics.flushWithCamera((short)x, (short)y, (short)320, (short)224); glFinish();
        TestReelCapture.recordFrame(ScreenshotCapture.captureFramebuffer(320, 224), mode,
                new Bk2FrameInput(0, held, pressed, false, "actual fixture logical input"));
    }
    @Override public void close() {
        ACTIVE.remove();
        if (context == null) return;
        try { graphics.cleanup(); }
        finally {
            try { if (rom != null) rom.close(); if (skRom != null) skRom.close(); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            finally { context.close(); }
        }
    }
}
