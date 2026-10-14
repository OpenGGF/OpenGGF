package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kBonusStageCoordinator;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.util.stream.Stream;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

/** Declared production bonus requests, actual loads/return and advertised timeline policy. */
@RequiresRom(SonicGame.SONIC_3K)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestS3kBonusStageReturnProducts {
    private final EnumMap<SonicConfiguration, Object> savedConfiguration = new EnumMap<>(SonicConfiguration.class);
    @BeforeEach
    void preserveConfiguration() {
        var config = GameServices.configuration();
        for (var key : SonicConfiguration.values()) {
            if (config.hasSessionOverride(key)) savedConfiguration.put(key, config.getConfigValue(key));
        }
    }
    @AfterEach
    void restoreConfiguration() {
        com.openggf.game.CrossGameFeatureProvider.getInstance().resetState();
        var config = GameServices.configuration();
        config.clearSessionOverrides();
        savedConfiguration.forEach(config::setSessionOverride);
        config.resolveDisplayAspect();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }
    static Stream<Arguments> finiteProducts() {
        return CanonicalEntryMatrixSupport.configurations().flatMap(row -> Stream.of(
                BonusStageType.GUMBALL, BonusStageType.GLOWING_SPHERE, BonusStageType.SLOT_MACHINE)
                .map(type -> Arguments.of(type, row.main(), row.followers(),
                        row.aspect().pixelWidth(), row.donor())));
    }
    @Order(2)
    @ParameterizedTest(name = "actual load and return {0}/{1}/{2}/{3}/{4}")
    @MethodSource("finiteProducts")
    void finiteSupportedProductsRetainRosterAndBothLoadBoundaries(BonusStageType type, String main,
            String followers, int width, String donor) throws Exception {
        checkStage(type, main, followers, width, donor);
    }
    static Stream<Arguments> pilots() {
        return Stream.of(Arguments.of(BonusStageType.GUMBALL,"sonic","tails",320,"off"),
                Arguments.of(BonusStageType.GLOWING_SPHERE,"knuckles","tails,sonic",352,"off"),
                Arguments.of(BonusStageType.SLOT_MACHINE,"tails","sonic,sonic",400,"off"),
                Arguments.of(BonusStageType.GUMBALL,"tails","sonic,sonic",800,"s2"),
                Arguments.of(BonusStageType.SLOT_MACHINE,"sonic","sonic,sonic",800,"s1"));
    }
    @Order(1)
    @ParameterizedTest @MethodSource("pilots")
    void declaredStageRequestLoadsAndReturnsThroughRealBoundaries(BonusStageType type,String main,
            String followers,int width,String donor) throws Exception {
        checkStage(type, main, followers, width, donor);
    }
    private void checkStage(BonusStageType type, String main, String followers, int width,
            String donor) throws Exception {
        var donorRom=donor.equals("off")?null:(donor.equals("s1")?RomTestUtils.ensureSonic1RomAvailable():RomTestUtils.ensureSonic2RomAvailable()).toPath();
        var settings=new GameplayCaptureSession.Settings(width,main,followers,donor,donorRom,null,null);
        try(var session=new GameplayCaptureSession(settings)) {
            var config=GameServices.configuration();
            var aspect=java.util.Arrays.stream(WidescreenAspect.values()).filter(a->a.pixelWidth()==width).findFirst().orElseThrow();
            config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,aspect.name());config.resolveDisplayAspect();
            config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED,true);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),8,0,settings);
            for(int frame=0;frame<60;frame++)step(session);
            assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(width,GameServices.camera().getWidth() & 0xFFFF);
            assertEquals(main,GameServices.sprites().getMainPlayable().characterKey().persisted());
            assertRoster(main, followers, donor);
            var outgoing=GameServices.level().getObjectManager();
            var oldRewind=SessionManager.getCurrentGameplayMode().getRewindController();
            assertNotNull(oldRewind);assertTrue(oldRewind.currentFrame()>10);
            long oldFrame=oldRewind.currentFrame();
            int lives=GameServices.gameState().getLives();
            // Declared logical inlet, not evidence of reaching a checkpoint's star portal.
            GameServices.level().requestBonusStageEntry(type);
            boolean entered=false;
            for(int frame=0;frame<600;frame++) {
                step(session);
                if(session.loop().getCurrentGameMode()==GameMode.BONUS_STAGE
                        &&!SessionManager.getCurrentGameplayMode().getFadeManager().isActive()
                        &&!SessionManager.getCurrentGameplayMode().getFadeManager().hasPendingCompletion()) {entered=true;break;}
            }
            assertTrue(entered,"actual bonus load/title/fade release");
            var coordinator=assertInstanceOf(Sonic3kBonusStageCoordinator.class,GameServices.bonusStage());
            assertEquals(type,coordinator.getActiveType());assertEquals(0x800,coordinator.getSavedState().savedZoneAndAct());
            assertNotSame(outgoing,GameServices.level().getObjectManager());
            assertEquals(coordinator.getZoneId(type)>>8,GameServices.level().getCurrentZone());
            assertEquals(type!=BonusStageType.SLOT_MACHINE,coordinator.supportsRewind());
            var incoming=SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(incoming==null||incoming.currentFrame()<oldFrame,"origin timeline is retired");
            assertEquals(main,GameServices.sprites().getMainPlayable().characterKey().persisted());
            assertEquals(width,GameServices.camera().getWidth() & 0xFFFF);
            if(type==BonusStageType.SLOT_MACHINE) {
                assertNotNull(coordinator.activeSlotRuntime());assertTrue(coordinator.activeSlotRuntime().isInitialized());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty(),"slot player owns the interior");
                for(int frame=0;frame<18;frame++)step(session);
            } else {
                replayWorld(session, 18, GameMode.BONUS_STAGE, main, width, lives);
            }
            var interior=GameServices.level().getObjectManager();
            var saved=coordinator.getSavedState();
            coordinator.requestExit(); // Explicit completion stimulus; actual GameLoop owns the return.
            boolean returned=false;
            for(int frame=0;frame<800;frame++) {
                step(session);
                if(GameServices.level().getObjectManager()!=interior&&session.loop().getCurrentGameMode()==GameMode.LEVEL
                        &&!SessionManager.getCurrentGameplayMode().getFadeManager().isActive()
                        &&!SessionManager.getCurrentGameplayMode().getFadeManager().hasPendingCompletion()
                        &&!GameServices.level().hasPendingFreshLevelTransitionBoundary()
                        &&!GameServices.sprites().getMainPlayable().isControlLocked()) {returned=true;break;}
            }
            assertTrue(returned,"actual origin title/fade/control release");
            assertEquals(saved.savedZoneAndAct()>>8,GameServices.level().getCurrentZone());
            assertEquals(saved.savedZoneAndAct()&255,GameServices.level().getCurrentAct());
            assertEquals(lives,GameServices.gameState().getLives());assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(main,GameServices.sprites().getMainPlayable().characterKey().persisted());
            assertEquals(width,GameServices.camera().getWidth() & 0xFFFF);
            assertRoster(main, followers, donor);
            assertEquals(BonusStageType.NONE,coordinator.getActiveType());assertNull(coordinator.activeSlotRuntime());
            var returnedRewind=SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(returnedRewind==null||returnedRewind.currentFrame()<oldFrame,
                    "return retires interior history rather than reviving origin snapshots");
            // The deliberate full-load boundary is followed by a real incoming-world replay.
            replayWorld(session, 45, GameMode.LEVEL, main, width, lives);
            assertRoster(main, followers, donor);
        }
    }
    private static void assertRoster(String main, String followers, String donor) {
        var leader = GameServices.sprites().getMainPlayable();
        assertEquals(main, leader.characterKey().persisted());
        assertSame(leader, GameServices.camera().getFocusedSprite());
        String[] expected = followers.isEmpty() ? new String[0] : followers.split(",");
        var actual = GameServices.sprites().getRegisteredSidekicks();
        assertEquals(expected.length, actual.size(), "all declared participants survive return");
        var identities = Collections.newSetFromMap(new IdentityHashMap<com.openggf.sprites.playable.AbstractPlayableSprite, Boolean>());
        identities.add(leader);
        for (int i = 0; i < expected.length; i++) {
            var follower = actual.get(i);
            assertEquals(expected[i], follower.characterKey().persisted(), "ordered roster " + i);
            assertTrue(identities.add(follower), "duplicate characters remain distinct actors");
            assertTrue(follower.isCpuControlled());
            assertNotNull(follower.getCpuController());
            assertSame(leader, follower.getCpuController().getLeader(), "ordered CPU leader chain " + i);
            leader = follower;
        }
        SozAcceptanceConfigurations.assertUsableTeam(donor);
    }
    private static void replayWorld(GameplayCaptureSession session, int frames, GameMode mode,
            String main, int width, int lives) {
        var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var objects=GameServices.level().getObjectManager();
        var runtime=GameServices.zoneRuntimeState();
        var before=registry.capture();
        Runnable advance=()->{for(int frame=0;frame<frames;frame++) {
            step(session);
            assertEquals(mode,session.loop().getCurrentGameMode());
            assertSame(objects,GameServices.level().getObjectManager());
            assertSame(runtime,GameServices.zoneRuntimeState());
            assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(main,GameServices.sprites().getMainPlayable().characterKey().persisted());
            assertEquals(width,GameServices.camera().getWidth() & 0xFFFF);
            assertEquals(lives,GameServices.gameState().getLives());
        }};
        advance.run();var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture());advance.run();same(after,registry.capture());
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
        }
    }
    private static void step(GameplayCaptureSession session) {
        session.step(new Bk2FrameInput(0,0,0,false,"declared neutral bonus lifecycle"));session.renderFrame();
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),key);
    }
}
