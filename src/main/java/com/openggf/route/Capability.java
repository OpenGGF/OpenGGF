package com.openggf.route;

/**
 * What an object's owner reports about itself, by implementing a read-only engine interface
 * (discovered with {@code instanceof}; there is no registration list): a named phase, custody of
 * a player (grab, hang, carry, tube, airflow, latch), or hit points.
 */
public enum Capability { PHASE, ATTACHMENT, HIT_POINTS }
