package com.openggf.level;

import com.openggf.game.DelegatingZoneRegistry;
import com.openggf.game.MusicReference;
import com.openggf.game.ZoneKey;
import com.openggf.game.ZoneRegistry;
import org.junit.jupiter.api.Test;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestDelegatingLevelContracts {
    @Test void optionalCollisionAndLayoutRulesSurviveDecoration() {
        Level nativeLevel=mock(Level.class);
        when(nativeLevel.getBlockPixelSize()).thenReturn(256);
        when(nativeLevel.getLayerHeightBlocks(1)).thenReturn(7);
        when(nativeLevel.hasBackgroundCollisionRowAt(0x8000)).thenReturn(false);
        when(nativeLevel.resolveCollisionBlockIndex(4,3,2)).thenReturn(5);
        var decorated=new DelegatingLevel(nativeLevel);
        assertEquals(256,decorated.getBlockPixelSize());
        assertEquals(7,decorated.getLayerHeightBlocks(1));
        assertFalse(decorated.hasBackgroundCollisionRowAt(0x8000));
        assertEquals(5,decorated.resolveCollisionBlockIndex(4,3,2));
        Palette palette=new Palette(); decorated.setPalette(2,palette);
        verify(nativeLevel).setPalette(2,palette);
    }
    @Test void semanticZoneIdentityAndTaggedMusicSurviveDecoration() {
        ZoneRegistry source=mock(ZoneRegistry.class);
        ZoneKey key=ZoneKey.mod("sample","garden");
        MusicReference music=MusicReference.namespaced("sample","theme");
        when(source.zoneKey(9)).thenReturn(key);
        when(source.resolveZoneKey(key)).thenReturn(OptionalInt.of(9));
        when(source.getMusicReference(9,0)).thenReturn(music);
        var decorated=new DelegatingZoneRegistry(source);
        assertSame(key,decorated.zoneKey(9));
        assertEquals(OptionalInt.of(9),decorated.resolveZoneKey(key));
        assertSame(music,decorated.getMusicReference(9,0));
    }
}
