# CSV Shape Check

A dependency-free, read-only Java CLI that catches CSV import mistakes without printing cell contents.

Use it before importing a spreadsheet export into a database or sharing a dataset: it checks strict quoting, header names and record widths. It reports the first problem with a **logical record number**, not a physical line number (quoted cells can span lines).

## Quick start

Install a **JDK 17 or newer** with both `java` and `javac` on your PATH, plus Git. No Maven, Gradle, downloads at runtime, or package installation is needed.

```sh
git clone https://github.com/KamiBuilds/csv-shape-check.git
cd csv-shape-check
java src/CsvShapeCheck.java examples/valid.csv
```

Expected result for the included example:

```text
OK: 3 columns, 2 data records
```

Check your own file or stdin:

```sh
java src/CsvShapeCheck.java export.csv
java src/CsvShapeCheck.java - < examples/valid.csv
java src/CsvShapeCheck.java --help
```

The stdin redirection example is for POSIX shells and Windows cmd.exe, not PowerShell. In PowerShell use the file argument; avoid `Get-Content` pipelines that can change encoding or newlines. With no argument, the CLI also reads stdin.

For repeated runs, compile once:

```sh
javac -encoding UTF-8 -Xlint:all -Werror -d build src/CsvShapeCheck.java
java -cp build CsvShapeCheck examples/valid.csv
```

## What is checked

- UTF-8 decoding is strict; malformed or incomplete byte sequences are rejected. One initial UTF-8 BOM is accepted.
- Commas separate fields. Double-quoted fields can contain commas, CR/LF, and escaped double quotes (`""`).
- A quote may only open at the start of a field. Only a comma, record ending or EOF may follow a closing quote. Spaces outside quotes are not trimmed.
- LF, CRLF and bare CR terminate records outside quoted fields; mixed endings are accepted. A final terminator does not create a phantom record.
- The first record is the mandatory header. Names must not be empty or Java `String.isBlank()` whitespace-only; decoded names must be unique. Comparison is exact and case-sensitive, with no trimming or Unicode normalization.
- Every data record must have the header's field count. Empty cells are allowed. Blank records are **not skipped**: they contain one empty cell.
- CLI limits: **1,048,576 UTF-16 code units per raw logical record**, **4,096 columns**, **1,000,000 data records**. Quotes, commas and quoted line endings count toward the character limit; the initial BOM and unquoted record terminators do not. Supplementary Unicode characters use two units. Exceeding a limit is an invalid-input result, not a claim about the remainder of the file.

The implementation streams the input and keeps only the header and current bounded record in memory. The Java API `audit(Reader, Limits)` permits positive custom limits; `audit(Reader)` uses the CLI defaults. Caller-owned readers and stdin are not closed by the API; file streams opened by the CLI are closed.

## Diagnostics and exit codes

```sh
java src/CsvShapeCheck.java examples/invalid.csv
```

This intentionally exits **1** and writes to stderr:

```text
INVALID: record 3: expected 3 fields, found 2
```

| Exit | Meaning |
| --- | --- |
| 0 | Valid complete input, or `--help` |
| 1 | Invalid CSV, header/width problem, or an input limit exceeded |
| 2 | Incorrect argument count, unreadable input/path, or invalid UTF-8 |

Diagnostics contain fixed messages and counts only: no header names, cell values, input paths or raw exception messages. An invalid UTF-8 file can report a read error before an earlier CSV structure error because the decoder buffers input. The tool never edits input files or contacts a network.

## Test and automated checks

Run from the repository root on Linux, macOS, Windows cmd.exe or PowerShell:

```sh
javac -encoding UTF-8 -Xlint:all -Werror -d build src/CsvShapeCheck.java tests/CsvShapeCheckTest.java
java -cp build CsvShapeCheckTest
git diff --check
```

On POSIX shells the same compilation/tests plus source-launcher example and invalid-example exit-code checks are bundled:

```sh
sh scripts/check.sh
```

Tests cover quoting, multiline cells, escaped quotes, headers, record widths, BOM, UTF-8 failures, bounds, stdin/file input, CLI exit statuses and diagnostic redaction. These commands are CI-ready. No hosted Actions workflow is included: the publication credential lacks GitHub's workflow permission. Local and clean-clone checks are the publication gates; this is not a claim of hosted CI success.

## Platform status

- **Live-tested:** Linux x86_64, Temurin JDK 17.0.20.1.
- **Designed but unverified:** macOS and Windows with JDK 17+, other Linux distributions/CPU architectures, newer JDKs. The Java test commands are platform-neutral; `scripts/check.sh` requires a POSIX shell.

## Scope and safety

This is a structural checker, not a schema/type validator, spreadsheet-formula sanitizer, delimiter detector or CSV repair tool. Semicolon/tab dialects are not supported. It does not validate data correctness or make CSV safe to open in a spreadsheet. Control characters inside cells are permitted and are never printed. Use complete, stable files or trusted streams: concurrent modifications are not snapshot-isolated, and stdin or special files can block waiting for input. There is no read timeout. Source-file launch may use JVM temporary compiler storage; compiled tests create only `build/` in this clone.

## Update and cleanup

Update a clean checkout:

```sh
git pull --ff-only
```

Re-run the test commands after updating. No global installation was made. Remove the generated `build/` directory to clean compiled files (`rm -rf build` in POSIX shells, `rmdir /s /q build` in cmd.exe, or `Remove-Item -Recurse -Force build` in PowerShell), from the repository root only. To uninstall, delete this clone after saving any CSV files you placed in it. Do not delete the JDK unless you intentionally installed it solely for this tool.

## License

MIT. See [LICENSE](LICENSE).
