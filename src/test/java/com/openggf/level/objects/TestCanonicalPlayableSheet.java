package com.openggf.level.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.sprites.animation.*;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.PlayableSheetWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class)
@FullReset
class TestCanonicalPlayableSheet {
    @Test @RequiresRom(SonicGame.SONIC_1)
    void sonic1() throws Exception { verify(new Sonic1GameModule()); }
    @Test @RequiresRom(SonicGame.SONIC_2)
    void sonic2() throws Exception { verify(new Sonic2GameModule()); }
    @Test @RequiresRom(SonicGame.SONIC_3K)
    void sonic3k() throws Exception { verify(new Sonic3kGameModule()); }

    private void verify(GameModule module) throws Exception {
        TestEnvironment.activeGameplayMode();
        var host = module.getDonorCapabilities();
        var nativeArt = host.getPlayerArtProvider(RomByteReader.fromRom(TestEnvironment.currentRom())).loadPlayerSpriteArt("sonic");
        var source = sheet();
        var art = PlayableSheetAnimationBinding.bind(source, host,
                (ScriptedVelocityAnimationProfile) nativeArt.animationProfile());
        for (var pose : List.of(CanonicalAnimation.WALK, CanonicalAnimation.RUN, CanonicalAnimation.ROLL,
                CanonicalAnimation.SPRING, CanonicalAnimation.HURT, CanonicalAnimation.DEATH,
                CanonicalAnimation.PUSH, CanonicalAnimation.DUCK, CanonicalAnimation.LOOK_UP)) {
            var script = art.animationSet().getScript(host.resolveNativeId(pose));
            assertNotNull(script, pose.name());
            assertEquals(source.animationSet().getScript(pose.ordinal()).frames(), script.frames(), pose.name());
        }
        // Every host-published native ID receives a script, including unprovided poses.
        for (var pose : CanonicalAnimation.values()) {
            if (host.resolveNativeId(pose) >= 0)
                assertNotNull(art.animationSet().getScript(host.resolveNativeId(pose)), pose.name());
        }
        assertEquals(source.animationSet().getScript(CanonicalAnimation.WAIT.ordinal()).frames(),
                art.animationSet().getScript(host.resolveNativeId(CanonicalAnimation.BALANCE)).frames());

        var missingRun = new SpriteAnimationSet();
        source.animationSet().getAllScripts().forEach((id, script) -> {
            if (id != CanonicalAnimation.RUN.ordinal()) missingRun.addScript(id, script);
        });
        var sparse = new SpriteArtSet(source.artTiles(), source.mappingFrames(), source.dplcFrames(),
                source.paletteIndex(), source.basePatternIndex(), source.frameDelay(), source.bankSize(),
                source.animationProfile(), missingRun);
        var sparseBound = PlayableSheetAnimationBinding.bind(sparse, host,
                (ScriptedVelocityAnimationProfile) nativeArt.animationProfile());
        var sparseSprite = sprite(sparseBound, module, 0x900);
        sparseSprite.setAngle((byte) 0x20);
        sparseSprite.getAnimationManager().update(0);
        assertTrue(missingRun.getScript(CanonicalAnimation.WAIT.ordinal()).frames().contains(sparseSprite.getMappingFrame()),
                "missing RUN uses WAIT without indexing neighboring slope frames");

        var profile = (ScriptedVelocityAnimationProfile) art.animationProfile();
        var nativeProfile = (ScriptedVelocityAnimationProfile) nativeArt.animationProfile();
        assertEquals(nativeProfile.isAnglePreAdjust(), profile.isAnglePreAdjust());
        assertEquals(nativeProfile.isPushUsesWalkSpecialHandler(), profile.isPushUsesWalkSpecialHandler());
        assertEquals(nativeProfile.getPushDelayShift(), profile.getPushDelayShift());
        assertEquals(nativeProfile.isDuckReleasePublishesWalk(), profile.isDuckReleasePublishesWalk());
        assertEquals(nativeProfile.isAirborneSlidePreservesPublishedAnimation(), profile.isAirborneSlidePreservesPublishedAnimation());
        assertEquals(-1, profile.getBlinkAnimId(), "WAIT fallback cannot serve a native input-locking Blink");
        assertEquals(-1, profile.getGetUpAnimId(), "WAIT fallback cannot serve a native input-locking GetUp");
        int slow = changes(art, module, 0x100);
        int fast = changes(art, module, 0x500);
        assertTrue(fast > slow, "native ground-speed walk timing: " + slow + " / " + fast);
        assertTrue(changes(art, module, 0x900) > 1, "run cycle advances");
        assertTrue(rollChanges(art, module, 0x700) > rollChanges(art, module, 0x100), "native roll speed timing");
        for (int speed : new int[]{0x100, 0x900}) {
            var flat = sprite(art, module, speed);
            flat.setAngle((byte) 0);
            flat.getAnimationManager().update(0);
            var slope = sprite(art, module, speed);
            slope.setAngle((byte) 0x20);
            slope.getAnimationManager().update(0);
            assertNotEquals(flat.getMappingFrame(), slope.getMappingFrame(), "slope selects another mapping set");
            assertNotEquals(art.mappingFrames().get(flat.getMappingFrame()), art.mappingFrames().get(slope.getMappingFrame()));
            var reference = sprite(nativeArt, module, speed);
            var authored = sprite(art, module, speed);
            for (int clock = 0; clock < 40; clock++) {
                reference.getAnimationManager().update(clock);
                authored.getAnimationManager().update(clock);
                assertEquals(reference.getAnimationTick(), authored.getAnimationTick(), "native timer at " + clock);
            }
        }
    }

    private TestablePlayableSprite sprite(SpriteArtSet art, GameModule module, int speed) {
        var sprite = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        sprite.setGameRulesForTest(module.getRules());
        sprite.setAnimationProfile(art.animationProfile());
        sprite.setAnimationSet(art.animationSet());
        sprite.setAnimationFrameCount(art.mappingFrames().size());
        sprite.setGSpeed((short) speed);
        sprite.setAnimationId(module.getDonorCapabilities().resolveNativeId(CanonicalAnimation.WALK));
        return sprite;
    }
    private int changes(SpriteArtSet art, GameModule module, int speed) {
        var sprite = sprite(art, module, speed);
        return countChanges(sprite);
    }
    private int rollChanges(SpriteArtSet art, GameModule module, int speed) {
        var sprite = sprite(art, module, speed);
        sprite.setRolling(true);
        sprite.setAnimationId(module.getDonorCapabilities().resolveNativeId(CanonicalAnimation.ROLL));
        return countChanges(sprite);
    }
    private int countChanges(TestablePlayableSprite sprite) {
        int changes = 0, previous = -1;
        for (int clock = 0; clock < 120; clock++) {
            sprite.getAnimationManager().update(clock);
            if (sprite.getMappingFrame() != previous) changes++;
            previous = sprite.getMappingFrame();
        }
        return changes;
    }
    private SpriteArtSet sheet() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeBytes("GGFS"); out.writeShort(1); out.writeInt(1); out.write(new byte[32]);
            out.writeShort(20);
            for (int f = 0; f < 20; f++) {
                out.writeShort(1); out.writeShort(1);
                out.writeShort(f); out.writeShort(0); out.writeByte(1); out.writeByte(1);
                out.writeInt(0); out.writeByte(0); out.writeByte(0);
            }
            out.writeByte(1); out.writeByte(0); out.write(new byte[32]);
        }
        var frames = new ArrayList<PlayableSheetReader.Frame>();
        for (int f = 0; f < 20; f++) frames.add(new PlayableSheetReader.Frame(0, 0, 1, 1,
                List.of(new PlayableSheetReader.DplcRun(0, 1, 0))));
        var table = new TreeMap<String, PlayableSheetReader.Animation>();
        int index = 0;
        for (var pose : List.of(CanonicalAnimation.WAIT, CanonicalAnimation.WALK, CanonicalAnimation.RUN,
                CanonicalAnimation.ROLL, CanonicalAnimation.SPRING, CanonicalAnimation.HURT,
                CanonicalAnimation.DEATH, CanonicalAnimation.PUSH, CanonicalAnimation.DUCK, CanonicalAnimation.LOOK_UP)) {
            boolean speed = Set.of(CanonicalAnimation.WALK, CanonicalAnimation.RUN, CanonicalAnimation.ROLL, CanonicalAnimation.PUSH).contains(pose);
            var steps = List.of(new PlayableSheetReader.AnimationStep(index++, 1, false),
                    new PlayableSheetReader.AnimationStep(index++, 1, true));
            var slopes = pose == CanonicalAnimation.WALK || pose == CanonicalAnimation.RUN
                    ? List.of(List.of(0, 1), List.of(2, 3), List.of(4, 5), List.of(6, 7)) : List.<List<Integer>>of();
            table.put(pose.name(), new PlayableSheetReader.Animation(steps, speed, SpriteAnimationEndAction.LOOP, "WAIT", slopes));
        }
        return PlayableSheetMaterializer.read(PlayableSheetWriter.write(new PlayableSheetReader.PlayableSheet(
                bytes.toByteArray(), new PlayableSheetReader.Meta(0, 1, 0), frames, table, Map.of()))).art();
    }
}
