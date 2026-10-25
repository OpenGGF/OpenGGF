package com.openggf.stageplay.runtime;

import com.openggf.stageplay.StageplayDocument.Facing;

import com.openggf.audio.GameSound;
import com.openggf.audio.rewind.AudioCommand;
import com.openggf.audio.rewind.AudioReplayReason;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.sprites.playable.PlayerAbilityAudio;
import com.openggf.sprites.render.PlayerAttachedEffectArt;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestStageplayDonorEffectsRom {
    private SceneRomLibrary library() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("s3k.rom.path") != null,
                "The donor ability check requires the explicit S3K ROM property");
        EngineServices.current().graphics().initHeadless();
        return SceneRomLibrary.open(Map.of("s2",Path.of(System.getProperty("sonic2.rom.path")),
                "s3k",Path.of(System.getProperty("s3k.rom.path"))));
    }

    @Test void twoSonicDonorsHaveIndependentNativeShieldOwnersArtBanksAndRewind() throws Exception {
        try(var library=library();var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            var scene=scene();
            world.resolve(scene,StageplayTimeline.sample(scene,0));
            var mode=SessionManager.getCurrentGameplayMode();
            var lead=mode.getSpriteManager().getMainPlayable();
            var follower=mode.getSpriteManager().getRegisteredSidekicks().getFirst();
            var first=lead.getInstaShieldObject();var second=follower.getInstaShieldObject();
            assertNotNull(first);assertNotNull(second);assertNotSame(first,second);
            assertTrue(first.isShieldFor(lead,ShieldType.BASIC));assertFalse(first.isShieldFor(follower,ShieldType.BASIC));
            assertTrue(second.isShieldFor(follower,ShieldType.BASIC));assertFalse(second.isShieldFor(lead,ShieldType.BASIC));
            var leadArt=(PlayerAttachedEffectArt)lead.getPowerUpSpawner();
            var followerArt=(PlayerAttachedEffectArt)follower.getPowerUpSpawner();
            assertNotNull(leadArt.instaShieldArt().animationSet());
            assertNotEquals(leadArt.instaShieldRenderer().patternBankBase(),followerArt.instaShieldRenderer().patternBankBase());
            assertNotEquals(lead.getSpriteRenderer().patternBankBase(),leadArt.instaShieldRenderer().patternBankBase());
            assertThrows(IllegalArgumentException.class,()->lead.getPowerUpSpawner().createInstaShield(follower));
            boolean leadAttack=false,followerAttack=false;
            StageplayTimeline.Frame end=null;
            com.openggf.game.rewind.CompositeSnapshot registryState=null;
            com.openggf.level.objects.PerObjectRewindSnapshot objectState=null,followerState=null;
            for(int frame=1;frame<=65;frame++) {
                end=world.resolve(scene,StageplayTimeline.sample(scene,frame));
                leadAttack|=lead.getDoubleJumpFlag()!=0;followerAttack|=follower.getDoubleJumpFlag()!=0;
                if(frame==29) { assertFalse(lead.getAir());assertFalse(follower.getAir()); }
                if(frame==44) assertTrue(com.openggf.sprites.managers.NativeAirAbilityReadiness.canAttempt(lead));
                if(frame==47) assertTrue(com.openggf.sprites.managers.NativeAirAbilityReadiness.canAttempt(follower));
                if(frame==53) {
                    registryState=mode.getRewindRegistry().capture();
                    objectState=((AbstractObjectInstance)lead.getInstaShieldObject()).captureRewindState();
                    followerState=((AbstractObjectInstance)follower.getInstaShieldObject()).captureRewindState();
                }
            }
            assertTrue(leadAttack,"The main donor must enter its native Insta-Shield routine");
            assertTrue(followerAttack,"The follower donor must enter its own native Insta-Shield routine");
            var objects=mode.getLevelManager().getObjectManager();
            var identityContext=objects.captureIdentityContext();
            var identities=identityContext.requireIdentityTable();
            var followerRole=identities.encodePlayer(follower);
            assertEquals(PlayerRefId.sidekick(0),followerRole);
            assertSame(follower,identities.resolvePlayer(followerRole,true));
            var followerShield=(AbstractObjectInstance)follower.getInstaShieldObject();
            var followerShieldId=identities.idFor(followerShield);
            assertNotNull(followerShieldId);
            var captured=objects.rewindSnapshottable().capture();
            var followerEntries=captured.dynamicObjects().stream()
                    .filter(entry->followerShieldId.equals(entry.objectId())).toList();
            assertEquals(1,followerEntries.size(),"The actual follower shield has one captured identity");
            var followerEntry=followerEntries.getFirst();
            assertTrue(followerEntry.rewindableAuxiliary(),"Follower effects need rewind-owned auxiliary identity");
            assertEquals(followerRole,followerEntry.playerOwnerId());
            assertNull(followerEntry.playerOwner(),"Registered owners use a role rather than a legacy incarnation");
            assertEquals(followerShield.getClass().getName(),followerEntry.className());
            var forwardLeadEffectState=((AbstractObjectInstance)lead.getInstaShieldObject())
                    .captureRewindState(identityContext);
            var forwardFollowerEffectState=followerShield.captureRewindState(identityContext);
            var timeline=EngineServices.current().audio().commandTimeline();
            int shieldSound=library.module("s3k").getAudioProfile().getSoundMap().get(GameSound.INSTA_SHIELD);
            long donorSounds=timeline.entries().stream().filter(entry->entry.command() instanceof AudioCommand.PlaySfx sfx
                    && sfx.route()==AudioCommand.SfxRoute.DONOR_SMPS && sfx.sfxId()==shieldSound).count();
            assertEquals(2,donorSounds,"Each forward ability edge emits one donor request");
            world.resolve(scene,StageplayTimeline.sample(scene,65));
            assertEquals(donorSounds,timeline.entries().stream().filter(entry->entry.command() instanceof AudioCommand.PlaySfx sfx
                    && sfx.route()==AudioCommand.SfxRoute.DONOR_SMPS && sfx.sfxId()==shieldSound).count());
            mode.getRewindRegistry().restore(registryState);
            assertTrue(follower.getInstaShieldObject().isShieldFor(follower,ShieldType.BASIC));
            assertEquals(objectState,((AbstractObjectInstance)lead.getInstaShieldObject()).captureRewindState());
            assertEquals(followerState,((AbstractObjectInstance)follower.getInstaShieldObject()).captureRewindState(),
                    "An already-registered auxiliary effect must reconstruct its captured animation and owner");
            assertFalse(follower.getInstaShieldObject().isShieldFor(lead,ShieldType.BASIC));
            var restoredIdentities=objects.captureIdentityContext().requireIdentityTable();
            assertEquals(followerRole,restoredIdentities.encodePlayer(follower));
            assertEquals(followerShieldId,restoredIdentities.idFor((AbstractObjectInstance)follower.getInstaShieldObject()));
            var restoredFollowerEntries=objects.rewindSnapshottable().capture().dynamicObjects().stream()
                    .filter(entry->followerShieldId.equals(entry.objectId())).toList();
            assertEquals(1,restoredFollowerEntries.size());
            var restoredFollowerEntry=restoredFollowerEntries.getFirst();
            assertEquals(followerEntry.className(),restoredFollowerEntry.className());
            assertTrue(restoredFollowerEntry.rewindableAuxiliary());
            assertEquals(followerRole,restoredFollowerEntry.playerOwnerId());
            assertNull(restoredFollowerEntry.playerOwner());
            world.resolve(scene,StageplayTimeline.sample(scene,0));
            int commands=timeline.entryCount();
            assertEquals(end,world.resolve(scene,StageplayTimeline.sample(scene,65)));
            assertEquals(commands,timeline.entryCount(),"Backward seek and silent replay cannot emit old ability sounds");
            var replayedMode=SessionManager.getCurrentGameplayMode();
            var replayedIdentityContext=replayedMode.getLevelManager().getObjectManager().captureIdentityContext();
            assertEquals(forwardLeadEffectState,((AbstractObjectInstance)replayedMode.getSpriteManager()
                    .getMainPlayable().getInstaShieldObject()).captureRewindState(replayedIdentityContext));
            assertEquals(forwardFollowerEffectState,((AbstractObjectInstance)replayedMode.getSpriteManager()
                    .getRegisteredSidekicks().getFirst().getInstaShieldObject()).captureRewindState(replayedIdentityContext));
            world.close();
            assertThrows(IllegalStateException.class,()->PlayerAbilityAudio.play(lead,GameSound.INSTA_SHIELD));
        }
    }

    @Test void scopedRomAbilityAudioUsesDonorSourceAndClosesWithoutChangingWorldSoundBindings() throws Exception {
        try(var library=library()) {
            var audio=EngineServices.current().audio();
            var previous=audio.captureLogicalSnapshot();
            var owner=new StageplayDonorAudio(library,audio,"s2");
            try {
                var sounds=List.of(GameSound.INSTA_SHIELD,GameSound.TAILS_FLYING,GameSound.TAILS_FLY_TIRED,
                        GameSound.SPINDASH_CHARGE,GameSound.SPINDASH_RELEASE,GameSound.JUMP,GameSound.ROLLING,
                        GameSound.SKID,GameSound.GLIDE_LAND,GameSound.GRAB,GameSound.FIRE_ATTACK,
                        GameSound.LIGHTNING_ATTACK,GameSound.BUBBLE_ATTACK);
                for(var sound:sounds) {
                    owner.play("s3k",sound);
                    var command=(AudioCommand.PlaySfx)audio.commandTimeline().entries().getLast().command();
                    assertEquals(AudioCommand.SfxRoute.DONOR_SMPS,command.route());
                    assertEquals("s3k",command.donorGameId());
                    assertEquals(library.module("s3k").getAudioProfile().getSoundMap().get(sound),command.sfxId());
                }
                int count=audio.commandTimeline().entryCount();
                try(var ignored=audio.beginRewindReplay(10,0,AudioReplayReason.SEEK)) { owner.play("s3k",GameSound.INSTA_SHIELD); }
                assertEquals(count,audio.commandTimeline().entryCount());
                assertTrue(audio.captureLogicalSnapshot().donorGameIds().contains("s3k"));
            } finally { owner.close(); }
            assertEquals(previous.donorGameIds(),audio.captureLogicalSnapshot().donorGameIds());
            assertEquals(previous.donorBindings(),audio.captureLogicalSnapshot().donorBindings());
            owner.close();
            assertThrows(IllegalStateException.class,()->owner.play("s3k",GameSound.INSTA_SHIELD));
        }
    }

    @Test void followerShieldCompletesItsStructuralParentWithoutWritingThePassedMainPlayer() throws Exception {
        try(var library=library();var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode())) {
            var scene=scene();
            world.resolve(scene,StageplayTimeline.sample(scene,0));
            var mode=SessionManager.getCurrentGameplayMode();
            var lead=mode.getSpriteManager().getMainPlayable();
            var follower=mode.getSpriteManager().getRegisteredSidekicks().getFirst();
            var captured=mode.getRewindRegistry().capture();
            lead.setDoubleJumpFlag(1);follower.setDoubleJumpFlag(1);
            var effect=follower.getInstaShieldObject();effect.triggerAttack();
            // The production object walk passes the primary player to every routine.
            // Ani_InstaShield's delay-zero script must complete the effect's own parent.
            for(int tick=0;tick<32 && follower.getDoubleJumpFlag()==1;tick++) effect.update(tick,lead);
            assertEquals(2,follower.getDoubleJumpFlag());
            assertEquals(1,lead.getDoubleJumpFlag(),"A follower's attached routine cannot write the lead's state");
            mode.getRewindRegistry().restore(captured);
            assertEquals(0,lead.getDoubleJumpFlag());assertEquals(0,follower.getDoubleJumpFlag());
        }
    }

    @Test void cueAndPerformerSourceLeasesCanCloseInEitherOrder() throws Exception {
        try(var library=library()) {
            var audio=EngineServices.current().audio();
            var original=audio.captureLogicalSnapshot();
            for(boolean cueFirst:List.of(false,true)) {
                var actor=library.acquireAudioSource(audio,"s3k");
                var cue=library.acquireAudioSource(audio,"s3k");
                try {
                    assertTrue(actor.active());assertTrue(cue.active());
                    if(cueFirst) cue.close();else actor.close();
                    assertTrue(audio.captureLogicalSnapshot().donorGameIds().contains("s3k"));
                    if(cueFirst) actor.close();else cue.close();
                    assertEquals(original.donorGameIds(),audio.captureLogicalSnapshot().donorGameIds());
                    assertEquals(original.donorBindings(),audio.captureLogicalSnapshot().donorBindings());
                } finally { actor.close();cue.close(); }
            }
        }
    }

    @Test void retiredSameGameAndCrossGameProducerLeasesCannotOverwriteTheNewSource() throws Exception {
        try(var library=library()) {
            var audio=EngineServices.current().audio();
            for(String nextGame:List.of("s2","s3k")) {
                var actor=library.acquireAudioSource(audio,"s3k");
                var cue=library.acquireAudioSource(audio,"s3k");
                replaceProducer(audio,library,nextGame);
                var fresh=library.acquireAudioSource(audio,"s3k");
                try {
                    assertFalse(actor.active());assertFalse(cue.active());assertTrue(fresh.active());
                    actor.close();cue.close();
                    assertTrue(fresh.active());
                    assertTrue(audio.captureLogicalSnapshot().donorGameIds().contains("s3k"));
                    fresh.close();
                    assertTrue(audio.captureLogicalSnapshot().donorGameIds().isEmpty());
                    actor.close();cue.close();
                    assertTrue(audio.captureLogicalSnapshot().donorGameIds().isEmpty());
                } finally { fresh.close();actor.close();cue.close(); }
            }
        }
    }

    @Test void abandoningOneCurrentEpochLeaseLeavesItsCoOwnerActiveAndRestoresPriorRouteOnFinalClose() throws Exception {
        try(var library=library()) {
            var audio=EngineServices.current().audio();
            var original=audio.captureLogicalSnapshot();
            var first=library.acquireAudioSource(audio,"s3k");
            var remaining=library.acquireAudioSource(audio,"s3k");
            try {
                first.abandon();
                assertFalse(first.active());assertTrue(remaining.active());
                int id=library.module("s3k").getAudioProfile().getSoundMap().get(GameSound.SPINDASH_CHARGE);
                audio.playDonorSfx("s3k",id);assertDonorCommand(audio,"s3k",id);
                remaining.close();
                assertEquals(original.donorGameIds(),audio.captureLogicalSnapshot().donorGameIds());
                assertEquals(original.donorBindings(),audio.captureLogicalSnapshot().donorBindings());
            } finally { first.close();remaining.close(); }
        }
    }

    @Test void authoredCuesUseTheActualCurrentProducerAcrossSameGameAndCrossGameShots() throws Exception {
        try(var library=library()) {
            var audio=EngineServices.current().audio();
            replaceProducer(audio,library,"s2");
            int s2Jump=library.module("s2").getAudioProfile().getSoundMap().get(GameSound.JUMP);
            int s3Jump=library.module("s3k").getAudioProfile().getSoundMap().get(GameSound.JUMP);
            var document=StageplayDocument.empty("s2",0,0,0,500,320,224).withAudio(List.of(
                    new AudioCue("first",0,AudioKind.ROM_SFX,"s3k",s3Jump,"",1),
                    new AudioCue("cross",60,AudioKind.ROM_SFX,"s2",s2Jump,"",1),
                    new AudioCue("same",120,AudioKind.ROM_SFX,"s2",s2Jump,"",1)));
            try(var cues=new StageplayAudio(audio,library,path->{throw new AssertionError("No external assets");})) {
                cues.prepare(document);cues.playFrame(document,0);
                assertDonorCommand(audio,"s3k",s3Jump);
                replaceProducer(audio,library,"s3k");
                cues.refreshAfterNativeBoundary(60);cues.playFrame(document,60);
                assertDonorCommand(audio,"s2",s2Jump);
                replaceProducer(audio,library,"s3k");
                cues.refreshAfterNativeBoundary(120);cues.playFrame(document,120);
                assertDonorCommand(audio,"s2",s2Jump);
            }
            assertTrue(audio.captureLogicalSnapshot().donorGameIds().isEmpty());
        }
    }

    @Test void transportedLibraryRetainsTheExplicitOpeningGameInsteadOfSortingItsCastSources() throws Exception {
        try(var library=library();var transported=SceneRomLibrary.openReaders(Map.of(
                "s2",com.openggf.data.RomByteReader.fromRom(library.sourceRom("s2")),
                "s3k",com.openggf.data.RomByteReader.fromRom(library.sourceRom("s3k"))),"s3k")) {
            assertEquals("s3k",transported.activeGame());
            assertEquals(library.rom("s2").romSha1(),transported.rom("s2").romSha1());
            assertEquals(library.rom("s3k").romSha1(),transported.rom("s3k").romSha1());
        }
    }

    @Test void libraryCleanupAttemptsEveryIndependentSourceAndClosesRomsWhenAudioRestoreFails() throws Exception {
        try(var library=library()) {
            var audio=EngineServices.current().audio();
            var originalBackend=audio.getBackend();
            audio.clearDonorAudio();
            var s2=library.sourceRom("s2");var s3=library.sourceRom("s3k");
            audio.setBackend(new com.openggf.audio.NullAudioBackend() {
                @Override public void registerAudioProfileCoordHandlers(com.openggf.audio.GameAudioProfile profile) {
                    if(profile==null) throw new IllegalStateException("test donor route restore failure");
                }
            });
            try {
                var first=library.acquireAudioSource(audio,"s2");
                var second=library.acquireAudioSource(audio,"s3k");
                var failure=assertThrows(IllegalStateException.class,library::close);
                assertEquals(1,failure.getSuppressed().length,"Both independent scopes must attempt restore");
                assertTrue(audio.captureLogicalSnapshot().donorGameIds().isEmpty());
                assertFalse(first.active());assertFalse(second.active());
                assertFalse(s2.isOpen());assertFalse(s3.isOpen());
            } finally { audio.setBackend(originalBackend); }
        }
    }

    private static void replaceProducer(com.openggf.audio.AudioManager audio,SceneRomLibrary library,String game) {
        // AbstractLevelInitProfile resets audio, then configureAudio installs a fresh
        // stock module/profile even when two Stageplay shots use the same game.
        audio.resetState();
        audio.setRom(library.sourceRom(game));
        var module=com.openggf.game.GameModuleRegistry.createStockModule(com.openggf.game.GameId.fromCode(game));
        audio.setAudioProfile(module.getAudioProfile());
        audio.setSoundMap(module.getAudioProfile().getSoundMap());
    }

    private static void assertDonorCommand(com.openggf.audio.AudioManager audio,String game,int id) {
        var command=(AudioCommand.PlaySfx)audio.commandTimeline().entries().getLast().command();
        assertEquals(AudioCommand.SfxRoute.DONOR_SMPS,command.route());
        assertEquals(game,command.donorGameId());assertEquals(id,command.sfxId());
    }

    private static StageplayDocument scene() {
        var cast=List.of(new Actor("lead","Lead","s3k","sonic",List.of(new ActorKey(0,96,655,0,-1, Facing.RIGHT,true,1,Interpolation.HOLD))),
                new Actor("follower","Follower","s3k","sonic",List.of(new ActorKey(0,128,655,0,-1, Facing.RIGHT,true,1,Interpolation.HOLD))));
        return new StageplayDocument(VERSION,"Donor effects",90,320,224,
                new SetSource("s2",0,0,"",""),cast,
                List.of(new CameraKey(0,0,500,1,Interpolation.HOLD)),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),
                List.of(new PuppetTrack("lead",true,0,List.of(new PuppetCommand("jump",30,PuppetCommandKind.JUMP,0,0,0,8),
                                new PuppetCommand("shield",45,PuppetCommandKind.JUMP,0,0,0,1))),
                        new PuppetTrack("follower",true,0,List.of(new PuppetCommand("jump2",30,PuppetCommandKind.JUMP,0,0,0,8),
                                new PuppetCommand("shield2",48,PuppetCommandKind.JUMP,0,0,0,1)))));
    }
}
