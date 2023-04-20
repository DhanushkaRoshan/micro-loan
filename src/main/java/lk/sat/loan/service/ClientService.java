package lk.sat.loan.service;

import java.util.List;
import java.util.Optional;
import lk.sat.loan.model.Client;
import lk.sat.loan.util.Db;

public class ClientService {

    public List<Client> findAll() {
        return Db.read(s -> s.createSelectionQuery(
                "select distinct c from Client c left join fetch c.center ce left join fetch ce.area"
                        + " order by c.fullName", Client.class).getResultList());
    }

    public List<Client> search(String term) {
        if (term == null || term.isBlank()) {
            return findAll();
        }
        String like = "%" + term.trim().toLowerCase() + "%";
        return Db.read(s -> s.createSelectionQuery(
                        "select distinct c from Client c left join fetch c.center ce left join fetch ce.area"
                                + " where lower(c.fullName) like :q or lower(c.clientCode) like :q"
                                + " or lower(coalesce(c.nicNumber, '')) like :q"
                                + " or lower(coalesce(c.phone, '')) like :q"
                                + " or lower(coalesce(c.village, '')) like :q"
                                + " order by c.fullName", Client.class)
                .setParameter("q", like).getResultList());
    }

    public Optional<Client> findById(Long id) {
        return Db.read(s -> Optional.ofNullable(s.find(Client.class, id)));
    }

    public byte[] loadPhoto(Long clientId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select c.photo from Client c where c.id = :id", byte[].class)
                .setParameter("id", clientId).uniqueResult());
    }

    public Client save(Client c) {
        return Db.tx(s -> s.merge(c));
    }

    public void delete(Long id) {
        Db.txVoid(s -> {
            Client c = s.find(Client.class, id);
            if (c != null) {
                s.remove(c);
            }
        });
    }

    public long loanCount(Long clientId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select count(l) from Loan l where l.client.id = :id", Long.class)
                .setParameter("id", clientId).getSingleResult());
    }

    public boolean codeTaken(String code, Long excludingId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select count(c) from Client c where c.clientCode = :c and (:id is null or c.id <> :id)", Long.class)
                .setParameter("c", code).setParameter("id", excludingId).getSingleResult() > 0);
    }

    public boolean nicTaken(String nic, Long excludingId) {
        if (nic == null || nic.isBlank()) {
            return false;
        }
        return Db.read(s -> s.createSelectionQuery(
                        "select count(c) from Client c where c.nicNumber = :n and (:id is null or c.id <> :id)", Long.class)
                .setParameter("n", nic).setParameter("id", excludingId).getSingleResult() > 0);
    }

    public String nextClientCode() {
        return Db.read(s -> {
            String max = s.createSelectionQuery(
                            "select max(c.clientCode) from Client c where c.clientCode like 'C%'", String.class)
                    .uniqueResult();
            int next = 1;
            if (max != null && max.length() > 1) {
                try {
                    next = Integer.parseInt(max.substring(1).trim()) + 1;
                } catch (NumberFormatException ignored) {
                }
            }
            return String.format("C%04d", next);
        });
    }
}
