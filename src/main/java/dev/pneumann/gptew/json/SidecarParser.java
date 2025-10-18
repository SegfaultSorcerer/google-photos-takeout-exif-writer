package dev.pneumann.gptew.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SidecarParser {
  private static final ObjectMapper M = new ObjectMapper();

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

      String desc = textAt(n, "description");
      String title = textAt(n, "title");

      return new GoogleSidecar(taken, created, modified, lat, lon, alt, desc, title);
    }
  }

  private static Long longAt(JsonNode root, String obj, String child) {
    JsonNode x = root.path(obj).path(child);
    return x.isMissingNode() || x.isNull() ? null : x.asLong();
    }

  private static Double doubleAt(JsonNode root, String field) {
    JsonNode x = root.path(field);
    return x.isMissingNode() || x.isNull() ? null : x.asDouble();
  }

  private static String textAt(JsonNode root, String field) {
    JsonNode x = root.path(field);
    return x.isMissingNode() || x.isNull() ? null : x.asText();
  }
}
