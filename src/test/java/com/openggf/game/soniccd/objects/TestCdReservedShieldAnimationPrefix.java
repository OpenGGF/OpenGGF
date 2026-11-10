package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.openggf.game.soniccd.objects.CdObjectAnimation.Kind.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed reserved-owner/source-format controls. No World, media, capture or art admission.
 * Native N1ecc5f3a: Powerup/Main.asm:40-60 and Object Animate.asm:14-95.
 * Literal native store/read order is checked independently of successful final-value parity.
 */
class TestCdReservedShieldAnimationPrefix {
    @Test void genuineShieldPublishesEachNativeAnimationStoreOnceAndNeverAggregatesTheResult() {
        var f = new Fixture(table(3, 0x25)); var before = f.bank.capture();
        var source = f.bank.state(6); var result = f.body.run(6);
        var expected = new ArrayList<CdReservedObjectBank.Image>();
        var s = source.moveWord(X, 0x1234); expected.add(s);
        s = s.moveWord(Y, 0xFEDC); expected.add(s);
        s = s.moveByte(FLAGS, 2); expected.add(s);
        for (var store : List.of(new Store(PREVIOUS_ANIMATION,0), new Store(ANIMATION_FRAME,0),
                new Store(ANIMATION_TIME,0), new Store(ANIMATION_TIME,255), new Store(ANIMATION_TIME,3),
                new Store(MAP_FRAME,5), new Store(SPRITE_FLAGS,188), new Store(SPRITE_FLAGS,191),
                new Store(ANIMATION_FRAME,1))) {
            s = s.moveByte(store.field(),store.value()); expected.add(s);
        }
        assertEquals(expected, f.publications); assertEquals(s, result.state());
        assertEquals(1, result.effects().stream().filter(CdShieldPowerup.AnimationReturned.class::isInstance).count());
        assertEquals(List.of("powerup-read","draw"), f.io); assertOthers(before,f.bank);
        assertFalse(f.body.isCurrentCall(f.last));
    }

    @Test void reservedKernelExposesTheSameReadStoreOrderWithoutADynamicSurrogate() {
        var bank = dirtyBank(); seed(bank, state(0,9,7,5)); var before = bank.capture();
        var trace = new Trace(bank); trace.prefix.step(table(3,0x25));
        assertEquals(List.of(ENTER,STORE,STORE,STORE,STORE,DIRECTORY,SPEED,STORE,FRAME,
                STORE,STORE,STORE,STORE,RETURN), trace.kinds);
        assertEquals(List.of("PREVIOUS_ANIMATION=0","ANIMATION_FRAME=0","ANIMATION_TIME=0",
                "ANIMATION_TIME=255","ANIMATION_TIME=3","MAP_FRAME=5","SPRITE_FLAGS=188",
                "SPRITE_FLAGS=191","ANIMATION_FRAME=1"), trace.stores);
        assertOthers(before,bank); assertFalse(trace.prefix.isCurrent(trace.last));
    }

    @Test void heldRawIdPublishesOnlyTheNativeTimerStoreWithoutReadingItsDirectory() {
        var f = new Fixture(table(3,4));
        f.bank.write(f.bank.state(6).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,255)
                .moveByte(ANIMATION_FRAME,233).moveByte(ANIMATION_TIME,1).moveByte(MAP_FRAME,0xE7));
        f.body.run(6);
        assertEquals(4,f.publications.size()); // X, Y, Flags, then exactly one SUBQ.B.
        assertEquals(0,f.bank.state(6).unsignedByte(ANIMATION_TIME));
        assertEquals(255,f.bank.state(6).unsignedByte(ANIMATION));
        assertEquals(233,f.bank.state(6).unsignedByte(ANIMATION_FRAME));
        assertEquals(0xE7,f.bank.state(6).unsignedByte(MAP_FRAME));
    }

    @Test void zeroShieldWarpAndInvincibleAvoidEveryUnreachedAnimationAndOwnerOperation() {
        for (int gate=0;gate<3;gate++) {
            var f = new Fixture(table(3,4));
            f.bank.write(f.bank.state(6).moveByte(ANIMATION,255));
            var before = f.bank.capture();
            if(gate==0) f.values[CdShieldPowerup.ReadField.SHIELD.ordinal()]=0;
            if(gate==1) f.values[CdShieldPowerup.ReadField.TIME_WARP.ordinal()]=255;
            if(gate==2) f.values[CdShieldPowerup.ReadField.INVINCIBLE.ordinal()]=255;
            f.body.run(6); assertTrue(f.io.isEmpty());
            assertEquals(gate+1,f.reads.size());
            if(gate==0) { assertEquals(1,f.publications.size()); assertArrayEquals(new byte[64],f.bank.state(6).copyBytes()); }
            else { assertTrue(f.publications.isEmpty()); assertEquals(before.slots(),f.bank.capture().slots()); }
            assertOthers(before,f.bank);
        }
    }

    @Test void allRawTimersKeepTheSameSignedByteGateAndUnselectedInvalidId() {
        for(int timer=0;timer<256;timer++) {
            var bank=dirtyBank(); seed(bank,state(255,255,233,timer)); var trace=new Trace(bank);
            if(((timer-1)&255)<128) {
                trace.prefix.step(table(3,4)); assertEquals(List.of(ENTER,STORE,RETURN),trace.kinds);
            } else {
                var failure=assertThrows(CdObjectAnimation.SelectedReadFailure.class,()->trace.prefix.step(table(3,4)));
                assertEquals(DIRECTORY,failure.boundary()); assertEquals(510,failure.selectedAddress());
            }
            assertEquals((timer-1)&255,bank.state(6).unsignedByte(ANIMATION_TIME));
            assertEquals(233,bank.state(6).unsignedByte(ANIMATION_FRAME));
            assertEquals(0xE7,bank.state(6).unsignedByte(MAP_FRAME));
        }
    }

    @Test void genuineShieldDirectoryFaultRetainsSeparateResetsAndDecrementAndTypedAddress() {
        var f=new Fixture(table(3,4));
        f.bank.write(f.bank.state(6).moveByte(ANIMATION,255).moveByte(PREVIOUS_ANIMATION,8)
                .moveByte(ANIMATION_FRAME,77).moveByte(ANIMATION_TIME,44));
        var before=f.bank.capture();
        var failure=assertThrows(CdShieldPowerup.UnsupportedShieldPowerup.class,()->f.body.run(6));
        assertEquals(CdShieldPowerup.UnsupportedBoundary.ANIMATION_DIRECTORY_ADDRESS,failure.boundary());
        assertEquals(510,failure.value()); assertEquals(7,f.publications.size());
        assertEquals(255,f.bank.state(6).unsignedByte(PREVIOUS_ANIMATION));
        assertEquals(0,f.bank.state(6).unsignedByte(ANIMATION_FRAME));
        assertEquals(255,f.bank.state(6).unsignedByte(ANIMATION_TIME));
        assertInstanceOf(CdObjectAnimation.SelectedReadFailure.class,failure.getCause());
        assertTrue(f.io.isEmpty()); assertOthers(before,f.bank);
    }

    @Test void genuineShieldSpeedFrameAndOperandFaultsRetainOnlyCompletedStores() {
        var cases=List.of(
                new Fault(truncated(18,40),CdShieldPowerup.UnsupportedBoundary.ANIMATION_SPEED_ADDRESS,40,255,0,4),
                new Fault(table(9),CdShieldPowerup.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS,19,9,0,5),
                new Fault(table(2,0xFE),CdShieldPowerup.UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS,20,2,0,5),
                new Fault(table(2,0xFD),CdShieldPowerup.UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS,20,2,0,5),
                new Fault(table(2,0xFE,2),CdShieldPowerup.UnsupportedBoundary.ANIMATION_FRAME_ADDRESS,273,2,254,6));
        for(var c:cases) {
            var f=new Fixture(c.table()); seed(f.bank,state(0,0,0,0));var before=f.bank.capture();
            var failure=assertThrows(CdShieldPowerup.UnsupportedShieldPowerup.class,()->f.body.run(6));
            assertEquals(c.boundary(),failure.boundary());assertEquals(c.address(),failure.value());
            assertEquals(c.timer(),f.bank.state(6).unsignedByte(ANIMATION_TIME));
            assertEquals(c.index(),f.bank.state(6).unsignedByte(ANIMATION_FRAME));
            assertEquals(c.publications(),f.publications.size());assertTrue(f.io.isEmpty());assertOthers(before,f.bank);
        }
    }

    @Test void ffTargetGuardFaultRetainsIndexResetBeforeTheUnperformedRead() {
        var bank=dirtyBank();seed(bank,state(0,0,1,0));var original=new AssertionError("original FF source cut");
        var trace=new Trace(bank){@Override public void before(CdObjectAnimation.Operation op){super.before(op);if(op.kind()==FF_FIRST)throw original;}};
        assertSame(original,assertThrows(AssertionError.class,()->trace.prefix.step(table(3,9,0xFF))));
        assertEquals(List.of("ANIMATION_TIME=255","ANIMATION_TIME=3","ANIMATION_FRAME=0"),trace.stores);
        assertEquals(0,bank.state(6).unsignedByte(ANIMATION_FRAME));assertEquals(0xE7,bank.state(6).unsignedByte(MAP_FRAME));
        assertFalse(trace.prefix.isCurrent(trace.last));
    }

    @Test void negativeCommandsUseOneKernelAndNeverRecursivelyInterpretFrameTargets() {
        for(var selected:List.of(table(3,0xFF),table(3,0xFE,0),table(3,0xFD,255),
                table(3,0xFC),table(3,0xFB),table(3,0xFA),table(3,0x80))) {
            var bank=dirtyBank();var input=state(0,0,0,0);seed(bank,input);var trace=new Trace(bank);
            assertEquals(CdObjectAnimation.step(input,selected),trace.prefix.step(selected));
            assertEquals(1,trace.kinds.stream().filter(k->k==ENTER).count());
            assertEquals(1,trace.kinds.stream().filter(k->k==RETURN).count());
        }
    }

    @Test void issuedReservedTokensRejectReentryForeignScopesThreadsAndCompletedLifetimes()throws Exception {
        var bank=dirtyBank();seed(bank,state(0,0,0,0));var retained=new ArrayList<CdObjectAnimation.Operation>();
        var trace=new Trace(bank){@Override public void before(CdObjectAnimation.Operation op){
            super.before(op);assertTrue(prefix.isCurrent(op));for(var old:retained)assertFalse(prefix.isCurrent(old));
            assertThrows(IllegalStateException.class,()->prefix.step(table(0,1)));
            var problem=new AtomicReference<Throwable>();var other=new Thread(()->{try{assertFalse(prefix.isCurrent(op));
                assertThrows(IllegalStateException.class,()->prefix.step(table(0,1)));}catch(Throwable failure){problem.set(failure);}});
            other.start();try{other.join();}catch(InterruptedException failure){throw new AssertionError(failure);}
            if(problem.get()!=null)throw new AssertionError(problem.get());retained.add(op);
        }};
        trace.prefix.step(table(0,1,0xFF));trace.prefix.step(table(0,1,0xFF));
        var foreign=new Trace(bank);for(var op:retained){assertFalse(trace.prefix.isCurrent(op));assertFalse(foreign.prefix.isCurrent(op));}
    }

    @Test void reservedGuardMutationOfOwnNonAnimationOrNeighborIsRejectedWithoutRollback() {
        for(int whole:new int[]{6,20}) {
            var bank=dirtyBank();seed(bank,state(0,0,0,0));var trace=new Trace(bank){
                @Override public void before(CdObjectAnimation.Operation op){super.before(op);bank.write(bank.state(whole).moveByte(ID,77));}};
            assertThrows(IllegalStateException.class,()->trace.prefix.step(table(0,1)));
            assertEquals(77,bank.state(whole).unsignedByte(ID));assertFalse(trace.prefix.isCurrent(trace.last));
        }
    }

    @Test void originalGuardFailureSurvivesAnUnwindFaultAndRetiresItsOperation() {
        var bank=dirtyBank();seed(bank,state(0,0,0,0));var original=new AssertionError("before");var secondary=new IllegalStateException("after");
        var trace=new Trace(bank){@Override public void before(CdObjectAnimation.Operation op){super.before(op);throw original;}
            @Override public void after(CdObjectAnimation.Operation op){super.after(op);throw secondary;}};
        assertSame(original,assertThrows(AssertionError.class,()->trace.prefix.step(table(0,1))));
        assertArrayEquals(new Throwable[]{secondary},original.getSuppressed());assertTrue(trace.stores.isEmpty());assertFalse(trace.prefix.isCurrent(trace.last));
    }

    @Test void genuineShieldKeepsOriginalPublicationFailureAndHealthyCallerTokenRejection() {
        var f=new Fixture(table(3,0x25));var original=new AssertionError("completed animation store callback");
        f.after=call->{assertThrows(IllegalStateException.class,()->f.body.run(6));
            if(call instanceof CdShieldPowerup.Publication p&&p.output().unsignedByte(MAP_FRAME)==5)throw original;};
        assertSame(original,assertThrows(AssertionError.class,()->f.body.run(6)));
        assertEquals(5,f.bank.state(6).unsignedByte(MAP_FRAME));assertEquals(0,f.bank.state(6).unsignedByte(ANIMATION_FRAME));
        assertTrue(f.io.isEmpty());assertFalse(f.body.isCurrentCall(f.last));assertFalse(f.body.isCurrentPrimitive(f.last));
        assertThrows(IllegalStateException.class,()->f.body.currentResult(f.last));
    }

    @Test void unrelatedPreviouslyIssuedReadFailureIsNeverRelabelledAsThisShieldSourceFault() {
        var other=dirtyBank();seed(other,state(255,255,0,0));var trace=new Trace(other);
        var borrowed=assertThrows(CdObjectAnimation.SelectedReadFailure.class,()->trace.prefix.step(table(0,1)));
        var f=new Fixture(table(3,0x25));f.before=call->{if(call instanceof CdShieldPowerup.Publication p
                &&p.output().unsignedByte(PREVIOUS_ANIMATION)==0)throw borrowed;};
        assertSame(borrowed,assertThrows(CdObjectAnimation.SelectedReadFailure.class,()->f.body.run(6)));
        assertEquals(3,f.publications.size());assertTrue(f.io.isEmpty());
    }

    @Test void rejectedReservedPublicationIsNotRetriedAndRetainsThePreviousActualImage() {
        var bank=dirtyBank();seed(bank,state(0,9,7,5));var before=bank.capture();var original=new AssertionError("owner refused store");
        var calls=new int[1];var guard=new CdObjectAnimation.Guard(){public void before(CdObjectAnimation.Operation op){}public void after(CdObjectAnimation.Operation op){}};
        var prefix=new CdObjectAnimation.Prefix(bank,6,guard,(field,value)->{calls[0]++;throw original;});
        assertSame(original,assertThrows(AssertionError.class,()->prefix.step(table(3,0x25))));
        assertEquals(1,calls[0]);assertEquals(before.slots(),bank.capture().slots());
    }

    @Test void dynamicAdmissionAndLegacyPureNullsStayUnchangedAndReservedJoinRequiresAnOwner() {
        var bank=dirtyBank();var pool=new CdObjectPool();var trace=new Trace(bank);
        assertThrows(IndexOutOfBoundsException.class,()->new CdObjectAnimation.Prefix(pool,6,trace));
        assertThrows(NullPointerException.class,()->new CdObjectAnimation.Prefix(bank,6,null,(field,value)->{}));
        assertThrows(NullPointerException.class,()->new CdObjectAnimation.Prefix(bank,6,trace,null));
        assertEquals("state",assertThrows(NullPointerException.class,()->CdObjectAnimation.step(null,null)).getMessage());
    }

    @Test void realShieldRefusedResetRetainsOnlyCallerStoresWithoutFalseSuppressedCorruption() {
        var f=new Fixture(table(3,0x25));var before=f.bank.capture();var original=f.bank.state(6);
        var expected=original.moveWord(X,0x1234).moveWord(Y,0xFEDC).moveByte(FLAGS,2);
        var wanted=new AssertionError("refused before first animation primitive");var refused=new int[1];
        f.onPrepared=call->{if(call instanceof CdShieldPowerup.Publication p
                &&p.source().unsignedByte(PREVIOUS_ANIMATION)==9&&p.output().unsignedByte(PREVIOUS_ANIMATION)==0){refused[0]++;throw wanted;}};
        assertSame(wanted,assertThrows(AssertionError.class,()->f.body.run(6)));
        assertEquals(expected,f.bank.state(6));assertEquals(3,f.publications.size());assertEquals(1,refused[0]);
        assertTrue(f.io.isEmpty());assertOthers(before,f.bank);assertFalse(f.body.isCurrentCall(f.last));
        assertFalse(f.body.isCurrentPrimitive(f.last));assertThrows(IllegalStateException.class,()->f.body.currentResult(f.last));
        assertArrayEquals(new Throwable[0],wanted.getSuppressed(),"A healthy retained prefix is not corruption");
    }

    @Test void realShieldLandedResetRetainsItsNativeStoreWithoutFalseSuppressedCorruption() {
        var f=new Fixture(table(3,0x25));var before=f.bank.capture();var original=f.bank.state(6);
        var expected=original.moveWord(X,0x1234).moveWord(Y,0xFEDC).moveByte(FLAGS,2).moveByte(PREVIOUS_ANIMATION,0);
        var wanted=new AssertionError("failed after first animation primitive");var landed=new int[1];
        f.onCompleted=call->{if(call instanceof CdShieldPowerup.Publication p
                &&p.source().unsignedByte(PREVIOUS_ANIMATION)==9&&p.output().unsignedByte(PREVIOUS_ANIMATION)==0){landed[0]++;throw wanted;}};
        assertSame(wanted,assertThrows(AssertionError.class,()->f.body.run(6)));
        assertEquals(expected,f.bank.state(6));assertEquals(4,f.publications.size());assertEquals(1,landed[0]);
        assertTrue(f.io.isEmpty());assertOthers(before,f.bank);assertFalse(f.body.isCurrentCall(f.last));
        assertFalse(f.body.isCurrentPrimitive(f.last));assertThrows(IllegalStateException.class,()->f.body.currentResult(f.last));
        assertArrayEquals(new Throwable[0],wanted.getSuppressed());
    }

    private record Store(CdObjectState.ByteField field,int value){}
    private record Fault(CdObjectAnimation.AnimationTable table,CdShieldPowerup.UnsupportedBoundary boundary,
                         int address,int timer,int index,int publications){}
    private static class Trace implements CdObjectAnimation.Guard {
        final CdReservedObjectBank bank;final CdObjectAnimation.Prefix prefix;
        final List<CdObjectAnimation.Kind> kinds=new ArrayList<>();final List<String> stores=new ArrayList<>();
        CdObjectAnimation.Operation last;
        Trace(CdReservedObjectBank bank){this.bank=bank;prefix=new CdObjectAnimation.Prefix(bank,6,this,(field,value)->{
            stores.add(field+"="+value);bank.write(bank.state(6).moveByte(field,value));});}
        public void before(CdObjectAnimation.Operation op){last=op;kinds.add(op.kind());assertEquals(6,op.wholeIndex());assertFalse(op.completed());}
        public void after(CdObjectAnimation.Operation op){assertSame(last,op);}
    }
    private static final class Fixture implements CdShieldPowerup.DispatchObserver,CdShieldPowerup.ReceiptGuard {
        final CdReservedObjectBank bank=dirtyBank();final List<CdReservedObjectBank.Image> publications=new ArrayList<>();
        final List<CdShieldPowerup.ReadField> reads=new ArrayList<>();final List<String> io=new ArrayList<>();
        final int[] values={1,0,0,0x1234,0xFEDC,2,0,0};CdShieldPowerup body;CdShieldPowerup.Call last;
        Consumer<CdShieldPowerup.Call> before=call->{},after=call->{};
        Consumer<CdShieldPowerup.Call> onPrepared=call->{},onCompleted=call->{};
        Fixture(CdObjectAnimation.AnimationTable table){seed(bank,state(0,9,7,5));
            body=CdShieldPowerup.withWorldOwner(bank,new CdTimeStars.Assets(table,0x10203040),field->{reads.add(field);return values[field.ordinal()];},
                new CdShieldPowerup.PowerupPort(){public int read(){io.add("powerup-read");return 2;}public void write(int value){fail("unexpected selector write");}},
                (source,identity)->{assertSame(bank,source);assertEquals(6,identity.wholeIndex());io.add("draw");},this,this);}
        public void beforeOperation(CdShieldPowerup.Call call){last=call;assertTrue(body.isCurrentCall(call));assertFalse(body.isCurrentPrimitive(call));before.accept(call);}
        public void prepared(CdShieldPowerup.Call call){assertSame(last,call);onPrepared.accept(call);}
        public void afterOperation(CdShieldPowerup.Call call){assertSame(last,call);if(call instanceof CdShieldPowerup.Publication p)publications.add(p.output());after.accept(call);}
        public void completed(CdShieldPowerup.Call call){assertSame(last,call);onCompleted.accept(call);}
        public void failedOperation(CdShieldPowerup.Call call,Throwable original){assertSame(last,call);}
    }
    private static CdReservedObjectBank dirtyBank(){var bank=CdReservedObjectBank.clearedAfterLevelStart();
        for(int whole=1;whole<32;whole++){byte[] bytes=new byte[64];for(int at=0;at<64;at++)bytes[at]=(byte)(whole*3+at*7);bank.write(bank.state(whole).withBytes(bytes));}return bank;}
    private static CdObjectAnimation.State state(int a,int p,int i,int t){return new CdObjectAnimation.State(a,p,i,t,0xE7,0xBF,2,2,9);}
    private static void seed(CdReservedObjectBank bank,CdObjectAnimation.State s){bank.write(bank.state(6).moveByte(ID,3).moveByte(ANIMATION,s.animationId())
            .moveByte(PREVIOUS_ANIMATION,s.previousId()).moveByte(ANIMATION_FRAME,s.scriptIndex()).moveByte(ANIMATION_TIME,s.timer())
            .moveByte(MAP_FRAME,s.mapFrame()).moveByte(SPRITE_FLAGS,s.spriteFlags()).moveByte(FLAGS,s.statusFlags()).moveByte(ROUTINE,s.routine()).moveByte(ROUTINE_2,s.routine2()));}
    private static CdObjectAnimation.AnimationTable table(int speed,int...script){byte[] bytes=new byte[19+script.length];
        for(int at=0;at<18;at+=2)bytes[at+1]=18;bytes[18]=(byte)speed;for(int n=0;n<script.length;n++)bytes[19+n]=(byte)script[n];
        return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes),0,9);}
    private static CdObjectAnimation.AnimationTable truncated(int length,int pointer){byte[] bytes=new byte[length];
        for(int at=0;at<18;at+=2){bytes[at]=(byte)(pointer>>>8);bytes[at+1]=(byte)pointer;}return new CdObjectAnimation.AnimationTable(CdAssetBytes.copyOf(bytes),0,9);}
    private static void assertOthers(CdReservedObjectBank.Snapshot before,CdReservedObjectBank bank){for(int whole=1;whole<32;whole++)if(whole!=6)assertEquals(before.slots().get(whole-1),bank.state(whole));}
}
