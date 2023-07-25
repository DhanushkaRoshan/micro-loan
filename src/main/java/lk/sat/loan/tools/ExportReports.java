package lk.sat.loan.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lk.sat.loan.report.ReportService;
import lk.sat.loan.report.ReportTable;
import lk.sat.loan.util.Db;

public final class ExportReports {

    private ExportReports() {
    }

    public static void main(String[] args) throws Exception {
        Db.start();
        ReportService reports = new ReportService();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate wideStart = today.minusYears(20);

        List<ReportTable> tables = new ArrayList<>(List.of(
                reports.loanBook(today),
                reports.arrears(today),
                reports.settledLoans(today),
                reports.collections(wideStart, today),
                reports.collectionsGrouped(wideStart, today, ReportService.GroupBy.CENTER),
                reports.collectionsGrouped(wideStart, today, ReportService.GroupBy.AREA),
                reports.collectionsGrouped(wideStart, today, ReportService.GroupBy.COORDINATOR)));

        System.out.println("Collections window: " + wideStart + " to " + today
                + "  (month-to-date would be " + monthStart + " to " + today + ")\n");

        int failures = 0;
        for (ReportTable t : tables) {
            try {
                Path pdf = reports.exportPdf(t);
                Path csv = reports.exportCsv(t);
                System.out.printf("%-34s %4d rows   %s (%,d B)   %s (%,d B)%n",
                        t.title(), t.rows().size(),
                        pdf.getFileName(), Files.size(pdf),
                        csv.getFileName(), Files.size(csv));
                if (!isPdf(pdf)) {
                    System.out.println("   ! " + pdf + " is not a valid PDF");
                    failures++;
                }
            } catch (Exception e) {
                System.out.println("   ! " + t.title() + " failed: " + e.getMessage());
                failures++;
            }
        }

        System.out.println(failures == 0
                ? "\nAll reports exported successfully."
                : "\n" + failures + " report(s) failed.");
        Db.stop();
        System.exit(failures == 0 ? 0 : 1);
    }

    private static boolean isPdf(Path p) throws java.io.IOException {
        byte[] head = new byte[5];
        try (var in = Files.newInputStream(p)) {
            return in.read(head) == 5 && new String(head).equals("%PDF-");
        }
    }
}
