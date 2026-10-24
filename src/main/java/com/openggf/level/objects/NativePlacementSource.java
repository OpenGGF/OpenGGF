package com.openggf.level.objects;

import java.util.List;

/** Internal decoded layout context retained when a disposable world filters initial objects.
 * This preserves native placement order/respawn identity; it never authorizes a factory.
 */
@FunctionalInterface
public interface NativePlacementSource {
    List<ObjectSpawn> placements();
}
