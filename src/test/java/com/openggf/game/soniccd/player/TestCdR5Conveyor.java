package com.openggf.game.soniccd.player;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** SOURCE candidates, not execution evidence. Native r5/player_block_check.asm:3-339. */
class TestCdR5Conveyor {
    static class Fixture implements CdR5Conveyor.Environment, CdR5Conveyor.Player,
            CdR5Conveyor.Terrain, CdR5Conveyor.BlockDirectory, CdR5ConveyorReverse.Port {
        int x=100<<16, y=100, height=19, width=9, boss, era=1, good, reverse;
        int block=1, secondBlock=1, distance, count, blockReads, selectedSlot=-1;
        final List<String> calls=new ArrayList<>();
        RuntimeException frontFailure;
        @Override public int bossFlags(){calls.add("boss");return boss;}
        @Override public int timeZone(){calls.add("era");return era;}
        @Override public int goodFuture(){calls.add("good");return good;}
        @Override public int xRaw(){calls.add("x");return x;}
        @Override public int yWord(){calls.add("y");return y;}
        @Override public int heightByte(){calls.add("height");return height;}
        @Override public int widthByte(){calls.add("width");return width;}
        @Override public void publishX(CdR5Conveyor.PositionChange c,CdR5ConveyorScope.Operation op){
            assertEquals(x,c.beforeRaw());x=c.afterRaw();calls.add("storeX");
        }
        @Override public int collisionBlock(int xWord,int yWord){
            calls.add("foot:"+xWord+":"+yWord);return blockReads++==0?block:secondBlock;
        }
        @Override public int frontDistance(int angleByte){
            calls.add("front:"+angleByte);if(frontFailure!=null)throw frontFailure;return distance;
        }
        @Override public int directoryWord(int slot){selectedSlot=slot;calls.add("directory");return 8;}
        @Override public int countMinusOne(int slot){calls.add("count");return count;}
        @Override public int entryWord(int slot,int index){calls.add("entry:"+index);return index+1;}
        @Override public int read(){calls.add("reverse");return reverse;}
        @Override public void publish(CdR5ConveyorReverse.Change c,CdR5ConveyorScope.Operation op){
            assertEquals(reverse,c.before());reverse=c.after();
        }
        CdR5Conveyor create(CdR5ConveyorScope.Guard guard){return new CdR5Conveyor(this,this,this,this,this,guard);}
        CdR5Conveyor create(){return create(CdR5ConveyorScope.Guard.NONE);}
    }

    @Test void bossByteBypassesEveryActorAndTerrainRead(){
        var f=new Fixture();f.boss=0x80;assertFalse(f.create().run().displaced());assertEquals(List.of("boss"),f.calls);
    }
    @Test void firstMatchMasksCollisionToLowElevenBitsAndSkipsRightFoot(){
        var f=new Fixture();f.block=0xF801;assertTrue(f.create().run().displaced());assertEquals(1,f.blockReads);
        assertTrue(f.calls.contains("foot:91:121"));assertEquals(101<<16,f.x);
    }
    @Test void secondFootReReadsPlayerAndUsesSignedWidthAndHeight(){
        var f=new Fixture();f.block=0x777;f.secondBlock=1;f.height=0xFF;f.width=0xFE;
        assertTrue(f.create().run().displaced());assertEquals(2,f.blockReads);
        assertTrue(f.calls.contains("foot:102:101"));assertTrue(f.calls.contains("foot:98:101"));
    }
    @Test void noMatchHasNoReverseFrontOrPositionPublication(){
        var f=new Fixture();f.block=f.secondBlock=0x777;assertFalse(f.create().run().displaced());
        assertEquals(100<<16,f.x);assertFalse(f.calls.contains("reverse"));
    }
    @Test void countMinusOneIncludesItsLastComparison(){
        var f=new Fixture();f.count=2;f.block=3;f.create().run();assertEquals(1,f.blockReads);assertTrue(f.calls.contains("entry:2"));
    }
    @Test void futureTableSelectsBadThenGoodWithoutChangingRawEraSpeed(){
        for(int good:new int[]{0,1}){var f=new Fixture();f.era=2;f.good=good;f.create().run();assertEquals(2+good,f.selectedSlot);assertEquals(102<<16,f.x);}
    }
    @Test void tableMasksTimeZoneButDisplacementReadsItsRawByteAgain(){
        var f=new Fixture();f.era=0x80;f.create().run();assertEquals(0,f.selectedSlot);assertEquals(102<<16,f.x);
        assertEquals(2,f.calls.stream().filter("era"::equals).count());
    }
    @Test void rawPastPresentFutureUseHalfOneAndTwoPixelDisplacements(){
        int[] speeds={0x8000,0x10000,0x20000};
        for(int era=0;era<3;era++){var f=new Fixture();f.era=era;f.create().run();assertEquals((100<<16)+speeds[era],f.x);}
    }
    @Test void anyNonzeroReverseNegatesMotionAndFrontAngle(){
        var f=new Fixture();f.reverse=0x80;f.create().run();assertEquals(99<<16,f.x);assertTrue(f.calls.contains("front:64"));
    }
    @Test void negativeFrontDistanceCorrectsWholeWordAndPreservesFraction(){
        var f=new Fixture();f.era=0;f.distance=0xFFFE;f.create().run();assertEquals((98<<16)|0x8000,f.x);
    }
    @Test void frontCorrectionReReadsSharedReverseAfterTheFrontCall(){
        var f=new Fixture(){@Override public int frontDistance(int angle){reverse=1;return 0xFFFE;}};
        f.create().run();assertEquals(103<<16,f.x);
    }
    @Test void positiveFrontResultDoesNotReadReverseAgain(){
        var f=new Fixture();f.distance=1;f.create().run();assertEquals(1,f.calls.stream().filter("reverse"::equals).count());
    }
    @Test void frontFailureRetainsCompletedFixedPointPositionAndOriginalFault(){
        var f=new Fixture();var original=new IllegalStateException("front");f.frontFailure=original;var c=f.create();
        assertSame(original,assertThrows(IllegalStateException.class,c::run));assertEquals(101<<16,f.x);
        assertTrue(c.lastPrefix().stream().anyMatch(t->t.operation().kind()==CdR5ConveyorScope.Kind.MOVE_X&&t.completed()));
        assertFalse(c.lastPrefix().getLast().completed());
    }
    @Test void guardRejectionBeforePositionHasNoPositionWrite(){
        var f=new Fixture();var c=f.create(new CdR5ConveyorScope.Guard(){
            @Override public void before(CdR5ConveyorScope.Operation op){if(op.kind()==CdR5ConveyorScope.Kind.MOVE_X)throw new IllegalStateException("lease");}
            @Override public void after(CdR5ConveyorScope.Operation op,boolean done){}
        });assertThrows(IllegalStateException.class,c::run);assertEquals(100<<16,f.x);
    }
    @Test void caughtReentryRejectsWithoutReplacingTheHealthyOuterCall(){
        var f=new Fixture();CdR5Conveyor[] c=new CdR5Conveyor[1];
        c[0]=f.create(new CdR5ConveyorScope.Guard(){
            @Override public void before(CdR5ConveyorScope.Operation op){assertThrows(IllegalStateException.class,c[0]::run);}
            @Override public void after(CdR5ConveyorScope.Operation op,boolean done){}
        });assertTrue(c[0].run().displaced());assertEquals(101<<16,f.x);
    }
    @Test void byteAdditionForGoodFutureWrapsBeforeDirectoryRead(){
        var f=new Fixture();f.era=2;f.good=255;f.create().run();assertEquals(1,f.selectedSlot);
    }
    @Test void unknownReverseIsNotAnImplicitZero(){
        assertThrows(IllegalStateException.class,()->CdR5ConveyorReverse.unknown().read());
        assertEquals(0xA4,CdR5ConveyorReverse.known(0xA5).toggle().read());
    }
}
