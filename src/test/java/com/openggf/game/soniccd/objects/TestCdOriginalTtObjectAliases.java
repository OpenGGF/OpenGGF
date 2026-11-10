package com.openggf.game.soniccd.objects;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Native r4/bubble_spawner.asm BYTE aliases; source-first, all Java UNRUN. */
class TestCdOriginalTtObjectAliases {
 @Test void bubbleReloadAndBurstHighAppendAfterEveryLegacyByteField(){
  assertEquals(60,CdObjectState.ByteField.BOSS_LIMB_SPEED.ordinal());
  assertEquals(61,CdObjectState.ByteField.WATER_BUBBLE_RELOAD.ordinal());
  assertEquals(62,CdObjectState.ByteField.WATER_BUBBLE_BURST_HIGH.ordinal());
  assertEquals(0x33,CdObjectState.ByteField.WATER_BUBBLE_RELOAD.byteOffset());
  assertEquals(0x36,CdObjectState.ByteField.WATER_BUBBLE_BURST_HIGH.byteOffset());
 }
}
