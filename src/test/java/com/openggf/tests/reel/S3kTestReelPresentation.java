package com.openggf.tests.reel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.Engine;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.features.HCZWaterSkimHandler;
import com.openggf.level.Pattern;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.graphics.PatternAtlasRange;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.physics.Direction;
import com.openggf.data.RomManager;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.Sonic3kPlcArtRegistry;
import com.openggf.game.sonic3k.Sonic3kObjectArt;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.level.objects.*;
import com.openggf.graphics.GLCommand;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import com.openggf.game.sonic3k.specialstage.*;
import com.openggf.tools.PresentationOffscreenTestContext;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.lwjgl.opengl.GL11.*;

/** Opt-in S3K component presentation over the fixture's actual state, without simulation. */
public final class S3kTestReelPresentation implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper().setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private PresentationOffscreenTestContext context;
    private Sonic3kSpecialStageRenderer sphereRenderer;
    private Sonic3kSpecialStagePalette spherePalette;
    private int frame;
    private ObjectRenderManager objectArt;
    private PatternSpriteRenderer skimRenderer;
    private AbstractPlayableSprite participant;

    private S3kTestReelPresentation() { }

    /** Observe an actual fixture participant for input/state guards, without attaching or drawing it. */
    public void observeParticipant(AbstractPlayableSprite player) { participant=player; }

    /** The inactive owner allocates no graphics, ROM or presentation resources. */
    public static S3kTestReelPresentation open() { return new S3kTestReelPresentation(); }

    public static void afterSessionFrame(GameplayCaptureSession session, Bk2FrameInput input) {
        if (TestReelCapture.isRecording()) {
            TestReelCapture.recordFrame(session.render(), session.loop().getCurrentGameMode().name(), input);
        }
    }

    /**
     * Render the existing component manager with its production renderer. Only art/mapping/
     * palette loading uses a detached manager: its ROM layout/player is never drawn or copied
     * to the tested manager. The complete fixture rewind snapshot is compared across drawing.
     */
    public void afterBlueSphere(Sonic3kSpecialStageManager manager) {
        if (!TestReelCapture.isRecording()) return;
        try {
            JsonNode before = snapshot(manager);
            if (sphereRenderer == null) prepareBlueSphere(manager);
            var graphics = GameServices.graphics();
            var perspective = (Sonic3kSpecialStagePerspective) field(manager,"perspective");
            spherePalette.updateRotation(perspective.getAnimFrame(), perspective.getPaletteFrame(),
                    manager.getPlayer().getTurning() < 0);
            for (int i=0;i<4;i++) graphics.cachePaletteTexture(spherePalette.getPalette(i),i);
            glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            sphereRenderer.render(manager);
            graphics.flush(); glFinish();
            if (!before.equals(snapshot(manager)))
                throw new IllegalStateException("Blue Sphere recording mutated the fixture rewind state");
            TestReelCapture.recordFrame(ScreenshotCapture.captureFramebuffer(320,224),
                    "SPECIAL_STAGE_COMPONENT",new Bk2FrameInput(frame++,0,0,false,"manager.update()"));
        } catch (Exception failure) { throw new IllegalStateException("S3K component recording failed",failure); }
    }

    /** Actual object component geometry on a blank backdrop, with an explicit view origin.
     * Only the draw's renderManager service is overlaid; original services are reinstated
     * before returning. No object update, spawn, camera write or state restore occurs here.
     */
    public void afterObjects(List<? extends AbstractObjectInstance> objects, int zone, int act,
            int viewX, int viewY, String... artKeys) {
        if (!TestReelCapture.isRecording()) return;
        try {
            var before = objectSnapshots(objects);
            if (objectArt == null) prepareObjectArt(zone,act,artKeys);
            var graphics=GameServices.graphics();
            glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            for (var object : objects) {
                ObjectServices original = (ObjectServices) inheritedField(object,"services");
                var renderServices = (ObjectServices) Proxy.newProxyInstance(ObjectServices.class.getClassLoader(),
                        new Class<?>[]{ObjectServices.class}, (proxy,method,args) ->
                                method.getName().equals("renderManager") ? objectArt : method.invoke(original,args));
                object.setServices(renderServices);
                try {
                    var commands=new ArrayList<GLCommand>();
                    object.appendRenderCommands(commands);
                    if (!commands.isEmpty()) throw new IllegalStateException("Component emitted primitive fallback imagery");
                } finally { object.setServices(original); }
            }
            graphics.flushWithCamera((short)viewX,(short)viewY,(short)320,(short)224); glFinish();
            if (!before.equals(objectSnapshots(objects)))
                throw new IllegalStateException("Object recording mutated gameplay/rewind state");
            TestReelCapture.recordFrame(ScreenshotCapture.captureFramebuffer(320,224),
                    "OBJECT_COMPONENT",new Bk2FrameInput(frame++,participant==null?0:participant.getLogicalInputState(),0,false,"actual object update boundary; logical pad only"));
        } catch (Exception failure) { throw new IllegalStateException("S3K object component recording failed",failure); }
    }

    /** The handler's native splash art, actual participant coordinates and actual animation.
     * This fixture has no drawable player; no replacement player or water plane is created.
     */
    @SuppressWarnings("unchecked")
    public void afterWaterSkim(AbstractPlayableSprite player,int waterLevel) {
        if (!TestReelCapture.isRecording()) return;
        try {
            var before=HCZWaterSkimHandler.snapshot();
            JsonNode playerBefore=JSON.valueToTree(player.captureRewindState());
            if (skimRenderer==null) {
                prepareGraphics();
                try (var rom=new Rom()) {
                    if (!rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()))
                        throw new IllegalStateException("Original S3K art ROM did not open");
                    Method patterns=HCZWaterSkimHandler.class.getDeclaredMethod("loadSplashPatterns",Rom.class);
                    Method maps=HCZWaterSkimHandler.class.getDeclaredMethod("loadSplashMappings",RomByteReader.class);
                    patterns.setAccessible(true);maps.setAccessible(true);
                    skimRenderer=new PatternSpriteRenderer(new ObjectSpriteSheet((Pattern[])patterns.invoke(null,rom),
                            (List<SpriteMappingFrame>)maps.invoke(null,RomByteReader.fromRom(rom)),0,0));
                    skimRenderer.ensurePatternsCached(GameServices.graphics(),PatternAtlasRange.OBJECTS.base());
                    loadObjectPalettes(rom,1,0);
                }
            }
            glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            if (before.skimActiveP1() && before.splashAnimFrameP1()<5)
                skimRenderer.drawFrameIndex(before.splashAnimFrameP1(),player.getCentreX(),waterLevel,
                        player.getDirection()==Direction.LEFT,false);
            GameServices.graphics().flushWithCamera((short)0x60,(short)0x190,(short)320,(short)224);glFinish();
            if (!before.equals(HCZWaterSkimHandler.snapshot()) || !playerBefore.equals(JSON.valueToTree(player.captureRewindState())))
                throw new IllegalStateException("Splash recording mutated gameplay state");
            TestReelCapture.recordFrame(ScreenshotCapture.captureFramebuffer(320,224),"WATER_SPLASH_COMPONENT",
                    new Bk2FrameInput(frame++,player.getLogicalInputState(),0,false,"actual skim/manager update boundary; logical pad only"));
        } catch (Exception failure) { throw new IllegalStateException("S3K splash recording failed",failure); }
    }

    private void prepareObjectArt(int zone,int act,String[] keys) throws Exception {
        prepareGraphics();
        try (var rom=new Rom()) {
            if (!rom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()))
                throw new IllegalStateException("Original S3K art ROM did not open");
            var provider=new Sonic3kObjectArtProvider();
            for (String key:keys) loadComponentArt(provider,rom,key);
            objectArt=new ObjectRenderManager(provider);
            loadObjectPalettes(rom,zone,act);
        }
    }
    private static void loadComponentArt(Sonic3kObjectArtProvider provider,Rom rom,String key) throws Exception {
        if (key.equals(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.MONITOR)) {
            var romManager=RomManager.getInstance();
            Rom previous=(Rom)field(romManager,"rom");
            romManager.setRom(rom);
            try { call(provider,"loadMonitorArt"); }
            finally { romManager.setRom(previous); }
            provider.ensurePatternsCached(GameServices.graphics(),PatternAtlasRange.OBJECTS.base());
            return;
        }
        // Prefer the object's native standalone PLC. The encounter library's first matching
        // level-art registration can be an unloaded/blank tile range for the shared BUTTON
        // key; that range is not this cutscene object's ArtNem_GrayButton source.
        for (int zone=0;zone<=12;zone++) for (int act=0;act<2;act++) {
            for (var entry:Sonic3kPlcArtRegistry.getPlan(zone,act).standaloneArt()) {
                if (key.equals(entry.key())) {
                    var sheet=new Sonic3kObjectArt(null,RomByteReader.fromRom(rom)).loadStandaloneSheet(rom,entry);
                    Method register=Sonic3kObjectArtProvider.class.getDeclaredMethod("registerSheet",String.class,ObjectSpriteSheet.class);
                    register.setAccessible(true);register.invoke(provider,key,sheet);
                    provider.ensurePatternsCached(GameServices.graphics(),PatternAtlasRange.OBJECTS.base());
                    return;
                }
            }
        }
        if (!provider.ensureEncounterArtLoaded(rom,GameServices.graphics(),key))
            throw new IllegalStateException("Missing production component art: "+key);
    }

    private static void loadObjectPalettes(Rom rom,int zone,int act) throws Exception {
            // Same PalPointers/LevelLoadBlock decode as Sonic3k; only ROM color bytes,
            // no detached terrain, player or route is installed or drawn.
            int block=Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR+(zone*2+act)*Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
            int paletteIndex=(rom.read32BitAddr(block+8)>>>24)&255;
            int pointer=Sonic3kConstants.PAL_POINTERS_ADDR+paletteIndex*Sonic3kConstants.PAL_POINTER_ENTRY_SIZE;
            int address=rom.read32BitAddr(pointer)&0xFFFFFF;
            int firstLine=(rom.read16BitAddr(pointer+4)&255)/32;
            int bytes=((rom.read16BitAddr(pointer+6)&65535)+1)*4;
            cachePalette(rom,0,Sonic3kConstants.SONIC_PALETTE_ADDR);
            for (int offset=0;offset<bytes;offset+=32) cachePalette(rom,firstLine+offset/32,address+offset);
            if (zone==7 && act==1) cachePalette(rom,1,Sonic3kConstants.PAL_MHZ_END_BOSS_ADDR);
    }
    private static void cachePalette(Rom rom,int line,int address) throws Exception {
        if (line<0 || line>=4) throw new IllegalStateException("Invalid ROM palette line "+line);
        var palette=new Palette();palette.fromSegaFormat(rom.readBytes(address,32));
        GameServices.graphics().cachePaletteTexture(palette,line);
    }
    private List<JsonNode> objectSnapshots(List<? extends AbstractObjectInstance> objects) throws Exception {
        var snapshots=new ArrayList<JsonNode>();
        for (var object:objects) {
            var services=(ObjectServices)inheritedField(object,"services");
            var manager=services.objectManager();
            var context=manager==null ? com.openggf.game.rewind.schema.RewindCaptureContext.withIdentityTable(
                    new com.openggf.game.rewind.identity.RewindIdentityTable()) : manager.captureIdentityContext();
            if (participant!=null) context.requireIdentityTable().registerPlayer(participant,
                    com.openggf.game.rewind.identity.PlayerRefId.mainPlayer());
            snapshots.add(JSON.valueToTree(object.captureRewindState(context)));
            if (participant!=null) snapshots.add(JSON.valueToTree(participant.captureRewindState()));
        }
        return snapshots;
    }
    private static Object inheritedField(Object owner,String name) throws Exception {
        for (Class<?> type=owner.getClass();type!=null;type=type.getSuperclass()) {
            try { Field field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private void prepareBlueSphere(Sonic3kSpecialStageManager fixture) throws Exception {
        prepareGraphics();
        // No bootstrap reset or level boot: retain the fixture's services and audio backend.
        var romManager = RomManager.getInstance();
        Rom previousRom = (Rom) field(romManager,"rom");
        try (var artRom = new Rom()) {
            if (!artRom.open(RomTestUtils.ensureSonic3kRomAvailable().getAbsolutePath()))
                throw new IllegalStateException("Original S3K art ROM did not open");
            romManager.setRom(artRom);
            try {
                var artOwner = new Sonic3kSpecialStageManager();
                sphereRenderer = new Sonic3kSpecialStageRenderer(GameServices.graphics());
                spherePalette = new Sonic3kSpecialStagePalette();
                set(artOwner,"renderer",sphereRenderer);
                set(artOwner,"palette",spherePalette);
                set(artOwner,"playerCharacter",fixture.getPlayerCharacter());
                set(artOwner,"currentStage",field(fixture,"currentStage"));
                call(artOwner,"loadRomData");
            } finally { romManager.setRom(previousRom); }
        }
    }

    private void prepareGraphics() throws Exception {
        if (context != null) return;
        context = PresentationOffscreenTestContext.openConfigured(320,224,
                PresentationOffscreenTestContext.LegacyProfile.CORE_41);
        var graphics=GameServices.graphics();
        graphics.setHeadlessMode(false); graphics.init(Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
        glViewport(0,0,320,224); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        graphics.setProjectionWidth(320); graphics.applyResolvedDisplayWidth(320);
        graphics.setProjectionMatrixBuffer(new Matrix4f().ortho2D(0,320,0,224).get(new float[16]));
        graphics.setViewport(0,0,320,224);
    }

    private static JsonNode snapshot(Sonic3kSpecialStageManager manager) throws Exception {
        return JSON.valueToTree(call(manager,"captureRewindSnapshot"));
    }
    private static Object call(Object owner,String name) throws Exception {
        Method method=owner.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(owner);
    }
    private static Object field(Object owner,String name) throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);
    }
    @Override public void close() {
        if (context != null) {
            try { GameServices.graphics().cleanup(); }
            finally { context.close(); }
        }
    }
}
