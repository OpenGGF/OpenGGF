package com.openggf.level.metadata;

import com.openggf.game.modzone.ModObjectZoneSet;

/** Internal editor capability for an explicitly selected native object table; not creator API. */
public interface ObjectTableNameProvider {
    String objectTableName(int stockObjectId, ModObjectZoneSet table);
}
