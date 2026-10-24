package com.openggf.stageplay.route;

/** An observed centre position after the named native tick, or visual object route sample. */
public record StageplayRoutePoint(int frame, double x, double y, boolean grounded) {}
