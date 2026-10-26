package nro.models.database;

import java.util.ArrayList;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.data.LocalResultSet;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.utils.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

/**
 * Dữ liệu thêm của người chơi lưu ở bảng player_extra (không đụng vào câu update lớn của bảng player):
 * ô bóng bước chân, điểm đua top Quy Lão, aura thưởng top và phần thưởng top đang chờ nhận.
 */
public class PlayerExtraDAO {

    public static void load(Player player) {
        LocalResultSet rs = null;
        try {
            rs = LocalManager.executeQuery("select * from player_extra where player_id = ? limit 1", player.id);
            if (rs.first()) {
                player.itemFoot = parseItem(rs.getString("item_foot"));
                player.qlPoints = rs.getInt("ql_points");
                player.auraReward = rs.getInt("aura_reward");
                player.auraRewardUntil = rs.getLong("aura_until");
                player.qlRewardRank = rs.getInt("ql_reward_rank");
                player.hdbaCount = rs.getInt("hdba_count");
                player.hdbaRewardRank = rs.getInt("hdba_reward_rank");
            }
        } catch (Exception e) {
            Logger.logException(PlayerExtraDAO.class, e, "Lỗi tải player_extra " + player.name);
        } finally {
            if (rs != null) {
                rs.dispose();
            }
        }
    }

    public static void save(Player player) {
        try {
            LocalManager.executeUpdate("replace into player_extra (player_id, item_foot, ql_points, aura_reward, aura_until, ql_reward_rank, hdba_count, hdba_reward_rank) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    player.id, toJson(player.itemFoot), player.qlPoints, player.auraReward, player.auraRewardUntil, player.qlRewardRank,
                    player.hdbaCount, player.hdbaRewardRank);
        } catch (Exception e) {
            Logger.logException(PlayerExtraDAO.class, e, "Lỗi lưu player_extra " + player.name);
        }
    }

    /**
     * Cộng điểm trực tiếp trong DB để không mất điểm nếu server dừng trước lần lưu kế tiếp.
     */
    public static void addQlPoints(Player player, int add) {
        player.qlPoints += add;
        save(player);
    }

    /**
     * Top người chơi theo điểm Quy Lão: mỗi phần tử {player_id, name, points}.
     */
    public static List<Object[]> topQl(int limit) {
        return top("ql_points", limit);
    }

    /**
     * Top theo một cột điểm của player_extra (ql_points, hdba_count): mỗi phần tử {player_id, name, points}.
     */
    public static List<Object[]> top(String column, int limit) {
        if (!column.equals("ql_points") && !column.equals("hdba_count")) {
            throw new IllegalArgumentException(column);
        }
        List<Object[]> list = new ArrayList<>();
        LocalResultSet rs = null;
        try {
            rs = LocalManager.executeQuery("select e.player_id, p.name, e." + column + " from player_extra e join player p on p.id = e.player_id "
                    + "where e." + column + " > 0 order by e." + column + " desc, e.player_id asc limit ?", limit);
            while (rs.next()) {
                list.add(new Object[]{rs.getInt("player_id"), rs.getString("name"), rs.getInt(column)});
            }
        } catch (Exception e) {
            Logger.logException(PlayerExtraDAO.class, e);
        } finally {
            if (rs != null) {
                rs.dispose();
            }
        }
        return list;
    }

    static String toJson(Item item) {
        JSONArray data = new JSONArray();
        if (item != null && item.isNotNullItem()) {
            data.add(item.template.id);
            data.add(item.quantity);
            JSONArray options = new JSONArray();
            for (Item.ItemOption io : item.itemOptions) {
                JSONArray opt = new JSONArray();
                opt.add(io.optionTemplate.id);
                opt.add(io.param);
                options.add(opt);
            }
            data.add(options);
            data.add(item.createTime);
        }
        return data.toJSONString();
    }

    static Item parseItem(String json) {
        try {
            JSONArray data = (JSONArray) JSONValue.parse(json);
            if (data == null || data.size() < 4) {
                return null;
            }
            Item item = ItemService.gI().createNewItem(Short.parseShort(String.valueOf(data.get(0))), Integer.parseInt(String.valueOf(data.get(1))));
            for (Object o : (JSONArray) data.get(2)) {
                JSONArray opt = (JSONArray) o;
                item.itemOptions.add(new Item.ItemOption(Integer.parseInt(String.valueOf(opt.get(0))), Integer.parseInt(String.valueOf(opt.get(1)))));
            }
            item.createTime = Long.parseLong(String.valueOf(data.get(3)));
            if (ItemService.gI().isOutOfDateTime(item)) {
                return null;
            }
            return item;
        } catch (Exception e) {
            return null;
        }
    }
}
