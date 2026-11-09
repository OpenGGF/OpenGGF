package com.openggf.level.objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.data.RomByteReader;
import com.openggf.game.*;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.sprites.animation.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.modsdk.GgfModCli;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Purpose: inventory actual ROM scripts/mappings and production canonical-sheet resolution.
 * Inputs: verified host ROM, example packed PNG/YAML; output directory via art.audit.output.
 * Origin: original-character art audit requested 2026-11-09. No decoded ROM pixels are written.
 * Limits: frame publication sampled for 512 clocks at four slopes/two speeds; this is not a
 * route test. TALON requests the host's native Tails vocabulary; MICA retains Sonic's.
 * Raw targets report actual relocated alias binding. Presentation banks are separate.
 */
@ExtendWith(SingletonResetExtension.class) @FullReset
class TestCharacterArtCoverageAudit {
    @TempDir Path temporary;
    @Test @RequiresRom(SonicGame.SONIC_1) void s1() throws Exception { audit("S1",new Sonic1GameModule(),"mica","sonic"); }
    @Test @RequiresRom(SonicGame.SONIC_2) void s2() throws Exception { audit("S2",new Sonic2GameModule(),"mica","sonic"); audit("S2",new Sonic2GameModule(),"talon","tails"); }
    @Test @RequiresRom(SonicGame.SONIC_3K) void s3k() throws Exception { audit("S3K",new Sonic3kGameModule(),"mica","sonic"); audit("S3K",new Sonic3kGameModule(),"talon","tails"); }

    private void audit(String game, GameModule module, String character, String nativeCharacter) throws Exception {
        TestEnvironment.activeGameplayMode();
        Path project=Path.of("examples/mica-and-talon/src/main/mod"), encoded=temporary.resolve(character+".ggfp");
        assertEquals(0,GgfModCli.run(new String[]{"convert","art","--playable","--image",project.resolve(character+".png").toString(),
                "--sheet",project.resolve(character+"-sheet.yaml").toString(),"--out",encoded.toString()},System.out));
        var bytes=Files.readAllBytes(encoded);
        var sheet=PlayableSheetReader.read(bytes);
        var source=PlayableSheetMaterializer.read(bytes).art();
        var host=module.getDonorCapabilities().forPlayableCharacter(nativeCharacter.equals("tails") ? PlayerCharacter.TAILS_ALONE : PlayerCharacter.SONIC_ALONE);
        var nativeArt=host.getPlayerArtProvider(RomByteReader.fromRom(TestEnvironment.currentRom())).loadPlayerSpriteArt(nativeCharacter);
        assertNotNull(nativeArt);
        var bound=PlayableSheetAnimationBinding.bind(source,host,nativeArt);
        int relocation=PlayableSheetAnimationBinding.hasNativeFrameAliases(source) && !host.getNativeFramePoses().isEmpty()?256:0;
        int generatedMappings=bound.mappingFrames().size()-source.mappingFrames().size()-relocation;
        var result=new LinkedHashMap<String,Object>();
        result.put("game",game); result.put("character",character); result.put("relocation",relocation);
        result.put("generatedFamilyMappings",generatedMappings);
        result.put("sourceScripts",source.animationSet().getAllScripts());
        var packedIndices=new TreeMap<Integer,Integer>();
        for(int i=0;i<source.mappingFrames().size();i++) for(int j=0;j<sheet.frames().size();j++)
            if(source.mappingFrames().get(i)==source.mappingFrames().get(j)) { packedIndices.put(i,j); break; }
        result.put("packedIndices",packedIndices);
        result.put("sourceAnimations",sheet.animations()); result.put("nativeAliases",sheet.nativeFrames());
        result.put("nativePoses",host.getNativeFramePoses());
        result.put("mappingCount",nativeArt.mappingFrames().size());
        result.put("nativeScripts",nativeArt.animationSet().getAllScripts());
        if(character.equals("mica") && module instanceof Sonic2GameModule) {
            var s2=new com.openggf.game.sonic2.Sonic2PlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom()));
            var powered=s2.loadSuperSonicAnimationSet().getAllScripts();
            assertFalse(powered.isEmpty()); result.put("superScripts",powered);
        }
        if(character.equals("mica") && module instanceof Sonic3kGameModule) {
            var s3=new com.openggf.game.sonic3k.Sonic3kPlayerArt(RomByteReader.fromRom(TestEnvironment.currentRom()));
            var powered=s3.loadSuperSonicAnimationSet().getAllScripts();
            assertFalse(powered.isEmpty()); result.put("superScripts",powered);
            result.put("superMappingCount",s3.loadSuperSonicArtSet().mappingFrames().size());
        }
        var names=new TreeMap<Integer,List<String>>();
        for(var canonical:CanonicalAnimation.values()) {
            int id=host.resolveNativeId(canonical);
            if(id>=0) names.computeIfAbsent(id,k->new ArrayList<>()).add(canonical.name());
        }
        result.put("hostNames",names);
        result.put("familyBindings",host.getAnimationFamilies());
        var resolutions=new TreeMap<Integer,Object>();
        for(var entry:nativeArt.animationSet().getAllScripts().entrySet()) {
            int id=entry.getKey(); var script=bound.animationSet().getScript(id);
            var displayed=new TreeSet<Integer>();
            if(script!=null) for(int speed:new int[]{0x100,0x900}) for(int angle:new int[]{0,0x20,0x40,0x60}) {
                var sprite=new TestablePlayableSprite(nativeCharacter,(short)0,(short)0);
                sprite.setGameRulesForTest(module.getRules()); sprite.setAnimationProfile(bound.animationProfile());
                sprite.setAnimationSet(bound.animationSet()); sprite.setAnimationFrameCount(bound.mappingFrames().size());
                sprite.setGSpeed((short)speed); sprite.setAngle((byte)angle); sprite.setAnimationId(id);
                for(int clock=0;clock<512;clock++) { sprite.getAnimationManager().update(clock); displayed.add(sprite.getMappingFrame()); }
            }
            var row=new LinkedHashMap<String,Object>();
            row.put("boundScript",script); row.put("displayedFrames",displayed); resolutions.put(id,row);
        }
        result.put("resolutions",resolutions);
        var raw=new TreeMap<Integer,Object>();
        for(int frame=0;frame<nativeArt.mappingFrames().size();frame++) {
            var targets=new ArrayList<Integer>();
            if(frame<bound.mappingFrames().size()) for(int i=0;i<source.mappingFrames().size();i++)
                if(bound.mappingFrames().get(frame)==source.mappingFrames().get(i) && bound.dplcFrames().get(frame)==source.dplcFrames().get(i)) targets.add(i);
            raw.put(frame,targets);
        }
        result.put("rawTargets",raw);
        Path output=Path.of(System.getProperty("art.audit.output","target/character-art-audit"));
        Files.createDirectories(output);
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve(character+"-"+game+".json").toFile(),result);
        assertFalse(resolutions.isEmpty());
        assertEquals(256,relocation,"creator raw poses occupy a disjoint namespace");
    }
}
