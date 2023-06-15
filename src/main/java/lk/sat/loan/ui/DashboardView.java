package lk.sat.loan.ui;

import java.time.LocalDate;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import lk.sat.loan.service.LoanService;
import lk.sat.loan.service.PortfolioSummary;
import lk.sat.loan.util.Money;

public class DashboardView extends VBox implements MainShell.Refreshable {

    private final LoanService loans = new LoanService();
    private final FlowPane cards = new FlowPane(14, 14);

    public DashboardView() {
        super(16);
        setPadding(new Insets(4));
        cards.setPadding(new Insets(0));
        getChildren().addAll(Fx.h1("Dashboard"), cards);
    }

    @Override
    public void refresh() {
        cards.getChildren().clear();
        PortfolioSummary s = loans.portfolio(LocalDate.now());

        cards.getChildren().addAll(
                statCard("Clients", String.valueOf(s.clientCount()), null),
                statCard("Active Loans", String.valueOf(s.activeLoanCount()), "accent-blue"),
                statCard("Total Loans", String.valueOf(s.loanCount()), null),
                statCard("Disbursed", Money.format(s.totalDisbursed()), null),
                statCard("Expected", Money.format(s.totalExpected()), null),
                statCard("Collected", Money.format(s.totalCollected()), "accent-green"),
                statCard("Outstanding", Money.format(s.outstanding()), null),
                statCard("Arrears", Money.format(s.arrears()), "accent-red"));
    }

    private VBox statCard(String label, String value, String accent) {
        Label val = new Label(value);
        val.getStyleClass().add("stat-value");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-label");

        VBox card = new VBox(4, val, lbl);
        card.getStyleClass().add("stat-card");
        if (accent != null) {
            card.getStyleClass().add(accent);
        }
        return card;
    }
}
