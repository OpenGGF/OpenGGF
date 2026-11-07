package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Literal N1ecc5f3 CC native vectors over original CdProgramAssets bytes. These are component
 * calls, not Frame/World admission, selected pixels, natural controller routes or native execution.
 * The test port executes shared Solid/Hurt/animation, never returns invented contact outcomes.
 */
class TestCdCcSolidsHazards {
    private static final List<CdR31SolidHazardAssets> ASSETS = new ArrayList<>();
    private static CdR31aAssets present;
    private static CdSolidObject solid;
    private static CdPlayerMath math;
    private static final String[] HASHES = {
        "30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e",
        "2a7dbe5463233d4cfb69b34dfcf480f8bec93407918d53883ee1d96771247862",
        "cdb48963c2661c537c19986f2f360ce89bc39d8dab0974d05ebca6557fefa987",
        "6980ad899736183083261cd57e05d897f76e01d9da517e9a07d82145b3af229d"};
    // Independently observed actual file offsets, never a period delta or production reader.
    private static final int[][] OFFSETS = {
        {0xBFCC,0xC2BC,0xC5A6,0xC5C6,0xC662,0xC682,0xC77E,0xC7EA,0xCED8,0xCF1C,0xCC66},
        {0xBE2E,0xC11E,0xC408,0xC428,0xC4C4,0xC4E4,0xC5E0,0xC64C,0xCD3A,0xCD7E,0xCAC8},
        {0xBF5C,0xC24C,0xC536,0xC556,0xC5F2,0xC612,0xC70E,0xC77A,0xCE68,0xCEAC,0xCBF6},
        {0xBF0C,0xC1FC,0xC4E6,0xC506,0xC5A2,0xC5C2,0xC6BE,0xC72A,0xCE18,0xCE5C,0xCBA6}};
    private static final int[][] MAPS = {
        {0xC176,0xC2CC,0xC434,0xC7A2,0xC8C6,0xCC88,0xCF2E},
        {0xBFD8,0xC12E,0xC296,0xC604,0xC728,0xCAEA,0xCD90},
        {0xC106,0xC25C,0xC3C4,0xC732,0xC856,0xCC18,0xCEBE},
        {0xC0B6,0xC20C,0xC374,0xC6E2,0xC806,0xCBC8,0xCE6E}};
    @BeforeAll static void originalOperands() throws Exception {
        Path root = Path.of(Objects.requireNonNull(System.getProperty("soniccd.media.root"), "absolute media root required"));
        Path cue;
        try (var paths = Files.list(root.resolve("USA"))) {
            cue = paths.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).findFirst().orElseThrow();
        }
        try (var programs = CdProgramAssets.open(cue, Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            present = CdR31aAssets.fromPrograms(programs, CdStagePayload.R31A.stage());
            var player = CdR11aPlayerAssets.fromR31aAssets(present);
            solid = new CdSolidObject(CdPlayerMovement.fromStageSource(CdStagePlayerSource.fromR31aAssets(present))); math = new CdPlayerMath(player);
            var files = new CdNativeFile[]{CdNativeFile.R31A,CdNativeFile.R31B,CdNativeFile.R31C,CdNativeFile.R31D};
            for (int n = 0; n < 4; n++) {
                var image = programs.mmd(files[n]);
                assertEquals(HASHES[n], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.readFile(0,262144))));
                int[] o = OFFSETS[n], m = MAPS[n];
                ASSETS.add(new CdR31SolidHazardAssets(0x200000+m[0],0x200000+m[1],0x200000+m[2],0x200000+m[3],
                    0x200000+m[4],0x200000+m[5],0x200000+m[6],view(image,o[0],12),view(image,o[1],256),
                    view(image,o[2],256),view(image,o[3],256),view(image,o[4],256),view(image,o[5],256),
                    view(image,o[6],256),view(image,o[6]+12,256),view(image,o[6]+24,256),
                    view(image,o[7],16),view(image,o[8],258),view(image,o[9],256),new CdObjectAnimation.AnimationTable(view(image,o[10],34),0,5)));
                // Actual fixed mapping STORE operand, including the previously uncatalogued Door root.
                int[] stores = switch(n) {
                    case 0 -> new int[]{0xBFFE,0xC1E6,0xC320,0xC46E,0xC800,0xC934,0xCDA6};
                    case 1 -> new int[]{0xBE60,0xC048,0xC182,0xC2D0,0xC662,0xC796,0xCC08};
                    case 2 -> new int[]{0xBF8E,0xC176,0xC2B0,0xC3FE,0xC790,0xC8C4,0xCD36};
                    default -> new int[]{0xBF3E,0xC126,0xC260,0xC3AE,0xC740,0xC874,0xCCE6};
                };
                for(int j=0;j<7;j++) {
                    byte[] bytes=image.readFile(stores[j],8);
                    assertEquals("217c",HexFormat.of().formatHex(Arrays.copyOf(bytes,2)));
                    assertEquals(0x200000+m[j], java.nio.ByteBuffer.wrap(bytes,2,4).getInt());
                    assertEquals(4,java.nio.ByteBuffer.wrap(bytes,6,2).getShort());
                }
            }
        }
    }
    private static CdAssetBytes view(CdMmdImage image,int offset,int size) { return CdAssetBytes.copyOf(image.readFile(offset,size)); }
    private static CdR31SolidHazardAssets a() { return ASSETS.getFirst(); }
    private static CdObjectPool pool(int id,int subtype) {
        var p=new CdObjectPool(); p.write(p.state(40).moveByte(CdObjectState.ByteField.ID,id)
            .moveByte(CdObjectState.ByteField.SUBTYPE,subtype).moveWord(CdObjectState.WordField.X,0x400)
            .moveWord(CdObjectState.WordField.Y,0x300)); return p;
    }
    private static CdCcBodyCursor c(CdObjectPool p,Port port) { return new CdCcBodyCursor(p,40,p.state(40).id(),port); }
    private static void fill(CdObjectPool p) {
        for(int n=32;n<128;n++) if(n!=40)p.write(p.state(n).moveByte(CdObjectState.ByteField.ID,0x7F));
    }
    private static void run(int id,CdR31SolidHazardAssets a,CdObjectPool p,Port port) {
        switch(id) {
            case 0x20 -> new CdCcSpikes(a).run(p,40,port); case 0x21 -> new CdBlock(a).run(p,40,port);
            case 0x22 -> new CdHiddenBlock(a).run(p,40,port); case 0x23 -> new CdMetalPlatform(a).run(p,40,port);
            case 0x26 -> new CdDoor(a).run(p,40,port); case 0x27 -> new CdFireShooter(a).run(p,40,port);
            case 0x28 -> new CdDrumPlatform(a).run(p,40,port); default -> throw new AssertionError();
        }
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3}) void independentlyReadProgrammeTables(int n) {
        var a=ASSETS.get(n);
        assertEquals("100800120c830c10820c1082",HexFormat.of().formatHex(a.spikesProperties().copy()));
        assertEquals("40f8020040f800004008fe0040080000",HexFormat.of().formatHex(a.blockMotion().slice(0,16).copy()));
        assertEquals("1c00030008d0030008d000003800fd000830fd00083000003800030008d00300",HexFormat.of().formatHex(a.metalRight().slice(0,32).copy()));
        assertEquals("1c00fd000830fd00083000003800030008d0030008d000003800fd000830fd00",HexFormat.of().formatHex(a.metalLeft().slice(0,32).copy()));
        assertEquals("2300fe000840fe00084000004600020008c0020008c000004600fe000840fe00",HexFormat.of().formatHex(a.metalUp().slice(0,32).copy()));
        assertEquals("2300020008c0020008c000004600fe000840fe00084000004600020008c00200",HexFormat.of().formatHex(a.metalDown().slice(0,32).copy()));
        assertEquals("10f000003000fe001010fe00",HexFormat.of().formatHex(a.metalDrop5().slice(0,12).copy()));
        assertEquals("10d000004500fd001030fd00",HexFormat.of().formatHex(a.metalDrop6().slice(0,12).copy()));
        assertEquals("10c000005800fc001040fc00",HexFormat.of().formatHex(a.metalDrop7().slice(0,12).copy()));
        assertEquals("001e3c00",HexFormat.of().formatHex(a.doorDelays().slice(0,4).copy()));
        assertEquals("010800050801060c02080b03080b04080b05060b06050807010808ff0409fb040afa080af8080af8080af8080afa080afb080aff040b",HexFormat.of().formatHex(a.drumMotion().slice(0,54).copy()));
        assertEquals("050403020202030405060606",HexFormat.of().formatHex(a.drumPriorities().slice(0,12).copy()));
    }
    @ParameterizedTest @CsvSource({"0,16,8,0","1,18,12,131","2,12,16,130","3,12,16,130","4,16,8,0","5,18,12,131","7,12,16,130"})
    void ccSpikesAllPlacedShapes(int subtype,int width,int radius,int collision) {
        for(var a:ASSETS) {
            var p=pool(0x20,subtype);var s=new Port();run(0x20,a,p,s);var c=c(p,s);
            assertEquals(width,c.b(0x19));assertEquals(radius,c.b(0x16));assertEquals(collision,c.b(0x20));
            assertEquals(subtype&3,c.b(0x1A));assertEquals(0x31E,c.w(2));assertEquals(1,s.contacts);
        }
    }
    @Test void spikeRetractCarryBorrowAndSoundFailurePrefix() {
        var p=pool(0x20,4);var s=new Port();var c=c(p,s);c.b(0x24,2);c.w(0x32,0xFFFF);c.w(0x36,0x400);
        c.b(0x3A,1);c.b(1,0x80);s.fail="fm";
        assertSame(s.failure,assertThrows(RuntimeException.class,()->run(0x20,a(),p,s)));
        assertEquals(0,c.b(0x3A));assertEquals(0,c.b(0x3B));assertEquals(0,s.contacts);assertEquals(List.of("fm:183"),s.events);
        s.fail="";c.b(0x3C,1);c.b(0x3B,0);run(0x20,a(),p,s);
        assertEquals(0,c.b(0x3B));assertEquals(60,c.b(0x3A));assertEquals(0xFFFF,c.w(12));
    }
    @Test void spikesFollowAfterDrawAndRetainFirstParentReadOnSecondFault() {
        var p=pool(0x20,0);var s=new Port();var c=c(p,s);c.w(0x34,0xDCC0);c.b(0x38,0xFF);c.b(0x39,0x80);
        s.parentX=0;s.parentY=0x8000;run(0x20,a(),p,s);
        assertEquals(0x400,s.drawX);assertEquals(0xFFFF,c.w(8));assertEquals(0x7F80,c.w(12));
        s.fail="parentY";s.parentX=0x1234;
        assertSame(s.failure,assertThrows(RuntimeException.class,()->run(0x20,a(),p,s)));
        assertEquals(0x1234,c.w(8));assertEquals(0x7F80,c.w(12));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4}) void hiddenChildGraphAndDirtyNonwrites(int subtype) {
        var p=pool(0x22,subtype);var s=new Port();var c=c(p,s);
        p.write(p.state(41).moveWord(CdObjectState.WordField.Y_FRACTION,0xCAFE).moveByte(CdObjectState.ByteField.ROUTINE,2)
            .moveByte(CdObjectState.ByteField.SPRING_PARENT_Y_OFFSET,0xF1));
        run(0x22,a(),p,s);
        var child=new CdCcBodyCursor(p,41,p.state(41).id(),s);
        if(subtype==0||subtype==4)assertEquals(0,p.state(41).id());
        else {
            assertEquals(subtype==1?0x20:0xA,p.state(41).id());assertEquals(0xDA00,child.w(0x34));
            assertEquals(subtype==1?0x20:0x18,child.b(0x38));assertEquals(subtype==1?2:subtype==2?4:6,child.b(0x28));
        }
        assertEquals(0xCAFE,child.w(14));assertEquals(0xF1,child.b(0x39));assertEquals(2,child.b(0x24));
        assertEquals(2,c.b(0x24));assertEquals(1,s.contacts);
    }
    @Test void hiddenMovesAtUnsignedWindowAndWrapEdges() {
        var p=pool(0x22,4);var s=new Port();s.x=0x3EF;run(0x22,a(),p,s);var c=c(p,s);
        assertEquals(8,c.b(0x3B));assertEquals(0x2F8,c.w(12));
        s.x=0x440;run(0x22,a(),p,s);assertEquals(0,c.b(0x3B));assertEquals(0x300,c.w(12));
        s.x=0x43F;run(0x22,a(),p,s);assertEquals(0,c.b(0x3B));
        c.w(8,0);s.x=0;run(0x22,a(),p,s);assertEquals(8,c.b(0x3B));
    }
    @Test void noAllocationPrefixesDifferBlockDeletesHiddenRetainsEmitterRetries() {
        for(int id:new int[]{0x21,0x22,0x27}) {
            var p=pool(id,id==0x27?0:2);fill(p);var s=new Port();var before=p.capture();run(id,a(),p,s);
            assertEquals(id==0x21?0:id,p.state(40).id());assertEquals(id==0x27?0:id==0x21?0:2,p.state(40).routine());
            for(int n=32;n<128;n++)if(n!=40)assertEquals(before.slots().get(n-32),p.state(n));
            assertEquals(id==0x22?1:0,s.contacts);
        }
    }
    @Test void nextAllocationNeverUsesEarlierFreeSlotAndSpringLinkIsRaw() {
        var p=pool(0x21,3);var s=new Port();run(0x21,a(),p,s);
        assertEquals(0,p.state(32).id());assertEquals(0xA,p.state(41).id());
        var child=new CdCcBodyCursor(p,41,0xA,s);assertEquals(0xDA00,child.w(0x34));assertEquals(0xEA,child.b(0x39));assertEquals(2,child.b(0x28));
    }
    @Test void blockSetupReturnThenAccelerationAndTimerRollover() {
        var p=pool(0x21,0);var s=new Port();s.period=1;run(0x21,a(),p,s);var c=c(p,s);
        assertEquals(0x400,c.w(8));assertEquals(64,c.b(0x3A));assertEquals(0x200,c.w(0x36));
        run(0x21,a(),p,s);assertEquals(0x401,c.w(8));assertEquals(0xF800,c.w(10));assertEquals(0x1F8,c.w(0x10));
        c.b(0x3A,1);c.b(0x3B,3);run(0x21,a(),p,s);
        assertEquals(0,c.b(0x3B));assertEquals(64,c.b(0x3A));assertEquals(0x200,c.w(0x36));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7}) void metalAllBehaviorsSetupAndTopPort(int subtype) {
        for(var a:ASSETS) {
            var p=pool(0x23,subtype);var s=new Port();run(0x23,a,p,s);var c=c(p,s);
            assertEquals(0x400,c.w(8));assertEquals(0x300,c.w(12));assertTrue(s.contacts>=1);
            if(subtype==1||subtype==2){assertEquals(28,c.b(0x38));assertEquals(subtype==1?0x300:0xFD00,c.w(0x34));}
            if(subtype==3||subtype==4){assertEquals(35,c.b(0x38));assertEquals(subtype==3?0xFE00:0x200,c.w(0x34));}
            assertEquals(CdSolidObject.Entry.TOP_SOLID_OBJECT,s.lastEntry);
        }
    }
    @Test void metalDrop7LiteralAcceleratesBeforeMovingAndCompletesAllThreeRecords() {
        var p=pool(0x23,7);var s=new Port();run(0x23,ASSETS.get(2),p,s);var c=c(p,s);
        c.b(0x3A,2);run(0x23,ASSETS.get(2),p,s);
        assertEquals(16,c.b(0x38));assertEquals(0,c.w(0x34));assertEquals(0xFFC0,c.w(0x36));
        run(0x23,ASSETS.get(2),p,s);assertEquals(0xFF00,c.w(0x12));assertEquals(0x2FF,c.w(12));assertEquals(0xC000,c.w(14));
        for(int n=0;n<120;n++)run(0x23,ASSETS.get(2),p,s);
        assertEquals(4,c.b(0x3A));assertEquals(3,c.b(0x39));assertEquals(0,c.w(0x12));
    }
    @Test void metalWeightUsesRetainedSineAndTailBeforeDraw() {
        var p=pool(0x23,0);var s=new Port();run(0x23,a(),p,s);var c=c(p,s);
        c.b(0x3E,0x40);c.b(0x22,0);s.camera=0x2000;run(0x23,a(),p,s);
        assertEquals(0x38,s.beforeDespawn.copyBytes()[0x3E]&255);assertEquals(0x303,s.beforeDespawn.centreY());assertEquals(0,s.drawX);assertTrue(s.events.indexOf("despawn")<s.events.lastIndexOf("draw"));
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,3,128}) void doorDirectionDelayAndByteOverflow(int subtype) {
        var p=pool(0x26,subtype);var s=new Port();s.y=0x300;run(0x26,a(),p,s);var c=c(p,s);
        assertEquals(subtype==128?0x410:0x3F0,c.w(8));assertEquals(16,c.b(0x3B));
        c.b(0x3B,0xF8);run(0x26,a(),p,s);assertEquals(8,c.b(0x3B));
        c.b(0x3B,0);s.y=0x2FF;run(0x26,a(),p,s);assertEquals(0,c.b(0x3B));
        if((subtype&15)==0){assertEquals(60,c.b(0x3A));assertEquals(255,c.b(0x3C));}
    }
    @Test void emitterChildrenUseFirstFreeAndCanRunInSamePhysicalPass() {
        var p=pool(0x27,0);var s=new Port();run(0x27,a(),p,s);
        assertEquals(0x27,p.state(32).id());assertEquals(1,p.state(32).subtype());assertEquals(0x308,p.state(32).centreY());
        assertEquals(2,p.state(40).routine());assertEquals(0,p.state(32).routine()); // lower slot waits
        fill(p);p.deleteObject(41);c(p,s).b(0x24,0);run(0x27,a(),p,s);
        new CdFireShooter(a()).run(p,41,s);assertEquals(2,p.state(41).routine());assertEquals(1,p.state(41).unsignedByte(CdObjectState.ByteField.MAP_FRAME));
    }
    @ParameterizedTest @CsvSource({"0,0,0","1,1,2","2,1,0","2,0,2","255,0,2"})
    void flamePeriodAndGoodFutureReads(int period,int good,int children) {
        var p=pool(0x27,1);var s=new Port();s.period=period;s.good=good;run(0x27,a(),p,s);var c=c(p,s);
        c.b(0x24,4);c.b(0x3B,24);c.w(0x32,0x11B8);run(0x27,a(),p,s);
        assertEquals(children,p.capture().slots().stream().filter(v->v.id()==0x27&&v.subtype()==2).count());
        assertEquals(period==0||period==1?0:1,s.goodReads);
        if(children!=0)assertEquals(0x11A0,p.state(32).centreY());
    }
    @Test void projectilesRetainDirtyWordDirectionAndLowByteYWrapAndPartialPool() {
        var p=pool(0x27,1);var s=new Port();s.period=1;fill(p);p.deleteObject(32);
        var c=c(p,s);c.b(0x24,4);c.b(0x3B,24);c.w(0x32,0x1220); // at top Y=$1200, child ADDQ.B ->1208
        c.w(0x32,0x131C); // at top Y=$12FC, child must be $1204, never $1304
        p.write(p.state(32).moveByte(CdObjectState.ByteField.TUNNEL_PATH_END,1));
        run(0x27,a(),p,s);assertEquals(0x1204,p.state(32).centreY());assertEquals(0x3E8,p.state(32).centreX());
        new CdFireShooter(a()).run(p,32,s);var child=new CdCcBodyCursor(p,32,0x27,s);
        assertEquals(0x110,child.w(0x10));assertEquals(2,child.b(0x1C)); // low timer=1 makes full direction WORD nonzero
        assertEquals(0x27,p.state(32).id());assertEquals(2,p.state(32).subtype());
    }
    @Test void projectileBurnoutDeletesButStillUsesProjectileTail() {
        var p=pool(0x27,2);var s=new Port();var c=c(p,s);c.b(0x24,2);c.w(0x36,0x300);c.b(0x3B,1);
        c.b(0x1C,1);run(0x27,a(),p,s);assertEquals(0,p.state(40).id());
        assertEquals(List.of("animate","draw","projectileTail"),s.events);
    }
    @Test void flameSignedTimerAndWordClearDoNotEarlyDetectOrAnimate() {
        var p=pool(0x27,1);var s=new Port();run(0x27,a(),p,s);var c=c(p,s);c.b(0x3A,0x80);
        run(0x27,a(),p,s);assertEquals(0x7F,c.b(0x3A));assertEquals(0,s.xReads);assertEquals(0,s.animations);
        c.b(0x3A,1);c.b(0x3B,0xE1);int calls=s.contacts;run(0x27,a(),p,s);
        assertEquals(0,c.w(0x3A));assertEquals(4,c.b(0x24));assertEquals(calls,s.contacts);
    }
    @ParameterizedTest @ValueSource(ints={0,1,2,5,6,17}) void drumZeroLayoutComponentCycleAndDirtyVelocityLowByte(int subtype) {
        var p=pool(0x28,subtype);var s=new Port();s.period=1;var c=c(p,s);c.w(0x36,0xAA55);
        run(0x28,a(),p,s);assertEquals(subtype<2?2:subtype<6?4:6,c.b(0x24));assertEquals(0,c.b(0x3A));
        run(0x28,a(),p,s);assertEquals(4,c.b(0x3A));assertEquals(0x55,c.w(0x36)&255);
        assertEquals(a().drumMotion().unsignedByte(subtype*3+2),c.b(0x1A));
        int y=c.w(12);s.artTimer=1;run(0x28,a(),p,s);assertEquals(y,c.w(12));assertEquals(4,c.b(0x3A));
        s.artTimer=0;for(int n=0;n<4;n++)run(0x28,a(),p,s);
        if(subtype==17){assertEquals(0,c.w(0x3A));assertEquals(2,c.b(0x24));}
    }
    @Test void drumPresentUsesFrameRadiusButNeverAdvancesTimedCycle() {
        var p=pool(0x28,2);var s=new Port();var c=c(p,s);run(0x28,a(),p,s);run(0x28,a(),p,s);
        c.b(1,0x84);c.b(0x1A,3);run(0x28,a(),p,s);
        assertEquals(5,c.b(0x16));assertEquals(4,c.b(0x3A));assertEquals(0x300,c.w(12));
    }
    @ParameterizedTest @ValueSource(ints={0x20,0x21,0x22,0x23,0x26,0x27,0x28})
    void exactDrawFaultRetainsInitAndDoesNotReachTail(int id) {
        var p=pool(id,0);var s=new Port();s.fail="draw";
        assertSame(s.failure,assertThrows(RuntimeException.class,()->run(id,a(),p,s)));
        assertTrue((p.state(40).spriteFlags()&4)!=0);assertEquals(0x400,p.state(40).centreX());
        assertFalse(s.events.contains("despawn"));
    }
    @ParameterizedTest @CsvSource({"1024,896,false","1024,768,false","1024,640,false","1024,512,false","1024,384,true","0,65408,false"})
    void chunkDespawnEqualityAndWordWrap(int anchor,int camera,boolean despawn) {
        var p=pool(0x26,0);var s=new Port();var c=c(p,s);c.w(8,anchor);s.camera=camera;run(0x26,a(),p,s);
        assertEquals(despawn,s.events.contains("despawn"));
    }
    @Test void stagingCopyAndWhole96RestoreKeepParentChildImages() {
        var original=pool(0x22,1);var saved=original.capture();var staged=CdObjectPool.stagingCopy(saved);var s=new Port();
        run(0x22,a(),staged,s);assertEquals(0,original.state(41).id());var result=staged.capture();
        staged.deleteObject(41);staged.restore(result);assertEquals(0x20,staged.state(41).id());
        assertEquals(0xDA00,staged.state(41).word(CdObjectState.WordField.SPRING_PARENT));
        assertThrows(IllegalArgumentException.class,()->new CdObjectPool().restore(result));
        assertEquals(saved.slots(),original.capture().slots());
    }
    @ParameterizedTest @CsvSource({"0,0,6,0","0,7,4,7","1,7,2,7"})
    void spikeGenuineSolidThenDirectHurtOrInvincibilityGate(int invincible,int rings,int routine,int retainedRings) {
        var p=pool(0x20,0);var s=new Port();var c=c(p,s);
        s.actor=actor(0x400,0x2E8,0,0x100,2);s.inv=invincible;s.world=new CdPlayerTouchResponse.WorldGlobals(rings,3,0,0,5000,0);
        c.b(1,0x84);run(0x20,a(),p,s);
        assertEquals(routine,s.actor.motion().routine());assertEquals(retainedRings,s.world.rings());
        assertEquals(invincible==0?1:0,s.hurts);assertTrue(s.contacts>0);
    }
    @Test void directHurtFaultKeepsReachedSolidAndYRewindButNoDraw() {
        var p=pool(0x20,0);var s=new Port();var c=c(p,s);s.actor=actor(0x400,0x2E8,0,0x100,2);c.b(1,0x84);s.fail="hurt";
        assertSame(s.failure,assertThrows(RuntimeException.class,()->run(0x20,a(),p,s)));
        assertEquals(1,s.rewinds);assertTrue((c.b(0x22)&8)!=0);assertFalse(s.events.contains("draw"));
    }
    @Test void dirtyIndicesReadActualAdjacentBytesAndByteWrappedAliases() {
        for(var a:ASSETS) {
            var s=new Port();s.period=1;
            var p=pool(0x21,0);var c=c(p,s);c.b(0x24,2);c.w(0x32,0x400);c.b(0x3B,0x44);
            run(0x21,a,p,s);assertEquals(0,c.b(0x3A));assertEquals(2,c.w(0x38));assertEquals(0x1F0,c.w(0x36));
            p=pool(0x23,1);c=c(p,s);c.b(0x39,8);run(0x23,a,p,s);
            assertEquals(28,c.b(0x38));assertEquals(0xFD00,c.w(0x34)); // right table +32 is the actual left table
            p=pool(0x26,15);s.y=0;run(0x26,a,p,s);c=c(p,s);assertEquals(2,c.b(0x3A));
            p=pool(0x28,18);run(0x28,a,p,s);c=c(p,s);c.w(0x36,0xAA55);run(0x28,a,p,s);
            assertEquals(0x7055,c.w(0x36));assertEquals(0,c.b(0x16));assertEquals(0x10,c.b(0x1A));
        }
    }
    @Test void primaryPairFenceFailureIsAtReachedContactAfterInitBeforeMotionAndDraw() {
        var p=pool(0x21,0);var s=new Port();s.period=1;s.fail="contact";
        assertSame(s.failure,assertThrows(RuntimeException.class,()->run(0x21,a(),p,s)));
        var c=c(p,s);assertEquals(2,c.b(0x24));assertEquals(0x400,c.w(0x32));assertEquals(0,c.b(0x3A));
        assertFalse(s.events.contains("draw"));
    }
    @Test void blockPlacedSubtypeOneIsStationaryInPastIncludingDirtyTimer() {
        var p=pool(0x21,1);var s=new Port();s.period=1;var c=c(p,s);c.b(0x3A,0x80);c.w(0x36,0x7FFF);
        run(0x21,a(),p,s);assertEquals(0x400,c.w(8));assertEquals(0x80,c.b(0x3A));assertEquals(0x7FFF,c.w(0x36));
    }
    @Test void flameLowerWaitDetectCycleAndContactAtTimerZero() {
        var p=pool(0x27,1);var s=new Port();run(0x27,a(),p,s);var c=c(p,s);c.b(0x24,6);c.b(0x3B,4);c.b(0x3A,1);
        int calls=s.contacts;run(0x27,a(),p,s);assertEquals(calls+1,s.contacts);assertEquals(4,c.b(0x3B));
        run(0x27,a(),p,s);assertEquals(0,c.b(0x3B));assertEquals(60,c.b(0x3A));assertEquals(8,c.b(0x24));assertEquals(0x300,c.w(12));
        c.b(0x3A,1);calls=s.contacts;run(0x27,a(),p,s);assertEquals(calls+1,s.contacts);assertEquals(8,c.b(0x24));
        calls=s.contacts;run(0x27,a(),p,s);assertEquals(calls,s.contacts);assertEquals(2,c.b(0x24));
        s.x=0x400;run(0x27,a(),p,s);assertEquals(59,c.b(0x3A));assertEquals(0,s.animations);
    }
    @Test void flameLaunchUsesSameGenuineSolidPrimaryAndNativeFixedStores() {
        var p=pool(0x27,1);var s=new Port();run(0x27,a(),p,s);var c=c(p,s);c.b(0x24,4);c.b(0x3B,24);c.b(1,0x84);
        s.actor=actor(0x400,0x2B8,0,0x100,2);run(0x27,a(),p,s);
        assertEquals(-0x700,s.actor.motion().yVelocity());assertEquals(0x10,s.actor.motion().animation());
        assertEquals(2,s.actor.motion().flags()&10);assertEquals(0,c.b(0x22)&8);assertEquals(4,c.b(0x24));
        assertEquals(40,s.player.standObject()); // Native launch clears flags, not the raw stand link.
    }
    @Test void secondTopCallFenceRetainsFirstSharedResultAndStopsBeforeDraw() {
        var p=pool(0x23,7);var s=new Port();s.failContactNumber=2;c(p,s).b(0x25,0xA5);
        assertSame(s.failure,assertThrows(RuntimeException.class,()->run(0x23,a(),p,s)));
        var c=c(p,s);
        // N common/solid_object.asm:452-457: TopSolid first stores1, then offscreen
        // SolidObjectRelease clears routine_2 back to0 before this second-call fence.
        assertEquals(0,c.b(0x25));assertEquals(1,c.b(0x3F));assertEquals(0,c.b(0x38));
        assertEquals(CdSolidObject.Path.GET_OFF,s.lastResult.path());
        assertEquals(CdSolidObject.GetOffReason.OBJECT_OFFSCREEN,s.lastResult.getOffReason().orElseThrow());
        assertTrue(s.lastResult.objectWrites().contains(CdObjectState.ByteField.SOLID_TYPE));
        assertEquals(0,s.lastResult.object().unsignedByte(CdObjectState.ByteField.SOLID_TYPE));
        assertEquals(2,s.contacts);assertFalse(s.events.contains("draw"));
    }
    @ParameterizedTest @CsvSource({"1,0,0","0,4,0","0,2,256"})
    void spikesReachedNegativeHurtGuardsKeepGenuineSolidButNeverHurt(int warp,int routine,int hurt) {
        var p=pool(0x20,0);var s=new Port();var c=c(p,s);c.b(1,0x84);s.warp=warp;
        var m=actor(0x400,0x2E8,0,0x100,2).motion();
        s.actor=new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),0,0x100,0,routine,0,2,0,9,19,0,0,0,0,0,0,0,0,0),0,2,hurt,0);
        run(0x20,a(),p,s);assertEquals(0,s.hurts);assertTrue(s.contacts>0);assertTrue(s.events.contains("draw"));
    }
    private static CdPlayerTouchResponse.Actor actor(int x,int y,int xv,int yv,int flags) {
        return new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(x<<16,y<<16,xv,yv,0,2,0,flags,0,9,19,0,0,0,0,0,0,0,0,0),0,2,0,0);
    }
    /** Read-only scripted globals; contact/Hurt/math/animation execute genuine existing kernels. */
    private static class Port implements CdCcObjectServices {
        int x=0,y=0,period,good,camera=0x400,artTimer,inv,warp,contacts,animations,goodReads,xReads,drawX,parentX,parentY,hurts,rewinds,failContactNumber;
        CdObjectState beforeDespawn;
        String fail="";final RuntimeException failure=new IllegalStateException("reached test port fault");
        final List<String> events=new ArrayList<>();CdSolidObject.Entry lastEntry;
        CdPlayerTouchResponse.Actor actor=actor(0,0,0,0,0);
        CdSolidObject.Result lastResult;
        CdSolidObject.Player player;CdSolidObject.Globals globals=new CdSolidObject.Globals(0,0,0,0);
        CdPlayerTouchResponse.WorldGlobals world=new CdPlayerTouchResponse.WorldGlobals(0,3,0,0,5000,0);
        void fault(String at){if(fail.equals(at))throw failure;}
        public int act(){return 0;}public int timeZone(){return period;}public int goodFuture(){goodReads++;return good;}
        public int cameraX(){return camera;}public int animationArtTimer(){return artTimer;}
        public int primaryX(){xReads++;return x;}public int primaryY(){return y;}
        public int timeWarp(){return warp;}public int invincible(){return inv;}public int primaryRoutine(){return actor.motion().routine();}
        public int primaryHurt(){return actor.hurt();}
        public int contact(CdObjectPool p,int index,CdSolidObject.Entry entry){
            contacts++;lastEntry=entry;fault("contact");if(contacts==failContactNumber)throw failure;
            if(player==null)player=new CdSolidObject.Player(actor,1,0,0);
            var out=solid.runWithPrefix(entry,p.state(index),player,new CdSolidObject.Gates(0,0),globals,p.capture());
            if(out instanceof CdSolidObject.NativeStopped stopped){
                p.write(stopped.prefix().object());for(var other:stopped.prefix().otherSlots())p.write(other);
                player=stopped.prefix().player();actor=player.actor();globals=stopped.prefix().globals();throw stopped.stop().originalException();
            }
            var result=((CdSolidObject.Completed)out).completedResult();lastResult=result;p.write(result.object());for(var other:result.otherSlots())p.write(other);
            player=result.player();actor=player.actor();globals=result.globals();return result.d0().orElse(0);
        }
        public void rewindPrimaryYBeforeHurt(){rewinds++;events.add("rewind");var m=actor.motion();
            actor=replaceMotion(m,m.centreY16_16()-(m.yVelocity()<<8),m.yVelocity(),m.flags(),m.animation());}
        private CdPlayerTouchResponse.Actor replaceMotion(CdPlayerMotionState m,int y,int yv,int flags,int anim){
            return new CdPlayerTouchResponse.Actor(new CdPlayerMotionState(m.centreX16_16(),y,m.xVelocity(),yv,m.groundVelocity(),m.routine(),m.angle(),flags,m.spriteFlags(),m.xRadius(),m.yRadius(),anim,m.previousAnimation(),m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle()),actor.tile(),actor.priority(),actor.hurt(),actor.rotationDistance());}
        public void hurtPrimary(CdObjectPool p,int index){events.add("hurt");fault("hurt");hurts++;
            var result=CdPlayerTouchResponse.hurtPlayer(actor,new CdPlayerTouchResponse.PlayerGlobals(0,inv,0,0,0,0,0,0),world,p.state(index),p.capture());
            for(var slot:result.changedSlots())p.write(slot);actor=result.actor();world=result.world();}
        public void launchPrimaryFromFlame(){fault("launch");var m=actor.motion();actor=replaceMotion(m,m.centreY16_16(),-0x700,(m.flags()|2)&~8,0x10);}
        public int sine(int angle){return math.calcSine(angle).sine();}
        public void playFm(int sound){events.add("fm:"+sound);fault("fm");}
        public void draw(CdObjectPool p,int index){drawX=p.state(index).centreX();events.add("draw");fault("draw");}
        public void despawnR3(CdObjectPool p,int index){events.add("despawn");fault("despawn");beforeDespawn=p.state(index);
            if(beforeDespawn.savedFlagsId()!=0)throw new UnsupportedOperationException("test port has no saved-bank owner");
            p.deleteObject(index);}
        public void checkProjectileDespawn(CdObjectPool p,int index){events.add("projectileTail");fault("projectileTail");}
        public int parentX(int raw,CdObjectPool p){events.add("parentX");fault("parentX");return parentX;}
        public int parentY(int raw,CdObjectPool p){events.add("parentY");fault("parentY");return parentY;}
        public void animate(CdObjectPool p,int index,CdObjectAnimation.AnimationTable table){events.add("animate");animations++;fault("animate");
            var c=new CdCcBodyCursor(p,index,p.state(index).id(),this);
            var n=CdObjectAnimation.step(new CdObjectAnimation.State(c.b(0x1C),c.b(0x1D),c.b(0x1B),c.b(0x1E),c.b(0x1A),c.b(1),c.b(0x22),c.b(0x24),c.b(0x25)),table);
            c.b(0x1C,n.animationId());c.b(0x1D,n.previousId());c.b(0x1B,n.scriptIndex());c.b(0x1E,n.timer());c.b(0x1A,n.mapFrame());
            c.b(1,n.spriteFlags());c.b(0x24,n.routine());c.b(0x25,n.routine2());}
    }
}
