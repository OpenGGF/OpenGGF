package external;

import com.openggf.tools.NativeModLoadingProbe;
import java.util.function.IntUnaryOperator;

/**
 * Unseen-at-image-build fixture for the 2026-10-08 native loading investigation.
 * Compile ONLY after the host image, into a separate JAR absent from its classpath.
 * Change version-one to version-two and rebuild only this JAR to test replacement.
 */
public final class Plugin implements NativeModLoadingProbe.Callback {
    public Plugin() {
    }

    @Override
    public String run(int value) {
        IntUnaryOperator operation = n -> NativeModLoadingProbe.fromHost(n) + 2;
        try {
            if (operation.applyAsInt(value) != 23) {
                throw new IllegalStateException("Unexpected host callback");
            }
        } catch (IllegalStateException failure) {
            throw new AssertionError(failure);
        }
        return "version-one:" + operation.applyAsInt(value);
    }
}
