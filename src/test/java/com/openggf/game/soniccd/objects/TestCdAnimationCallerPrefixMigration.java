package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed component formats over real owned placement/pools; no media or World admission. */
class TestCdAnimationCallerPrefixMigration {
    record Body(CdObjectPlacementRuntime placement, int whole, Supplier<?> run,
                Consumer<CdObjectAnimationBinding.Guard> bind, int speed, int script) { }
    static Body body(int which) {
        if (which == 0) {
            var h = TestCdTamabboh.ready(8);
            return new Body(h.placement, TestCdTamabboh.WHOLE, h::run, h.body::bindAnimationGuard, 29, 4);
        }
        if (which == 1) {
            var h = TestCdCheckpoint.prepared(6);
            return new Body(h.placement, TestCdCheckpoint.WHOLE, h::run, h.body::bindAnimationGuard, 3, 2);
        }
        var h = TestCdSpinningDisc.ready(); h.timeStop = 0;
        h.edit(s -> s.moveByte(SPRITE_FLAGS, 4)); // native invisible Main bypass, still reaches animation.
        return new Body(h.placement, TestCdSpinningDisc.WHOLE, h::run, h.body::bindAnimationGuard, 1, 2);
    }
    static void seed(Body b, int index) {
        var p = b.placement.pool(); var s = p.state(b.whole);
        p.write(s.moveByte(ANIMATION, 0).moveByte(PREVIOUS_ANIMATION, 0)
                .moveByte(ANIMATION_TIME, 0).moveByte(ANIMATION_FRAME, index));
    }
    @Test void actualReadFailureMapsLegacyWholeAndValueAndRetainsOnlyNativePrefix() {
        for (int which = 0; which < 3; which++) {
            var b = body(which); seed(b, 255); var before = b.placement.capture();
            RuntimeException e = assertThrows(RuntimeException.class, b.run::get);
            int value; int whole; String boundary;
            if (e instanceof CdTamabboh.UnsupportedTamabboh x) { value=x.value();whole=x.wholeIndex();boundary=x.boundary().name(); }
            else if (e instanceof CdCheckpoint.UnsupportedCheckpoint x) { value=x.value();whole=x.wholeIndex();boundary=x.boundary().name(); }
            else { var x=(CdSpinningDisc.UnsupportedSpinningDisc)e;value=x.value();whole=x.wholeIndex();boundary=x.boundary().name(); }
            assertEquals("ANIMATION_FRAME_ADDRESS", boundary); assertEquals(b.whole, whole); assertEquals(b.script + 256, value);
            assertInstanceOf(CdObjectAnimation.SelectedReadFailure.class,e.getCause());
            for (var s : before.pool().slots()) assertArrayEquals((s.identity().wholeIndex()==b.whole ? s.moveByte(ANIMATION_TIME,b.speed):s).copyBytes(), b.placement.pool().state(s.identity().wholeIndex()).copyBytes());
            assertEquals(before.status(),b.placement.status()); assertArrayEquals(before.savedFlags().copy(),b.placement.capture().savedFlags().copy());
        }
    }
    @Test void realIssuedStoreCutsIncludeEqualDuplicateWritesAndRetireOnErrorOrReturn() {
        for (int which=0;which<3;which++) for (boolean fail:new boolean[]{false,true}) {
            var b=body(which);seed(b,0);var stores=new ArrayList<String>();var retained=new ArrayList<CdObjectAnimation.Operation>();
            var scopes=new ArrayList<CdObjectAnimation.Prefix>(); var original=new AssertionError("after clear");
            b.bind.accept(new CdObjectAnimationBinding.Guard() {
                public void before(CdObjectAnimation.Prefix p,CdObjectAnimation.Operation op) {
                    assertTrue(p.isCurrent(op));scopes.add(p);retained.add(op);
                    assertThrows(IllegalStateException.class,b.run::get);
                }
                public void after(CdObjectAnimation.Prefix p,CdObjectAnimation.Operation op) {
                    assertTrue(p.isCurrent(op));
                    if(op.kind()==CdObjectAnimation.Kind.STORE && op.completed()) {
                        stores.add(op.field()+"="+op.value());
                        if(fail && op.field()==SPRITE_FLAGS) throw original;
                    }
                }
            });
            if(fail) assertSame(original,assertThrows(AssertionError.class,b.run::get)); else b.run.get();
            assertEquals(1,scopes.stream().distinct().count());
            for(int n=0;n<retained.size();n++) assertFalse(scopes.get(n).isCurrent(retained.get(n)));
            assertTrue(stores.contains("SPRITE_FLAGS=4"));
            assertEquals(fail?1:2,stores.stream().filter("SPRITE_FLAGS=4"::equals).count());
        }
    }
    @Test void retainedSelectedReadFailureFromCallbackNeverBecomesCallerByteBoundary() {
        var pool=new CdObjectPool();
        var retained=assertThrows(CdObjectAnimation.SelectedReadFailure.class,()->new CdObjectAnimation.Prefix(pool,32,new CdObjectAnimation.Guard(){
            public void before(CdObjectAnimation.Operation o){} public void after(CdObjectAnimation.Operation o){}
        }).step(new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(new byte[]{0,2}),0,1)));
        for(int which=0;which<3;which++) for(boolean after:new boolean[]{false,true}) {
            var b=body(which);seed(b,0);
            b.bind.accept(new CdObjectAnimationBinding.Guard(){
                public void before(CdObjectAnimation.Prefix p,CdObjectAnimation.Operation op){if(!after && op.kind()==CdObjectAnimation.Kind.FRAME)throw retained;}
                public void after(CdObjectAnimation.Prefix p,CdObjectAnimation.Operation op){if(after && op.kind()==CdObjectAnimation.Kind.FRAME)throw retained;}
            });
            assertSame(retained,assertThrows(CdObjectAnimation.SelectedReadFailure.class,b.run::get));
            assertEquals(b.speed,b.placement.pool().state(b.whole).unsignedByte(ANIMATION_TIME));
        }
    }
    @Test void sourceAndPlacementMutationAreRejectedWithoutReplacingOriginalCallbackFault() {
        for(int which=0;which<3;which++) for(int attack=0;attack<3;attack++) {
            var b=body(which);seed(b,0);final int kind=attack;var original=new IllegalStateException("callback");
            b.bind.accept(new CdObjectAnimationBinding.Guard(){
                public void before(CdObjectAnimation.Prefix p,CdObjectAnimation.Operation op) {
                    if(op.kind()!=CdObjectAnimation.Kind.FRAME)return;
                    if(kind==0)b.placement.pool().write(b.placement.pool().state(b.whole).addByte(ID,1));
                    if(kind==1)b.placement.pool().write(b.placement.pool().state(127).addByte(ID,1));
                    if(kind==2)b.placement.bsetSavedFlag(0,0,CdObjectPlacementRuntime.SavedBit.MEMBER_1);
                    throw original;
                }
                public void after(CdObjectAnimation.Prefix p,CdObjectAnimation.Operation op){}
            });
            assertSame(original,assertThrows(IllegalStateException.class,b.run::get));
            assertTrue(original.getSuppressed().length>0);
        }
    }
    @Test void displacedAdaptersAndPureStepCallsAreDeletedInAllThreeBodies() throws Exception {
        for(String name:List.of("CdTamabboh","CdCheckpoint","CdSpinningDisc")) {
            String text=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/openggf/game/soniccd/objects/"+name+".java"));
            assertFalse(text.contains("animationReadPrefixes"));assertFalse(text.contains("animationByte("));assertFalse(text.contains("CdObjectAnimation.step("));
            assertTrue(text.contains("CdObjectAnimationBinding.run("));
        }
    }
}
