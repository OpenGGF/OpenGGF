package com.openggf.game.soniccd.resources;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.SavedDecoder;
import com.openggf.game.soniccd.resources.CdR11aSectionArt.Writer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Declared configuration controls; no native execution or authenticated positive owner. Java UNRUN. */
class TestCdSectionArtKernelNativeRts {
    private static final SavedDecoder CLEAR=new SavedDecoder(Writer.CLEARED,0,0,0,0,0);
    private static CdSectionArtKernel.Binding binding(){return new CdSectionArtKernel.Binding(new Object(),CdNativeFile.R13C,CdMmdImage.Domain.ORIGINAL_WORD_RAM);}
    @SuppressWarnings("unchecked") private static CdSectionArtKernel.States<Object,Object> states(){return mock(CdSectionArtKernel.States.class);}
    @Test void explicitRtsReturnsTheExactValueAndOnlyChecksItsOwnerEvenWithInvalidCameraBits(){
        var states=states();var k=CdSectionArtKernel.nativeSectionRts(binding(),Map.of(),List.of(),states,CLEAR);Object value=new Object();
        assertSame(value,k.nativeSectionRts(value));assertSame(value,k.loadSection(value,-1));assertSame(value,k.updateSection(value,0x10000));
        verify(states,times(3)).checked(value);verifyNoMoreInteractions(states);
    }
    @Test void legacySixArgumentTableConstructorStillRejectsEmptyAndNonterminalTables(){
        var b=binding();var states=states();
        assertEquals("A bounded section byte-offset table is required",assertThrows(IllegalArgumentException.class,()->new CdSectionArtKernel<>(b,Map.of(),List.of(),List.of(),states,CLEAR)).getMessage());
        assertEquals("Terminal section threshold must be FFFF",assertThrows(IllegalArgumentException.class,()->new CdSectionArtKernel<>(b,Map.of(),List.of(new CdSectionArtKernel.Section(10,0,0)),List.of(),states,CLEAR)).getMessage());
        var table=new CdSectionArtKernel<>(b,Map.of(),List.of(new CdSectionArtKernel.Section(0xFFFF,0,0)),List.of(),states,CLEAR);
        assertThrows(IllegalStateException.class,()->table.nativeSectionRts(new Object()));assertThrows(IllegalArgumentException.class,()->table.loadSection(new Object(),-1));
    }
    @Test void rtsConfigurationRetainsForeignAndDuplicateSourceAndListWordValidation(){
        var b=binding();var foreign=new CdSectionArtKernel.Source(binding(),new Object(),CdNativeFile.R13C,CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x100,1,0x200100,com.openggf.game.soniccd.level.CdAssetBytes.copyOf(new byte[0]));
        assertThrows(IllegalArgumentException.class,()->CdSectionArtKernel.nativeSectionRts(b,Map.of(),List.of(foreign),states(),CLEAR));
        var source=new CdSectionArtKernel.Source(b,new Object(),CdNativeFile.R13C,CdMmdImage.Domain.ORIGINAL_WORD_RAM,0x100,1,0x200100,com.openggf.game.soniccd.level.CdAssetBytes.copyOf(new byte[0]));
        assertThrows(IllegalArgumentException.class,()->CdSectionArtKernel.nativeSectionRts(b,Map.of(),List.of(source,source),states(),CLEAR));
        assertThrows(IllegalArgumentException.class,()->CdSectionArtKernel.nativeSectionRts(b,Map.of(0x10000,List.of()),List.of(),states(),CLEAR));
    }
}
