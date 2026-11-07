package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.CdPlayerMath;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.CdR4Boss.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Literal native vectors + labelled constructed placement/environment controls. No fake World,
 * Frame, gameplay route or pixels. Actual-media operand authentication is separate below. */
class TestCdR4BossComponents {
    static final class Data implements Assets {
        final EnumMap<Table,CdAssetBytes> tables=new EnumMap<>(Table.class);
        boolean mappingMissing;
        CdObjectAnimation.AnimationTable bubbleAnimation;
        Data() {
            tables.put(Table.BUBBLE_POP_DIRECTION,hex("00")); // constructed adjacent-byte control; no runtime fallback
            tables.put(Table.BUBBLE_THRESHOLDS,hex("0e0c0a080604ffff"));
            tables.put(Table.MISSILE_DECLARATIONS,hex("e317fffec39800013c68fa1f000000000001c000101c0000e000000183f71d1100013c6800013c68"));
            tables.put(Table.BUBBLE_SPACING,hex("0000000080005555400033332aaa249220001c7119991745155513b1124911111000"));
            tables.put(Table.EXPLOSION_OFFSETS,hex("ffd0fff000300010fff0fff000100010ffe000000030fff0ffd00010fff000100010fff000200000"));
            tables.put(Table.ESCAPE_BOUNDS,hex("00000f0000000000")); // constructed bound vector, not a retail operand
        }
        public int mapping(Part p){if(mappingMissing)throw new UnsupportedOperationException("constructed absent mapping");return 0x212000;}
        public CdObjectAnimation.AnimationTable animation(Part p) {
            if(p==Part.BUBBLE&&bubbleAnimation!=null)return bubbleAnimation;
            // Constructed inert script: ten independent directory aliases to [timer7,frame0,FF].
            byte[] b=new byte[23];for(int n=0;n<10;n++)b[2*n+1]=20;b[20]=7;b[22]=(byte)255;
            return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(b),0,10);
        }
        public CdAssetBytes table(Table t){return Objects.requireNonNull(tables.get(t),"constructed absent "+t);}
        public int savedFlagsAddress(){return 0xFFF000;}
    }
    static CdAssetBytes hex(String s){return CdAssetBytes.copyOf(HexFormat.of().parseHex(s));}
    static final class H implements Ports {
        final CdObjectPlacementRuntime placement=new CdObjectPlacementRuntime(CdObjectPlacements.decodeLayout(hex("000c0014ffff000000000000ffff000000000000ffff00000000").copy()));
        final CdObjectPool pool=placement.pool();final Data data=new Data();final CdR4Boss body=new CdR4Boss(placement,data,this);
        final EnumMap<Field,Integer> values=new EnumMap<>(Field.class);final List<String> events=new ArrayList<>();
        final List<Call> calls=new ArrayList<>();final ArrayDeque<Integer> randoms=new ArrayDeque<>();
        Consumer<Call> onCall;
        H(){for(var f:Field.values())values.put(f,0);}
        void call(Call c){assertTrue(body.isCurrentCall(c));assertTrue(c.isCurrent());assertEquals(pool.capture().slots(),c.actualCut().pool().slots());c.claim();calls.add(c);if(onCall!=null)onCall.accept(c);}
        public int read(Call c,Field f){call(c);events.add("read:"+f);return values.get(f);}
        public void store(Call c,Field f,int v){call(c);events.add("store:"+f+":"+v);values.put(f,v);}
        public void sound(Call c,Sound s){call(c);events.add("sound:"+s);}
        public void palette(Call c,int n){call(c);events.add("palette:"+n);}
        public void capsulePalette(Call c){call(c);events.add("capsulePalette");}
        public void queueBubbleArt(Call c){call(c);events.add("plc4");}
        public int random(Call c){call(c);events.add("rng");return randoms.removeFirst();}
        public CdPlayerMath.SineCosine sineCosine(Call c,int angle){call(c);events.add("sine:"+angle);return new CdPlayerMath.SineCosine(0,256);}
        public void points100(Call c){call(c);events.add("points100");}
        public void draw(Call c){call(c);events.add("draw");}
        public void solidDoor(Call c){call(c);events.add("solid");}
        public void resetDrown(Call c){call(c);events.add("drown");}
        // Explicit guard-only legacy selector control. Substantive Hurt coverage has its own harness.
        com.openggf.game.soniccd.player.CdPlayerTouchResponse.HurtPair guardedHurtPair=guardedHurtPair();
        private static com.openggf.game.soniccd.player.CdPlayerTouchResponse.HurtPair guardedHurtPair(){
            var m=new com.openggf.game.soniccd.player.CdPlayerMotionState(0,0,0,0,0,2,0,0,4,9,19,0,0,0,0,0,0,0,0,0);
            return new com.openggf.game.soniccd.player.CdPlayerTouchResponse.HurtPair(new com.openggf.game.soniccd.player.CdPlayerTouchResponse.Actor(m,0,4,0,0),
                new com.openggf.game.soniccd.player.CdPlayerTouchResponse.PlayerGlobals(1,0,0,0,0,0,0,0),
                new com.openggf.game.soniccd.player.CdPlayerTouchResponse.WorldGlobals(0,0,0,0,0,0));
        }
        public com.openggf.game.soniccd.player.CdPlayerTouchResponse.HurtPair hurtPair(Call c){assertTrue(body.isCurrentHurtCall(c));return guardedHurtPair;}
        public PreparedHurtPair prepareHurtPair(HurtPublication p){
            assertTrue(body.isCurrentHurtPublication(p));var before=guardedHurtPair;var after=p.reachedPrefix().pair();events.add("hurt");
            return new PreparedHurtPair(){public HurtPublication original(){return p;}public com.openggf.game.soniccd.player.CdPlayerTouchResponse.HurtPair before(){return before;}
                public com.openggf.game.soniccd.player.CdPlayerTouchResponse.HurtPair after(){return after;}public void publish(){guardedHurtPair=after;}};
        }
        void put(int n,int id,int routine){pool.write(pool.state(n).moveByte(ID,id).moveByte(ROUTINE,routine));}
        void b(int n,int at,int v){pool.write(CdR4Boss.byteStore(pool.state(n),at,v));}
        void w(int n,int at,int v){pool.write(CdR4Boss.wordStore(pool.state(n),at,v));}
        int b(int n,int at){return Byte.toUnsignedInt(pool.state(n).copyBytes()[at]);}
        int w(int n,int at){return CdR4Boss.word(pool.state(n),at);}
        void l(int n,int at,int v){w(n,at,v>>>16);w(n,at+2,v);}
        int raw(int n){return 0xD000+n*64;}
        void full(){for(int n=32;n<128;n++)put(n,1,0);}
        void safeEggman(){values.put(Field.PLAYER_HURT_TIMER,1);}
    }
    @Test void missileInitRetainsDirtyFractionsAndWaitsFortyFiveActualVisits(){
        H h=new H();h.put(70,0x4E,0);h.put(71,0x4C,24);h.w(70,0x2E,h.raw(71));h.w(71,8,0xA00);h.w(71,12,0x520);h.w(70,10,0xBEEF);h.w(70,0x38,-29);h.w(70,0x3A,23);
        for(int i=0;i<44;i++)h.body.run(70);assertEquals(2,h.pool.state(70).routine());assertEquals(0,h.b(70,0x20));
        h.body.run(70);assertEquals(4,h.pool.state(70).routine());assertEquals(0xFE,h.b(70,0x20));assertEquals(2,h.b(70,0x21));assertEquals(0xBEEF,h.w(70,10));assertEquals(0x9E3,h.w(70,8));
    }
    @Test void missileRawParentMayBeIdZeroAndDefeatDeletesWithoutDraw(){
        H h=new H();h.put(70,0x4E,4);h.w(70,0x2E,h.raw(71));h.w(70,8,0xA00);h.w(70,12,0x520);h.body.run(70);assertEquals(0x4E,h.pool.state(70).id());
        h.events.clear();h.b(71,0x2C,128);h.body.run(70);assertEquals(0,h.pool.state(70).id());assertFalse(h.events.contains("draw"));
    }
    @Test void missileInclusiveBoundsAndNoLowerYBound(){
        for(int x:new int[]{0x99F,0x9A0,0xB00,0xB01}){H h=new H();h.put(70,0x4E,4);h.w(70,0x2E,h.raw(71));h.w(70,8,x);h.w(70,12,0x5D0);h.body.run(70);assertEquals(x>=0x9A0&&x<=0xB00?0x4E:0,h.pool.state(70).id());}
        H h=new H();h.put(70,0x4E,4);h.w(70,0x2E,h.raw(71));h.w(70,8,0xA00);h.w(70,12,0xFFFF);h.body.run(70);assertEquals(0x4E,h.pool.state(70).id());
    }
    @Test void invalidRawLinkFailsBeforeAnimationWithInitPrefixKept(){H h=new H();h.put(70,0x4E,0);h.w(70,0x2E,0xD801);assertThrows(UnsupportedOperationException.class,()->h.body.run(70));assertEquals(2,h.pool.state(70).routine());assertEquals(0x31E,h.w(70,2));assertTrue(h.events.isEmpty());}
    @Test void missileFourChildrenKeepPhysicalDirtyOrderAndShortAllocationPrefix(){
        H h=new H();h.full();h.safeEggman();h.put(70,0x4C,12);h.w(70,0x34,0x700);h.b(70,0x20,0x3C);h.w(70,8,0xAB0);h.w(70,12,0x520);
        h.put(33,0,0xA4);h.put(89,0,0xB6);h.w(33,10,0xA55A);h.body.run(70);
        assertEquals(0x4E,h.pool.state(33).id());assertEquals(0x4E,h.pool.state(89).id());assertEquals(0xA4,h.pool.state(33).routine());assertEquals(0xB6,h.pool.state(89).routine());assertEquals(0xA55A,h.w(33,10));
        assertEquals(0xFFE3,h.w(33,0x38));assertEquals(0x0017,h.w(33,0x3A));assertEquals(0xFFFE,h.w(33,0x3C));assertEquals(0xC398,h.w(33,0x3E));assertEquals(0,h.w(89,0x3C));
        assertEquals(h.raw(70),h.w(33,0x2E));
    }
    @Test void chaseTerminalWakesRawLinkBeforeList4AndSkipsDraw(){H h=new H();h.put(70,0x4A,4);h.put(71,0x4B,2);h.w(70,0x2E,0xAA55);h.w(70,0x30,h.raw(71));h.w(70,12,0x580);h.l(70,0x3C,0);h.body.run(70);assertEquals(4,h.pool.state(71).routine());assertEquals(0,h.pool.state(70).id());assertEquals(List.of("plc4"),h.events);}
    @Test void chaseList4FailureRetainsWakeAndMobile(){H h=new H();h.put(70,0x4A,4);h.put(71,0x4B,2);h.w(70,0x30,h.raw(71));h.w(70,12,0x580);RuntimeException fault=new RuntimeException("original PLC");h.onCall=c->{throw fault;};assertSame(fault,assertThrows(RuntimeException.class,()->h.body.run(70)));assertEquals(4,h.pool.state(71).routine());assertEquals(0x4A,h.pool.state(70).id());assertEquals(0x3000,h.w(70,0x3E));}
    @Test void chaseRetiresOnlyFirstNonzeroDoorPerVisitThenSelf(){H h=new H();h.put(70,0x4B,4);h.put(33,0x47,0);h.put(34,0x47,0);h.w(70,0x32,h.raw(33));h.w(70,0x34,h.raw(34));h.values.put(Field.PLAYER_X,0x840);h.values.put(Field.PLAYER_Y,0x550);
        h.body.run(70);assertEquals(0,h.pool.state(33).id());assertEquals(0x47,h.pool.state(34).id());h.body.run(70);assertEquals(0,h.pool.state(34).id());assertEquals(0x4B,h.pool.state(70).id());h.body.run(70);assertEquals(0,h.pool.state(70).id());assertEquals(4,h.values.get(Field.BOSS_FLAGS));}
    @Test void chaseInitFailedAllocationKeepsDirtyLinkAndStillAnimates(){H h=new H();h.full();h.put(70,0x4A,0);h.w(70,0x30,0xBEEF);h.body.run(70);assertEquals(8,h.pool.state(70).routine());assertEquals(0xBEEF,h.w(70,0x30));assertEquals(List.of("palette:5","draw"),h.events);}
    @Test void chaseHorizontalTargetReadIsLazyAndBobUsesSharedMath(){H h=new H();h.put(70,0x4A,2);h.b(70,0x21,0);h.data.tables.put(Table.CHASE_MAIN,hex("000004000000"));h.l(70,0x3C,0x10000);h.w(70,8,0x3FE);h.body.run(70);assertEquals(0x3FF,h.w(70,8));assertTrue(h.events.contains("sine:2"));assertEquals(0,h.b(70,0x25));}
    @Test void bubbleInitSignedRemainderReadsRngTwiceAndRetainsFractions(){H h=new H();h.put(70,0x4F,0);h.b(70,0x25,1);h.w(70,12,0x500);h.w(70,14,0x8000);h.randoms.add(0xFFFF);h.randoms.add(15);h.body.run(70);assertEquals(0xFF7F,h.w(70,0x36));assertEquals(25,h.w(70,0x3C));assertEquals(0x4FF,h.w(70,12));assertEquals(2,h.events.stream().filter("rng"::equals).count());}
    @Test void detachedBubbleDeletionSkipsParentReadAnimationAndDraw(){H h=new H();h.put(70,0x4F,2);h.b(70,0x25,1);h.w(70,12,0x470);h.body.run(70);assertEquals(0,h.pool.state(70).id());assertTrue(h.events.isEmpty());}
    @Test void orbitKeepsNativeLowWordMultiplyOverflow(){H h=new H();h.put(70,0x4F,8);h.w(70,0x2E,h.raw(71));h.put(71,0x4C,24);h.w(71,8,0xAB0);h.w(71,12,0x520);h.w(70,0x3C,0x100);h.b(70,0x20,255);h.body.run(70);assertEquals(0xAB0,h.w(70,8));assertEquals(0,h.w(70,0x38));}
    @Test void firstBubblePopSetsParentBitAndSecondRestoresCollision(){H h=new H();h.put(71,0x4C,24);h.b(71,0x2D,16);for(int n:new int[]{70,72}){h.put(n,0x4F,8);h.w(n,0x2E,h.raw(71));h.body.run(n);}assertEquals(12,h.pool.state(70).routine());assertEquals(8,h.pool.state(72).routine());assertEquals(15,h.b(71,0x2D));assertEquals(255,h.b(72,0x20));assertEquals(2,h.b(72,0x21));}
    @Test void inhalingRequiresBossByteFourAndBallFlagsThenOrderedReset(){H h=new H();h.put(70,0x4F,8);h.b(70,0x20,255);h.w(70,8,0xA50);h.values.put(Field.BOSS_STARTED,4);h.values.put(Field.PLAYER_FLAGS,0x95);h.values.put(Field.PLAYER_X,0xA50);h.body.special(70);assertEquals(0x81,h.values.get(Field.PLAYER_FLAGS));assertEquals(0,h.b(70,0x20));assertEquals(0x400,h.values.get(Field.PLAYER_Y_SPEED));assertEquals(0x200,h.values.get(Field.PLAYER_X_SPEED));assertTrue(h.events.indexOf("sound:INHALE")<h.events.indexOf("drown"));assertTrue(h.events.indexOf("drown")<h.events.indexOf("store:PLAYER_Y_SPEED:1024"));}
    @Test void arenaSpecialType3dHurtsButBubbleDoesNotInhaleWhenBossByteFive(){H h=new H();h.put(70,0x4F,8);h.b(70,0x20,255);h.values.put(Field.BOSS_STARTED,5);h.body.special(70);assertFalse(h.events.contains("drown"));h.b(70,0x20,0xFD);h.body.special(70);assertEquals(1,h.b(70,0x21));assertTrue(h.events.contains("hurt"));}
    @Test void shieldSpawnFailureStopsAtFirstUnownedStoreAfterNativeSoundAndTimer(){H h=new H();h.full();h.safeEggman();h.put(70,0x4C,6);h.b(70,0x2A,19);h.w(70,12,0x520);assertThrows(UnsupportedOperationException.class,()->h.body.run(70));assertEquals(0,h.b(70,0x2A));assertEquals(0,h.b(70,0x2D));assertTrue(h.events.contains("sound:BUBBLE"));assertFalse(h.events.contains("draw"));}
    @Test void hitContinuesNativePhaseThenDefeatScoresAndEscapes(){H h=new H();h.safeEggman();h.put(70,0x4C,12);h.put(71,0x4D,2);h.w(70,0x30,h.raw(71));h.b(70,0x21,1);h.body.run(70);assertEquals(18,h.pool.state(70).routine());assertEquals(0x100,h.w(70,0x34));assertEquals(0x80,h.b(70,0x2C)&128);
        h.b(70,0x2A,60);h.body.run(70);assertEquals(20,h.pool.state(70).routine());assertTrue(h.events.contains("points100"));h.b(70,0x2A,59);h.w(70,8,0xB2F);h.body.run(70);assertEquals(4,h.pool.state(70).routine());assertEquals(0,h.pool.state(71).id());}
    @Test void escapeReadsAllFourBoundsBeforeAnyStoreAndOrdersMusicPaletteDelete(){H h=new H();h.safeEggman();h.put(70,0x4C,4);h.data.tables.put(Table.ESCAPE_BOUNDS,hex("00000f000000"));assertThrows(IndexOutOfBoundsException.class,()->h.body.run(70));assertFalse(h.events.stream().anyMatch(x->x.startsWith("store:")));
        h.data.tables.put(Table.ESCAPE_BOUNDS,hex("00000f0000000000"));h.values.put(Field.RIGHT_BOUND,0xEFA);h.values.put(Field.TARGET_RIGHT_BOUND,0xEFA);h.values.put(Field.GOOD_FUTURE,1);h.events.clear();h.body.run(70);assertEquals(0,h.pool.state(70).id());assertEquals(0xF00,h.values.get(Field.RIGHT_BOUND));assertTrue(h.events.indexOf("sound:GOOD_MUSIC")<h.events.indexOf("capsulePalette"));assertFalse(h.events.contains("draw"));}
    @Test void savedDoorLatchesOriginalPeriodPointerAndOpensInSourceOrder(){H h=new H();h.put(70,0x47,0);h.b(70,0x23,3);h.b(70,0x28,1);h.values.put(Field.TIME_ZONE,2);h.body.run(70);assertEquals(4,h.pool.state(70).routine());assertEquals(0xF00D,h.w(70,0x2E));h.values.put(Field.TIME_ZONE,0);h.b(70,0x3F,1);h.body.run(70);assertEquals(1,h.placement.savedFlag(3,2));assertEquals(0,h.placement.savedFlag(3,0));assertEquals(6,h.pool.state(70).routine());assertTrue(h.events.indexOf("solid")<h.events.indexOf("draw"));h.body.run(70);assertEquals(4,h.w(70,12));assertEquals(31,h.w(70,0x2A));}
    @Test void staleCallsAndReentryAreRejectedAndFaultFencesPreserveOriginal(){H h=new H();h.put(70,0x4A,8);h.body.run(70);Call retained=h.calls.getFirst();assertFalse(retained.isCurrent());assertThrows(IllegalStateException.class,retained::claim);
        RuntimeException original=new RuntimeException("owner failure");h.onCall=c->{h.b(90,0,9);assertThrows(IllegalStateException.class,()->h.body.run(70));throw original;};assertSame(original,assertThrows(RuntimeException.class,()->h.body.run(70)));assertEquals(1,original.getSuppressed().length);assertEquals(9,h.pool.state(90).id());}
    @Test void mappingFaultRetainsAllPrecedingNativeInitStores(){H h=new H();h.put(70,0x4E,0);h.data.mappingMissing=true;assertThrows(UnsupportedOperationException.class,()->h.body.run(70));assertEquals(2,h.pool.state(70).routine());assertEquals(4,h.b(70,0x16));assertEquals(0x31E,h.w(70,2));assertTrue(h.events.isEmpty());}

    @Test void actualUsaMediaAuthenticatesAllTenProgrammesAndR43MissileOperands() throws Exception {
        String root=System.getProperty("soniccd.media.root");assertNotNull(root,"Required actual-media property");
        Path cue=Path.of(root,"USA","Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue),"Actual USA media required; no assumption skip");
        String[] files={"R41A","R41B","R41C","R41D","R42A","R42B","R42C","R42D","R43C","R43D"};
        String[] hashes={"6ddd5b53e1530c15099948053280809271319d35a3c0424b424393ce513054ca","4d9698d1bb3ef967171fcb65e31ae6bb74fbadce706a2016385fba091ec0d4f2","da73414c78fd8108816f39633c9b39fc8d9e8761296b8dfb5bbf457b1ef93e90","101a602503dd1db59bdd52125c450690a816e303e97f2c08907c72f3cc89da11","5ccaa4f12da09f0aaaf9308be81f8345b02c68e11f78173693c347dec1784024","c3c987ae4113d94d0ae03e893dd44c66988ada3baa2f657b26391e654bab6355","e478ce24bf9fdd5216102b42b08c908ee383f6bdb02258ef53802b00b5b27dc8","eb1f5c2d3cb11735438f74b799e0f8626426ce4beb66cbf577359d2ed7454496","c27a455859c4820182c8119d0de3618dde55dd47d34d32c396963e677ac1994c","a11d1d547841697d87d2a1b5fd5c3fe3ebab9aeaf5e8f1d3964f165a521b4789"};
        try(var media=CdProgramAssets.open(cue)){
            assertEquals(CdReleaseVariant.USA,media.header().observedVariant().orElseThrow());
            for(int n=0;n<files.length;n++){var image=media.mmd(CdNativeFile.valueOf(files[n]));byte[] bytes=image.readFile(0,image.fileBytes());assertEquals(hashes[n],HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),files[n]);
                if(n>=8){byte[] needle=new Data().tables.get(Table.MISSILE_DECLARATIONS).copy();int found=-1;
                    for(int at=0;at<=bytes.length-needle.length;at++){boolean match=true;for(int k=0;k<needle.length;k++)if(bytes[at+k]!=needle[k]){match=false;break;}if(match){assertEquals(-1,found,"Unique retail missile declaration");found=at;}}
                    assertTrue(found>=0,"literal native missile table in "+files[n]);
                    H h=new H();h.data.tables.put(Table.MISSILE_DECLARATIONS,CdAssetBytes.copyOf(image.readFile(found,needle.length)));h.safeEggman();h.put(70,0x4C,12);h.b(70,0x20,0x3C);h.w(70,0x34,0x700);h.body.run(70);assertEquals(0x4E,h.pool.state(32).id());assertEquals(0xFFE3,h.w(32,0x38));
                }
            }
        }
    }
    @Test void unusedDefeatAddressLoadDoesNotDereferenceDirtyRawLink(){
        H h=new H();h.safeEggman();h.put(70,0x4C,18);h.b(70,0x2A,59);h.w(70,0x30,0x1235);
        h.body.run(70);assertEquals(18,h.pool.state(70).routine());assertEquals(3,h.b(70,0x1C));
        assertThrows(UnsupportedOperationException.class,()->h.body.run(70));assertEquals(20,h.pool.state(70).routine());assertFalse(h.events.contains("points100"));
    }
    @Test void contractUsesSignedSubtractionPredicateAcrossDirtyLongOverflow(){
        H h=new H();h.put(70,0x4F,10);h.put(71,0x4C,24);h.w(70,0x2E,h.raw(71));h.b(70,0x20,255);h.l(70,0x3C,Integer.MIN_VALUE);
        h.body.run(70);assertEquals(1,h.b(70,0x25));assertEquals(0x7FFF,h.w(70,0x3C));
    }
    @Test void chaseDistanceRetainsSignedWordSubtractionAndLongOverflow(){
        H h=new H();h.put(70,0x4A,6);h.put(71,0x4B,2);h.w(70,0x30,h.raw(71));h.b(70,0x2A,1);h.w(70,8,0x8000);
        h.values.put(Field.PLAYER_X,0x7FFF);h.values.put(Field.PLAYER_Y,200);h.data.tables.put(Table.CHASE_MAIN,hex("000000000000"));
        h.body.run(70);assertEquals(8,h.w(70,0x3C));assertEquals(0,h.w(70,0x3E));
    }
    private static CdSolidObject.Result constructedSolid(CdObjectState source){
        // Explicit constructed typed-publication control, NOT a native Solid collision witness.
        var motion=new com.openggf.game.soniccd.player.CdPlayerMotionState(0,0,0,0,0,2,0,0,4,9,19,0,0,0,0,0,0,0,0,0);
        var actor=new com.openggf.game.soniccd.player.CdPlayerTouchResponse.Actor(motion,0,4,0,0);
        return new CdSolidObject.Result(CdSolidObject.Entry.SOLID_OBJECT,source,
            new CdSolidObject.Player(actor,1,0,0),new CdSolidObject.Globals(0,0,0,0),List.of(),Set.of(FLAGS,SOLID_TYPE),Set.of(),Set.of(),List.of(),
            CdSolidObject.Path.STAND_ON,Optional.empty(),OptionalInt.of(1));
    }
    @Test void originalSolidCallAdoptsOnlyItsFiniteSameOwnerPrefixAndExpires(){
        H h=new H();h.put(70,0x47,4);h.b(70,0x28,1);
        h.onCall=call->{if(call.site().equals("SolidObject:boss door")){
            var before=h.pool.state(70);
            var bad=constructedSolid(before.moveByte(ID,99));
            assertThrows(IllegalArgumentException.class,()->call.adoptSolid(new CdSolidObject.Completed(bad)));
            assertEquals(before,h.pool.state(70));
            var good=constructedSolid(before.moveByte(FLAGS,before.flags()|8).moveByte(SOLID_TYPE,0));
            call.adoptSolid(new CdSolidObject.Completed(good));
            assertThrows(IllegalStateException.class,()->call.adoptSolid(new CdSolidObject.Completed(good)));
        }};
        h.body.run(70);assertEquals(8,h.pool.state(70).flags());
        Call old=h.calls.getFirst();assertThrows(IllegalStateException.class,()->old.adoptSolid(new CdSolidObject.Completed(constructedSolid(h.pool.state(70)))));
    }
    @Test void failedOriginalSolidRetainsTypedPrefixWithoutReplacingItsFault(){
        H h=new H();h.put(70,0x47,4);h.b(70,0x28,1);RuntimeException fault=new RuntimeException("actual missing Solid continuation");
        h.onCall=call->{if(call.site().equals("SolidObject:boss door")){
            var r=constructedSolid(h.pool.state(70).moveByte(FLAGS,8));
            var prefix=new CdSolidObject.Prefix(r.entry(),r.object(),r.player(),r.globals(),r.otherSlots(),r.objectWrites(),r.playerWrites(),r.globalWrites(),r.calls());
            call.adoptSolid(new CdSolidObject.NativeStopped(prefix,new CdSolidObject.NativeStop(CdSolidObject.StopSite.STAND_RELEASE,new CdSolidObject.CollisionId(0),fault)));
            throw fault;
        }};
        assertSame(fault,assertThrows(RuntimeException.class,()->h.body.run(70)));assertEquals(8,h.pool.state(70).flags());assertEquals(0,fault.getSuppressed().length);assertFalse(h.events.contains("draw"));
    }
    @Test void activeForeignComponentDoesNotSatisfyCachedProducerIdentity(){
        H h=new H();h.put(70,0x4A,8);CdR4Boss[] foreign=new CdR4Boss[1];
        foreign[0]=new CdR4Boss(h.placement,h.data,new Ports(){
            private void check(Call c){assertFalse(h.body.isCurrentCall(c));assertTrue(foreign[0].isCurrentCall(c));c.claim();}
            public int read(Call c,Field f){check(c);return 0;}
            public void draw(Call c){check(c);}
        });
        foreign[0].run(70);
    }
    @Test void missileChildrenMirrorOnlyNativeXOperands(){
        H h=new H();h.safeEggman();h.put(70,0x4C,12);h.b(70,0x20,0x3C);h.b(70,0x22,1);h.w(70,0x34,0x700);h.body.run(70);
        assertEquals(29,h.w(32,0x38));assertEquals(23,h.w(32,0x3A));assertEquals(1,h.w(32,0x3C));assertEquals(0x3C68,h.w(32,0x3E));
        assertEquals(1,h.w(32,0x10));assertEquals(0x3C68,h.w(32,0x12));assertEquals(0xFFE3,h.w(35,0x38));
    }
    @Test void inhaleBallFlagGateAndUnsignedLeftSideRemainLiteral(){
        H h=new H();h.put(70,0x4F,8);h.b(70,0x20,255);h.w(70,8,0xA50);h.values.put(Field.BOSS_STARTED,4);h.body.special(70);
        assertEquals(255,h.b(70,0x20));assertFalse(h.events.contains("drown"));
        h.values.put(Field.PLAYER_FLAGS,4);h.values.put(Field.PLAYER_X,0xA4F);h.body.special(70);
        assertEquals(0xFE00,h.values.get(Field.PLAYER_X_SPEED));assertEquals(0x400,h.values.get(Field.PLAYER_Y_SPEED));
    }
    @Test void arenaEntryLocksBoundsThenReadsThresholdBeforeMusicAndLeftLock(){
        H h=new H();h.safeEggman();h.put(70,0x4C,2);h.values.put(Field.PLAYER_X,0xA4F);h.body.run(70);
        assertEquals(2,h.pool.state(70).routine());assertEquals(0x9AF,h.values.get(Field.LEFT_BOUND));assertEquals(0x9B0,h.values.get(Field.RIGHT_BOUND));
        assertTrue(h.events.indexOf("store:RIGHT_BOUND:2480")<h.events.indexOf("read:PLAYER_X"));assertFalse(h.events.contains("sound:BOSS_MUSIC"));
        h.events.clear();h.values.put(Field.PLAYER_X,0xA50);h.body.run(70);assertEquals(6,h.pool.state(70).routine());assertEquals(0x9B0,h.values.get(Field.LEFT_BOUND));
        assertTrue(h.events.indexOf("sound:BOSS_MUSIC")<h.events.indexOf("store:LEFT_BOUND:2480"));
    }
    @Test void commonSpecialPrefixRemainsWithItsOriginalPlayerOwner(){
        H h=new H();h.put(70,0x4F,8);h.b(70,0x20,0xDF);
        assertThrows(UnsupportedOperationException.class,()->h.body.special(70));assertEquals(0,h.b(70,0x21));assertTrue(h.events.isEmpty());
    }

}
