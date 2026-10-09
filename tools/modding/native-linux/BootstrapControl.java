package nativecontrol;

import com.openggf.mods.code.GgfMod;
import com.openggf.mods.code.ModContext;

/** Runtime-loaded JDK bootstrap regression control, originating in the
 * 2026-10-09 Linux native gameplay failure. Never include in the image classpath:
 * closed-world reachability would hide missing interpreter bootstrap members.
 */
public final class BootstrapControl implements GgfMod {
    private record Pair(String name, int value) { }
    public interface Value { String value(); }

    @Override
    public void register(ModContext context) {
        Pair first = new Pair("control", 7);
        Pair second = new Pair("control", 7);
        if (!first.equals(second) || first.hashCode() != second.hashCode()
                || !first.toString().equals("Pair[name=control, value=7]")) {
            throw new AssertionError("Record bootstrap failed");
        }
        Object value = first;
        int result = switch (value) {
            case Pair pair -> pair.value();
            case String text -> text.length();
            default -> -1;
        };
        if (result != 7) throw new AssertionError("Pattern-switch bootstrap failed");
        Value proxy = (Value) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Value.class}, (instance, method, arguments) -> "proxy control");
        if (!proxy.value().equals("proxy control")) throw new AssertionError("Runtime proxy bootstrap failed");
    }
}
