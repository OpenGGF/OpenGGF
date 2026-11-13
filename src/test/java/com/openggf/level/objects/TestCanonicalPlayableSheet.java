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

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void completeRawTumbleBankEnablesNativeSelectorOnlyWhenAllPhasesExist() throws Exception {
        TestEnvironment.activeGameplayMode();
        var host = new Sonic3kGameModule().getDonorCapabilities();
        var nativeArt = host.getPlayerArtProvider(RomByteReader.fromRom(TestEnvironment.currentRom()))
                .loadPlayerSpriteArt("sonic");
        var nativeProfile = (ScriptedVelocityAnimationProfile) nativeArt.animationProfile();
        var source = sheet();
        var aliases = new HashMap<String, Integer>();
        int base = nativeProfile.getTumbleFrameBase();
        for (int i = 0; i < 12; i++) aliases.put(host.getNativeFramePoses().get(base + i).key(), 3);
        for (boolean complete : List.of(true, false)) {
            if (!complete) aliases.remove(host.getNativeFramePoses().get(base + 11).key());
            var authored = new SpriteArtSet(source.artTiles(), source.mappingFrames(), source.dplcFrames(),
                    source.paletteIndex(), source.basePatternIndex(), source.frameDelay(), source.bankSize(),
                    new PlayableSheetAnimationBinding.CanonicalProfile(Map.of(), aliases), source.animationSet());
            var bound = PlayableSheetAnimationBinding.bind(authored, host, nativeProfile);
            var profile = (ScriptedVelocityAnimationProfile) bound.animationProfile();
            assertEquals(complete, profile.hasTumbleFrames());
            if (complete) assertEquals(base, profile.getTumbleFrameBase());
        }
    }

    @Test @RequiresRom(SonicGame.SONIC_2)
    void tailsObjectAliasesS2() throws Exception { verifyTailsObjectAliases(new Sonic2GameModule()); }

    @Test @RequiresRom(SonicGame.SONIC_3K)
    void tailsObjectAliasesS3k() throws Exception { verifyTailsObjectAliases(new Sonic3kGameModule()); }

    private void verifyTailsObjectAliases(GameModule module) throws Exception {
        TestEnvironment.activeGameplayMode();
        var host = module.getDonorCapabilities().forPlayableCharacter(PlayerCharacter.TAILS_ALONE);
        var nativeArt = host.getPlayerArtProvider(RomByteReader.fromRom(TestEnvironment.currentRom()))
                .loadPlayerSpriteArt("tails");
        assertSame(nativeArt, PlayableSheetAnimationBinding.bind(nativeArt, host, nativeArt),
                "native Tails keeps its original mappings, scripts and profile");
        var source = sheet();
        var alias = host.getNativeFramePoses().get(1);
        var authored = new SpriteArtSet(source.artTiles(), source.mappingFrames(), source.dplcFrames(),
                source.paletteIndex(), source.basePatternIndex(), source.frameDelay(), source.bankSize(),
                new PlayableSheetAnimationBinding.CanonicalProfile(Map.of(), Map.of(alias.key(), 3)),
                source.animationSet());
        var bound = PlayableSheetAnimationBinding.bind(authored, host, nativeArt);
        assertSame(source.mappingFrames().get(3), bound.mappingFrames().get(1));
        assertSame(source.dplcFrames().get(3), bound.dplcFrames().get(1));
        int idle = source.animationSet().getScript(CanonicalAnimation.WAIT.ordinal()).frames().getFirst();
        assertFalse(host.getNativeFramePoses().containsKey(255));
        assertSame(source.mappingFrames().get(idle), bound.mappingFrames().get(255),
                "unknown native byte safely uses creator WAIT, never an accidental packed frame");
        assertSame(source.dplcFrames().get(idle), bound.dplcFrames().get(255));
        var missingRoll = host.getNativeFramePoses().entrySet().stream()
                .filter(entry -> entry.getValue().fallback() == CanonicalAnimation.ROLL)
                .findFirst().orElseThrow();
        int roll = source.animationSet().getScript(CanonicalAnimation.ROLL.ordinal()).frames().getFirst();
        assertSame(source.mappingFrames().get(roll), bound.mappingFrames().get(missingRoll.getKey()),
                "known unaliased pose uses its descriptor's canonical fallback");
        assertTrue(bound.mappingFrames().getFirst().pieces().isEmpty(), "native zero is invisible");
        assertTrue(bound.animationSet().getScript(host.resolveNativeId(CanonicalAnimation.WAIT))
                .frames().stream().allMatch(frame -> frame >= 256), "authored scripts are relocated");
    }

    private void verify(GameModule module) throws Exception {
        TestEnvironment.activeGameplayMode();
        var host = module.getDonorCapabilities();
        var nativeArt = host.getPlayerArtProvider(RomByteReader.fromRom(TestEnvironment.currentRom())).loadPlayerSpriteArt("sonic");
        var source = sheet();
        verifyEndings(source, module, nativeArt);
        if (host.hasSidekick()) verifyEndings(source, module, host.getPlayerArtProvider(
                RomByteReader.fromRom(TestEnvironment.currentRom())).loadPlayerSpriteArt("tails"));
        assertSame(nativeArt, PlayableSheetAnimationBinding.bind(nativeArt, host,
                (ScriptedVelocityAnimationProfile) nativeArt.animationProfile()), "stock art is untouched");
        var aliasKey = host.getNativeFramePoses().entrySet().iterator().next();
        var aliasSource = new SpriteArtSet(source.artTiles(), source.mappingFrames(), source.dplcFrames(),
                source.paletteIndex(), source.basePatternIndex(), source.frameDelay(), source.bankSize(),
                new PlayableSheetAnimationBinding.CanonicalProfile(Map.of(), Map.of(aliasKey.getValue().key(), 3)), source.animationSet());
        var aliasBound = PlayableSheetAnimationBinding.bind(aliasSource, host, (ScriptedVelocityAnimationProfile) nativeArt.animationProfile());
        assertEquals(source.mappingFrames().get(3), aliasBound.mappingFrames().get(aliasKey.getKey()));
        assertTrue(aliasBound.mappingFrames().getFirst().pieces().isEmpty(), "native zero stays invisible");
        assertTrue(aliasBound.dplcFrames().getFirst().requests().isEmpty());
        assertEquals(source.dplcFrames().get(3), aliasBound.dplcFrames().get(aliasKey.getKey()));
        assertEquals(source.animationSet().getScript(CanonicalAnimation.WAIT.ordinal()).frames().stream().map(f -> f + 256).toList(),
                aliasBound.animationSet().getScript(host.resolveNativeId(CanonicalAnimation.WAIT)).frames());

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

    private void verifyEndings(SpriteArtSet source, GameModule module, SpriteArtSet nativeArt) {
        var host = module.getDonorCapabilities();
        int waitId = host.resolveNativeId(CanonicalAnimation.WAIT);
        int rewind = nativeArt.animationSet().getScript(waitId).endParam();
        int delay = nativeArt.animationSet().getScript(waitId).delay();
        assertTrue(Set.of(2, 6, 28, 53).contains(rewind));
        var scripts = new SpriteAnimationSet();
        source.animationSet().getAllScripts().forEach(scripts::addScript);
        var frames = new ArrayList<Integer>();
        for (int i = 0; i < rewind + 3; i++) frames.add(i < 3 ? 0 : 1);
        scripts.addScript(CanonicalAnimation.WAIT.ordinal(), new SpriteAnimationScript(delay, frames,
                SpriteAnimationEndAction.LOOP_BACK, rewind));
        scripts.addScript(CanonicalAnimation.BLINK.ordinal(), new SpriteAnimationScript(1, List.of(2, 3),
                SpriteAnimationEndAction.HOLD, 0));
        scripts.addScript(CanonicalAnimation.GET_UP.ordinal(), new SpriteAnimationScript(3, List.of(4),
                SpriteAnimationEndAction.SWITCH, CanonicalAnimation.WALK.ordinal()));
        var authored = new SpriteArtSet(source.artTiles(), source.mappingFrames(), source.dplcFrames(),
                source.paletteIndex(), source.basePatternIndex(), source.frameDelay(), source.bankSize(),
                source.animationProfile(), scripts);
        var bound = PlayableSheetAnimationBinding.bind(authored, host,
                (ScriptedVelocityAnimationProfile) nativeArt.animationProfile());
        assertEquals(rewind, bound.animationSet().getScript(waitId).endParam());
        var idle = sprite(bound, module, 0);
        idle.setAnimationId(waitId);
        for (int clock = 0; clock <= (rewind + 3) * (delay + 1); clock++) {
            idle.getAnimationManager().update(clock);
            assertEquals(clock < 3 * (delay + 1) ? 0 : 1, idle.getMappingFrame(), "WAIT at " + clock);
        }
        assertEquals(4, idle.getAnimationFrameIndex(), "FE publishes first tail entry immediately");
        for (var key : List.of(CanonicalAnimation.BLINK, CanonicalAnimation.GET_UP)) {
            int id = host.resolveNativeId(key);
            if (id < 0) continue;
            var finite = sprite(bound, module, 0);
            finite.setAnimationId(id);
            // External animation ownership: hosts without S2 Sonic's deep-idle
            // interrupt otherwise publish WAIT before executing this script.
            finite.setMoveLockTimer(32);
            for (int clock = 0; clock < 4; clock++) {
                finite.getAnimationManager().update(clock);
                assertEquals(id, finite.getAnimationId());
            }
            finite.getAnimationManager().update(4);
            if (key == CanonicalAnimation.BLINK) {
                for (int clock = 5; clock < 24; clock++) {
                    finite.getAnimationManager().update(clock);
                    assertEquals(3, finite.getMappingFrame());
                    assertEquals(id, finite.getAnimationId());
                }
            } else {
                assertEquals(host.resolveNativeId(CanonicalAnimation.WALK), finite.getAnimationId());
                assertEquals(4, finite.getMappingFrame(), "FD defers target frame until next update");
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
