# PRD — Google Photos Sidecar Merge (Pure-Java)

## 1. Problem & Goals
- **Problem**: After Google Takeout, many images/videos lack correct embedded metadata or filesystem times.
- **Goal**: A **pure-Java** cross-platform CLI that:
  1) reads Google sidecar JSONs,
  2) writes capture time & GPS (and description) into the media’s metadata (**v1 = JPEG EXIF**),
  3) aligns the file system timestamps to the capture time.

## 2. Scope
- **In v1**: JPEG write (EXIF Date/Time, GPS), file mtime, dry-run, backups, recursive scan.
- **Out of v1**: Video (MP4/MOV), HEIC, albums, face tags, Google-only extended fields.

## 3. Users & Constraints
- Runs on Windows/macOS/Linux with Java 17; no native deps.
- Must be idempotent and safe: dry-run default, optional backups, verify after write.

## 4. Inputs & Matching
- Inputs: A root directory containing media and Google sidecars (`*.ext.json` next to the media).
- Matching strategy v1: `basename.ext` ↔ `basename.ext.json` in the same directory.
- v1.1: Sidecar index across subfolders / split archives; fuzzy handling for duplicated files.

## 5. Field Mapping (JPEG)
| Google JSON | JPEG EXIF/XMP target | Notes |
|---|---|---|
| `photoTakenTime.timestamp` | `EXIF:DateTimeOriginal`, `EXIF:CreateDate` | `yyyy:MM:dd HH:mm:ss` in local zone |
| `modificationTime.timestamp` | `EXIF:ModifyDate` | optional |
| `geoData{lat,lon,altitude}` | `GPSLatitude/GPSLongitude/GPSAltitude` (+Ref) | DMS rational encoding |
| `description` / `title` | (future) `XMP-dc:Description` | optional |

**Timezone policy**: Treat `timestamp` as epoch seconds; write local-time EXIF (no TZ).

## 6. CLI
```
gpmf --input <dir> [--recursive] [--dry-run] [--set-filetimes] [--backup]
```
- Exit codes: 0 ok, 1 warnings, 2 errors.
- Structured logs (stdout), one line per processed file.

## 7. Safety & Idempotency
- Dry-run by default.
- Lossless EXIF update where possible; write to temp file then atomic replace; optional `.bak` backup.
- Verify by reading back key tags.

## 8. Performance
- Stream directory tree; bounded parallelism (future); minimal memory per file.

## 9. Testing
- Unit tests: JSON parsing, DMS conversion, timestamp formatting.
- Integration tests: sample JPEG w/o EXIF; with partial EXIF; missing or malformed sidecar.

## 10. Risks
- PNG/HEIC/MP4 writing in pure Java requires additional libraries / format-specific code.
