package lk.sat.loan.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReportTable {

    private final String title;
    private String subtitle = "";
    private final List<String> columns = new ArrayList<>();
    private final List<Boolean> numeric = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private final Map<String, String> totals = new LinkedHashMap<>();

    public ReportTable(String title) {
        this.title = title;
    }

    public ReportTable column(String name) {
        return column(name, false);
    }

    public ReportTable column(String name, boolean isNumeric) {
        columns.add(name);
        numeric.add(isNumeric);
        return this;
    }

    public ReportTable row(String... cells) {
        List<String> r = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            r.add(i < cells.length && cells[i] != null ? cells[i] : "");
        }
        rows.add(r);
        return this;
    }

    public ReportTable total(String label, String value) {
        totals.put(label, value);
        return this;
    }

    public ReportTable subtitle(String s) {
        this.subtitle = s == null ? "" : s;
        return this;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public List<String> columns() {
        return columns;
    }

    public boolean isNumeric(int col) {
        return col < numeric.size() && numeric.get(col);
    }

    public List<List<String>> rows() {
        return rows;
    }

    public Map<String, String> totals() {
        return totals;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
