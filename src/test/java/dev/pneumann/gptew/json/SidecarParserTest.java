package dev.pneumann.gptew.json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SidecarParserTest {

  private final SidecarParser parser = new SidecarParser();

  @Test
  void testParse_FullMetadata(@TempDir Path tempDir) throws IOException {
    // Create a complete Google Takeout JSON sidecar
    String json = """
      {
        "title": "My Photo",
        "description": "A beautiful sunset",
        "photoTakenTime": {
          "timestamp": "1631456789",
          "formatted": "Sep 12, 2021, 1:59:49 PM UTC"
        },
        "creationTime": {
          "timestamp": "1631456790",
          "formatted": "Sep 12, 2021, 1:59:50 PM UTC"
        },
        "modificationTime": {
          "timestamp": "1631456791",
          "formatted": "Sep 12, 2021, 1:59:51 PM UTC"
        },
        "geoData": {
          "latitude": 52.520008,
          "longitude": 13.404954,
          "altitude": 34.5,
          "latitudeSpan": 0.0,
          "longitudeSpan": 0.0
        }
      }
      """;

    Path jsonFile = tempDir.resolve("test.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertEquals(1631456789L, sidecar.photoTakenTimeTimestamp());
    assertEquals(1631456790L, sidecar.creationTimeTimestamp());
    assertEquals(1631456791L, sidecar.modificationTimeTimestamp());
    assertEquals(52.520008, sidecar.latitude());
    assertEquals(13.404954, sidecar.longitude());
    assertEquals(34.5, sidecar.altitude());
    assertEquals("A beautiful sunset", sidecar.description());
    assertEquals("My Photo", sidecar.title());
  }

  @Test
  void testParse_MinimalMetadata(@TempDir Path tempDir) throws IOException {
    // Minimal JSON with only timestamp
    String json = """
      {
        "photoTakenTime": {
          "timestamp": "1234567890"
        }
      }
      """;

    Path jsonFile = tempDir.resolve("minimal.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertEquals(1234567890L, sidecar.photoTakenTimeTimestamp());
    assertNull(sidecar.creationTimeTimestamp());
    assertNull(sidecar.modificationTimeTimestamp());
    assertNull(sidecar.latitude());
    assertNull(sidecar.longitude());
    assertNull(sidecar.altitude());
    assertNull(sidecar.description());
    assertNull(sidecar.title());
  }

  @Test
  void testParse_GpsOnly(@TempDir Path tempDir) throws IOException {
    // JSON with only GPS data
    String json = """
      {
        "geoData": {
          "latitude": -33.865143,
          "longitude": 151.209900,
          "altitude": 12.0
        }
      }
      """;

    Path jsonFile = tempDir.resolve("gps-only.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertNull(sidecar.photoTakenTimeTimestamp());
    assertEquals(-33.865143, sidecar.latitude());
    assertEquals(151.209900, sidecar.longitude());
    assertEquals(12.0, sidecar.altitude());
  }

  @Test
  void testParse_ZeroCoordinates_AreFiltered(@TempDir Path tempDir) throws IOException {
    // JSON with (0.0, 0.0) coordinates - should be treated as null
    String json = """
      {
        "photoTakenTime": {
          "timestamp": "1234567890"
        },
        "geoData": {
          "latitude": 0.0,
          "longitude": 0.0,
          "altitude": 100.0
        }
      }
      """;

    Path jsonFile = tempDir.resolve("zero-coords.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertEquals(1234567890L, sidecar.photoTakenTimeTimestamp());
    assertNull(sidecar.latitude(), "Latitude (0.0) should be filtered out");
    assertNull(sidecar.longitude(), "Longitude (0.0) should be filtered out");
    assertNull(sidecar.altitude(), "Altitude should also be null when coords are (0,0)");
  }

  @Test
  void testParse_NegativeCoordinates(@TempDir Path tempDir) throws IOException {
    // Test negative coordinates (Southern/Western hemispheres)
    String json = """
      {
        "geoData": {
          "latitude": -34.603684,
          "longitude": -58.381559,
          "altitude": 25.0
        }
      }
      """;

    Path jsonFile = tempDir.resolve("negative-coords.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertEquals(-34.603684, sidecar.latitude(), "Buenos Aires latitude");
    assertEquals(-58.381559, sidecar.longitude(), "Buenos Aires longitude");
    assertEquals(25.0, sidecar.altitude());
  }

  @Test
  void testParse_MissingGeoData(@TempDir Path tempDir) throws IOException {
    // JSON without geoData field
    String json = """
      {
        "photoTakenTime": {
          "timestamp": "1234567890"
        },
        "title": "No GPS"
      }
      """;

    Path jsonFile = tempDir.resolve("no-geo.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertEquals(1234567890L, sidecar.photoTakenTimeTimestamp());
    assertEquals("No GPS", sidecar.title());
    assertNull(sidecar.latitude());
    assertNull(sidecar.longitude());
    assertNull(sidecar.altitude());
  }

  @Test
  void testParse_PartialGeoData(@TempDir Path tempDir) throws IOException {
    // GPS with lat/lon but no altitude
    String json = """
      {
        "geoData": {
          "latitude": 48.858844,
          "longitude": 2.294351
        }
      }
      """;

    Path jsonFile = tempDir.resolve("partial-geo.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertEquals(48.858844, sidecar.latitude(), "Paris latitude");
    assertEquals(2.294351, sidecar.longitude(), "Paris longitude");
    assertNull(sidecar.altitude(), "Altitude not provided");
  }

  @Test
  void testParse_EmptyJson(@TempDir Path tempDir) throws IOException {
    // Empty JSON object
    String json = "{}";

    Path jsonFile = tempDir.resolve("empty.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertNull(sidecar.photoTakenTimeTimestamp());
    assertNull(sidecar.latitude());
    assertNull(sidecar.longitude());
    assertNull(sidecar.title());
    assertNull(sidecar.description());
  }

  @Test
  void testParse_MalformedJson_ThrowsException(@TempDir Path tempDir) throws IOException {
    // Invalid JSON syntax
    String json = "{ invalid json }";

    Path jsonFile = tempDir.resolve("malformed.json");
    Files.writeString(jsonFile, json);

    assertThrows(IOException.class, () -> parser.parse(jsonFile));
  }

  @Test
  void testParse_NonexistentFile() {
    Path nonexistent = Path.of("/nonexistent/file.json");
    assertThrows(IOException.class, () -> parser.parse(nonexistent));
  }

  @Test
  void testParse_RealGoogleTakeoutExample(@TempDir Path tempDir) throws IOException {
    // Real example from Google Takeout
    String json = """
      {
        "title": "IMG_20210912_135949.jpg",
        "description": "",
        "imageViews": "0",
        "photoTakenTime": {
          "timestamp": "1631456389",
          "formatted": "Sep 12, 2021, 1:53:09 PM UTC"
        },
        "geoData": {
          "latitude": 52.520008,
          "longitude": 13.404954,
          "altitude": 0.0,
          "latitudeSpan": 0.0,
          "longitudeSpan": 0.0
        },
        "geoDataExif": {
          "latitude": 52.520008,
          "longitude": 13.404954,
          "altitude": 0.0,
          "latitudeSpan": 0.0,
          "longitudeSpan": 0.0
        },
        "url": "https://photos.google.com/photo/...",
        "googlePhotosOrigin": {
          "mobileUpload": {
            "deviceType": "ANDROID_PHONE"
          }
        }
      }
      """;

    Path jsonFile = tempDir.resolve("real-example.json");
    Files.writeString(jsonFile, json);

    GoogleSidecar sidecar = parser.parse(jsonFile);

    assertNotNull(sidecar);
    assertEquals(1631456389L, sidecar.photoTakenTimeTimestamp());
    assertEquals(52.520008, sidecar.latitude());
    assertEquals(13.404954, sidecar.longitude());
    assertEquals(0.0, sidecar.altitude());
    assertEquals("IMG_20210912_135949.jpg", sidecar.title());
  }
}
