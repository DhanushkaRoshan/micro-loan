package lk.sat.loan.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PdfExporter {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, Color.BLACK);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
    private static final Font TOTAL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
    private static final Font FOOT = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.GRAY);
    private static final Color HEADER_BG = new Color(0x1F, 0x4E, 0x79);
    private static final Color STRIPE = new Color(0xF2, 0xF5, 0xF9);

    private PdfExporter() {
    }

    public static Path write(ReportTable table, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        boolean landscape = table.columns().size() > 6;
        Document doc = new Document(landscape ? PageSize.A4.rotate() : PageSize.A4, 32, 32, 36, 36);
        try (OutputStream out = Files.newOutputStream(target)) {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("Sat Loan Management System", TITLE);
            title.setAlignment(Element.ALIGN_CENTER);
            doc.add(title);

            Paragraph heading = new Paragraph(table.title(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
            heading.setAlignment(Element.ALIGN_CENTER);
            heading.setSpacingBefore(4f);
            doc.add(heading);

            if (!table.subtitle().isBlank()) {
                Paragraph sub = new Paragraph(table.subtitle(), SUBTITLE);
                sub.setAlignment(Element.ALIGN_CENTER);
                sub.setSpacingAfter(10f);
                doc.add(sub);
            } else {
                doc.add(new Paragraph(" ", SUBTITLE));
            }

            doc.add(buildTable(table));

            if (!table.totals().isEmpty()) {
                PdfPTable totals = new PdfPTable(2);
                totals.setWidthPercentage(45);
                totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
                totals.setSpacingBefore(12f);
                for (var e : table.totals().entrySet()) {
                    totals.addCell(bodyCell(e.getKey(), false, TOTAL, null));
                    totals.addCell(bodyCell(e.getValue(), true, TOTAL, null));
                }
                doc.add(totals);
            }

            Paragraph footer = new Paragraph(
                    "Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                            + "  -  " + table.rows().size() + " row(s)", FOOT);
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setSpacingBefore(14f);
            doc.add(footer);

            doc.close();
        }
        return target;
    }

    private static PdfPTable buildTable(ReportTable table) {
        PdfPTable pdf = new PdfPTable(Math.max(1, table.columns().size()));
        pdf.setWidthPercentage(100);
        pdf.setHeaderRows(1);

        for (String col : table.columns()) {
            PdfPCell c = new PdfPCell(new Phrase(col, HEADER));
            c.setBackgroundColor(HEADER_BG);
            c.setPadding(5f);
            c.setBorderColor(HEADER_BG);
            pdf.addCell(c);
        }

        int i = 0;
        for (List<String> row : table.rows()) {
            Color bg = (i++ % 2 == 0) ? null : STRIPE;
            for (int col = 0; col < row.size(); col++) {
                pdf.addCell(bodyCell(row.get(col), table.isNumeric(col), CELL, bg));
            }
        }
        return pdf;
    }

    private static PdfPCell bodyCell(String text, boolean numeric, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setPadding(4f);
        c.setHorizontalAlignment(numeric ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        c.setBorderColor(new Color(0xDD, 0xDD, 0xDD));
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        return c;
    }
}
