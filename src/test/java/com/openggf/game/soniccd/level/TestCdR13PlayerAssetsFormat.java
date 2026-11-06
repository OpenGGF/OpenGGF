package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.player.CdPlayerAnimation;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.openggf.game.soniccd.level.TestCdR13FutureAssets.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source-format extension only; actual source scripts retained, selected-frame runtime fence unchanged. Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR13PlayerAssetsFormat {
    @Test void all60ActualScriptsRetainTheirSixLatentHighFrameBodiesAndEveryOtherByte()throws Exception {
        int[] ids={15,51,52,53,56,57};String[] expected={"076162b963baff00","0301b5b5b6b6b7b7bbfd0500","0301b5b50101b8b8bbfd0500","ffa6a7a8a9ffffff","0179abacabacabaca9aaa9aaa9aaa7a8a7a8a7a8fd1a","015da7a8a7a8a7a8a9aaa9aaa9aaabacabacabacfd1a"};
        for(var a:retail().all()){var p=a.player();assertEquals(60,p.animations().size());assertEquals("96c145e86b5f841f36dbfca9361c7139df4ff5705423566836d361ace6098af9",hash(p.animationTable().copy()));
            var meta=new StringBuilder();var high=new ArrayList<Integer>();
            for(int id=0;id<60;id++){var body=p.animation(id);boolean unavailable=false;for(int i=1;i<body.bytes().length();i++){int v=body.bytes().unsignedByte(i);if(v>=253)break;if(v>=167)unavailable=true;}if(unavailable)high.add(id);meta.append(id).append(' ').append(Integer.toHexString(body.relativeOffset())).append(' ').append(body.bytes().length()).append(' ').append(hash(body.bytes().copy())).append('\n');}
            assertEquals(List.of(15,51,52,53,56,57),high);assertEquals("b3a9b6e328ba28f85516828dc3f997da66052391cfe4a23a79907596e99f9125",hash(meta.toString().getBytes(StandardCharsets.UTF_8)));
            for(int i=0;i<ids.length;i++)assertEquals(expected[i],hex(p.animation(ids[i]).bytes().copy()));assertEquals(167,p.sonic().mappingFrames().size());
        }
    }
    @Test void actualLowFrameSucceedsWhileSelected185FailsAtomicallyAndPositiveTimerDefersItsRead()throws Exception {
        for(var a:retail().all()){var owner=new CdPlayerAnimation(a.player());var input=new CdPlayerAnimation.Input(15,0,0,0,0xA4,0,0);var start=new CdPlayerAnimation.State(15,0,0,0);
            var first=owner.step(start,input);assertEquals(new CdPlayerAnimation.State(15,1,7,0x61),first.state());assertEquals(start,new CdPlayerAnimation.State(15,0,0,0));
            var pending=new CdPlayerAnimation.State(15,2,1,0x62);assertEquals(new CdPlayerAnimation.State(15,2,0,0x62),owner.step(pending,input).state());
            var selected=new CdPlayerAnimation.State(15,2,0,0x62);String before=hash(a.player().animationTable().copy());var fault=assertThrows(UnsupportedOperationException.class,()->owner.step(selected,input));assertTrue(fault.getMessage().contains("Map frame 185 outside admitted Sonic mappings (167)"));
            assertEquals(new CdPlayerAnimation.State(15,2,0,0x62),selected);assertEquals(new CdPlayerAnimation.Input(15,0,0,0,0xA4,0,0),input);assertEquals(before,hash(a.player().animationTable().copy()));
            assertThrows(UnsupportedOperationException.class,()->owner.step(new CdPlayerAnimation.State(15,0,1,185),input));
        }
    }
    @Test void explicit167FormatStillRejectsBadDirectoryHeaderCommandsAndPadding()throws Exception {
        var a=retail().good();byte[] raw=a.player().animationTable().copy();
        byte[] directory=raw.clone();directory[0]=(byte)255;directory[1]=(byte)254;assertThrows(IllegalArgumentException.class,()->decode(a,directory));
        byte[] header=raw.clone();header[0xDE]=(byte)128;assertThrows(UnsupportedOperationException.class,()->decode(a,header));
        byte[] command=raw.clone();command[0xDF]=(byte)253;assertThrows(IllegalArgumentException.class,()->decode(a,command));
        byte[] padding=raw.clone();padding[0xE5]=1;assertThrows(IllegalArgumentException.class,()->decode(a,padding));
    }
    @Test void legacy198StillRejectsMalformedFrameBeforeAnyRuntimeSelection()throws Exception {
        var a=retail().legacy();byte[] raw=a.player().animationTable().copy();raw[0xDF]=(byte)198;var p=a.player();
        var fault=assertThrows(IllegalArgumentException.class,()->CdR11aPlayerAssets.decode(raw,p.miniAnimations().copy(),p.rampChunks().copy(),p.specialChunks().copy(),p.sineTable().copy(),p.arcTanTable().copy(),a.sonic()));assertEquals("Animation frame outside Sonic mappings",fault.getMessage());
    }
    private static CdR11aPlayerAssets decode(CdR13FutureAssets a,byte[] raw){var p=a.player();return CdR11aPlayerAssets.decode(raw,p.miniAnimations().copy(),p.rampChunks().copy(),p.specialChunks().copy(),p.sineTable().copy(),p.arcTanTable().copy(),a.sonic());}
}
