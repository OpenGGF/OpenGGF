package com.openggf.game.sonic3k.objects;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameStep;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.difficulty.AizEndBossRecipe;
import com.openggf.game.sonic3k.difficulty.AizLogBridgeRecipe;
import com.openggf.graphics.RgbaImage;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.graphics.SpritePresentation;
import com.openggf.level.LevelSpritePresentation;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.mods.objects.AizLogBridgeFactory;
import com.openggf.sprites.NativePositionOps;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Second Strike AIZ, 2026-10-15: original-ROM positioned native producer/pixel witness.
 * Boss: isolated full ObjectManager and production hardware/SAT boundaries; no
 * zone-event/player-physics dispatch, stationary observer, declared arena framing.
 * Log: normal GameLoop landing with declared fire-continuation apparentAct0.
 * The sole service substitute supplies an enum recipe. No boss phase, timer, HP,
 * readiness or published-table writes. This is not mod-host, playable-arena,
 * post-bombing entry, cold-route or actual reload/progression evidence.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizNativeDifficultyPresentation {
    @ParameterizedTest @ValueSource(strings = {"sonic", "knuckles"})
    void nativeWaterfallArmsShowTheirCompleteWarningAndCharacterRecovery(String character) throws Exception {
        boolean knuckles = character.equals("knuckles");
        var settings = settings(character);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 1, settings);
            revealOrdinaryScene(session);
            var level = GameServices.level();
            var manager = level.getObjectManager();
            int lockX = knuckles ? 0x4100 : 0x4880;
            int bossY = knuckles ? 0x640 : 0x1C0;
            NativePositionOps.writeXPosResetSubpixel(session.player(), lockX + 200);
            NativePositionOps.writeYPosResetSubpixel(session.player(), bossY + 64);
            pinCamera(lockX, knuckles ? 0x5DA : 0x15A);
            var context = LevelFrameContext.from(SessionManager.getCurrentGameplayMode());
            var plc = new PlcFrameLifecycleCoordinator(context.gameModule());
            objectFrame(context, plc, session); // admit the actual camera window before creating anything
            var parents = manager.activeObjectsOfType(AizEndBossInstance.class);
            assertTrue(parents.size() <= 1, "parents before explicit producer: " + describe(parents));
            String origin = parents.isEmpty() ? "explicit_original_0x92" : "already_native_admitted";
            var boss = parents.isEmpty() ? manager.createDynamicObject(() -> new AizEndBossInstance(
                    new ObjectSpawn(lockX + 0x20, bossY, 0x92, 0, 0, false, bossY))) : parents.getFirst();
            supplyOnlyEndBossRecipe(boss, manager.getObjectServices());
            int setup = 0;
            for (; setup < 900; setup++) {
                objectFrame(context, plc, session);
                session.renderFrame();
                var liveParents = manager.activeObjectsOfType(AizEndBossInstance.class);
                assertEquals(1, liveParents.size(), "producer duplication: " + describe(liveParents));
                if (boss.usesAlternatingArmPattern()) break;
            }
            assertTrue(setup < 900, "untouched native phases/art must reach optional commitment");
            assertEquals(8, boss.getState().hitCount);
            var renderer = manager.getObjectServices().renderManager().getRenderer(Sonic3kObjectArtKeys.AIZ_END_BOSS);
            assertNotNull(renderer); assertTrue(renderer.isReady());
            assertTrue((boolean) value(boss, "bossArtLoaded"), "actual submitted ROM KosM job must retire");
            var props = manager.activeObjectsOfType(AizEndBossPropellerChild.class);
            assertEquals(2, props.size(), "propellers and parent identities: " + describeProps(props));
            for (var prop : props) assertSame(boss, value(prop, "boss"));
            int[] warnings = new int[2], starts = {-1, -1}, shots = new int[2];
            Set<ObjectInstance> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Map<String, List<String>> poses = new HashMap<>();
            boolean retreat = false;
            var rows = new ArrayList<String>();
            rows.add("scope,producer,parent_slot,parent_spawn,level_frame,object_vint_counter,presentation,arm,routine,mapping,x,y,warning_remaining,shots,cam_x,cam_y,rom_renderer_ready,boss_art_loaded,matched_published_tiles");
            Path out = output("waterfall-" + character);
            try {
                for (int presentation = 0; presentation <= 220; presentation++) {
                    // The native loop tail prepared THIS state. The next real
                    // VInt publishes it before the following object update.
                    var displayed = new ArrayList<ArmPose>();
                    for (var prop : props) displayed.add(pose(prop, boss));
                    objectFrame(context, plc, session);
                    RgbaImage image = session.render();
                    save(image, out, presentation);
                    assertPositiveScene(image);
                    var published = LevelSpritePresentation.published(level);
                    RgbaImage terrain = session.render(false);
                    for (var flame : manager.activeObjectsOfType(AizEndBossFlameChild.class)) {
                        if (seen.add(flame) && (boolean) value(flame, "difficultyAttack")) {
                            int arm = (int) value(value(flame, "propeller"), "subtype");
                            shots[arm]++;
                            assertEquals(knuckles && shots[arm] == 1 ? 0x2F : 0x5F,
                                    value(props.stream().filter(p -> subtype(p) == arm).findFirst().orElseThrow(), "waitTimer"));
                        }
                    }
                    for (var pose : displayed) {
                        var expected = romPose(renderer, pose);
                        int matched = matches(published, expected);
                        rows.add("isolated_native_object_scan_no_zone_events_stationary_observer," + origin + ","
                                + boss.getSlotIndex() + "," + spawnKey(boss) + "," + level.getFrameCounter() + ","
                                + manager.getVblaCounter() + "," + presentation + "," + pose.arm + "," + pose.routine + ","
                                + pose.mapping + "," + pose.x + "," + pose.y + "," + pose.remaining + "," + shots[pose.arm]
                                + "," + GameServices.camera().getX() + "," + GameServices.camera().getY() + ","
                                + renderer.isReady() + "," + value(boss, "bossArtLoaded") + "," + matched);
                        if (pose.routine != 10) continue;
                        warnings[pose.arm]++;
                        if (starts[pose.arm] < 0) starts[pose.arm] = presentation;
                        int x = pose.x - GameServices.camera().getX(), y = pose.y - GameServices.camera().getY();
                        assertTrue(x >= 16 && x < 304 && y >= 16 && y < 208, "warning outside320x224: " + pose);
                        assertTrue(matched > 0, "native ROM warning pose absent from published SAT: " + pose);
                        assertTrue(differences(image, terrain, x - 24, y - 24, x + 24, y + 24) > 0,
                                "warning region has no sprite pixels: " + pose);
                        poses.putIfAbsent(pose.arm + ":" + pose.mapping, signature(expected, pose));
                    }
                    if ((int) value(boss, "waitTimer") == 0x3F
                            && value(boss, "waitCallback").toString().equals("BEGIN_RE_SUBMERGE")) retreat = true;
                }
            } finally { writeRows(out, rows); }
            assertArrayEquals(new int[] {24, 24}, warnings);
            assertEquals(48, starts[1] - starts[0]);
            for (int arm = 0; arm < 2; arm++) {
                assertNotNull(poses.get(arm + ":4")); assertNotNull(poses.get(arm + ":5"));
                assertNotEquals(poses.get(arm + ":4"), poses.get(arm + ":5"), "two genuine ROM cue poses");
            }
            assertArrayEquals(knuckles ? new int[] {2, 2} : new int[] {1, 1}, shots);
            assertTrue(retreat, "native parent63-update retreat recovery");
        }
    }

    @Test
    void originalA6LogShowsItsCueBeforeContactThenTenUpdateSupport() throws Exception {
        LogTrace stock = logTrace(0, "stock-log");
        LogTrace brittle = logTrace(10, "brittle-log");
        int compared = 0;
        for (int i = 2; i < brittle.frames.size(); i++) {
            LogFrame frame = brittle.frames.get(i);
            if (!frame.idle) break;
            LogFrame control = stock.frames.get(i);
            assertEquals(control.position, frame.position, "matched native camera/player/level clock before contact");
            assertTrue(differences(frame.image, control.image, 48, 136, 272, 184) > 0,
                    "published precontact ROM cue must change on-screen bridge pixels at" + i);
            compared++;
        }
        assertTrue(compared >= 16, "complete visible precontact cue cycle");
        assertTrue(brittle.commitment >= 0, "normal native top-solid contact commits bridge");
        assertTrue(brittle.supportNine, "normal rider supported through9");
        assertEquals(10, brittle.release - brittle.commitment, "normal rider release at10");
    }

    private LogTrace logTrace(int support, String name) throws Exception {
        var settings = settings("sonic");
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 0, 1, settings);
            revealOrdinaryScene(session);
            var level = GameServices.level();
            // Declared native fire-continuation identity, not actual reload proof.
            level.setApparentAct(0);
            var spawn = level.getCurrentLevel().getObjects().stream().filter(e -> e.layoutIndex() == 66).findFirst().orElseThrow();
            assertEquals(0x2C, spawn.objectId()); assertEquals(0x6E6, spawn.x()); assertEquals(0x4F8, spawn.y());
            NativePositionOps.writeXPosResetSubpixel(session.player(), spawn.x() - 40);
            NativePositionOps.writeYPosResetSubpixel(session.player(), spawn.y() - 160);
            session.player().setAir(true); session.player().setYSpeed((short) 0);
            pinCamera(spawn.x() - 160, spawn.y() - 160);
            var manager = level.getObjectManager();
            level.updateObjectPositions();
            for (var original : manager.activeObjectsOfType(AizCollapsingLogBridgeObjectInstance.class))
                if (original.getSpawn().equals(spawn)) original.setDestroyed(true);
            var log = manager.createDynamicObject(() -> support == 0 ? new AizCollapsingLogBridgeObjectInstance(spawn)
                    : (AizCollapsingLogBridgeObjectInstance) AizLogBridgeFactory.create(spawn, new AizLogBridgeRecipe(support)));
            var renderer = manager.getObjectServices().renderManager().getRenderer(Sonic3kObjectArtKeys.AIZ_COLLAPSING_LOG_BRIDGE);
            assertNotNull(renderer); assertTrue(renderer.isReady());
            int commitment = -1, release = -1;
            boolean supportNine = false;
            var frames = new ArrayList<LogFrame>();
            var rows = new ArrayList<String>();
            rows.add("scope,support,apparent_act,frame,level_frame,log_state,timer,warning_tick,rider,on_object,air,x,y,cam_x,cam_y,rom_renderer_ready");
            Path out = output(name);
            try {
                for (int frame = 0; frame < 100; frame++) {
                    session.step(null);
                    var image = session.render(); save(image, out, frame);
                    assertPositiveScene(image);
                    boolean idle = !(boolean) value(log, "segmentsSpawned");
                    if (!idle && commitment < 0) commitment = frame;
                    if (commitment >= 0 && frame - commitment == 9) supportNine = session.player().isOnObject();
                    if (commitment >= 0 && release < 0 && !session.player().isOnObject()) release = frame;
                    var position = List.of(level.getFrameCounter(), (int) session.player().getCentreX(),
                            (int) session.player().getCentreY(), (int) GameServices.camera().getX(), (int) GameServices.camera().getY());
                    frames.add(new LogFrame(image, idle, position));
                    rows.add("declared_fire_continuation_positioned_normal_gameloop," + support + "," + level.getApparentAct()
                            + "," + frame + "," + level.getFrameCounter() + "," + value(log, "state") + "," + value(log, "collapseTimer")
                            + "," + value(log, "brittleWarningTick") + "," + log.isTrackingStandingPlayer(session.player()) + ","
                            + session.player().isOnObject() + "," + session.player().getAir() + "," + session.player().getCentreX()
                            + "," + session.player().getCentreY() + "," + GameServices.camera().getX() + "," + GameServices.camera().getY()
                            + "," + renderer.isReady());
                    if (release >= 0 && frame > release + 12) break;
                }
            } finally { writeRows(out, rows); }
            return new LogTrace(frames, commitment, release, supportNine);
        }
    }

    private static void revealOrdinaryScene(GameplayCaptureSession session) throws Exception {
        for (int frame = 0; frame < 240; frame++) {
            session.step(null);
            var image = session.render();
            var ui = GameServices.graphics().getUiRenderPipeline();
            boolean fading = GameServices.fade().isActive() || ui != null && ui.isFadeActive();
            boolean player = LevelSpritePresentation.published(GameServices.level()).tiles().stream()
                    .anyMatch(tile -> tile.layer() == SpritePresentation.Layer.PLAYER);
            if (!fading && player && colours(image) > 16) return;
        }
        fail("ordinary boot reveal/player/scene publication not ready; no cue witness");
    }

    private static void objectFrame(LevelFrameContext context, PlcFrameLifecycleCoordinator plc, GameplayCaptureSession session) {
        var frame = plc.latchBeforeFadeUpdate();
        LevelFrameStep.executeHardwareTimedObjectScan(context, frame, PlcLifecyclePhase.ORDINARY_LEVEL, () -> {
            var level = GameServices.level(); level.advanceLevelFrameCounter();
            context.paletteOwnershipRegistry().beginFrame();
            level.getObjectManager().update(GameServices.camera().getX(), session.player(), List.of(), level.getFrameCounter(), false);
        });
        // The normal LevelFrameStep tail updates parallax/ROM animation and
        // BuildSprites after hardware services. No zone-event dispatcher or
        // player physics is called by this isolated producer fixture.
        var level = GameServices.level();
        level.update();
        level.refreshObjectPostCameraRenderState();
        GameServices.sprites().refreshPlayableRenderFlags(GameServices.camera());
        LevelSpritePresentation.prepare(level, GameServices.sprites());
        frame.finish();
    }

    private record ArmPose(int arm, int routine, int mapping, int x, int y, int remaining, boolean flip) { }
    private record LogFrame(RgbaImage image, boolean idle, List<Integer> position) { }
    private record LogTrace(List<LogFrame> frames, int commitment, int release, boolean supportNine) { }
    private static ArmPose pose(AizEndBossPropellerChild p, AizEndBossInstance b) throws Exception {
        return new ArmPose(subtype(p), (int) value(p,"routine"), (int) value(p,"mappingFrame"),p.getX(),p.getY(),
                (int) value(p,"warningPresentationsRemaining"),b.isFacingRight());
    }
    private static SpritePresentation.Frame romPose(PatternSpriteRenderer renderer, ArmPose pose) {
        return SpritePresentationRenderer.prepare(GameServices.graphics(), GameServices.camera().getX(), GameServices.camera().getY(),
                () -> renderer.drawFrameIndex(pose.mapping, pose.x, pose.y, pose.flip, false));
    }
    private static int matches(SpritePresentation.Frame displayed, SpritePresentation.Frame expected) {
        return (int) expected.tiles().stream().filter(e -> displayed.tiles().stream().anyMatch(a -> a.patternId()==e.patternId()
                && a.x()==e.x() && a.y()==e.y() && a.palette()==e.palette() && a.rowEnd()>a.rowStart())).count();
    }
    private static List<String> signature(SpritePresentation.Frame frame, ArmPose pose) {
        return frame.tiles().stream().map(t -> t.patternId()+":"+(t.x()-pose.x)+":"+(t.y()-pose.y)).toList();
    }
    private static String describe(List<AizEndBossInstance> parents) {
        return parents.stream().map(p -> "slot="+p.getSlotIndex()+" spawn="+p.getSpawn()).toList().toString();
    }
    private static String describeProps(List<AizEndBossPropellerChild> props) throws Exception {
        var result = new ArrayList<String>();
        for (var p:props) result.add("slot="+p.getSlotIndex()+" boss="+describe(List.of((AizEndBossInstance)value(p,"boss"))));
        return result.toString();
    }
    private static String spawnKey(AizEndBossInstance b) { return b.getSpawn().x()+":"+b.getSpawn().y()+":"+b.getSpawn().layoutIndex(); }
    private static int subtype(AizEndBossPropellerChild p) { try { return (int)value(p,"subtype"); } catch(Exception e) { throw new AssertionError(e); } }
    private static GameplayCaptureSession.Settings settings(String character) { return new GameplayCaptureSession.Settings(320, character,"","off",null,null,null); }
    private static void pinCamera(int x,int y) {
        var c=GameServices.camera(); c.setMinX((short)x); c.setMaxX((short)x); c.setMinY((short)y); c.setMaxY((short)y); c.setX((short)x); c.setY((short)y);
    }
    private static void supplyOnlyEndBossRecipe(AizEndBossInstance boss, ObjectServices production) {
        GameModule module=mock(GameModule.class,delegatesTo(production.gameModule()));
        doReturn(AizEndBossRecipe.ALTERNATING_ARMS).when(module).getGameService(AizEndBossRecipe.class);
        ObjectServices adapter=mock(ObjectServices.class,delegatesTo(production)); doReturn(module).when(adapter).gameModule(); boss.setServices(adapter);
    }
    private static int colours(RgbaImage image) { return (int)Arrays.stream(image.pixels()).distinct().count(); }
    private static void assertPositiveScene(RgbaImage image) { assertEquals(320,image.width()); assertEquals(224,image.height()); assertTrue(colours(image)>16,"ordinary scene must contain ROM pixels"); }
    private static int differences(RgbaImage a,RgbaImage b,int x0,int y0,int x1,int y1) {
        int count=0; for(int y=Math.max(0,y0);y<Math.min(224,y1);y++) for(int x=Math.max(0,x0);x<Math.min(320,x1);x++) if(a.argb(x,y)!=b.argb(x,y)) count++; return count;
    }
    private static Path output(String name) throws Exception {
        String root=System.getProperty("openggf.aiz.nativePresentation.out"); if(root==null)return null;
        Path dir=Path.of(root).toAbsolutePath().normalize(); assertFalse(dir.startsWith(Path.of("").toAbsolutePath().normalize()));
        Path out=dir.resolve(name); Files.createDirectories(out); return out;
    }
    private static void save(RgbaImage image,Path out,int frame) throws Exception { if(out!=null)ScreenshotCapture.savePNG(image,out.resolve(String.format("%03d.png",frame))); }
    private static void writeRows(Path out,List<String> rows) throws Exception { if(out!=null)Files.write(out.resolve("native-state.csv"),rows); }
    private static Object value(Object target,String name) throws Exception { return field(target.getClass(),name).get(target); }
    private static Field field(Class<?> type,String name) throws Exception {
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try { Field f=c.getDeclaredField(name);f.setAccessible(true);return f; }catch(NoSuchFieldException ignored){} throw new NoSuchFieldException(name);
    }
}
