package dev.pneumann.gptew.json;

public record GoogleSidecar(
    Long photoTakenTimeTimestamp,
    Long creationTimeTimestamp,
    Long modificationTimeTimestamp,
    Double latitude, Double longitude, Double altitude,
    String description, String title) {}
