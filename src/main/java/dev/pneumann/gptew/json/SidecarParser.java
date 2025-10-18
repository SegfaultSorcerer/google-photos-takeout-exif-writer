package dev.pneumann.gptew.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The SidecarParser class provides utilities for parsing JSON files typically
 * associated with media files. These JSON files contain metadata such as
 * timestamps, geolocation data, descriptions, and titles.
 *
 * The primary responsibility of this class is to parse the JSON file and
 * convert the extracted metadata into a {@link GoogleSidecar} object.
 *
 * This class ensures that:
 * - Missing or invalid fields are handled gracefully by returning null for
 *   the corresponding values in the {@link GoogleSidecar} object.
 * - Specific faulty cases, such as geolocation data with default fallback
 *   values (e.g., Gulf of Guinea coordinates 0.0, 0.0), are treated as
 *   missing data.
 *
 * This class is immutable and cannot be extended.
 *
 * @author Patrik Neumann
 */
public final class SidecarParser {
  private static final ObjectMapper M = new ObjectMapper();

  /**
   * Parses a JSON file containing metadata associated with media files
   * and creates a {@link GoogleSidecar} object from the extracted data.
   *
   * The method reads and interprets the specified JSON file to retrieve
   * timestamps, geolocation information, and textual data such as descriptions
   * and titles. Missing or invalid fields are handled by returning null values
   * in the corresponding properties of the {@link GoogleSidecar} object.
   *
   * @param jsonPath the {@link Path} to the JSON file containing the metadata to be parsed
   * @return a {@link GoogleSidecar} instance populated with the parsed metadata, or null values for missing elements
   * @throws IOException if an I/O error occurs when reading the JSON file
   */
  public GoogleSidecar parse(Path jsonPath) throws IOException {
    try (var in = Files.newInputStream(jsonPath)) {
      JsonNode n = M.readTree(in);

      Long taken = longAt(n, "photoTakenTime", "timestamp");
      Long created = longAt(n, "creationTime", "timestamp");
      Long modified = longAt(n, "modificationTime", "timestamp");

      JsonNode gd = n.path("geoData");
      Double lat = doubleAt(gd, "latitude");
      Double lon = doubleAt(gd, "longitude");
      Double alt = doubleAt(gd, "altitude");

      // Treat (0.0, 0.0) as missing GPS data (Gulf of Guinea default)
      if (lat != null && lon != null && lat == 0.0 && lon == 0.0) {
        lat = null;
        lon = null;
        alt = null;
      }

      String desc = textAt(n, "description");
      String title = textAt(n, "title");

      return new GoogleSidecar(taken, created, modified, lat, lon, alt, desc, title);
    }
  }

  /**
   * Retrieves a Long value from a nested JSON structure based on the specified object and child keys.
   * If the specified node is missing or null, this method returns {@code null}.
   *
   * @param root the root JSON node to start searching within
   * @param obj the key identifying the first-level object within the JSON structure
   * @param child the key identifying the nested child element within the specified object
   * @return the Long value of the nested field, or {@code null} if the field is missing or its value is null
   */
  private static Long longAt(JsonNode root, String obj, String child) {
    JsonNode x = root.path(obj).path(child);
    return x.isMissingNode() || x.isNull() ? null : x.asLong();
    }

  /**
   * Retrieves a Double value from a JSON node based on the specified field name.
   * If the field is missing or its value is null, this method returns {@code null}.
   *
   * @param root the root JSON node to read the value from
   * @param field the name of the field to retrieve the value from
   * @return the Double value associated with the specified field, or {@code null} if the field is missing or null
   */
  private static Double doubleAt(JsonNode root, String field) {
    JsonNode x = root.path(field);
    return x.isMissingNode() || x.isNull() ? null : x.asDouble();
  }

  /**
   * Retrieves the text value of a specified field from a JSON node.
   * If the field is missing or its value is null, this method returns {@code null}.
   *
   * @param root the root JSON node containing the field
   * @param field the name of the field to retrieve the text value from
   * @return the text value of the specified field, or {@code null} if the field is missing or its value is null
   */
  private static String textAt(JsonNode root, String field) {
    JsonNode x = root.path(field);
    return x.isMissingNode() || x.isNull() ? null : x.asText();
  }
}
