package com.openggf.editor;
import org.junit.jupiter.api.Test;
import com.openggf.game.common.CommonObjectPlacementEncoding;
import com.openggf.game.sonic1.Sonic1ObjectPlacementEncoding;
import com.openggf.level.Level;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TestEditorSpawnFactory {
 private EditorPlacementIdAllocator ids(){var level=mock(Level.class);when(level.getObjects()).thenReturn(List.of());when(level.getRings()).thenReturn(List.of());return new EditorPlacementIdAllocator(level);}
 @Test void separateRingPlacementsRetainFullHeightAndIdentity(){
  var factory=new EditorSpawnFactory(new CommonObjectPlacementEncoding(),ids());
  var ring=factory.createRingSpawn(100,4096);
  var moved=factory.moveRingSpawn(ring,200,8192);
  assertEquals(8192,moved.y());assertEquals(ring.placementId(),moved.placementId());
 }
 @Test void objectBackedRingsKeepNativeHeightLimit(){
  var factory=new EditorSpawnFactory(new Sonic1ObjectPlacementEncoding(),ids());
  assertThrows(IllegalArgumentException.class,()->factory.createRingSpawn(100,4096));
 }
}
