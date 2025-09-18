package infrastructure.jdbc;

import domain.common.Money;
import domain.inventory.Batch;
import domain.inventory.InventoryReservation;
import domain.inventory.Item;
import ports.out.InventoryRepository;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcInventoryRepository implements InventoryRepository {

    @Override
    public Optional<Item> findItemByCode(String itemCode) {
        String sql = "SELECT id, item_code, name, unit_price FROM items WHERE item_code=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Item(
                        rs.getLong("id"),
                        rs.getString("item_code"),
                        rs.getString("name"),
                        new Money(rs.getBigDecimal("unit_price"))
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException("findItemByCode failed", e);
        }
    }

    @Override
    public Money priceOf(String itemCode) {
        String sql = "SELECT unit_price FROM items WHERE item_code=?";
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new RuntimeException("Unknown item code: " + itemCode);
                return new Money(rs.getBigDecimal("unit_price"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("priceOf failed", e);
        }
    }

    @Override
    public List<Batch> findBatchesOnShelf(String itemCode) {
        String sql = """
            SELECT id, item_code, expiry, qty_on_shelf, qty_in_store
            FROM batches
            WHERE item_code=? AND qty_on_shelf > 0
            ORDER BY (expiry IS NULL), expiry ASC
            """;
        List<Batch> list = new ArrayList<>();
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("expiry");
                    list.add(new Batch(
                            rs.getLong("id"),
                            rs.getString("item_code"),
                            d == null ? null : d.toLocalDate(),
                            rs.getInt("qty_on_shelf"),
                            rs.getInt("qty_in_store")
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("findBatchesOnShelf failed", e);
        }
    }

    @Override
    public void commitReservations(Iterable<InventoryReservation> reservations) {
        String sql = "UPDATE batches SET qty_on_shelf = qty_on_shelf - ? WHERE id=? AND item_code=? AND qty_on_shelf>=?";
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (InventoryReservation r : reservations) {
                    ps.setInt(1, r.quantity);
                    ps.setLong(2, r.batchId);
                    ps.setString(3, r.itemCode);
                    ps.setInt(4, r.quantity);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        throw new IllegalStateException("Concurrent/insufficient batch " + r.batchId);
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("commitReservations failed", e);
        }
    }

    @Override
    public List<Batch> findBatchesInStore(String itemCode) {
        String sql = """
            SELECT id, item_code, expiry, qty_on_shelf, qty_in_store
            FROM batches
            WHERE item_code=? AND qty_in_store > 0
            ORDER BY (expiry IS NULL), expiry ASC
            """;
        List<Batch> list = new ArrayList<>();
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date d = rs.getDate("expiry");
                    list.add(new Batch(
                            rs.getLong("id"),
                            rs.getString("item_code"),
                            d == null ? null : d.toLocalDate(),
                            rs.getInt("qty_on_shelf"),
                            rs.getInt("qty_in_store")
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException("findBatchesInStore failed", e);
        }
    }

    @Override
    public void commitStoreReservations(Iterable<InventoryReservation> reservations) {
        String sql = "UPDATE batches SET qty_in_store = qty_in_store - ? WHERE id=? AND item_code=? AND qty_in_store>=?";
        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (InventoryReservation r : reservations) {
                    ps.setInt(1, r.quantity);
                    ps.setLong(2, r.batchId);
                    ps.setString(3, r.itemCode);
                    ps.setInt(4, r.quantity);
                    if (ps.executeUpdate() == 0) {
                        c.rollback();
                        throw new IllegalStateException("Concurrent/insufficient store batch " + r.batchId);
                    }
                }
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("commitStoreReservations failed", e);
        }
    }

    // ---- Restock support ----
    @Override
    public int shelfQty(String itemCode) {
        String sql = "SELECT COALESCE(SUM(qty_on_shelf),0) FROM batches WHERE item_code=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) {
            throw new RuntimeException("shelfQty failed", e);
        }
    }

    @Override
    public int storeQty(String itemCode) {
        String sql = "SELECT COALESCE(SUM(qty_in_store),0) FROM batches WHERE item_code=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        } catch (SQLException e) {
            throw new RuntimeException("storeQty failed", e);
        }
    }

    @Override
    public int mainStoreQty(String itemCode) {
        // TODO: Implement logic to return quantity in main store area
        return 0;
    }

    @Override
    public void moveStoreToShelfFEFO(String itemCode, int qty) {
        if (qty <= 0) return;

        String select = """
            SELECT id, qty_on_shelf, qty_in_store
            FROM batches
            WHERE item_code=? AND qty_in_store > 0
            ORDER BY (expiry IS NULL), expiry ASC
            FOR UPDATE
            """;
        String update = "UPDATE batches SET qty_on_shelf = qty_on_shelf + ?, qty_in_store = qty_in_store - ? WHERE id=?";

        try (Connection c = Db.get()) {
            c.setAutoCommit(false);
            try (PreparedStatement psSel = c.prepareStatement(select);
                 PreparedStatement psUpd = c.prepareStatement(update)) {

                psSel.setString(1, itemCode);
                int remaining = qty;
                int moved = 0;

                try (ResultSet rs = psSel.executeQuery()) {
                    while (rs.next() && remaining > 0) {
                        long id = rs.getLong("id");
                        int inStore = rs.getInt("qty_in_store");
                        if (inStore <= 0) continue;

                        int move = Math.min(inStore, remaining);
                        psUpd.setInt(1, move);
                        psUpd.setInt(2, move);
                        psUpd.setLong(3, id);
                        psUpd.addBatch();

                        remaining -= move;
                        moved += move;
                    }
                }

                if (moved == 0) {
                    c.rollback();
                    throw new IllegalStateException("No stock in store to move for " + itemCode);
                }

                psUpd.executeBatch();
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("moveStoreToShelfFEFO failed", e);
        }
    }

    // ---------- ITEM (catalog) CRUD ----------
    @Override
    public void createItem(String itemCode, String name, Money unitPrice) {
        String sql = "INSERT INTO items (item_code, name, unit_price) VALUES (?,?,?)";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            ps.setString(2, name);
            ps.setBigDecimal(3, unitPrice.asBigDecimal());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("createItem failed", e);
        }
    }

    @Override
    public void renameItem(String itemCode, String newName) {
        String sql = "UPDATE items SET name=? WHERE item_code=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setString(2, itemCode);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Item not found: " + itemCode);
        } catch (SQLException e) {
            throw new RuntimeException("renameItem failed", e);
        }
    }

    @Override
    public void setItemPrice(String itemCode, Money newPrice) {
        String sql = "UPDATE items SET unit_price=? WHERE item_code=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBigDecimal(1, newPrice.asBigDecimal());
            ps.setString(2, itemCode);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Item not found: " + itemCode);
        } catch (SQLException e) {
            throw new RuntimeException("setItemPrice failed", e);
        }
    }

    @Override
    public void deleteItem(String itemCode) {
        String sql = "DELETE FROM items WHERE item_code=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Item not found: " + itemCode);
        } catch (SQLException e) {
            throw new RuntimeException("deleteItem failed (referenced by batches/bill_lines?)", e);
        }
    }

    // ---------- Batch admin ----------
    @Override
    public void addBatch(String itemCode, LocalDate expiry, int qtyOnShelf, int qtyInStore) {
        if (itemCode == null || itemCode.isBlank())
            throw new IllegalArgumentException("itemCode is required");
        if (qtyOnShelf < 0 || qtyInStore < 0)
            throw new IllegalArgumentException("Quantities must be >= 0");

        final String checkItemSql = "SELECT 1 FROM items WHERE item_code = ?";
        final String insertSql =
                "INSERT INTO batches (item_code, expiry, qty_on_shelf, qty_in_store) VALUES (?,?,?,?)";

        try (Connection c = Db.get()) {
            try (PreparedStatement chk = c.prepareStatement(checkItemSql)) {
                chk.setString(1, itemCode.trim());
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next()) {
                        throw new IllegalArgumentException("Unknown item: " + itemCode);
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(insertSql)) {
                ps.setString(1, itemCode.trim());
                if (expiry == null) {
                    ps.setNull(2, Types.DATE);
                } else {
                    ps.setDate(2, Date.valueOf(expiry));
                }
                ps.setInt(3, qtyOnShelf);
                ps.setInt(4, qtyInStore);
                int affected = ps.executeUpdate();
                if (affected != 1) throw new IllegalStateException("Insert batch failed (affected=" + affected + ")");
            }
        } catch (SQLException e) {
            throw new RuntimeException("addBatch failed for " + itemCode, e);
        }
    }

    @Override
    public void editBatchQuantities(long batchId, int qtyOnShelf, int qtyInStore) {
        String sql = "UPDATE batches SET qty_on_shelf=?, qty_in_store=? WHERE id=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, qtyOnShelf);
            ps.setInt(2, qtyInStore);
            ps.setLong(3, batchId);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Batch not found: " + batchId);
        } catch (SQLException e) {
            throw new RuntimeException("editBatchQuantities failed", e);
        }
    }

    @Override
    public void updateBatchExpiry(long batchId, LocalDate newExpiry) {
        String sql = "UPDATE batches SET expiry=? WHERE id=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (newExpiry == null) ps.setNull(1, Types.DATE); else ps.setDate(1, Date.valueOf(newExpiry));
            ps.setLong(2, batchId);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Batch not found: " + batchId);
        } catch (SQLException e) {
            throw new RuntimeException("updateBatchExpiry failed", e);
        }
    }

    @Override
    public void deleteBatch(long batchId) {
        String sql = "DELETE FROM batches WHERE id=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, batchId);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Batch not found: " + batchId);
        } catch (SQLException e) {
            throw new RuntimeException("deleteBatch failed", e);
        }
    }

    // ---------- NEW: Restock level ----------
    @Override
    public int restockLevel(String itemCode) {
        // If you added a column items.restock_level, we read it; otherwise default to 50.
        final String sql = "SELECT restock_level FROM items WHERE item_code=?";
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Unknown item: " + itemCode);
                int lvl = rs.getInt(1);
                return rs.wasNull() ? 50 : lvl; // null -> default
            }
        } catch (SQLException e) {
            // Fallback if the column doesn't exist yet
            return 50;
        }
    }

    @Override
    public void moveMainToStoreFEFO(String itemCode, int qty) {
        // TODO: Implement main store to store movement logic (FEFO)
        throw new UnsupportedOperationException("moveMainToStoreFEFO not implemented yet.");
    }

    @Override
    public void moveMainToShelfFEFO(String itemCode, int qty) {
        // TODO: Implement main store to shelf movement logic (FEFO)
        throw new UnsupportedOperationException("moveMainToShelfFEFO not implemented yet.");
    }
}
