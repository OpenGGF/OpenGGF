package com.openggf.game.soniccd;
import com.openggf.game.session.SessionManager;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** SOURCE ONLY / UNRUN. Actual named prelude/World; no progress/Frame/capture hydration. */
class TestSonicCdColdProgressCustody {
    final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    @Test void namedColdPreludeOwnsClearFactsBeforeTheLaterDevelopmentBeginLevel()throws Exception{
        try(var egl=TestSonicCdPastResultsRoute.Egl.open()){
            var h=TestSonicCdPastLoadingWorld.opening();assertEquals(1,Collections.frequency(h.module().programServicesState().completedCalls(),SonicCdGameModule.ProgramStartCall.CLEAR_MAIN_VARIABLES));var p=h.module().ipxProgressState();assertEquals(OptionalInt.of(0),p.timeAttackUnlockByte());assertEquals(OptionalInt.of(0),p.titleFlagsByte());assertEquals(OptionalInt.of(0),p.unknownBackupByte());assertEquals(OptionalInt.of(0),p.saveDisabledByte());assertEquals(OptionalInt.of(0),p.savedLevel().word());
        }
    }
    @Test void plainLaterDevelopmentEntryCannotBorrowTheNamedEarlierClear()throws Exception{
        var module=SonicCdGameModule.openingObjectsDevelopmentPlayer();var facts=TestSonicCdPastLoadingWorld.actualField(module,"ipxProgress");var getter=facts.getClass().getDeclaredMethod("timeAttackUnlockByte");getter.setAccessible(true);assertEquals(OptionalInt.empty(),getter.invoke(facts));
    }
    @Test void missingPcmAfterAuthenticatedClearRetainsProducerAndCannotRetryIt()throws Exception{
        var s=TestCdOriginalBootPrefix.source();var partial=new CdGameDataSource(s.release(),s.assets(),s.fmSoundAssets(),Optional.empty(),s.act2Assets(),s.act2PastAssets(),s.act3FutureAssets(),s.r31aAssets(),s.initialSaveAssets(),s.r3PcmSource(),s.systemProgramSource());
        var m=SonicCdGameModule.openingObjectsDevelopmentPlayerFromColdProgramStart(new CdProgramInitialization.ExecutionEnvironment(Optional.of(new CdProgramInitialization.ExecutionRegion(0x80))));SessionManager.openGameplaySession(m,m,partial,null);
        assertThrows(IllegalStateException.class,()->m.createGame(partial));assertEquals(List.of(SonicCdGameModule.ProgramStartCall.CLEAR_MAIN_VARIABLES),m.programServicesState().completedCalls());
        var facts=TestSonicCdPastLoadingWorld.actualField(m,"ipxProgress");var getter=facts.getClass().getDeclaredMethod("timeAttackUnlockByte");getter.setAccessible(true);assertEquals(OptionalInt.of(0),getter.invoke(facts));var before=m.programServicesState();assertThrows(IllegalStateException.class,()->m.createGame(partial));assertSame(before,m.programServicesState());
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
