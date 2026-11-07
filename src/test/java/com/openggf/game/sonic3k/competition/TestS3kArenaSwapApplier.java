package com.openggf.game.sonic3k.competition;

import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.mods.run.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kArenaSwapApplier {
    private void enabled(HostedArenaFixture fixture,boolean enabled) {
        var base = GameplayRunPolicy.competition();
        fixture.host.world().context().beginGameplayRun(new GameplayRunPolicy(false,false,
                base.actCompletion(),false,false,base.deathRestart(),false,false,
                MonitorRule.COMPETITION_ITEMS,enabled),0,0);
    }
    private SwapTarget target(S3kCompetitionPlayer player,int kind,int timer) {
        return new SwapTarget(player.centreX(),player.centreY(),-0x800,0x400,true,false,
                7,true,kind,timer,15);
    }

    @Test void nativeBreakRequestsWithoutChangingShoesAndSignalRestores() throws Exception {
        try (var fixture = new HostedArenaFixture()) {
            var arena = new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(0,"sonic",true),()->{});
            enabled(fixture,true);
            var session = fixture.host.session();
            session.applyImmediateItem(0,0);
            var before = session.capture();
            session.applyImmediateItem(0,2);
            assertEquals(before,session.capture(),"the request must not run the native solo swap first");
            var applier = (S3kArenaSwapApplier)arena.raceSwaps();
            var requested = applier.capture();
            assertEquals(RaceSignal.SWAP_REQUEST,applier.consumeSignal());
            assertEquals(RaceSignal.NONE,applier.consumeSignal());
            applier.restore(requested);
            assertEquals(RaceSignal.SWAP_REQUEST,applier.consumeSignal());
        }
    }

    @Test void applyChangesExactlyTheStreamedShoeFields() throws Exception {
        try (var fixture = new HostedArenaFixture()) {
            var arena = new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(0,"sonic",true),()->{});
            enabled(fixture,true);
            var session = fixture.host.session();
            var player = session.player(0);
            session.signalGo(); player.setObjectControl(0);
            player.setStatusSecondary(0x22); player.setSpeedShoesTimer(11);
            var before = player.capture();
            var event = new RaceEvent.SwapApply(7,target(player,1,23));
            assertEquals(RaceEventResult.Outcome.APPLIED,arena.raceSwaps().apply(event,fixture.host.world().context()).outcome());
            var expected = S3kCompetitionPlayer.recreate(before);
            expected.setStatusSecondary(before.statusSecondary()|4);
            expected.setSpeedShoesTimer(23);
            expected.setSpeeds(0xC00,0x18,0x80);
            assertEquals(expected.capture(),player.capture(),"all unstreamed native fields must stay local");
            var second = new RaceEvent.SwapApply(8,target(player,2,17));
            assertEquals(RaceEventResult.Outcome.APPLIED,arena.raceSwaps().apply(second,fixture.host.world().context()).outcome());
            assertEquals(new S3kCompetitionPlayer.Speeds(0x300,8,0x20),player.speeds());
            assertEquals(17,player.speedShoesTimer());
            var none = new RaceEvent.SwapApply(9,target(player,0,0));
            assertEquals(RaceEventResult.Outcome.APPLIED,arena.raceSwaps().apply(none,fixture.host.world().context()).outcome());
            assertEquals(0,player.statusSecondary()&4);
            assertEquals(before.baseSpeeds(),player.speeds());
        }
    }

    @Test void fallbackMatchesNativeEmptySlotAndDeadFallbackDoesNothing() throws Exception {
        try (var fixture = new HostedArenaFixture()) {
            var arena = new S3kHostedArenaSession(fixture.host,new HostedArenaRequest(0,"sonic",true),()->{});
            var session = fixture.host.session();
            session.applyImmediateItem(0,0);
            var before = session.capture();
            enabled(fixture,false);
            session.applyImmediateItem(0,2);
            var nativeSolo = session.capture();
            session.restore(before); enabled(fixture,true);
            var result = arena.raceSwaps().apply(new RaceEvent.SwapFallback(10),fixture.host.world().context());
            assertEquals(RaceEventResult.Outcome.FALLBACK,result.outcome());
            assertEquals(nativeSolo,session.capture());
            session.restore(before); session.player(0).kill();
            var dead = session.capture();
            arena.raceSwaps().apply(new RaceEvent.SwapFallback(11),fixture.host.world().context());
            assertEquals(dead,session.capture());
        }
    }
}
