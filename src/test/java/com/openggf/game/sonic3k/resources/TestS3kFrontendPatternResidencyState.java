package com.openggf.game.sonic3k.resources;

import com.openggf.game.GameDataSource;
import com.openggf.game.internal.FrontendPatternHandoffAccess;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.Pattern;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure native-pattern resource contracts; actual frontend ROM admission is tested separately. */
class TestS3kFrontendPatternResidencyState {
    @AfterEach void cleanup(){TestEnvironment.resetAll();}
    private S3kFrontendPatternResidency owner(){
        TestEnvironment.configureGameModuleFixture(new Sonic3kGameModule());
        return S3kRuntimeArtCoordinator.current().frontendPatternResidency();
    }
    private Pattern pattern(int packed){var p=new Pattern();byte[] bytes=new byte[32];java.util.Arrays.fill(bytes,(byte)packed);p.fromSegaFormat(bytes);return p;}
    @Test void uploadAndLiteralZeroFillRetainUnwrittenNativeBytesAndDefensiveCopies(){
        var owner=owner();owner.upload(10,new Pattern[]{pattern(0x12),pattern(0x34)});
        owner.clearRange(10*32+8,4);var bytes=owner.handoff();
        for(int i=0;i<32;i++)assertEquals(i>=8&&i<12?0:0x12,bytes[10*32+i]&255);
        for(int i=0;i<32;i++)assertEquals(0x34,bytes[11*32+i]&255);
        bytes[11*32]=0;assertEquals(0x34,owner.handoff()[11*32]&255);
        var saved=owner.capture();var defensive=saved.packed();defensive[11*32]=0;
        assertEquals(0x34,owner.capture().packed()[11*32]&255);
        assertThrows(IllegalArgumentException.class,()->owner.upload(0x800,new Pattern[]{pattern(0x11)}));
        assertTrue(RewindSnapshotDiff.diffKey("frontend",saved,owner.capture()).isEmpty());
    }
    @Test void matchingConstructionGenerationRestoresBothDirectionsAndRetiresOnlyAtInstall(){
        var owner=owner();var empty=owner.capture();owner.upload(10,new Pattern[]{pattern(0x12)});
        var before=owner.capture();var seed=owner.seed();owner.upload(10,new Pattern[]{pattern(0x34)});var later=owner.capture();
        assertThrows(IllegalStateException.class,()->S3kFrontendPatternResidency.validate(seed));
        for(int cycle=0;cycle<2;cycle++){
            owner.restore(before);assertTrue(RewindSnapshotDiff.diffKey("frontend",before,owner.capture()).isEmpty());
            S3kFrontendPatternResidency.validate(seed);S3kFrontendPatternResidency.installed(seed);assertNull(owner.handoff());
            owner.restore(later);assertTrue(RewindSnapshotDiff.diffKey("frontend",later,owner.capture()).isEmpty());
            assertThrows(IllegalStateException.class,()->S3kFrontendPatternResidency.validate(seed));
            owner.restore(empty);assertNull(owner.handoff());owner.restore(before);
        }
        owner.resetForMissingSnapshot();assertNull(owner.handoff());assertEquals(0,owner.capture().generation());
    }
    @Test void identicalGenerationAndBytesInADetachedOwnerDoNotAuthorizePreparedPublication(){
        var old=owner();old.upload(10,new Pattern[]{pattern(0x12)});var saved=old.capture();var seed=old.seed();
        var current=owner();current.inherit(saved.packed());
        old.restore(saved); // An old retained alias can still hold perfectly matching values.
        assertEquals(saved.generation(),current.capture().generation());
        assertArrayEquals(saved.packed(),current.capture().packed());
        assertThrows(IllegalStateException.class,()->S3kFrontendPatternResidency.validate(seed),
                "only the actual current resource owner may authorize publication");
    }
    @Test void menuHandoffCopiesOnlyTheExactReusedSourceIntoASupportedNewWorld(){
        TestEnvironment.resetAll();var module=new Sonic3kGameModule();var source=mock(GameDataSource.class);
        var old=SessionManager.openGameplaySession(module,module,source,null);
        var owner=S3kRuntimeArtCoordinator.from(old.runtimeArtCoordinator()).frontendPatternResidency();
        owner.upload(10,new Pattern[]{pattern(0x12)});var ticket=FrontendPatternHandoffAccess.capture(old);
        var next=SessionManager.openGameplaySession(module,module,source,null);
        assertNull(FrontendPatternHandoffAccess.capture(next));FrontendPatternHandoffAccess.transfer(ticket,next);
        assertEquals(0x12,S3kRuntimeArtCoordinator.from(next.runtimeArtCoordinator()).frontendPatternResidency().handoff()[320]&255);
        var unrelated=SessionManager.openGameplaySession(module,module,mock(GameDataSource.class),null);
        assertThrows(IllegalStateException.class,()->FrontendPatternHandoffAccess.transfer(ticket,unrelated));
        assertNull(FrontendPatternHandoffAccess.capture(unrelated));
        var wrongCoordinator=mock(com.openggf.game.RuntimeArtCoordinator.class,withSettings().extraInterfaces(
                com.openggf.game.internal.FrontendPatternHandoffOwner.class));
        var wrongOwner=(com.openggf.game.internal.FrontendPatternHandoffOwner)wrongCoordinator;
        when(wrongOwner.frontendPatternFormat()).thenReturn(ticket.format());
        var wrongModule=new Sonic3kGameModule(){
            @Override public com.openggf.game.RuntimeArtCoordinator createRuntimeArtCoordinator(
                    com.openggf.game.timing.HardwareTimingService timing){return wrongCoordinator;}
        };
        var differentOwner=SessionManager.openGameplaySession(wrongModule,wrongModule,source,null);
        assertThrows(IllegalStateException.class,()->FrontendPatternHandoffAccess.transfer(ticket,differentOwner),
                "an unrelated owner cannot acquire a ticket merely by declaring the same byte format");
        verify(wrongOwner,never()).acceptFrontendPatterns(any());
        var unsupported=SessionManager.openGameplaySession(new Sonic2GameModule(),new Sonic2GameModule(),source,null);
        assertThrows(IllegalStateException.class,()->FrontendPatternHandoffAccess.transfer(ticket,unsupported));
    }
}
