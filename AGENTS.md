# AGENTS.md
Agent guide for `gpmf-java` (artifact: `gptew`).
This is a Java 17 Maven CLI that merges Google Takeout sidecar metadata into media files.

## 1) Repository Snapshot
- Language: Java 17
- Build tool: Maven (`pom.xml`)
- Test framework: JUnit 5 (`junit-jupiter`)
- Main class: `dev.pneumann.gptew.App`
- Packaging: shaded executable JAR (`maven-shade-plugin`)
- Current write target: JPEG EXIF (PNG/MP4 classes are placeholders)
- Source root: `src/main/java/dev/pneumann/gptew/...`
- Test root: `src/test/java/dev/pneumann/gptew/...`
- Build output: `target/`

## 2) Build, Lint, Test Commands
Run commands from repo root.

### Build
- Compile only: `mvn clean compile`
- Full build (tests included): `mvn clean package`
- Fast build (skip tests): `mvn clean -DskipTests package`
- Expected artifact: `target/gptew-0.1.1.jar`

### Test
- Full suite: `mvn test`
- Clean test run: `mvn clean test`

Run a single test class (Surefire):
- `mvn -Dtest=SidecarParserTest test`
- `mvn -Dtest=JpegExifWriterTest test`
- `mvn -Dtest=ExifUtilTest test`

Run a single test method:
- `mvn -Dtest=SidecarParserTest#testParse_ZeroCoordinates_AreFiltered test`
- `mvn -Dtest=JpegExifWriterTest#testApply_WritesGpsCoordinates test`

Run selected tests/patterns:
- `mvn -Dtest=SidecarParserTest,JpegExifWriterTest test`
- `mvn -Dtest='*Exif*Test' test`

### Lint / Static Analysis
- No dedicated lint/format/static-analysis plugin is configured in `pom.xml`.
- Practical quality gate today: `mvn clean test`
- If lint tools are added (Checkstyle/PMD/SpotBugs/Spotless), document exact commands here.

### Run CLI Locally
- Dry-run (default):
  - `java -jar target/gptew-0.1.1.jar --input /path/to/takeout`
- Apply changes:
  - `java -jar target/gptew-0.1.1.jar --input /path/to/takeout --dry-run=false --set-filetimes`
- Optional backups:
  - add `--backup`

## 3) Code Style Conventions
Follow existing patterns unless you have a strong, explicit reason to refactor broadly.

### Formatting
- Indentation: 2 spaces, no tabs.
- Braces: K&R style (`if (...) {`).
- Keep methods focused and blocks shallow where possible.
- Use blank lines to separate logical units.

### Imports
- Prefer explicit imports in production code.
- Do not use wildcard imports in production (`import x.y.*`).
- Test code may use static wildcard assertions (`import static ...Assertions.*;`).
- Keep import grouping stable (project/libs, then `java.*`, then static imports).

### Types / Nullability
- Java 17 features are allowed (`record`, `var`, pattern matching `instanceof`).
- Use primitives for required values.
- Use boxed types (`Long`, `Double`) when null means metadata is missing.
- Preserve current `GoogleSidecar` semantics: null == absent value.
- Prefer immutable carriers (`record`) for parsed metadata.

### Naming
- Packages: lowercase (`dev.pneumann.gptew.<area>`)
- Classes/records: `PascalCase`
- Methods/fields/locals: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Tests: descriptive names, often `testXxx_Yyy`

### Class Design
- Keep utility classes `final` with private constructors if stateless.
- Keep JSON parsing in parser classes; avoid mixing with file-write orchestration.
- Keep side effects in scanner/writer layers (`MediaScanner`, `JpegExifWriter`).
- Prefer small helper methods over monolithic logic.

## 4) Error Handling & Logging
- Fail fast for fatal startup/input validation errors in CLI entry paths.
- For per-file processing, catch errors, log context, continue processing.
- Include path + concise reason in error messages.
- Use checked exceptions at I/O boundaries when useful.
- Wrap exceptions only when adding clear, actionable context.
- Silent swallowing is only acceptable for best-effort cleanup/log closing.

Operational behavior to preserve:
- `MediaScanner` keeps running after individual file failures.
- Summary counters and skipped/error file lists are printed at the end.
- Log file creation is best effort (fallback from input dir to CWD).

## 5) File I/O, Metadata Safety, and Domain Rules
- Prefer `java.nio.file.Path` + `Files` APIs.
- Metadata writes should follow safe write flow:
  - write temp output,
  - optionally create `.bak`,
  - atomically replace original when possible.
- Keep dry-run mode non-destructive.
- Preserve idempotency where feasible.

Domain constraints already used in code/tests:
- Sidecar timestamps are epoch seconds.
- EXIF timestamp format is local time: `yyyy:MM:dd HH:mm:ss`.
- GPS `(0.0, 0.0)` is treated as missing metadata.

## 6) Testing Guidelines
- Add or update tests whenever behavior changes.
- Prefer JUnit 5 with `@TempDir` for filesystem behavior.
- Keep tests deterministic and isolated.
- Use tolerances for floating-point GPS assertions.
- Use `assertThrows` for failure-path validation.
- Integration-style tests that write/read EXIF are encouraged.

## 7) Dependency & Build Policy
- Avoid casual dependency upgrades; justify version changes.
- Keep Java target at 17 unless intentionally changing platform support.
- Maintain shaded JAR behavior (`App` as main class).
- If build plugins change, update this document's command section.

## 8) Cursor / Copilot Rules Status
Checked these locations:
- `.cursor/rules/`
- `.cursorrules`
- `.github/copilot-instructions.md`

Current state in this repo:
- No Cursor rule files found.
- No Copilot instruction file found.

If these files are added later, treat them as authoritative and merge relevant rules into this guide.

## 9) Agent Checklist Before Hand-off
- Run targeted tests for touched code.
- Run `mvn test` when changes are broad.
- Run `mvn clean package` when packaging/runtime behavior changes.
- Update `README.md`/`AGENTS.md` if commands or behavior changed.
- Keep changes scoped; avoid unrelated refactors.
