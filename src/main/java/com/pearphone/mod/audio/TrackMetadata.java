package com.pearphone.mod.audio;

/** Immutable metadata returned by {@link YtDlpExtractor#fetchMetadata}. */
public record TrackMetadata(String title, int durationSeconds) {}
