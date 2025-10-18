# GPTEW — Google Photos Takeout EXIF Writer

**A pure-Java CLI tool to restore metadata from Google Photos Takeout exports**

After downloading your photos from Google Takeout, you'll notice that many images and videos lack proper EXIF metadata and have incorrect file timestamps. GPTEW fixes this by reading the JSON sidecar files that Google provides and writing the correct metadata back into your media files.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)

## Features

✅ **Pure Java** — Cross-platform, no native dependencies
✅ **JPEG Support** — Writes EXIF timestamps and GPS coordinates
✅ **Safe by default** — Dry-run mode prevents accidental changes
✅ **Smart matching** — Handles truncated sidecar filenames (e.g., `.jp.json` instead of `.jpg.json`)
✅ **GPS filtering** — Automatically ignores invalid (0.0, 0.0) coordinates
✅ **File timestamp sync** — Optionally aligns filesystem timestamps to photo capture time
✅ **Detailed logging** — Comprehensive log files with operation summary
✅ **Backup support** — Create `.bak` files before modification

## Quick Start

### Prerequisites

- Java 17 or higher
- Maven 3.6+ (for building from source)

### Build

```bash
mvn clean package
```

This creates a self-contained JAR at `target/gptew-0.1.1.jar` (~5 MB with all dependencies).

### Usage

#### Dry-run (preview changes without modifying files)

```bash
java -jar target/gptew-0.1.1.jar --input /path/to/google-takeout-folder
```

#### Actually apply changes

```bash
java -jar target/gptew-0.1.1.jar \
  --input /path/to/google-takeout-folder \
  --dry-run=false \
  --set-filetimes
```

#### With backup files

```bash
java -jar target/gptew-0.1.1.jar \
  --input /path/to/google-takeout-folder \
  --dry-run=false \
  --set-filetimes \
  --backup
```

### Command-Line Options

| Option | Default | Description |
|--------|---------|-------------|
| `--input <dir>` | *required* | Root directory containing media and sidecar JSON files |
| `--recursive` | `true` | Scan subdirectories recursively |
| `--dry-run` | `true` | Preview mode - no files are modified |
| `--set-filetimes` | `true` | Update file modification timestamps to match photo capture time |
| `--backup` | `false` | Create `.bak` backup files before modifying originals |

## How It Works

### Input Format

GPTEW expects the typical Google Takeout structure:

```
Google Photos/
├── Photos from 2023/
│   ├── IMG_1234.jpg
│   ├── IMG_1234.jpg.json        ← Sidecar with metadata
│   ├── IMG_5678.jpg
│   └── IMG_5678.jp.json         ← Truncated extension (also supported!)
```

### Metadata Mapping

GPTEW reads the following fields from Google's JSON sidecars and writes them to JPEG EXIF:

| Google JSON Field | JPEG EXIF Target | Notes |
|-------------------|------------------|-------|
| `photoTakenTime.timestamp` | `EXIF:DateTimeOriginal`<br>`EXIF:CreateDate` | Epoch seconds → `yyyy:MM:dd HH:mm:ss` |
| `modificationTime.timestamp` | `EXIF:ModifyDate` | Optional |
| `geoData.latitude`<br>`geoData.longitude` | `GPSLatitude`<br>`GPSLongitude` | DMS format with hemisphere refs |
| `geoData.altitude` | `GPSAltitude` | Optional |

**GPS Data Validation:** Coordinates of (0.0, 0.0) are treated as missing data and ignored.

### Output

After processing, GPTEW provides:

1. **Console Summary:**
   ```
   ================================================================================
   SUMMARY
   ================================================================================
   Total files processed: 1234
   Successfully updated:  856
   Skipped (no sidecar): 234
   Skipped (no data):    123
   Errors:               21
   ================================================================================

   SKIPPED FILES (357):
     /path/to/file1.jpg (no sidecar)
     /path/to/file2.jpg (no relevant data)
     ... (first 50 shown)
     ... and 307 more (see log file for full list)

   Full log written to: /path/to/folder/gptew-20241018-223045.log
   ```

2. **Detailed Log File:**
   - Timestamped filename: `gptew-YYYYMMDD-HHmmss.log`
   - Created in the input directory (or current directory if that fails)
   - Contains every operation with full details

## Current Limitations

**v0.1.1 supports:**
- ✅ JPEG/JPG files only

**Not yet supported (planned for future versions):**
- ⏳ Video files (MP4, MOV)
- ⏳ HEIC/HEIF images
- ⏳ PNG files
- ⏳ Album information
- ⏳ Face tags
- ⏳ Description/title fields

## Safety & Best Practices

⚠️ **Always work on a copy of your photo library first!**

1. **Test with dry-run** before applying changes
2. **Use `--backup`** for extra safety (creates `.bak` files)
3. **Check the log file** after processing to review what happened
4. **Verify results** on a small subset before processing your entire library

The tool is designed to be idempotent — running it multiple times on the same files is safe.

## Technical Details

### Technologies

- **Java 17** — Modern Java with records and pattern matching
- **Apache Commons Imaging 1.0.0-alpha6** — EXIF/TIFF writing
- **metadata-extractor 2.19.0** — EXIF verification
- **Jackson 2.17.2** — JSON parsing
- **Maven** — Build & dependency management

### Build from Source

```bash
# Clone the repository
git clone https://github.com/yourusername/gptew.git
cd gptew

# Build
mvn clean package

# Run tests
mvn test

# Create shaded JAR
mvn package
```

## Troubleshooting

### "No sidecar" messages

Some Google Takeout exports have inconsistent sidecar naming. GPTEW tries multiple variations:
- `photo.jpg` → `photo.jpg.json` (standard)
- `photo.jpg` → `photo.jp.json` (truncated)
- `photo.jpg` → `photo.j.json` (heavily truncated)

If files are still skipped, check that the JSON files exist in the same directory as the media files.

### GPS coordinates showing (0.0, 0.0)

This is normal for photos without GPS data. GPTEW automatically filters these out and won't write them to EXIF.

### Permission errors when creating log file

If GPTEW can't create a log file in the input directory, it will fall back to creating one in the current working directory.

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

### Future enhancements:
- Video file support (MP4/MOV metadata)
- HEIC/PNG support
- Parallel processing for large libraries
- Progress bar for long-running operations
- Album reconstruction from Google's metadata

## License

This project is licensed under the **GNU General Public License v3.0** - see the [LICENSE](LICENSE) file for details.

```
Copyright (C) 2024

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
```

## Acknowledgments

- Inspired by the need to fix metadata from Google Takeout exports
- Built with excellent open-source libraries from Apache, Drew Noakes, and FasterXML

---

**Made with ❤️ for everyone dealing with Google Takeout's metadata quirks**
