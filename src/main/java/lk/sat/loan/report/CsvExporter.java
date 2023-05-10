package lk.sat.loan.report;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvExporter {

    private CsvExporter() {
    }

    public static Path write(ReportTable table, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(target), StandardCharsets.UTF_8))) {
            out.write('﻿');
            out.write(escape(table.title()));
            out.newLine();
            if (!table.subtitle().isBlank()) {
                out.write(escape(table.subtitle()));
                out.newLine();
            }
            out.newLine();

            out.write(join(table.columns()));
            out.newLine();
            for (List<String> row : table.rows()) {
                out.write(join(row));
                out.newLine();
            }
            if (!table.totals().isEmpty()) {
                out.newLine();
                for (var e : table.totals().entrySet()) {
                    out.write(escape(e.getKey()) + "," + escape(e.getValue()));
                    out.newLine();
                }
            }
        }
        return target;
    }

    private static String join(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells.get(i)));
        }
        return sb.toString();
    }

    private static String escape(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
