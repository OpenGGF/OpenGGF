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

/** Actual placed starpost/portal contacts and stage-owned exit mechanisms from declared local inlets. */
@RequiresRom(SonicGame.SONIC_3K)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestS3kBonusPortalMechanismProducts {
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
    void finitePortalAndExitProductsRetainRosterAndBothLoadBoundaries(BonusStageType type, String main,
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
    void shortPortalAndMechanismPilotsLoadAndReturnThroughRealBoundaries(BonusStageType type,String main,
            String followers,int width,String donor) throws Exception {
        checkStage(type, main, followers, width, donor);
    }
    static Stream<Arguments> slotRewindPilots() {
        return Stream.concat(pilots().filter(row->row.get()[0]==BonusStageType.SLOT_MACHINE),
                Stream.of(Arguments.of(BonusStageType.SLOT_MACHINE,"knuckles","tails,sonic",320,"off")));
    }
    @Order(1)
    @ParameterizedTest @MethodSource("slotRewindPilots")
    void shortSlotInteriorReplayUsesTheActualPortalAndReturn(BonusStageType type,String main,
            String followers,int width,String donor) throws Exception {
        checkStage(type,main,followers,width,donor,true,true);
    }

    static Stream<Arguments> finiteSlots() {
        return finiteProducts().filter(row->row.get()[0]==BonusStageType.SLOT_MACHINE);
    }
    @Order(2)
    @ParameterizedTest @MethodSource("finiteSlots")
    void finiteSlotMechanicsAndLiveHistoryProducts(BonusStageType type,String main,
            String followers,int width,String donor) throws Exception {
        checkStage(type,main,followers,width,donor,true,true);
    }

    private void checkStage(BonusStageType type, String main, String followers, int width,
            String donor) throws Exception {
        checkStage(type,main,followers,width,donor,false,false);
    }
    private void checkStage(BonusStageType type, String main, String followers, int width,
            String donor, boolean liveSlotChecks, boolean slotMechanics) throws Exception {
        var donorRom=donor.equals("off")?null:(donor.equals("s1")?RomTestUtils.ensureSonic1RomAvailable():RomTestUtils.ensureSonic2RomAvailable()).toPath();
        var settings=new GameplayCaptureSession.Settings(width,main,followers,donor,donorRom,0x1A18,0x42C);
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
            var configured=GameServices.sprites().getMainPlayable();
            int configuredFrames=configured.getAnimationFrameCount();
            int configuredBank=configured.getSpriteRenderer().patternBankCapacity();
            enterThroughPlacedPostPortal(session, type);
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
            assertTrue(coordinator.supportsRewind(),"active actual bonus interiors support complete rewind");
            var incoming=SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(incoming==null||incoming.currentFrame()<oldFrame,"origin timeline is retired");
            assertEquals(main,GameServices.sprites().getMainPlayable().characterKey().persisted());
            assertEquals(width,GameServices.camera().getWidth() & 0xFFFF);
            if(type==BonusStageType.SLOT_MACHINE) {
                assertNotNull(coordinator.activeSlotRuntime());assertTrue(coordinator.activeSlotRuntime().isInitialized());
                assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty(),"slot player owns the interior");
            }
            com.openggf.sprites.render.PlayerSpriteRenderer slotBody=null,slotTail=null;
            if(type==BonusStageType.SLOT_MACHINE) {
                var slotPlayer=GameServices.sprites().getMainPlayable();
                var host=hostArt().loadForCharacter(main);
                slotBody=slotPlayer.getSpriteRenderer();
                assertEquals(host.mappingFrames().size(),slotPlayer.getAnimationFrameCount(),
                        "loc_4B9E8 installs host mappings whatever the configured donor");
                assertEquals(host.bankSize(),slotBody.patternBankCapacity());
                slotTail=slotPlayer.getTailsTailsController()==null?null:slotPlayer.getTailsTailsController().getRenderer();
                assertEquals(main.equals("tails"),slotTail!=null,"only Tails alone gets the fresh Obj_Tails_Tail");
            }
            if(liveSlotChecks) verifyActualSlotController(session,coordinator);
            if(slotMechanics) verifyActualSlotMechanics(session,coordinator);
            replayWorld(session, 18, GameMode.BONUS_STAGE, main, width, lives);
            var interior=GameServices.level().getObjectManager();
            var saved=coordinator.getSavedState();
            reachStageOwnedExit(session, type, coordinator);
            if(type==BonusStageType.SLOT_MACHINE) verifySlotGoalWindAndFade(session,coordinator,main+"-"+width+"-"+donor);
            int expectedLives=lives+coordinator.getRewards().lives();
            int expectedRings=GameServices.sprites().getMainPlayable().getRingCount();
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
            assertEquals(expectedLives,GameServices.gameState().getLives());
            assertEquals(expectedRings,GameServices.sprites().getMainPlayable().getRingCount());assertFalse(GameServices.sprites().getMainPlayable().getDead());
            assertEquals(main,GameServices.sprites().getMainPlayable().characterKey().persisted());
            assertEquals(width,GameServices.camera().getWidth() & 0xFFFF);
            assertRoster(main, followers, donor);
            assertEquals(BonusStageType.NONE,coordinator.getActiveType());assertNull(coordinator.activeSlotRuntime());
            if(type==BonusStageType.SLOT_MACHINE) {
                var returnedPlayer=GameServices.sprites().getMainPlayable();
                assertNotSame(slotBody,returnedPlayer.getSpriteRenderer(),"configured body resources return");
                assertEquals(configuredFrames,returnedPlayer.getAnimationFrameCount(),"configured donor mappings return");
                assertEquals(configuredBank,returnedPlayer.getSpriteRenderer().patternBankCapacity());
                if(slotTail!=null)assertNotSame(slotTail,returnedPlayer.getTailsTailsController().getRenderer(),
                        "configured tail resources return");
            }
            var returnedRewind=SessionManager.getCurrentGameplayMode().getRewindController();
            assertTrue(returnedRewind==null||returnedRewind.currentFrame()<oldFrame,
                    "return retires interior history rather than reviving origin snapshots");
            // The deliberate full-load boundary is followed by a real incoming-world replay.
            replayWorld(session, 45, GameMode.LEVEL, main, width, expectedLives);
            assertRoster(main, followers, donor);
        }
    }
    private static void verifyActualSlotMechanics(GameplayCaptureSession session,
            Sonic3kBonusStageCoordinator coordinator) {
        var runtime=coordinator.activeSlotRuntime();var player=GameServices.sprites().getMainPlayable();
        assertEquals(0,runtime.activeSlotCageForTest().cageStateForTest(),"contacts precede natural cage admission");
        int ring=firstSlotCell(runtime,8);positionAtSlotCell(ring,true);
        int rings=player.getRingCount();int accumulated=coordinator.getRewards().rings();
        replayAction(session,1,()->step(session),()->{
            assertEquals(rings+1,player.getRingCount());
            assertEquals(accumulated+1,coordinator.getRewards().rings());
            assertNotEquals(8,runtime.activeLayoutForTest()[ring]&255);
        });
        int bumper=firstSlotCell(runtime,5);positionAtSlotCell(bumper,false);
        replayAction(session,1,()->step(session),()->{
            assertNotEquals(0,Math.abs(player.getXSpeed())+Math.abs(player.getYSpeed()));
            assertNotEquals(5,runtime.activeLayoutForTest()[bumper]&255);
        });
        int spike=firstSlotCell(runtime,6);positionAtSlotCell(spike,false);
        int scalar=runtime.stageController().scalarIndex();
        replayAction(session,1,()->step(session),()->{
            assertEquals((short)-scalar,(short)runtime.stageController().scalarIndex());
            assertNotEquals(6,runtime.activeLayoutForTest()[spike]&255);
        });
        int reel=firstSlotCell(runtime,1);positionAtSlotCell(reel,false);
        replayAction(session,1,()->step(session),()->assertTrue((runtime.activeLayoutForTest()[reel]&255)>=13,
                "real wall contact starts its loaded cell animation"));
        // The rotating marker is computed around the player. Its native fixed
        // point is the cage's immediate ($460,$430), not yesterday's rendered marker.
        positionFreePlayer(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_X,
                com.openggf.game.sonic3k.bonusstage.slots.S3kSlotRomData.SLOT_BONUS_CAGE_CENTER_Y);
        for(int frame=0;frame<4&&runtime.activeSlotCageForTest().cageStateForTest()==0;frame++)step(session);
        assertEquals(1,runtime.activeSlotCageForTest().cageStateForTest());assertTrue(player.isObjectControlled());
        for(int frame=0;frame<1000&&runtime.stageController().activeRewardObjects()==0;frame++) {
            step(session);assertEquals(GameMode.BONUS_STAGE,session.loop().getCurrentGameMode());
        }
        assertTrue(runtime.stageController().isReelsFrozen());
        assertTrue(runtime.stageController().activeRewardObjects()>0,"natural resolved reels create real reward actors");
        var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var occupied=registry.capture();
        var oldRewards=new java.util.ArrayList<com.openggf.level.objects.ObjectInstance>();
        oldRewards.addAll(runtime.activeSlotRingRewardsForTest());oldRewards.addAll(runtime.activeSlotSpikeRewardsForTest());
        for(var reward:oldRewards)GameServices.level().getObjectManager().removeDynamicObject(reward);
        registry.restore(occupied);same(occupied,registry.capture());
        var freshRewards=new java.util.ArrayList<com.openggf.level.objects.ObjectInstance>();
        freshRewards.addAll(runtime.activeSlotRingRewardsForTest());freshRewards.addAll(runtime.activeSlotSpikeRewardsForTest());
        assertEquals(oldRewards.size(),freshRewards.size());
        for(var reward:freshRewards)assertFalse(oldRewards.stream().anyMatch(old->old==reward),"reward actors are freshly recreated");
        replayAction(session,70,()->step(session),()->assertEquals(GameMode.BONUS_STAGE,session.loop().getCurrentGameMode()));
        var payoutBefore=registry.capture();int payoutFrames=0;
        while(runtime.activeSlotCageForTest().cageStateForTest()!=3&&payoutFrames<2000) {
            step(session);payoutFrames++;assertEquals(GameMode.BONUS_STAGE,session.loop().getCurrentGameMode());
        }
        assertEquals(3,runtime.activeSlotCageForTest().cageStateForTest(),"natural payout, expiry and ejection complete before the goal inlet");
        assertEquals(0,runtime.stageController().activeRewardObjects());
        assertTrue(runtime.activeSlotRingRewardsForTest().isEmpty());assertTrue(runtime.activeSlotSpikeRewardsForTest().isEmpty());
        var payoutAfter=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(payoutBefore);same(payoutBefore,registry.capture());
            for(int frame=0;frame<payoutFrames;frame++)step(session);
            same(payoutAfter,registry.capture());
            registry.restore(payoutBefore);same(payoutBefore,registry.capture());registry.restore(payoutAfter);same(payoutAfter,registry.capture());
        }
        assertFalse(player.getDead());
    }
    private static int firstSlotCell(com.openggf.game.sonic3k.bonusstage.slots.S3kSlotBonusStageRuntime runtime,int type) {
        var layout=runtime.activeLayoutForTest();
        for(int cell=0;cell<layout.length;cell++)if((layout[cell]&255)==type)return cell;
        throw new AssertionError("Missing actual Slot cell "+type);
    }
    private static void positionAtSlotCell(int cell,boolean ring) {
        int row=cell/32+32,col=cell%32+32;
        positionFreePlayer(col*24-(ring?0x20:0x14)-(ring?0:1),row*24-(ring?0x50:0x44)-(ring?0:1));
    }
    private static void verifySlotGoalWindAndFade(GameplayCaptureSession session,
            Sonic3kBonusStageCoordinator coordinator,String label) throws Exception {
        var runtime=coordinator.activeSlotRuntime();assertTrue(runtime.activeExitSequenceForTest().isWindingDown());
        replaySlotPictureAction(session,13,()->{step(session);captureSlotPicture(session,label,"wind");},
                ()->assertTrue(runtime.activeExitSequenceForTest().isWindingDown()));
        var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var before=registry.capture();
        int steps=0;
        while(!runtime.activeExitSequenceForTest().isFading()&&steps<160){step(session);steps++;}
        assertTrue(runtime.activeExitSequenceForTest().isFading());
        // loc_4BC54 dims Normal_palette with Pal_ToBlack (sonic3k.asm:98981-98987); no screen
        // overlay runs until Level:'s Pal_FadeToBlack after the $38 countdown.
        assertFalse(SessionManager.getCurrentGameplayMode().getFadeManager().isActive());
        var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture());
            for(int frame=0;frame<steps;frame++)step(session);
            same(after,registry.capture());
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
        }
        var art=hostArt();var sonicArt=art.loadSonic();var tailArt=art.loadTailsTail();
        int[] changedMappings={0};int[] sonicCueChecks={0};
        replaySlotPictureAction(session,13,()->{
            var player=GameServices.sprites().getMainPlayable();
            int previousMapping=player.getMappingFrame();
            byte[] previousBank=readPlayerPatternBank(player.getSpriteRenderer());
            step(session);assertActiveSlotPose();captureSlotPicture(session,label,"fade");
            byte[] currentBank=readPlayerPatternBank(player.getSpriteRenderer());
            int mapping=player.getMappingFrame();
            if(mapping!=previousMapping)changedMappings[0]++;
            if(player instanceof com.openggf.sprites.playable.Tails) {
                assertArrayEquals(previousBank,currentBank,
                        "native Sonic D000 writes do not change retained Tails D400 body art");
                // Obj_Tails_Tail keeps its own Player_prev_frame_P2_tail/D600 path.
                var tail=player.getTailsTailsController();var tailRam=tail.captureRewindState();
                if(tailRam.currentAnim()!=0&&tailRam.mappingFrame()>=0)
                    assertResidentCues(readPlayerPatternBank(tail.getRenderer()),
                            tailArt.dplcFrames().get(tailRam.mappingFrame()),tailArt.artTiles(),
                            "independent tail D600 cues for tail map "+tailRam.mappingFrame());
            } else if(mapping==previousMapping) {
                assertArrayEquals(previousBank,currentBank,"shared Player_prev_frame suppresses unchanged fade DPLC");
            } else if(mapping>=0x96&&mapping<=0x9A) {
                // Verified PLC_Sonic[96..9A]: FBA2/FBB2/FBC2/FBD2/FBE2, 16 tiles into
                // D000. Sonic draws Map_Sonic; Knuckles retains Map_Knuckles over them.
                byte[] expected=new byte[16*com.openggf.level.Pattern.PATTERN_SIZE_IN_MEM];
                int start=0xBA2+(mapping-0x96)*16;
                for(int tile=0;tile<16;tile++)sonicArt.artTiles()[start+tile].copyInto(expected,
                        tile*com.openggf.level.Pattern.PATTERN_SIZE_IN_MEM);
                assertArrayEquals(expected,java.util.Arrays.copyOf(currentBank,expected.length),
                        "native fade submits host Sonic cues for map "+Integer.toHexString(mapping));
                sonicCueChecks[0]++;
            }
        },()->assertTrue(runtime.activeExitSequenceForTest().isFading()));
        assertTrue(changedMappings[0]>0,"the fade interval exercises at least one Animate mapping change");
        if(!(GameServices.sprites().getMainPlayable() instanceof com.openggf.sprites.playable.Tails))
            assertTrue(sonicCueChecks[0]>0,"a changed fade mapping was checked against host Sonic cues");
    }
    /** Opt-in engine pictures for native comparison; no capture unless the directory is set. */
    private static void captureSlotPicture(GameplayCaptureSession session,String label,String phase) {
        String dir=System.getProperty("openggf.test.slot.captureDir");
        if(dir==null)return;
        var player=GameServices.sprites().getMainPlayable();
        String name=String.format("%s-lfc%05d-map%02X",phase,GameServices.level().getFrameCounter()&0xFFFF,player.getMappingFrame());
        try {
            var out=java.nio.file.Path.of(dir).resolve(label);java.nio.file.Files.createDirectories(out);
            com.openggf.graphics.ScreenshotCapture.savePNG(session.render(),out.resolve(name+".png"));
        } catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    private static com.openggf.game.sonic3k.Sonic3kPlayerArt hostArt() {
        try { return new com.openggf.game.sonic3k.Sonic3kPlayerArt(
                com.openggf.data.RomByteReader.fromRom(GameServices.rom().getRom())); }
        catch(java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }
    /** DynamicPatternBank cue placement: sequential from 0 unless a cue names its destination. */
    private static void assertResidentCues(byte[] bank,com.openggf.level.render.SpriteDplcFrame cues,
            com.openggf.level.Pattern[] source,String message) {
        int size=com.openggf.level.Pattern.PATTERN_SIZE_IN_MEM;int destination=0;
        for(var cue:cues.requests()) {
            if(cue.destinationOffset()>=0)destination=cue.destinationOffset();
            for(int tile=0;tile<cue.count();tile++,destination++) {
                byte[] expected=new byte[size];source[cue.startTile()+tile].copyInto(expected,0);
                assertArrayEquals(expected,java.util.Arrays.copyOfRange(bank,destination*size,(destination+1)*size),message);
            }
        }
    }
    private record SlotPictureBanks(byte[] body,byte[] tail) { }
    private static SlotPictureBanks slotPictureBanks() {
        var player=GameServices.sprites().getMainPlayable();
        var tail=player.getTailsTailsController();
        return new SlotPictureBanks(readPlayerPatternBank(player.getSpriteRenderer()),
                tail==null?null:readPlayerPatternBank(tail.getRenderer()));
    }
    private static byte[] readPlayerPatternBank(com.openggf.sprites.render.PlayerSpriteRenderer renderer) {
        if(renderer==null)return null;
        try {
            var field=com.openggf.sprites.render.PlayerSpriteRenderer.class.getDeclaredField("patternBank");
            field.setAccessible(true);
            var patterns=((com.openggf.level.render.DynamicPatternBank)field.get(renderer)).getPatterns();
            byte[] pixels=new byte[patterns.length*com.openggf.level.Pattern.PATTERN_SIZE_IN_MEM];
            for(int tile=0;tile<patterns.length;tile++)patterns[tile].copyInto(pixels,
                    tile*com.openggf.level.Pattern.PATTERN_SIZE_IN_MEM);
            return pixels;
        } catch(ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void sameSlotPictureBanks(SlotPictureBanks expected,String boundary) {
        var actual=slotPictureBanks();
        assertArrayEquals(expected.body(),actual.body(),boundary+" body bank");
        assertArrayEquals(expected.tail(),actual.tail(),boundary+" tail bank");
    }
    private static void replaySlotPictureAction(GameplayCaptureSession session,int frames,
            Runnable tick,Runnable assertion) {
        var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var before=registry.capture();var beforeBanks=slotPictureBanks();
        Runnable advance=()->{for(int frame=0;frame<frames;frame++)tick.run();assertion.run();};
        advance.run();var after=registry.capture();var afterBanks=slotPictureBanks();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture());
            sameSlotPictureBanks(beforeBanks,"restore before Slot picture interval");
            advance.run();same(after,registry.capture());
            sameSlotPictureBanks(afterBanks,"replay Slot picture interval");
            registry.restore(before);same(before,registry.capture());
            sameSlotPictureBanks(beforeBanks,"reverse restore before Slot picture interval");
            registry.restore(after);same(after,registry.capture());
            sameSlotPictureBanks(afterBanks,"restore after Slot picture interval");
        }
    }
    private static void assertActiveSlotPose() {
        var player=GameServices.sprites().getMainPlayable();
        assertNotNull(player.getSpriteRenderer(), "active Slot body has a renderer");
        assertTrue(player.getMappingFrame()>=0&&player.getMappingFrame()<player.getAnimationFrameCount(),
                ()->"active Slot mapping "+player.getMappingFrame()+" exceeds the selected "+
                        player.getAnimationFrameCount()+"-frame bank for "+player.getCode());
    }
    private static void replayAction(GameplayCaptureSession session,int frames,Runnable tick,Runnable assertion) {
        var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();var before=registry.capture();
        Runnable advance=()->{for(int frame=0;frame<frames;frame++)tick.run();assertion.run();};
        advance.run();var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            registry.restore(before);same(before,registry.capture());advance.run();same(after,registry.capture());
            registry.restore(before);same(before,registry.capture());registry.restore(after);same(after,registry.capture());
        }
    }

    private static void verifyActualSlotController(GameplayCaptureSession session,
            Sonic3kBonusStageCoordinator coordinator) {
        // Mode/title release publishes the fresh world first. The next ordinary
        // host step installs the live input controller for this interior.
        step(session);
        var mode=SessionManager.getCurrentGameplayMode();var controller=mode.getRewindController();
        var registry=mode.getRewindRegistry();assertNotNull(controller);
        var objects=GameServices.level().getObjectManager();
        int initial=controller.currentFrame();var initialState=registry.capture();
        controller.seekTo(0);
        assertEquals(0,controller.currentFrame());
        assertEquals(GameMode.BONUS_STAGE,session.loop().getCurrentGameMode());
        assertSame(objects,GameServices.level().getObjectManager());
        assertNotNull(coordinator.activeSlotRuntime());
        assertTrue(coordinator.activeSlotRuntime().isInitialized(),"the new floor belongs to the booted Slot world");
        while(controller.currentFrame()<initial){controller.step();session.renderFrame();}
        same(initialState,registry.capture());
        int start=controller.currentFrame();var before=registry.capture();
        for(int frame=0;frame<18;frame++) {
            session.step(new Bk2FrameInput(0,com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT,0,false,"real held right Slot replay"));
            session.renderFrame();
            assertSame(controller,mode.getRewindController(),"actual live controller was replaced at held frame "+frame+
                    " old="+controller.currentFrame()+" new="+mode.getRewindController().currentFrame()+
                    " mode="+session.loop().getCurrentGameMode()+" enabled="+GameServices.configuration().getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED));
        }
        assertEquals(start+18,controller.currentFrame());var after=registry.capture();
        for(int cycle=0;cycle<2;cycle++) {
            for(int frame=0;frame<18;frame++)assertTrue(controller.stepBackward());
            assertEquals(start,controller.currentFrame());same(before,registry.capture());
            while(controller.currentFrame()<start+18){controller.step();session.renderFrame();}
            same(after,registry.capture());
            controller.seekTo(start);same(before,registry.capture());
            while(controller.currentFrame()<start+18){controller.step();session.renderFrame();}
            same(after,registry.capture());
        }
    }

    private static void enterThroughPlacedPostPortal(GameplayCaptureSession session, BonusStageType type) throws Exception {
        var checkpoint = assertInstanceOf(com.openggf.game.CheckpointState.class, GameServices.level().getCheckpointState());
        assertFalse(checkpoint.isActive(), "declared inlet begins outside the actual placed post");
        int rings = switch (type) {
            case SLOT_MACHINE -> 20;
            case GLOWING_SPHERE -> 35;
            case GUMBALL -> 50;
            default -> throw new IllegalArgumentException("Unsupported portal type");
        };
        GameServices.sprites().getMainPlayable().setRingCount(rings); // Declared carried rings, not portal state.
        for (int frame=0; frame<60 && !checkpoint.isActive(); frame++) {
            session.step(new Bk2FrameInput(0,com.openggf.sprites.playable.AbstractPlayableSprite.INPUT_RIGHT,0,false,"ordinary right into the placed SOZ1 post"));
            session.renderFrame();
        }
        assertTrue(checkpoint.isActive(), "real placed post activation");
        assertEquals(1, checkpoint.getLastCheckpointIndex());
        var stars = GameServices.level().getObjectManager().activeObjectsOfType(
                com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild.class);
        assertEquals(4, stars.size(), "native four-star graph, not a logical stage request");
        var collision = com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild.class.getDeclaredField("collisionEnabled");
        var px = com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild.class.getDeclaredField("currentX");
        var py = com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild.class.getDeclaredField("currentY");
        collision.setAccessible(true); px.setAccessible(true); py.setAccessible(true);
        boolean armed=false;
        for (int frame=0; frame<160; frame++) {
            for (var star : GameServices.level().getObjectManager().activeObjectsOfType(
                    com.openggf.game.sonic3k.objects.Sonic3kStarPostBonusStarChild.class)) {
                if (!star.isDestroyed() && collision.getBoolean(star)) {
                    // Declared local approach to the live orbit; no lifetime/angle/request writes.
                    positionFreePlayer(px.getInt(star), py.getInt(star));
                    step(session); armed=true; break;
                }
            }
            if (armed) break;
            step(session);
        }
        assertTrue(armed, "ordinary star lifetime reaches its native collision-enable boundary");
        assertTrue(checkpoint.isUsedForSpecialStage(), "actual touch consumes the checkpoint portal");
    }
    private static void reachStageOwnedExit(GameplayCaptureSession session, BonusStageType type,
            Sonic3kBonusStageCoordinator coordinator) {
        var objects=GameServices.level().getObjectManager();
        if (type == BonusStageType.GUMBALL) {
            var trigger=objects.getActiveObjects().stream().filter(o ->
                    o.getClass().getEnclosingClass()==com.openggf.game.sonic3k.objects.GumballMachineObjectInstance.class
                    && o.getClass().getSimpleName().equals("ExitTriggerChild")).findFirst().orElseThrow();
            positionFreePlayer(trigger.getX(), trigger.getY());
            step(session); // Existing range owner requests exit.
        } else if (type == BonusStageType.GLOWING_SPHERE) {
            var trap=objects.activeObjectsOfType(com.openggf.game.sonic3k.objects.PachinkoEnergyTrapObjectInstance.class).getFirst();
            positionFreePlayer(0x100, trap.getY());
            for (int frame=0; frame<90 && !trap.isExitRequested(); frame++) step(session);
            assertTrue(trap.isExitArmed()); assertTrue(trap.isExitRequested(), "actual trap capture/countdown owns exit");
        } else {
            var runtime=coordinator.activeSlotRuntime();
            byte[] layout=runtime.activeLayoutForTest();
            int goal=-1;
            for (int i=0; i<layout.length; i++) if ((layout[i]&255)==4) {goal=i;break;}
            assertTrue(goal>=0, "goal exists in the actual loaded stage layout");
            int row=goal/32+32, col=goal%32+32;
            // Declared free-movement inlet: the bottom-right sampled corner is the actual goal.
            positionFreePlayer(col*24-0x14-1, row*24-0x44-1);
            for (int frame=0; frame<12 && runtime.activeExitSequenceForTest()==null; frame++) step(session);
            assertNotNull(runtime.activeExitSequenceForTest(), "real collision invokes goal exit, not a test startGoal hook");
        }
    }
    private static void positionFreePlayer(int x, int y) {
        var player=GameServices.sprites().getMainPlayable();
        com.openggf.sprites.NativePositionOps.writeXPosResetSubpixel(player,x);
        com.openggf.sprites.NativePositionOps.writeYPosResetSubpixel(player,y);
        player.setXSpeed((short)0);player.setYSpeed((short)0);player.setGSpeed((short)0);
        player.setAir(true);player.setControlLocked(false);
        com.openggf.sprites.playable.ObjectControlState.none().applyTo(player);
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
        for(String key:expected.entries().keySet())assertTrue(RewindSnapshotDiff.keyEquals(key,expected.get(key),actual.get(key)),()->key+": "+RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)));
    }
}
