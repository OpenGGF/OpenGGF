package typinghog.play;

/**
 * A stock badnik that was loaded and visible when a fight began, converted into a typing target:
 * its native class (simple name, looked up in {@code natives/<game>.txt}), centre position and
 * facing. The course's fight hand-off supplies these in native slot order.
 */
public record LoadedBadnik(String className, int x, int y, boolean facingRight) { }
