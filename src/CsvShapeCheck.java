import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Read-only CSV structure audit. */
public final class CsvShapeCheck {
    private CsvShapeCheck() { }

    public record Result(int columns, long dataRecords) { }

    public record Limits(int recordChars, int columns, int dataRecords) {
        public Limits {
            if (recordChars < 1 || columns < 1 || dataRecords < 1) {
                throw new IllegalArgumentException("limits must be positive");
            }
        }
    }

    public static final Limits DEFAULT_LIMITS = new Limits(1_048_576, 4096, 1_000_000);

    public static final class AuditException extends IOException {
        private static final long serialVersionUID = 1L;
        AuditException(long record, String reason) {
            super("record " + record + ": " + reason);
        }
    }

    private enum State { START, UNQUOTED, QUOTED, AFTER_QUOTE }

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err));
    }

    /** Exit codes: 0 valid/help, 1 invalid CSV or input limit, 2 usage/read failure. */
    public static int run(String[] args, InputStream stdin, PrintStream out, PrintStream err) {
        String usage = "Usage: java src/CsvShapeCheck.java [FILE|-|--help]\n";
        if (args.length > 1) {
            err.print(usage);
            return 2;
        }
        if (args.length == 1 && args[0].equals("--help")) {
            out.print(usage);
            return 0;
        }
        try {
            Result result;
            if (args.length == 0 || args[0].equals("-")) {
                result = audit(new InputStreamReader(stdin, StandardCharsets.UTF_8.newDecoder()));
            } else {
                try (var stream = Files.newInputStream(Path.of(args[0]))) {
                    result = audit(new InputStreamReader(stream, StandardCharsets.UTF_8.newDecoder()));
                }
            }
            out.print("OK: " + result.columns() + " columns, " + result.dataRecords() + " data records\n");
            return 0;
        } catch (AuditException invalid) {
            err.print("INVALID: " + invalid.getMessage() + "\n");
            return 1;
        } catch (IOException | IllegalArgumentException | SecurityException unreadable) {
            err.print("ERROR: cannot read UTF-8 input\n");
            return 2;
        }
    }

    public static Result audit(Reader input) throws IOException {
        return audit(input, DEFAULT_LIMITS);
    }

    public static Result audit(Reader input, Limits limits) throws IOException {
        var reader = new PushbackReader(new BufferedReader(input), 1);
        int first = reader.read();
        if (first != -1 && first != '\ufeff') {
            reader.unread(first);
        }
        List<String> names = nextRecord(reader, 1, limits);
        if (names == null) {
            throw new AuditException(1, "missing header");
        }
        var seen = new HashSet<String>();
        for (String name : names) {
            if (name.isBlank()) {
                throw new AuditException(1, "blank header");
            }
            if (!seen.add(name)) {
                throw new AuditException(1, "duplicate header");
            }
        }
        long records = 0;
        int next;
        while ((next = reader.read()) != -1) {
            if (records == limits.dataRecords()) {
                throw new AuditException(records + 2, "data record limit exceeded");
            }
            reader.unread(next);
            List<String> row = nextRecord(reader, records + 2, limits);
            if (row.size() != names.size()) {
                throw new AuditException(records + 2,
                    "expected " + names.size() + " fields, found " + row.size());
            }
            records++;
        }
        return new Result(names.size(), records);
    }

    private static List<String> nextRecord(PushbackReader reader, long record, Limits limits) throws IOException {
        var fields = new ArrayList<String>();
        var field = new StringBuilder();
        State state = State.START;
        boolean started = false;
        int chars = 0;
        int c;
        while ((c = reader.read()) != -1) {
            started = true;
            boolean terminator = state != State.QUOTED && (c == '\r' || c == '\n');
            if (!terminator && ++chars > limits.recordChars()) {
                throw new AuditException(record, "record character limit exceeded");
            }
            if (state == State.QUOTED) {
                if (c == '"') {
                    state = State.AFTER_QUOTE;
                } else {
                    field.append((char) c);
                }
            } else if (state == State.AFTER_QUOTE && c == '"') {
                field.append('"');
                state = State.QUOTED;
            } else if (c == ',') {
                fields.add(field.toString());
                if (fields.size() >= limits.columns()) {
                    throw new AuditException(record, "column limit exceeded");
                }
                field.setLength(0);
                state = State.START;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) {
                        reader.unread(next);
                    }
                }
                fields.add(field.toString());
                return fields;
            } else if (state == State.AFTER_QUOTE) {
                throw new AuditException(record, "text after closing quote");
            } else if (c == '"') {
                if (state != State.START) {
                    throw new AuditException(record, "quote inside unquoted field");
                }
                state = State.QUOTED;
            } else {
                field.append((char) c);
                state = State.UNQUOTED;
            }
        }
        if (state == State.QUOTED) {
            throw new AuditException(record, "unterminated quoted field");
        }
        if (!started) {
            return null;
        }
        fields.add(field.toString());
        return fields;
    }
}
