package com.openggf.game.soniccd;

import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/** SOURCE ONLY / UNRUN. Real Boot/FULL/loading/World/controller ingress. Slots edited through
 * the existing public pool are explicitly CONSTRUCTED source vectors, not natural placement.
 * No private Ready, capture, producer receipt, Frame state or actor is fabricated. */
@Isolated class TestSonicCdR31aTimePostWorld {
    @TempDir Path directory;final TestSonicCdR31aSelectedProfileLoading fixture=new TestSonicCdR31aSelectedProfileLoading();
    @BeforeEach void setup(){fixture.directory=directory;fixture.setup();}
    @AfterEach void cleanup()throws Exception{fixture.cleanup();}
    private static void post(TestSonicCdR31aSelectedProfileLoading.Booted b,int routine,int contact){
        var p=b.module.r31WorldHost().placement();p.pool().write(p.pool().state(127).moveByte(ID,0x19).moveByte(SUBTYPE,9).moveByte(ROUTINE,routine).moveByte(COLLISION_STATUS,contact).moveWord(X,96).moveWord(Y,1008).moveByte(SAVED_FLAGS_ID,7).moveByte(SPRITE_FLAGS,4));
    }
    @Test void genuineWorldInitUsesTheOriginalSourceAndSupportsClosedFullCourseNextState()throws Exception{
        try(var b=fixture.start(1,0x80)){TestSonicCdR31aSelectedProfileLoading.ready(b);post(b,0,0);TestSonicCdR31aOrdinaryRoute.step(b,0);var h=b.module.r31WorldHost();var s=h.placement().pool().state(127);assertEquals(2,s.routine());assertEquals(0xDF,s.unsignedByte(COLLISION_TYPE));assertEquals(12,s.unsignedByte(MAP_FRAME));assertEquals(32,s.unsignedByte(Y_RADIUS));assertEquals(14,s.unsignedByte(X_RADIUS));assertEquals(0,b.module.requirePlayerFrame().environment().movement().gates().timeWarpDirection());
            var before=TestSonicCdR31aOrdinaryRoute.comparison(b);TestSonicCdR31aOrdinaryRoute.step(b,0);TestSonicCdR31aOrdinaryRoute.verifyReturnedNext(b,0,before);
        }
    }
    @Test void genuineWorldContactPublishesBothFutureStoresThenOneTypedRequest()throws Exception{
        try(var b=fixture.start(1,0x80)){TestSonicCdR31aSelectedProfileLoading.ready(b);post(b,0,1);var h=b.module.r31WorldHost();int old=h.sounds().size();TestSonicCdR31aOrdinaryRoute.step(b,0);assertEquals(1,b.module.requirePlayerFrame().environment().movement().gates().timeWarpDirection());assertEquals(4,h.placement().pool().state(127).routine());var voices=h.sounds().subList(old,h.sounds().size()).stream().filter(SonicCdGameModule.PastTimePostSubCpu.class::isInstance).toList();assertEquals(1,voices.size());assertEquals(CdTimePost.SubCpuCommand.SCMD_FUTURESFX,((SonicCdGameModule.PastTimePostSubCpu)voices.getFirst()).command());
            // Actual next Movement warp remains an owned boundary, never cleared for this test.
        }
    }
    @Test void genuineUnknownRoutineStopsBeforeTheOriginalTimePostHeaderWrites()throws Exception{
        try(var b=fixture.start(1,0x80)){TestSonicCdR31aSelectedProfileLoading.ready(b);post(b,3,0);var placement=b.module.r31WorldHost().placement();var frame=b.module.requirePlayerFrame();var before=placement.pool().state(127);var e=assertThrows(CdTimePost.UnsupportedTimePost.class,()->TestSonicCdR31aOrdinaryRoute.step(b,0));assertEquals(CdTimePost.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertEquals(before,placement.pool().state(127));assertTrue(frame.closed());}
    }
    @Test void finiteFramePreparationDoesNotChangeTheGenuineWorldCut()throws Exception{
        try(var b=fixture.start(1,0x80)){TestSonicCdR31aSelectedProfileLoading.ready(b);var f=b.module.requirePlayerFrame();var before=TestSonicCdR31aSelectedProfileLoading.values(b);var prepared=f.prepareObjectTimePostDirection(f.state(),f.environment(),0,1);assertEquals(0,f.environment().movement().gates().timeWarpDirection());assertEquals(before,TestSonicCdR31aSelectedProfileLoading.values(b));assertNotNull(prepared);}
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
