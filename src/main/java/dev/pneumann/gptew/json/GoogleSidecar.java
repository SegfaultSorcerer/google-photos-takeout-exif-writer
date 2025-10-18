package dev.pneumann.gptew.json;

/**
 * Represents metadata parsed from a Google sidecar JSON file, commonly associated with
 * media files to store additional information such as timestamps, geolocation, and descriptions.
 *
 * The record encapsulates the following metadata:
 * - Timestamps for when the photo was taken, created, or last modified.
 * - Geographic coordinates including latitude, longitude, and altitude.
 * - A descriptive text and title related to the media.
 *
 * Instances of this record are immutable and are typically created via the {@code SidecarParser} class.
 *
 * @param photoTakenTimeTimestamp The timestamp representing when the photo was taken, as Unix time in milliseconds. Can be null.
 * @param creationTimeTimestamp The timestamp representing the creation time of the media, as Unix time in milliseconds. Can be null.
 * @param modificationTimeTimestamp The timestamp representing the last modification time of the media, as Unix time in milliseconds. Can be null.
 * @param latitude The geographic latitude in decimal degrees. Can be null if no GPS data is available.
 * @param longitude The geographic longitude in decimal degrees. Can be null if no GPS data is available.
 * @param altitude The geographic altitude in meters. Can be null if no GPS data is available.
 * @param description A textual description associated with the media. Can be null.
 * @param title A title associated with the media. Can be null.
 *
 * @author Patrik Neumann
 */
public record GoogleSidecar(
    Long photoTakenTimeTimestamp,
    Long creationTimeTimestamp,
    Long modificationTimeTimestamp,
    Double latitude, Double longitude, Double altitude,
    String description, String title) {}
