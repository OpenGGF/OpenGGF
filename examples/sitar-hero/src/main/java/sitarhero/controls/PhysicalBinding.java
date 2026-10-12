package sitarhero.controls;

import com.openggf.control.PhysicalInputEvent;
import java.util.Map;
import java.util.Set;

/** Existing settings encoding retained; framework owns physical binding behavior. */
public record PhysicalBinding(char kind, int device, int code, int direction) {
    public PhysicalBinding { new com.openggf.control.PhysicalBinding(kind, device, code, direction); }
    public com.openggf.control.PhysicalBinding framework() {
        return new com.openggf.control.PhysicalBinding(kind, device, code, direction);
    }
    public float amount(Set<Integer> keys, Map<String, Float> pads) { return framework().amount(keys, pads); }
    public String encode() { return framework().encode(); }
    public String label() { return framework().label(); }
    public static PhysicalBinding decode(String text) {
        var value = com.openggf.control.PhysicalBinding.decode(text);
        return new PhysicalBinding(value.kind(), value.device(), value.code(), value.direction());
    }
    public static PhysicalBinding capture(PhysicalInputEvent event, boolean gamepad) {
        var value = com.openggf.control.PhysicalBinding.capture(event, gamepad, .7f);
        return value == null ? null : new PhysicalBinding(value.kind(), value.device(), value.code(), value.direction());
    }
}
