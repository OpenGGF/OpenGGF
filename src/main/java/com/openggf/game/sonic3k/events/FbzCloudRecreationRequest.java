package com.openggf.game.sonic3k.events;

import com.openggf.game.rewind.identity.ObjectRefId;

public record FbzCloudRecreationRequest(int cloudIndex, ObjectRefId stableId) { }
