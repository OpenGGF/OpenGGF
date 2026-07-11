package com.openggf.mods;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSemanticVersionAndRange {
    @Test
    void strictVersionsParseCompareAndPublishPinnedApiVersion() {
        assertEquals(new SemanticVersion(1, 2, 3), SemanticVersion.parse("1.2.3"));
        assertTrue(SemanticVersion.parse("1.2.4").compareTo(SemanticVersion.parse("1.2.3")) > 0);
        assertTrue(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("1.999.999")) > 0);
        assertEquals("1.2.3", SemanticVersion.parse("1.2.3").toString());
        assertEquals(new SemanticVersion(1, 1, 0), ModApiVersion.CURRENT);
        VersionRange phaseOneCompatibility = VersionRange.parse(">=1.0.0 <2.0.0");
        assertTrue(phaseOneCompatibility.contains(SemanticVersion.parse("1.1.0")));
        assertFalse(VersionRange.parse(">=1.2.0 <2.0.0").contains(ModApiVersion.CURRENT));
    }

    @Test
    void strictVersionsRejectNonCanonicalAndOverflowForms() {
        for (String invalid : List.of(
                "1.2", "1.2.3.4", "01.2.3", "1.02.3", "1.2.03",
                "1.2.3-beta", "1.2.3+build", "-1.2.3", " 1.2.3",
                "1.2.3 ", "2147483648.0.0", "0.2147483648.0", "0.0.2147483648")) {
            assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(invalid), invalid);
        }
    }

    @Test
    void goldenRangesMatchTheirPinnedDomains() {
        VersionRange exact = VersionRange.parse("1.2.3");
        assertTrue(exact.contains(SemanticVersion.parse("1.2.3")));
        assertFalse(exact.contains(SemanticVersion.parse("1.2.4")));

        VersionRange bounded = VersionRange.parse(">=1.2.0 <2.0.0");
        assertTrue(bounded.contains(SemanticVersion.parse("1.2.0")));
        assertTrue(bounded.contains(SemanticVersion.parse("1.9.9")));
        assertFalse(bounded.contains(SemanticVersion.parse("2.0.0")));
        assertTrue(VersionRange.parse("*").contains(SemanticVersion.parse("2147483647.0.0")));
    }

    @Test
    void comparatorsNormalizeAndUsePinnedOperators() {
        VersionRange first = VersionRange.parse("<2.0.0 >=1.0.0");
        VersionRange second = VersionRange.parse(">=1.0.0 <2.0.0");
        assertEquals(second, first);
        assertEquals(second, new VersionRange(List.of(
                new VersionConstraint(VersionOperator.LT, new SemanticVersion(2, 0, 0)),
                new VersionConstraint(VersionOperator.GTE, new SemanticVersion(1, 0, 0)))));
        assertEquals(">=1.0.0 <2.0.0", new VersionRange(List.of(
                new VersionConstraint(VersionOperator.LT, new SemanticVersion(2, 0, 0)),
                new VersionConstraint(VersionOperator.GTE, new SemanticVersion(1, 0, 0)))).toString());
        assertEquals(second, VersionRange.parse("\t>=1.0.0   <2.0.0\n"));
        assertEquals(">=1.0.0 <2.0.0", second.toString());
        assertEquals("1.2.3", VersionRange.parse("1.2.3").toString());
        assertEquals("*", VersionRange.parse("*").toString());
        assertEquals(List.of(
                new VersionConstraint(VersionOperator.GTE, new SemanticVersion(1, 0, 0)),
                new VersionConstraint(VersionOperator.LT, new SemanticVersion(2, 0, 0))),
                first.constraints());
        assertTrue(VersionRange.parse(">1.0.0 <=1.0.1").contains(new SemanticVersion(1, 0, 1)));
        assertTrue(VersionRange.parse("=1.0.0").contains(new SemanticVersion(1, 0, 0)));
    }

    @Test
    void invalidAndEmptyRangeFormsAreRejected() {
        for (String invalid : List.of(
                "", " ", "^1.2.3", "~1.2.3", "1.2", "1.0.0 || 2.0.0",
                "1.0.0 - 2.0.0", ">=2.0.0 <1.0.0", ">1.0.0 <1.0.1",
                ">=1.0.0 <1.0.0", "=1.0.0 >1.0.0", ">=1.0.0 <2.0.0 >0.0.0 <=3.0.0 =1.5.0",
                ">=2147483648.0.0")) {
            assertThrows(IllegalArgumentException.class, () -> VersionRange.parse(invalid), invalid);
        }
    }
}
