package com.openggf.game.internal;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** This internal contract trusts concrete engine-loader SPI owners; it does not certify ROM provenance. */
class TestNativeStagePacingOwners {
    @Test void concreteSameEngineLoaderSpiImplementationsAreAccepted() {
        var special = new SpecialOwner(); var bonus = new BonusOwner();
        assertSame(special, NativeStagePacingOwners.special(special));
        assertSame(bonus, NativeStagePacingOwners.bonus(bonus));
    }

    @Test void concreteForeignLoaderSpiImplementationsAreDenied() throws Exception {
        var loader = new OwnerLoader();
        var special = loader.define(bytes(SpecialOwner.class)).getConstructor().newInstance();
        var bonus = loader.define(bytes(BonusOwner.class)).getConstructor().newInstance();
        assertInstanceOf(NativeSpecialStagePacing.class, special);
        assertInstanceOf(NativeBonusStagePacing.class, bonus);
        assertNotSame(NativeStagePacingOwners.class.getClassLoader(), special.getClass().getClassLoader());
        assertNull(NativeStagePacingOwners.special(special));
        assertNull(NativeStagePacingOwners.bonus(bonus));
    }

    private static byte[] bytes(Class<?> type) throws IOException {
        try (var in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(in); return in.readAllBytes();
        }
    }
    private static final class OwnerLoader extends ClassLoader {
        OwnerLoader() { super(NativeStagePacingOwners.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
    }
    public static final class SpecialOwner implements NativeSpecialStagePacing {
        private final NativeSpecialStagePacingOwner owner = new NativeSpecialStagePacingOwner();
        public SpecialOwner() { }
        public State pacingState() { return owner.state(true, 0, 0, true); }
        public NativeSpecialStagePacingOwner pacingOwner() { return owner; }
    }
    public static final class BonusOwner implements NativeBonusStagePacing {
        public BonusOwner() { }
        public long pacingEntryEpoch() { return 1; }
    }
}
