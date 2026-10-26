package nro.models.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.database.PlayerExtraDAO;
import nro.models.item.Item;
import nro.models.player.Inventory;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Nội dung đợt Trung Thu / 20-10 2026: bóng bước chân (ô riêng), hộp quà mở ngẫu nhiên theo bảng
 * item_box_reward, đua top tặng bộ ngọc rồng cho Quy Lão và đổi Quà Trăng Tròn / Trăng Khuyết.
 */
public class Event2026Service {

    private static Event2026Service instance;

    public static Event2026Service gI() {
        if (instance == null) {
            instance = new Event2026Service();
        }
        return instance;
    }

    public static final Set<Integer> FOOT_IDS = Set.of(1999, 1940);
    public static final int VAN_RONG_7_SAO = 1941;
    public static final int CAPSULE_VO = 1942;
    public static final int MANH_CAPSULE_VO = 1943;
    public static final int KHOA_BONG_TAI_3 = 1944;
    public static final int QUA_TRANG_TRON = 1950;
    public static final int QUA_TRANG_KHUYET = 1951;
    public static final int TRANG_TRON_POINT = 99;
    public static final long TRANG_KHUYET_GOLD = 20_000_000L;
    /** Số ngày giữ aura thưởng của top 1 - 3 */
    public static final int TOP_AURA_DAYS = 30;
    private static final int[] TOP_AURA = {122, 121, 120};

    // ------------------------------------------------------------------ bóng bước chân

    public boolean isFoot(Item item) {
        return item != null && item.isNotNullItem() && FOOT_IDS.contains((int) item.template.id);
    }

    public void equipFoot(Player pl, Item item) {
        int index = pl.inventory.itemsBag.indexOf(item);
        if (index < 0) {
            return;
        }
        Item old = pl.itemFoot;
        pl.itemFoot = item;
        pl.inventory.itemsBag.set(index, old != null && old.isNotNullItem() ? old : ItemService.gI().createItemNull());
        InventoryService.gI().sendItemBags(pl);
        Service.gI().point(pl);
        Service.gI().sendThongBao(pl, "Đã mang " + item.template.name + (old != null ? ", tháo " + old.template.name + " về hành trang" : "")
                + ". Chat \"thao buoc chan\" để tháo ra");
    }

    public void unequipFoot(Player pl) {
        if (pl.itemFoot == null) {
            Service.gI().sendThongBao(pl, "Bạn chưa mang bóng bước chân");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        Item foot = pl.itemFoot;
        pl.itemFoot = null;
        InventoryService.gI().addItemBag(pl, foot);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().point(pl);
        Service.gI().sendThongBao(pl, "Đã tháo " + foot.template.name);
    }

    // ------------------------------------------------------------------ hộp quà

    /** box_id -> các dòng phần thưởng */
    private final Map<Integer, List<BoxReward>> boxes = new ConcurrentHashMap<>();

    private static class BoxReward {

        int itemId, qtyMin, qtyMax, weight, permRate;
        int[] days;
    }

    public void loadBoxes(Connection con) {
        boxes.clear();
        int n = 0;
        try (PreparedStatement p = con.prepareStatement("select box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate from item_box_reward order by id");
                ResultSet r = p.executeQuery()) {
            while (r.next()) {
                BoxReward b = new BoxReward();
                b.itemId = r.getInt("item_id");
                b.qtyMin = Math.max(1, r.getInt("qty_min"));
                b.qtyMax = Math.max(b.qtyMin, r.getInt("qty_max"));
                b.weight = r.getInt("weight");
                b.permRate = r.getInt("perm_rate");
                String hsd = r.getString("hsd_days");
                List<Integer> days = new ArrayList<>();
                if (hsd != null) {
                    for (String s : hsd.split(",")) {
                        if (!s.isBlank() && Integer.parseInt(s.trim()) > 0) {
                            days.add(Integer.parseInt(s.trim()));
                        }
                    }
                }
                b.days = days.stream().mapToInt(Integer::intValue).toArray();
                if (b.weight <= 0 || (b.itemId >= 0 && (b.itemId >= nro.models.server.Manager.ITEM_TEMPLATES.size() || ItemService.gI().getTemplate(b.itemId).name.isEmpty()))) {
                    continue;
                }
                boxes.computeIfAbsent(r.getInt("box_id"), k -> new ArrayList<>()).add(b);
                n++;
            }
            Logger.success("Successfully loaded item box reward (" + n + ")\n");
        } catch (Exception e) {
            Logger.error("Không tải được item_box_reward: " + e.getMessage() + "\n");
        }
    }

    public boolean isBox(Item item) {
        return item != null && item.isNotNullItem() && boxes.containsKey((int) item.template.id);
    }

    public void openBox(Player pl, Item box) {
        List<BoxReward> list = boxes.get((int) box.template.id);
        if (list == null || list.isEmpty()) {
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        int total = 0;
        for (BoxReward b : list) {
            total += b.weight;
        }
        int roll = Util.nextInt(0, total - 1);
        BoxReward pick = list.get(list.size() - 1);
        for (BoxReward b : list) {
            roll -= b.weight;
            if (roll < 0) {
                pick = b;
                break;
            }
        }
        String boxName = box.template.name;
        InventoryService.gI().subQuantityItemsBag(pl, box, 1);
        int qty = Util.nextInt(pick.qtyMin, pick.qtyMax);
        String got;
        if (pick.itemId < 0) {
            got = giveCurrency(pl, pick.itemId, qty);
        } else {
            int days = 0;
            if (pick.days.length > 0 && !Util.isTrue(pick.permRate, 100)) {
                days = pick.days[Util.nextInt(0, pick.days.length - 1)];
            }
            Item it = createItem(pick.itemId, qty, days);
            InventoryService.gI().addItemBag(pl, it);
            got = (qty > 1 ? "x" + qty + " " : "") + it.template.name + (days > 0 ? " (" + days + " ngày)" : (pick.days.length > 0 ? " (vĩnh viễn)" : ""));
        }
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Mở " + boxName + " nhận được " + got);
    }

    private String giveCurrency(Player pl, int type, int qty) {
        switch (type) {
            case -1 -> {
                pl.inventory.gold = Math.min(Inventory.LIMIT_GOLD, pl.inventory.gold + qty);
                Service.gI().sendMoney(pl);
                return Util.numberToMoney(qty) + " vàng";
            }
            case -2 -> {
                pl.inventory.gem += qty;
                Service.gI().sendMoney(pl);
                return qty + " ngọc xanh";
            }
            default -> {
                pl.inventory.ruby += qty;
                Service.gI().sendMoney(pl);
                return qty + " hồng ngọc";
            }
        }
    }

    public Item createItem(int id, int qty, int days) {
        Item it = ItemService.gI().createNewItem((short) id, qty);
        it.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) id));
        if (days > 0) {
            it.itemOptions.removeIf(io -> io.optionTemplate.id == 93);
            it.itemOptions.add(new Item.ItemOption(93, days));
        }
        return it;
    }

    // ------------------------------------------------------------------ Quà Trăng Tròn / Trăng Khuyết

    public void exchangeTrangTron(Player pl) {
        if (pl.event.getEventPoint() < TRANG_TRON_POINT) {
            Service.gI().sendThongBao(pl, "Cần " + TRANG_TRON_POINT + " điểm sự kiện để đổi Quà Trăng Tròn");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        pl.event.subEventPoint(TRANG_TRON_POINT);
        InventoryService.gI().addItemBag(pl, createItem(QUA_TRANG_TRON, 1, 0));
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Đổi thành công Quà Trăng Tròn");
    }

    public void exchangeTrangKhuyet(Player pl) {
        if (pl.inventory.gold < TRANG_KHUYET_GOLD) {
            Service.gI().sendThongBao(pl, "Cần " + Util.numberToMoney(TRANG_KHUYET_GOLD) + " vàng để đổi Quà Trăng Khuyết");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        pl.inventory.gold -= TRANG_KHUYET_GOLD;
        Service.gI().sendMoney(pl);
        InventoryService.gI().addItemBag(pl, createItem(QUA_TRANG_KHUYET, 1, 0));
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Đổi thành công Quà Trăng Khuyết");
    }

    // ------------------------------------------------------------------ đua top Quy Lão

    /** Tặng bộ ngọc rồng 1 - 7 sao (id 14 - 20): mỗi bộ được 1 điểm. */
    public synchronized void giveDragonBallSet(Player pl, boolean all) {
        int sets = Integer.MAX_VALUE;
        Item[] balls = new Item[7];
        for (int i = 0; i < 7; i++) {
            balls[i] = InventoryService.gI().findItemBag(pl, 14 + i);
            sets = Math.min(sets, balls[i] == null ? 0 : balls[i].quantity);
        }
        if (sets <= 0) {
            Service.gI().sendThongBao(pl, "Con cần đủ 1 bộ ngọc rồng từ 1 đến 7 sao");
            return;
        }
        if (!all) {
            sets = 1;
        }
        for (Item b : balls) {
            InventoryService.gI().subQuantityItemsBag(pl, b, sets);
        }
        PlayerExtraDAO.addQlPoints(pl, sets);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Đã tặng " + sets + " bộ ngọc rồng, điểm đua top hiện tại: " + pl.qlPoints);
    }

    public String topText() {
        StringBuilder sb = new StringBuilder("|1|Top tặng bộ ngọc rồng\n");
        List<Object[]> top = PlayerExtraDAO.topQl(10);
        if (top.isEmpty()) {
            sb.append("Chưa có ai tham gia");
        }
        for (int i = 0; i < top.size(); i++) {
            sb.append("|").append(i < 3 ? 7 : 0).append("|").append(i + 1).append(". ").append(top.get(i)[1]).append(": ").append(top.get(i)[2]).append(" điểm\n");
        }
        return sb.toString();
    }

    /**
     * Admin: chốt top 100, lưu hạng thưởng cho từng người (nhận khi đăng nhập / ngay nếu đang online)
     * rồi đưa điểm về 0 cho mùa mới.
     */
    public synchronized int closeRace(Player admin) {
        try {
            return doCloseRace();
        } catch (Exception e) {
            Logger.logException(Event2026Service.class, e, "Lỗi chốt top Quy Lão");
            return 0;
        }
    }

    private int doCloseRace() throws Exception {
        // lưu điểm mới nhất của người đang online trước khi chốt
        for (Player p : new ArrayList<>(Client.gI().getPlayers())) {
            if (p != null && p.qlPoints > 0) {
                PlayerExtraDAO.save(p);
            }
        }
        List<Object[]> top = PlayerExtraDAO.topQl(100);
        for (int i = 0; i < top.size(); i++) {
            int playerId = (int) top.get(i)[0];
            int rank = i + 1;
            Player online = Client.gI().getPlayer((long) playerId);
            if (online != null) {
                online.qlRewardRank = rank;
            }
            LocalManager.executeUpdate("update player_extra set ql_reward_rank = ? where player_id = ?", rank, playerId);
        }
        LocalManager.executeUpdate("update player_extra set ql_points = 0");
        for (Player p : new ArrayList<>(Client.gI().getPlayers())) {
            if (p != null) {
                p.qlPoints = 0;
                deliverRaceReward(p);
            }
        }
        Logger.success("Đã chốt top Quy Lão: " + top.size() + " người\n");
        return top.size();
    }

    /** Phát quà top đang chờ (gọi khi vào game và khi chốt top). */
    public void deliverRaceReward(Player pl) {
        int rank = pl.qlRewardRank;
        if (rank <= 0) {
            return;
        }
        List<Item> items = raceRewards(rank);
        if (InventoryService.gI().getCountEmptyBag(pl) < items.size()) {
            Service.gI().sendThongBao(pl, "Bạn có quà đua top Quy Lão (hạng " + rank + "), cần " + items.size() + " ô trống hành trang để nhận");
            return;
        }
        pl.qlRewardRank = 0;
        for (Item it : items) {
            InventoryService.gI().addItemBag(pl, it);
        }
        if (rank <= 3) {
            pl.auraReward = TOP_AURA[rank - 1];
            pl.auraRewardUntil = System.currentTimeMillis() + TOP_AURA_DAYS * 86_400_000L;
            Service.gI().Send_Caitrang(pl);
        }
        PlayerExtraDAO.save(pl);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Chúc mừng bạn đạt hạng " + rank + " đua top tặng ngọc rồng Quy Lão, quà đã được gửi vào hành trang");
    }

    private List<Item> raceRewards(int rank) {
        List<Item> l = new ArrayList<>();
        if (rank <= 3) {
            int k = rank - 1;
            l.add(createItem(VAN_RONG_7_SAO, 1, 0));
            l.add(createItem(1283, new int[]{70, 60, 50}[k], 0)); // Cuốn sách cũ
            l.add(createItem(1559, new int[]{3, 2, 1}[k], 0)); // Capsule 1 món kích hoạt
            l.add(createItem(CAPSULE_VO, new int[]{3, 2, 1}[k], 0));
            l.add(createItem(1820, new int[]{10000, 5000, 1000}[k], 0)); // Mảnh vỡ bông tai cấp 3
            l.add(createItem(KHOA_BONG_TAI_3, new int[]{10, 5, 2}[k], 0));
            if (rank == 1) {
                l.add(createItem(1869, 1, 0)); // Giáp tập luyện cấp 5
            }
        } else if (rank <= 10) {
            l.add(createItem(VAN_RONG_7_SAO, 1, 0));
            l.add(createItem(1283, 20, 0));
            l.add(createItem(1690, 50, 0)); // Phiếu đổi Capsule
            l.add(createItem(MANH_CAPSULE_VO, 100, 0));
        } else if (rank <= 50) {
            l.add(createItem(VAN_RONG_7_SAO, 1, 90));
            l.add(createItem(1283, 10, 0));
            l.add(createItem(1690, 20, 0));
            l.add(createItem(MANH_CAPSULE_VO, 20, 0));
        } else {
            l.add(createItem(VAN_RONG_7_SAO, 1, 60));
            l.add(createItem(1283, 5, 0));
            l.add(createItem(1690, 10, 0));
        }
        return l;
    }
}
