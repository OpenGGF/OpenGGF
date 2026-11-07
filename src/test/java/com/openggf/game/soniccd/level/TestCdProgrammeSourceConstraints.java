package com.openggf.game.soniccd.level;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

/** Source-selection facts only; retained progress still belongs to its live native owner.
 * RunLevel (IPX.asm:559-572), InitStage (common/main.asm:15-25), N1ecc5f3. */
class TestCdProgrammeSourceConstraints {
    @ParameterizedTest @EnumSource(CdR6DataLayout.Programme.class)
    void presentAndPastHaveNoFixedGoodFutureByte(CdR6DataLayout.Programme p) {
        switch(p) {
            case R61A,R61B,R62A,R62B -> assertThrows(UnsupportedOperationException.class,p::goodFutureByte,
                    "A/B selection does not read or clear retained goodFuture");
            case R61C,R62C,R63C -> assertEquals(1,p.goodFutureByte());
            case R61D,R62D,R63D -> assertEquals(0,p.goodFutureByte());
        }
    }
    @Test void optionalFutureConstraintRetainsUnknownPresentAndPast() {
        for(var p:CdR6DataLayout.Programme.values()) {
            var expected=switch(p) {
                case R61C,R62C,R63C -> OptionalInt.of(1);
                case R61D,R62D,R63D -> OptionalInt.of(0);
                default -> OptionalInt.empty();
            };
            assertEquals(expected,p.goodFutureConstraint());
        }
    }
    @Test void sourceOnlyDefinitionsDoNotGrowZoneBindingsOrRuntimeAdmission() {
        for(var p:CdR5DataLayout.Programme.values()) {
            var d=CdProgrammeDefinition.select(p.payload().stage());
            assertEquals(p.payload(),d.payload());assertTrue(d.r3().isEmpty());assertTrue(d.tt().isEmpty());
        }
        for(var p:CdR6DataLayout.Programme.values()) {
            var d=CdProgrammeDefinition.select(p.payload().stage());
            assertEquals(p.payload(),d.payload());assertTrue(d.r3().isEmpty());assertTrue(d.tt().isEmpty());
        }
        for(var field:CdProgrammeDefinition.class.getDeclaredFields()) {
            assertNotEquals(CdR5DataLayout.Programme.class,field.getType());
            assertNotEquals(CdR6DataLayout.Programme.class,field.getType());
            assertFalse(field.getGenericType().getTypeName().contains("CdR6DataLayout"));
        }
        for(var method:CdProgrammeDefinition.class.getDeclaredMethods())assertNotEquals("r6",method.getName());
        assertTrue(CdProgrammeDefinition.select(com.openggf.game.soniccd.CdStagePayload.R31A.stage()).r3().isPresent());
        for(var p:CdR4DataLayout.Programme.values())assertSame(p,CdProgrammeDefinition.select(p.payload().stage()).tt().orElseThrow());
    }
}
