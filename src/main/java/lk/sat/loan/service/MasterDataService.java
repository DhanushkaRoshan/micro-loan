package lk.sat.loan.service;

import java.util.List;
import lk.sat.loan.model.Area;
import lk.sat.loan.model.Center;
import lk.sat.loan.model.Coordinator;
import lk.sat.loan.util.Db;

public class MasterDataService {

    public List<Area> areas() {
        return Db.read(s -> s.createSelectionQuery("from Area a order by a.name", Area.class).getResultList());
    }

    public List<Center> centers() {
        return Db.read(s -> s.createSelectionQuery(
                "select c from Center c left join fetch c.area order by c.name", Center.class).getResultList());
    }

    public List<Center> centersInArea(Long areaId) {
        if (areaId == null) {
            return centers();
        }
        return Db.read(s -> s.createSelectionQuery(
                        "select c from Center c left join fetch c.area where c.area.id = :a order by c.name", Center.class)
                .setParameter("a", areaId).getResultList());
    }

    public List<Coordinator> coordinators() {
        return Db.read(s -> s.createSelectionQuery(
                "from Coordinator c order by c.name", Coordinator.class).getResultList());
    }

    public Area saveArea(Area a) {
        return Db.tx(s -> s.merge(a));
    }

    public Center saveCenter(Center c) {
        return Db.tx(s -> s.merge(c));
    }

    public Coordinator saveCoordinator(Coordinator c) {
        return Db.tx(s -> s.merge(c));
    }

    public void deleteArea(Long id) {
        Db.txVoid(s -> {
            Area a = s.find(Area.class, id);
            if (a != null) {
                s.remove(a);
            }
        });
    }

    public void deleteCenter(Long id) {
        Db.txVoid(s -> {
            Center c = s.find(Center.class, id);
            if (c != null) {
                s.remove(c);
            }
        });
    }

    public void deleteCoordinator(Long id) {
        Db.txVoid(s -> {
            Coordinator c = s.find(Coordinator.class, id);
            if (c != null) {
                s.remove(c);
            }
        });
    }

    public boolean centerInUse(Long centerId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select count(c) from Client c where c.center.id = :id", Long.class)
                .setParameter("id", centerId).getSingleResult() > 0);
    }

    public boolean coordinatorInUse(Long coordinatorId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select count(l) from Loan l where l.coordinator.id = :id", Long.class)
                .setParameter("id", coordinatorId).getSingleResult() > 0);
    }

    public boolean areaInUse(Long areaId) {
        return Db.read(s -> s.createSelectionQuery(
                        "select count(c) from Center c where c.area.id = :id", Long.class)
                .setParameter("id", areaId).getSingleResult() > 0);
    }
}
