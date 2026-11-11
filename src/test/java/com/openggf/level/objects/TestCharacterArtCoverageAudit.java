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
        Path project=com.openggf.tests.ExampleCharacterArt.assemble(temporary.resolve("generated")), encoded=temporary.resolve(character+".ggfp");
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
        if (character.equals("talon")) for (var entry : host.getNativeFramePoses().entrySet()) {
            int raw = entry.getKey();
            Integer target = sheet.nativeFrames().get(entry.getValue().key());
            assertNotNull(target, "Unprovided native Tails pose " + game + "/" + raw);
            if (raw == 0) continue; // Hide sentinel is always empty.
            assertSame(source.mappingFrames().get(target), bound.mappingFrames().get(raw),
                    "Native Tails mapping alias " + game + "/" + raw);
            assertSame(source.dplcFrames().get(target), bound.dplcFrames().get(raw));
        }
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
        if (character.equals("mica") && !game.equals("S1")) {
            String suffix = game.toLowerCase(java.util.Locale.ROOT);
            Path poweredEncoded = temporary.resolve("powered-"+suffix+".ggfp");
            assertEquals(0,GgfModCli.run(new String[]{"convert","art","--playable","--image",project.resolve("mica_powered_"+suffix+".png").toString(),
                    "--sheet",project.resolve("mica_powered_"+suffix+"-sheet.yaml").toString(),"--out",poweredEncoded.toString()},System.out));
            var poweredArt = PlayableSheetMaterializer.read(Files.readAllBytes(poweredEncoded)).art();
            var aliases = java.util.EnumSet.allOf(CanonicalAnimation.class);
            aliases.removeAll(Set.of(CanonicalAnimation.WALK, CanonicalAnimation.RUN, CanonicalAnimation.PUSH, CanonicalAnimation.WAIT, CanonicalAnimation.BALANCE, CanonicalAnimation.DUCK));
            var palette=List.of(Collections.nCopies(16,0));
            if (game.equals("S3K")) aliases.removeAll(Set.of(CanonicalAnimation.BLINK,CanonicalAnimation.BALANCE2,CanonicalAnimation.BUBBLE,CanonicalAnimation.FLY));
            var phaseAliases = new ObjectMapper().readValue(Files.readAllBytes(Path.of("examples/mica-and-talon/art/mica/powered",suffix,"normal-phase-aliases.json")),new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){});
            for(int phase=0;phase<2;phase++) phaseAliases.put("family-hurt-fall/"+phase,"family-hurt-fall/"+phase);
            var form = new SuperFormProfile.Form(palette,palette,List.of(),1,1,PhysicsProfile.SONIC_2_SUPER_SONIC,CanonicalAnimation.SUPER_TRANSFORM,Map.of(),false,false,new PoweredPlayableSheet(poweredArt,aliases,phaseAliases));
            var nativeScripts = (Map<Integer,SpriteAnimationScript>) result.get("superScripts");
            var nativeSet = new SpriteAnimationSet(); nativeScripts.forEach(nativeSet::addScript);
            var poweredFamilies = new HashMap<>(host.getAnimationFamilies());
            poweredFamilies.put(33,new AnimationFamilies.Binding(null,CanonicalAnimation.WAIT,List.of(6)));
            var poweredSource = new com.openggf.game.internal.PoweredAnimationSource(nativeSet,poweredFamilies,8,game.equals("S2")?1:4,game.equals("S2")?3:0,game.equals("S2")?32:0,game.equals("S2")?181:0);
            var combined = PoweredPlayableArtBinding.combine(bound,Map.of(SuperFormProfile.Tier.SUPER,form),poweredSource);
            var view = combined.powered().get(SuperFormProfile.Tier.SUPER);
            var coverage = new TreeMap<Integer,Object>();
            for (var entry : nativeScripts.entrySet()) {
                int id = entry.getKey(); var script = view.animationSet().getScript(id);
                var family = host.getAnimationFamilies().get(id);
                boolean authored = script != null && script != bound.animationSet().getScript(id);
                boolean alias = family != null && family.animation() != null && aliases.contains(family.animation()) && script != null;
                boolean empty = entry.getValue().frames().stream().allMatch(f -> f == 0);
                boolean reserved = id == 18 || game.equals("S2") && id == 19;
                boolean unreachableBlink = game.equals("S3K") && id == 10;
                boolean phaseAlias = !poweredSource.poses(id).isEmpty() && poweredSource.poses(id).values().stream().allMatch(phaseAliases::containsKey);
                String status = unreachableBlink ? "N/A" : phaseAlias ? "alias" : authored ? "authored" : alias ? "alias" : empty || reserved ? "N/A" : "fallback";
                coverage.put(id,Map.of("status",status,"reason",unreachableBlink?"Blink interrupt is competition control; single-player powered WAIT never selects it, and C7 lacks isolated bank context":phaseAlias?"explicit normal phase alias; native timing retained":authored?"complete powered GGFP family with native timing":alias?"normal pose identity; explicit whole-animation alias":empty?"native blank script":reserved?"retained Dash2/Dash3 table data has no live Sonic selector":"missing powered family", "frames",script == null ? List.of() : script.frames()));
                if (authored) {
                    assertEquals(entry.getValue().delay(),script.delay());
                    assertEquals(entry.getValue().endAction(),script.endAction());
                    assertEquals(entry.getValue().endParam(),script.endParam());
                    assertEquals(entry.getValue().frames().size(),script.frames().size());
                }
                assertNotEquals("fallback",status,"Unresolved powered script "+game+"/"+id);
            }
            for (int id : List.of(0,1)) {
                var script=view.animationSet().getScript(id); var poses=poweredSource.poses(id);
                int relocationBase=script.frames().getFirst()-nativeScripts.get(id).frames().getFirst();
                for(int nativeFrame:poses.keySet()) assertFalse(view.mappingFrames().get(nativeFrame+relocationBase).pieces().isEmpty(),"implicit movement mapping "+id+"/"+nativeFrame);
            }
            var movement=(ScriptedVelocityAnimationProfile)view.animationProfile();
            assertEquals(0x800,movement.getRunSpeedThreshold());
            if(game.equals("S2")) {
                int walkBase=view.animationSet().getScript(0).frames().getFirst()-nativeScripts.get(0).frames().getFirst();
                int runFrame=view.animationSet().getScript(1).frames().getFirst();
                assertEquals(32,movement.applySuperAlternateFrame(walkBase+0x75,true,0)-(walkBase+0x75));
                assertEquals(walkBase+0x75,movement.applySuperAlternateFrame(walkBase+0x75,true,1));
                assertEquals(runFrame,movement.applySuperAlternateFrame(runFrame,true,0));
            }
            for(int speed : List.of(0x300,0x900)) {
                var sprite=new TestablePlayableSprite("sonic",(short)0,(short)0);
                sprite.setGameRulesForTest(module.getRules()); sprite.setAnimationProfile(view.animationProfile());
                sprite.setAnimationSet(view.animationSet()); sprite.setAnimationFrameCount(view.mappingFrames().size());
                sprite.setSuperSonic(true); sprite.setAir(false); sprite.setMovementInputActive(true);
                sprite.setDirection(com.openggf.physics.Direction.RIGHT); sprite.setGSpeed((short)speed); sprite.setAnimationId(0);
                sprite.getAnimationManager().update(0);
                var selected=view.animationSet().getScript(speed<0x800?0:1).frames();
                assertTrue(selected.contains(sprite.getMappingFrame()) || game.equals("S2") && speed<0x800 && selected.contains(sprite.getMappingFrame()-32),"speed selection "+game+"/"+speed+" frame "+sprite.getMappingFrame());
            }
            result.put("poweredCoverage",coverage);
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
