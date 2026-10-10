package com.openggf.mods.code;

import com.openggf.configuration.*;
import com.openggf.control.*;
import com.openggf.game.*;
import com.openggf.game.modzone.ModPaletteClaim;
import com.openggf.game.patch.*;
import com.openggf.game.session.SessionManager;
import com.openggf.io.*;
import com.openggf.level.objects.*;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Town objects in a genuine S3K additive act, independent of the Green Hill terrain lane.
 * External creator classes are packaged/validated and loaded exactly as TestInfiniteSonic.
 * Origin: Starpost real-town lane, 2026-10-09. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStarpostTownAct {
    @TempDir static Path temp;
    static URLClassLoader loader;
    static Path jar;
    SharedLevel bootstrap;
    HeadlessTestFixture fixture;
    GameModule effective;
    Object town, game;
    SceneRomLibrary artLibrary;
    final com.openggf.mods.ModRuntimeFindingStore findings=new com.openggf.mods.ModRuntimeFindingStore();
    static final String OWNER="starpost-valley";
    static final ZoneKey.Mod ZONE=new ZoneKey.Mod(OWNER,"town-test");

    @BeforeAll static void compile() throws Exception {
        Path project=Path.of("examples/starpost-valley"), classes=Files.createDirectories(temp.resolve("classes"));
        var args=new ArrayList<>(List.of("--release","21","-cp",System.getProperty("java.class.path"),"-d",classes.toString()));
        try (var files=Files.walk(project.resolve("src/main/java"))) {
            files.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->args.add(p.toString()));
        }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        try (var resources=Files.walk(project.resolve("src/main/resources"))) {
            for (Path source:resources.toList()) {
                Path destination=classes.resolve(project.resolve("src/main/resources").relativize(source));
                if (Files.isDirectory(source)) Files.createDirectories(destination);
                else Files.copy(source,destination);
            }
        }
        ExampleModHarness.generateResources(project,classes);
        jar=temp.resolve("town.jar");
        assertEquals(0,GgfModCli.run(new String[]{"package","--input",classes.toString(),"--out",jar.toString()},System.out));
        loader=new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()},TestStarpostTownAct.class.getClassLoader());
    }
    @AfterAll static void closeLoader() throws Exception { if (loader!=null) loader.close(); }
    @AfterEach void close() throws Exception {
        if (artLibrary!=null) artLibrary.close();
        if (bootstrap!=null) bootstrap.dispose();
        assertTrue(findings.snapshot().isEmpty(),findings.snapshot().toString());
    }

    private void launch(String farmer) throws Exception {
        bootstrap=SharedLevel.load(SonicGame.SONIC_3K,0,0);
        var config=SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,farmer);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,WidescreenAspect.WIDE_16_9.name());
        config.resolveDisplayAspect();
        GameModule base=GameServices.module();
        try (var assets=ModAssetRoot.jar(temp,jar,ModInputLimits.production())) {
            ModContext context=new ModContext(OWNER,"s3k",assets);
            ((GgfMod)loader.loadClass("starpost.StarpostValleyMod").getConstructor().newInstance()).register(context);
            var plan=context.freeze();
            Object registeredTown=plan.serviceBundles().get("town").get()
                .services().get(loader.loadClass("starpost.realtown.TownSession"));
            GameplayInputFilter filter=(GameplayInputFilter)loader.loadClass("starpost.realtown.TownInput")
                .getConstructor(loader.loadClass("starpost.realtown.TownSession")).newInstance(registeredTown);
            var declaration=ModZoneContribution.singleAct(ZONE.localName(),new BakedLevelRef("test-only/level.json"),null,null,false);
            var prepared=PreparedModZone.prepared(OWNER,declaration,placeholder());
            var combined=new ModRegistrationPlan(plan.ownerModId(),plan.baseGameId(),plan.objectFactories(),
                plan.objectArt(),plan.preparedObjectArt(),plan.explicitPatches(),List.of(declaration),List.of(prepared),
                plan.objectPreviewArtKeys(),plan.characters(),plan.standaloneModule(),plan.romObjectArt(),plan.launchTeams(),
                Map.of(ZONE,filter),plan.hudProfiles(),plan.startupScene(),plan.requiredDisplayAspect(),
                plan.serviceBundles(),plan.decodedLevelPatches(),plan.contributionLimit());
            var boundary=new ModFaultBoundary(Map.of(),findings,ignored->new com.openggf.mods.ModStateSaveResult.Saved(),
                ignored->{ });
            effective=new ModBackedGamePatch(combined,boundary).apply(base,
                new PatchContext(LogicalRomResolver.fromRomManager(GameServices.rom())::openOrThrow,config));
        }
        Class<?> catalogType=loader.loadClass("starpost.core.Catalog");
        Object catalog=catalogType.getConstructor().newInstance();
        game=loader.loadClass("starpost.core.Game").getMethod("fresh",catalogType,long.class,String.class)
            .invoke(null,catalog,17L,farmer);
        @SuppressWarnings("unchecked") List<Object> sections=(List<Object>)field(game,"sections");
        for (String name:List.of("people.People","valley.Pickups","core.Skills","festivals.Festivals"))
            sections.add(loader.loadClass("starpost."+name).getConstructor().newInstance());
        call(field(game,"calendar"),"set",1,0,2,9*60);
        call(field(game,"inventory"),"select",11);
        town=effective.getGameService(loader.loadClass("starpost.realtown.TownSession"));
        Object layout=loader.loadClass("starpost.realtown.TownLayout").getMethod("placeholder").invoke(null);
        call(town,"bind",game,layout,null);
        SessionManager.clear(); GameModuleRegistry.setCurrent(effective); TestEnvironment.activeGameplayMode();
        int zone=effective.getZoneRegistry().resolveZoneKey(ZONE).orElseThrow();
        fixture=HeadlessTestFixture.builder().withZoneAndAct(zone,0).startPosition((short)440,(short)173).startPositionIsCentre().build();
        GameServices.audio().stopMusic();
        fixture.runtime().getLevelManager().getObjectManager().setRewindClassResolver(new RewindClassResolver() {
            public Optional<Class<?>> resolve(String owner,String name) {
                try { return Optional.of(name.startsWith("starpost.")?loader.loadClass(name):Class.forName(name)); }
                catch (ClassNotFoundException missing) { return Optional.empty(); }
            }
            public Optional<String> ownerOf(Class<?> type) { return type.getName().startsWith("starpost.")?Optional.of(OWNER):Optional.empty(); }
        });
        assertNotNull(effective.getGameplayPolicyProvider().inputFilter(ZONE).orElse(null));
        var b=PlayerInputState.of(0,0,InputActionMasks.ACTION_B,InputActionMasks.ACTION_B,false,false);
        assertEquals(0,effective.getGameplayPolicyProvider().inputFilter(ZONE).orElseThrow().filter(b).heldMask());
        fixture.stepIdleFrames(30);
        assertInstanceOf(com.openggf.game.sonic3k.Sonic3kLevel.class,GameServices.level().getCurrentLevel());
        assertEquals(zone,GameServices.level().getCurrentZone());
        assertEquals(farmer,fixture.sprite().getCode());
        assertFalse(fixture.sprite().getAir(),"real S3K player stands on placeholder collision: centre="+fixture.sprite().getCentreX()+","+fixture.sprite().getCentreY()+" speed="+fixture.sprite().getYSpeed()+" held="+fixture.sprite().isObjectControlled()+" desc="+GameServices.level().getChunkDescAt((byte)0,440,192).getChunkIndex());
    }

    /** Empty upper block, flat collision below row 192, no stock events or objects. */
    private static ModLevelDefinition placeholder() {
        byte[] blocks=new byte[256], map=new byte[52], heights=new byte[32], widths=new byte[32];
        for (int i=32;i<64;i++) { blocks[128+i*2]=0x30; blocks[128+i*2+1]=1; }
        Arrays.fill(map,26,52,(byte)1); Arrays.fill(heights,16,32,(byte)16); Arrays.fill(widths,16,32,(byte)16);
        return new ModLevelDefinition(2,"TOWN",0x40,0x400,8,26,2,
            new ModLevelDefinition.Bounds(0,2928,0,32),new ModLevelDefinition.Start(440,173),
            new ModLevelDefinition.StockMusic(1),List.of(new ModLevelDefinition.KeyedObjectSpawn(0,150,192,
                OWNER+":town-controller",0,0,false,192)),List.of(),new byte[32],new byte[16],blocks,map,null,
            heights,widths,new byte[2],new int[]{0,1},new int[]{0,1},new byte[][]{new byte[32],new byte[32],new byte[32],new byte[32]},
            1,2,2,2,null,List.of(new ModPaletteClaim(2,0,0)));
    }
    private Object villager(String id) throws Exception {
        for (var object:GameServices.level().getObjectManager().getActiveObjects())
            if (object.getClass().getName().equals("starpost.realtown.TownVillager")&&id.equals(call(object,"id"))) return object;
        throw new AssertionError("Missing villager: "+id);
    }
    private void moveTo(int x,int y) {
        NativePositionOps.writeXPosResetSubpixel(fixture.sprite(),x); NativePositionOps.writeYPosResetSubpixel(fixture.sprite(),y);
        fixture.sprite().setGSpeed((short)0); fixture.sprite().setXSpeed((short)0); fixture.sprite().setYSpeed((short)0);
        fixture.sprite().setAir(false); fixture.camera().setX((short)Math.max(0,x-200));
    }
    private void action() {
        InputHandler input=new InputHandler();
        input.setLogicalOverride(LogicalInputSnapshot.ofPlayers(PlayerInputState.of(0,0,
            InputActionMasks.ACTION_B,InputActionMasks.ACTION_B,false,false),PlayerInputState.neutral()));
        effective.getGameService(LevelInputOverlay.class).handleInput(input);
        fixture.stepIdleFrames(1);
    }

    @ParameterizedTest @ValueSource(strings={"sonic","tails","knuckles"})
    void villagerWalksScheduleOnActFloor(String farmer) throws Exception {
        launch(farmer); Object dandel=villager("dandel");
        float before=(float)call(dandel,"x");
        call(field(game,"calendar"),"set",1,0,2,17*60);
        fixture.stepIdleFrames(60);
        assertTrue((float)call(dandel,"x")>before+30,"Dandel walks from stall to plaza");
        assertEquals(192,call(dandel,"feet")); assertTrue((boolean)call(dandel,"visible"));
        call(field(game,"calendar"),"set",1,0,2,19*60); fixture.stepIdleFrames(400);
        assertFalse((boolean)call(dandel,"visible"),"enters seed-stall doorway");
    }
    @Test void padActionOpensPicturesFreezesPlayerAndGiftingUsesExistingInventory() throws Exception {
        launch("sonic"); Object dandel=villager("dandel");
        moveTo(Math.round((float)call(dandel,"x")),173); action();
        assertNotNull(call(town,"speech")); assertTrue((boolean)call(call(town,"speech"),"inPictures"));
        assertTrue(fixture.sprite().isObjectControlled());
        int x=fixture.sprite().getCentreX(), minute=(int)call(field(game,"calendar"),"minutes");
        fixture.stepIdleFrames(90); assertEquals(x,fixture.sprite().getCentreX());
        assertEquals(minute,call(field(game,"calendar"),"minutes"));
        action(); assertFalse((boolean)call(town,"modal")); assertFalse(fixture.sprite().isObjectControlled());
        call(field(game,"inventory"),"set",0,"ring_radish",2); call(field(game,"inventory"),"select",0);
        action(); assertTrue((boolean)call(town,"askingGift")); action();
        assertEquals(1,call(field(game,"inventory"),"total","ring_radish"));
    }
    @Test void doorwayRaisesSingleHandBackThroughControllerInput() throws Exception {
        launch("sonic"); moveTo(896,173);
        call(town,"input",false,true,false,false,false,-1); fixture.stepIdleFrames(1);
        Object request=call(town,"handBack"); assertNotNull(request);
        assertEquals("inn",call(request,"place")); assertEquals(896,call(request,"returnX"));
        fixture.stepIdleFrames(20); assertSame(request,call(town,"handBack"));
        assertTrue(fixture.sprite().isObjectControlled());
    }
    @Test void collectsWalletMomentumAndNativeHealthRingOnce() throws Exception {
        launch("sonic"); int wallet=(int)field(game,"rings"); setField(game,"momentum",10);
        moveTo(320,173); fixture.stepIdleFrames(1);
        assertEquals(wallet+1,field(game,"rings")); assertEquals(11,field(game,"momentum"));
        assertEquals(1,GameServices.level().getLevelGamestate().getRings());
        fixture.stepIdleFrames(15); assertEquals(wallet+1,field(game,"rings"));
    }
    @Test void fullRegistryRewindRestoresTownObjectsRewardsAndForwardReplay() throws Exception {
        launch("sonic"); moveTo(320,173);
        var registry=fixture.runtime().getRewindRegistry();
        var before=registry.capture(); Object townBefore=call(town,"capture");
        fixture.stepIdleFrames(50); Object expected=call(town,"capture");
        float expectedX=(float)call(villager("dandel"),"x");
        registry.restore(before);
        assertEquals(townBefore,call(town,"capture"),"registered adapter, not a manual town restore");
        fixture.stepIdleFrames(50);
        assertEquals(expected,call(town,"capture")); assertEquals(expectedX,call(villager("dandel"),"x"));
        moveTo(Math.round(expectedX),173); action(); assertNotNull(call(town,"speech"));
        var speaking=registry.capture(); Object dialogue=call(town,"capture");
        fixture.stepIdleFrames(100); action(); assertFalse((boolean)call(town,"modal"));
        registry.restore(speaking); assertEquals(dialogue,call(town,"capture"));
        assertTrue(fixture.sprite().isObjectControlled());
    }
    @Test void romTownPresentationQueuesVisiblePicturesWithoutMutatingRules() throws Exception {
        launch("sonic");
        artLibrary=new SceneRomLibrary(effective,GameServices.rom().getRom(),GameServices.rom());
        assertNotNull(artLibrary.rom("s1"),"Sonic 1 must be supplied for ROM town art");
        var artType=loader.loadClass("starpost.art.Art");
        Object art=artType.getConstructor(com.openggf.mods.scene.SceneRomArt.class,com.openggf.mods.scene.SceneRomArt.class)
            .newInstance(artLibrary.rom("s1"),artLibrary.rom("s3k"));
        Object presentation=loader.loadClass("starpost.realtown.TownPresentation")
            .getConstructor(artType,loader.loadClass("starpost.festivals.FestivalSystem")).newInstance(art,null);
        call(town,"bind",game,call(town,"layout"),presentation);
        renderTown("stall",624);
        renderTown("inn",896);
        renderTown("capsule",3192);
        Object dandel=villager("dandel"); moveTo(Math.round((float)call(dandel,"x")),173); action();
        renderTown("pictures",fixture.sprite().getCentreX());
    }
    /** CPU witness of the actual object rectangle commands, not a native player/terrain capture.
     * Opt-in external pictures: -Dstarpost.town.capture.dir=/absolute/task/directory. */
    private void renderTown(String name,int centre) throws Exception {
        fixture.camera().setX((short)Math.max(0,centre-200)); fixture.camera().setY((short)0);
        Object before=call(town,"capture");
        var graphics=new RectangleWitness();
        graphics.paint.setColor(new java.awt.Color(0x5068C0)); graphics.paint.fillRect(0,0,400,224);
        graphics.paint.setColor(new java.awt.Color(0x306830)); graphics.paint.fillRect(0,192,400,32);
        var accessor=AbstractObjectInstance.class.getDeclaredMethod("services"); accessor.setAccessible(true);
        var objects=new ArrayList<>(GameServices.level().getObjectManager().getActiveObjects().stream()
            .filter(AbstractObjectInstance.class::isInstance).map(AbstractObjectInstance.class::cast).toList());
        objects.sort(Comparator.comparingInt(AbstractObjectInstance::getPriorityBucket).reversed());
        for (var object:objects) {
            if (!object.getClass().getName().startsWith("starpost.realtown.")) continue;
            ObjectServices original=(ObjectServices)accessor.invoke(object);
            ObjectServices witness=(ObjectServices)java.lang.reflect.Proxy.newProxyInstance(
                ObjectServices.class.getClassLoader(),new Class<?>[]{ObjectServices.class},(proxy,method,args)->
                    method.getName().equals("graphicsManager")?graphics:method.invoke(original,args));
            object.setServices(witness);
            try { object.appendRenderCommands(new ArrayList<>()); }
            finally { object.setServices(original); }
        }
        assertTrue(graphics.rectangles>100,"ROM art and labels emitted visible rectangle commands");
        assertEquals(before,call(town,"capture"),"drawing may repeat without advancing town rules");
        if (name.equals("pictures")) {
            // The unused right side of opaque dialogue must cover world rings and name labels.
            for (int y=165;y<190;y++) for (int x=300;x<380;x++)
                assertEquals(0xFF101848,graphics.image.getRGB(x,y),"world object drew over dialogue");
        }
        String external=System.getProperty("starpost.town.capture.dir");
        Path directory=external==null?temp.resolve("pictures"):Path.of(external);
        assertTrue(directory.isAbsolute()); Files.createDirectories(directory);
        javax.imageio.ImageIO.write(graphics.image,"png",directory.resolve(name+".png").toFile());
        graphics.paint.dispose();
    }
    static final class RectangleWitness extends com.openggf.graphics.GraphicsManager {
        final java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(400,224,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D paint=image.createGraphics();
        int rectangles;
        public void registerCommand(com.openggf.graphics.GLCommandable command) {
            if (!(command instanceof com.openggf.graphics.GLCommand rect)) throw new AssertionError("Unexpected command");
            try {
                var right=rect.getClass().getDeclaredField("x2"); right.setAccessible(true);
                var bottom=rect.getClass().getDeclaredField("y2"); bottom.setAccessible(true);
                var height=rect.getClass().getDeclaredField("screenHeightPixels"); height.setAccessible(true);
                int y=height.getInt(rect)-(int)rect.getY1();
                paint.setColor(new java.awt.Color(rect.getColour1(),rect.getColour2(),rect.getColour3(),rect.getAlpha()));
                paint.fillRect((int)rect.getX1(),y,right.getInt(rect)-(int)rect.getX1(),
                    height.getInt(rect)-bottom.getInt(rect)-y); rectangles++;
            } catch (ReflectiveOperationException invalid) { throw new AssertionError(invalid); }
        }
    }
    static Object field(Object obj,String name) throws Exception { return obj.getClass().getField(name).get(obj); }
    static void setField(Object obj,String name,Object value) throws Exception { obj.getClass().getField(name).set(obj,value); }
    static Object call(Object obj,String name,Object... args) throws Exception {
        for (var method:obj.getClass().getMethods()) {
            if (!method.getName().equals(name)||method.getParameterCount()!=args.length) continue;
            try { return method.invoke(obj,args); } catch (IllegalArgumentException mismatch) { /* another overload */ }
        }
        throw new NoSuchMethodException(obj.getClass().getName()+"."+name);
    }
}
