# gpmf — Google Photos Metadata Fix (pure Java)

Pure-Java CLI to merge Google Photos Takeout sidecars (`*.json`) into your media **(JPEG first)** and optionally align filesystem timestamps.

> Java 17 • Maven • Apache Commons Imaging (write EXIF) • metadata-extractor (verify) • Jackson (JSON)

## Quick start

```bash
# Build (force update deps in case of cached resolution failures)
mvn -U -q -DskipTests package

# Run (dry-run by default)
java -jar target/gpmf-0.1.1.jar --input /path/to/folder

# Actually write EXIF + set file times
java -jar target/gpmf-0.1.1.jar --input /path/to/folder --dry-run=false --set-filetimes
```

**Tip:** Work on a copy of your library. Use `--backup` to create `.bak` files before in-place updates.
