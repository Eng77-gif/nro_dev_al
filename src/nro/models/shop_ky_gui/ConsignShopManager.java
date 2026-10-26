package nro.models.shop_ky_gui;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import nro.models.data.LocalManager;
import org.json.simple.JSONValue;

/**
 *
 * @author By Mr Blue
 * 
 */

public class ConsignShopManager {

    private static ConsignShopManager instance;

    public static synchronized ConsignShopManager gI() {
        if (instance == null) {
            instance = new ConsignShopManager();
        }
        return instance;
    }

    public long lastTimeUpdate;

    public String[] tabName = {"Áo Quần", "Găng Tay", "Phụ Kiện", "Linh tinh", ""};

    public List<ConsignItem> listItem = new CopyOnWriteArrayList<>();

    public synchronized void save() {
        List<ConsignItem> items = new ArrayList<>(this.listItem);
        try (Connection con = LocalManager.getConnection()) {
            boolean autoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try (Statement s = con.createStatement();
                    PreparedStatement ps = con.prepareStatement("INSERT INTO `shop_ky_gui`(`id`, `player_id`, `tab`, `item_id`,`gold`, `gem`, `quantity`, `itemOption`, `isUpTop`, `isBuy`) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
                // DELETE thay vì TRUNCATE để có thể rollback nếu lỗi giữa chừng (TRUNCATE tự commit)
                s.executeUpdate("DELETE FROM shop_ky_gui");
                for (ConsignItem it : items) {
                    if (it != null) {
                        String options = JSONValue.toJSONString(it.options);
                        ps.setInt(1, it.id);
                        ps.setInt(2, it.player_sell);
                        ps.setInt(3, it.tab);
                        ps.setInt(4, it.itemId);
                        ps.setInt(5, it.goldSell);
                        ps.setInt(6, it.gemSell);
                        ps.setInt(7, it.quantity);
                        ps.setString(8, options == null || options.equals("null") ? "[]" : options);
                        ps.setInt(9, it.isUpTop);
                        ps.setInt(10, it.isBuy ? 1 : 0);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(autoCommit);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

