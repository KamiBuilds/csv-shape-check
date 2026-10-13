import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class CsvShapeCheckTest {
    private static int tests;
    private CsvShapeCheckTest() { }

    public static void main(String[] args) throws Exception {
        valid("simple", "name,score\nAda,42\n", 2, 1);
        invalid("duplicate header", "name,name\nAda,42\n", "record 1: duplicate header");
        valid("quoted multiline and escapes", "\"family,name\",note\r\nAda,\"hello\r\n\"\"world\"\"\"\r\n", 2, 1);
        invalid("quote in unquoted cell", "a,b\nx\"y,z\n", "record 2: quote inside unquoted field");
        invalid("text after closing quote", "a,b\n\"x\"oops,y\n", "record 2: text after closing quote");
        invalid("unclosed quote", "a,b\n\"x,y", "record 2: unterminated quoted field");
        invalid("short record", "a,b\nonly-one\n", "record 2: expected 2 fields, found 1");
        invalid("wide record", "a,b\nx,y,z\n", "record 2: expected 2 fields, found 3");
        invalid("empty input", "", "record 1: missing header");
        invalid("blank header", "a, \nx,y\n", "record 1: blank header");
        valid("UTF-8 BOM", "\ufeffa,b\nx,y\n", 2, 1);
        invalid("BOM duplicate", "\ufeffa,a\nx,y\n", "record 1: duplicate header");
        valid("header only", "a,b", 2, 0);
        valid("header final newline", "a,b\n", 2, 0);
        valid("CR endings", "a,b\rx,y\ru,v\r", 2, 2);
        valid("CRLF endings", "a,b\r\nx,y\r\n", 2, 1);
        valid("mixed endings", "a,b\r\nx,y\ru,v\n", 2, 2);
        valid("trailing empty cell", "a,b\nx,", 2, 1);
        valid("empty data cell", "a\n\n", 1, 1);
        valid("quoted empty data", "a\n\"\"\n", 1, 1);
        valid("quoted headers", "\"a\",\"b\"\nx,y", 2, 1);
        valid("quoted newline", "a,b\n\"x\ny\",z", 2, 1);
        valid("quoted comma", "a,b\n\"x,y\",z", 2, 1);
        valid("escaped quotes", "a\n\"\"\"x\"\"\"", 1, 1);
        valid("header case significant", "a,A\nx,y", 2, 1);
        valid("header spaces significant", "a, a\nx,y", 2, 1);
        valid("quoted BOM header", "\ufeff\"a\",b\nx,y", 2, 1);
        valid("Unicode", "naam,notitie\nZoë,\"火\n🌱\"", 2, 1);
        invalid("empty header", "\n", "record 1: blank header");
        invalid("quoted blank header", "\" \"\n", "record 1: blank header");
        invalid("trailing header comma", "a,\n", "record 1: blank header");
        invalid("decoded duplicate header", "a,\"a\"\n", "record 1: duplicate header");
        invalid("closing quote whitespace", "a\n\"x\" \n", "record 2: text after closing quote");
        invalid("quote after leading space", "a\n \"x\"\n", "record 2: quote inside unquoted field");
        invalid("odd escape count", "a\n\"\"\"", "record 2: unterminated quoted field");
        invalid("record bound", "a\nabcdef\n", "record 2: record character limit exceeded", new CsvShapeCheck.Limits(5, 4, 3));
        invalid("column bound", "a,b,c\nx,y,z\n", "record 1: column limit exceeded", new CsvShapeCheck.Limits(100, 2, 3));
        invalid("record count bound", "a\nx\ny\n", "record 3: data record limit exceeded", new CsvShapeCheck.Limits(100, 4, 1));
        var atBound = CsvShapeCheck.audit(new StringReader("a,b\nx,y\n"), new CsvShapeCheck.Limits(3, 2, 1));
        equal("exact bounds", 1L, atBound.dataRecords());
        tests++;
        invalid("raw quote character bound", "a\n\"xyz\"", "record 2: record character limit exceeded", new CsvShapeCheck.Limits(4, 1, 1));
        invalid("quoted newline bound", "a\n\"x\ny\"", "record 2: record character limit exceeded", new CsvShapeCheck.Limits(4, 1, 1));
        try {
            new CsvShapeCheck.Limits(0, 1, 1);
            throw new AssertionError("zero limit accepted");
        } catch (IllegalArgumentException expected) {
            equal("limit configuration", "limits must be positive", expected.getMessage());
            tests++;
        }
        cli("stdin", new String[0], bytes("a,b\nx,y\n"), 0, "OK: 2 columns, 1 data records\n", "");
        cli("bad width", new String[] {"-"}, bytes("a,b\nprivate-value\n"), 1, "", "INVALID: record 2: expected 2 fields, found 1\n");
        cli("invalid UTF-8", new String[0], new byte[] { (byte) 0xc3, 0x28 }, 2, "", "ERROR: cannot read UTF-8 input\n");
        cli("truncated UTF-8", new String[0], new byte[] { 0x61, 0x0a, (byte) 0xc3 }, 2, "", "ERROR: cannot read UTF-8 input\n");
        cli("unsafe path diagnostic", new String[] {"absent\u001b[31m-file.csv"}, new byte[0], 2, "", "ERROR: cannot read UTF-8 input\n");
        cli("invalid path", new String[] {"bad\u0000path"}, new byte[0], 2, "", "ERROR: cannot read UTF-8 input\n");
        cli("missing file", new String[] {"not-present-unique.csv"}, new byte[0], 2, "", "ERROR: cannot read UTF-8 input\n");
        cli("bad arguments", new String[] {"x", "y"}, new byte[0], 2, "", "Usage: java src/CsvShapeCheck.java [FILE|-|--help]\n");
        cli("help", new String[] {"--help"}, new byte[0], 0, "Usage: java src/CsvShapeCheck.java [FILE|-|--help]\n", "");
        var file = Files.createTempFile("csv-shape-test-", ".csv");
        try {
            Files.writeString(file, "a,b\nx,y\n", StandardCharsets.UTF_8);
            cli("file", new String[] {file.toString()}, new byte[0], 0, "OK: 2 columns, 1 data records\n", "");
        } finally {
            Files.delete(file);
        }
        System.out.println("PASS: " + tests + " cases");
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void cli(String label, String[] args, byte[] input, int code, String stdout, String stderr) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int actual = CsvShapeCheck.run(args, new ByteArrayInputStream(input),
            new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));
        equal(label, code, actual);
        equal(label, stdout, out.toString(StandardCharsets.UTF_8));
        equal(label, stderr, err.toString(StandardCharsets.UTF_8));
        tests++;
    }

    private static void valid(String label, String csv, int columns, long records) throws IOException {
        var result = CsvShapeCheck.audit(new StringReader(csv));
        equal(label, columns, result.columns());
        equal(label, records, result.dataRecords());
        tests++;
    }

    private static void invalid(String label, String csv, String message) throws IOException {
        invalid(label, csv, message, CsvShapeCheck.DEFAULT_LIMITS);
    }

    private static void invalid(String label, String csv, String message, CsvShapeCheck.Limits limits) throws IOException {
        try {
            CsvShapeCheck.audit(new StringReader(csv), limits);
            throw new AssertionError(label + ": accepted invalid CSV");
        } catch (CsvShapeCheck.AuditException expected) {
            equal(label, message, expected.getMessage());
        }
        tests++;
    }

    private static void equal(String label, Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
