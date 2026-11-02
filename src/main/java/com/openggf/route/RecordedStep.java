package com.openggf.route;

/** The row a host accepted for one poll and the observation after it. */
public record RecordedStep(PadRow accepted, RouteSample after) { }
