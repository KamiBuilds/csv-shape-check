#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
javac -encoding UTF-8 -Xlint:all -Werror -d build src/CsvShapeCheck.java tests/CsvShapeCheckTest.java
java -cp build CsvShapeCheckTest
actual=$(java src/CsvShapeCheck.java examples/valid.csv)
[ "$actual" = 'OK: 3 columns, 2 data records' ]
java -cp build CsvShapeCheck examples/valid.csv
java src/CsvShapeCheck.java --help
java src/CsvShapeCheck.java - < examples/valid.csv
status=0
java src/CsvShapeCheck.java examples/invalid.csv || status=$?
[ "$status" -eq 1 ]
printf '%s\n' 'PASS: source launcher, compiled CLI and invalid example'
