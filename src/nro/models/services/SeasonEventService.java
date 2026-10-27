package nro.models.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import nro.models.boss.Boss;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.boss.Boss_Manager.ChristmasEventManager;
import nro.models.boss.Boss_Manager.HalloweenEventManager;
import nro.models.boss.Boss_Manager.HungVuongEventManager;
import nro.models.boss.Boss_Manager.LunarNewYearEventManager;
import nro.models.boss.Boss_Manager.TrungThuEventManager;
import nro.models.consts.ConstFont;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.NpcService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.server.ServerManager;
import nro.models.server.ServerNotify;
import nro.models.utils.Logger;
import nro.models.utils.LunarCalendar;
import nro.models.utils.Util;

/**
 * Sự kiện theo mùa (Tết, Valentine, 8/3, Giỗ tổ, Hè, Trung Thu, Halloween, 20/11, Giáng sinh...).
 * Lịch, vật phẩm rơi và công thức đổi quà nằm trong các bảng season_event, season_event_drop,
 * season_event_exchange; quà trong hộp nằm ở item_box_reward. Sự kiện tự bật/tắt theo ngày
 * (dương lịch hoặc âm lịch), admin bật/tắt tay bằng lệnh "sukien".
 */
public class SeasonEventService implements Runnable {

    private static SeasonEventService instance;

    public static SeasonEventService gI() {
        if (instance == null) {
            instance = new SeasonEventService();
        }
        return instance;
    }

    // chỉ số menu ở NPC Santa
    public static final int MENU_LIST = 83000;
    public static final int MENU_EVENT = 83100; // + id sự kiện
    public static final int MENU_EXCHANGE = 84000; // + id công thức

    public static class Season {

        public int id;
        public String name, description;
        public boolean lunar, enabled;
        public int month, day, daysBefore, daysAfter;
        public int[][] bosses = new int[0][];
        public List<Drop> drops = new ArrayList<>();
        public List<Exchange> exchanges = new ArrayList<>();
        public LocalDate start, end;
        volatile boolean active;
        final List<Boss> spawned = new CopyOnWriteArrayList<>();
    }

    public static class Drop {

        int itemId, rate, qtyMin, qtyMax;
        boolean fromBoss;
    }

    public static class Exchange {

        int id;
        String name;
        int[][] need;
        long gold;
        int gem, ruby, rewardItem, rewardQty, eventPoint;
    }

    private final Map<Integer, Season> seasons = new LinkedHashMap<>();
    /** id sự kiện -> true bật tay / false tắt tay; không có = theo lịch */
    private final Map<Integer, Boolean> forced = new ConcurrentHashMap<>();
    private volatile LocalDate testDate;

    // ------------------------------------------------------------------ nạp dữ liệu
    public synchronized void load() {
        Map<Integer, Season> map = new LinkedHashMap<>();
        Map<Integer, Exchange> exchanges = new LinkedHashMap<>();
        try (Connection con = LocalManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("select * from season_event order by id"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Season s = new Season();
                    s.id = rs.getInt("id");
                    s.name = rs.getString("name");
                    s.description = rs.getString("description");
                    s.lunar = rs.getInt("lunar") == 1;
                    s.month = rs.getInt("month");
                    s.day = rs.getInt("day");
                    s.daysBefore = rs.getInt("days_before");
                    s.daysAfter = rs.getInt("days_after");
                    s.enabled = rs.getInt("enabled") == 1;
                    s.bosses = parsePairs(rs.getString("bosses"));
                    map.put(s.id, s);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("select * from season_event_drop order by id"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Season s = map.get(rs.getInt("event_id"));
                    Drop d = new Drop();
                    d.itemId = rs.getInt("item_id");
                    d.rate = rs.getInt("rate");
                    d.qtyMin = Math.max(1, rs.getInt("qty_min"));
                    d.qtyMax = Math.max(d.qtyMin, rs.getInt("qty_max"));
                    d.fromBoss = "boss".equals(rs.getString("source"));
                    if (s != null && validItem(d.itemId)) {
                        s.drops.add(d);
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("select * from season_event_exchange order by id"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Season s = map.get(rs.getInt("event_id"));
                    Exchange e = new Exchange();
                    e.id = rs.getInt("id");
                    e.name = rs.getString("name");
                    e.need = parsePairs(rs.getString("need"));
                    e.gold = rs.getLong("gold");
                    e.gem = rs.getInt("gem");
                    e.ruby = rs.getInt("ruby");
                    e.rewardItem = rs.getInt("reward_item");
                    e.rewardQty = Math.max(1, rs.getInt("reward_qty"));
                    e.eventPoint = rs.getInt("event_point");
                    if (s != null && validItem(e.rewardItem)) {
                        s.exchanges.add(e);
                        exchanges.put(e.id, e);
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Không tải được sự kiện theo mùa: " + e.getMessage() + "\n");
            return;
        }
        // giữ trạng thái đang chạy (boss đã gọi) của bản cũ khi nạp lại
        for (Season s : map.values()) {
            Season old = seasons.get(s.id);
            if (old != null) {
                s.active = old.active;
                s.spawned.addAll(old.spawned);
            }
        }
        for (Season old : seasons.values()) {
            if (!map.containsKey(old.id)) {
                removeBosses(old);
            }
        }
        seasons.clear();
        seasons.putAll(map);
        Logger.success("Successfully loaded season event (" + seasons.size() + " sự kiện, " + exchanges.size() + " công thức)\n");
    }

    private static boolean validItem(int id) {
        return id >= 0 && id < nro.models.server.Manager.ITEM_TEMPLATES.size()
                && !ItemService.gI().getTemplate(id).name.isEmpty();
    }

    /** "a:b,c:d" -> {{a,b},{c,d}} */
    private static int[][] parsePairs(String s) {
        List<int[]> list = new ArrayList<>();
        if (s != null) {
            for (String p : s.split(",")) {
                String[] kv = p.trim().split(":");
                if (kv.length == 2) {
                    try {
                        list.add(new int[]{Integer.parseInt(kv[0].trim()), Integer.parseInt(kv[1].trim())});
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return list.toArray(new int[0][]);
    }

    // ------------------------------------------------------------------ lịch
    public LocalDate today() {
        return testDate != null ? testDate : LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    /** Khoảng ngày [bắt đầu, kết thúc] của đợt sự kiện chứa ngày d, hoặc null. */
    public static LocalDate[] window(Season s, LocalDate d) {
        for (int y = d.getYear() - 1; y <= d.getYear() + 1; y++) {
            LocalDate anchor;
            try {
                anchor = s.lunar ? LunarCalendar.toSolar(s.day, s.month, y) : LocalDate.of(y, s.month, s.day);
            } catch (Exception e) {
                continue;
            }
            LocalDate a = anchor.minusDays(s.daysBefore), b = anchor.plusDays(s.daysAfter);
            if (!d.isBefore(a) && !d.isAfter(b)) {
                return new LocalDate[]{a, b};
            }
        }
        return null;
    }

    /** Lần diễn ra tiếp theo (từ ngày d trở đi). */
    public static LocalDate[] next(Season s, LocalDate d) {
        LocalDate[] w = window(s, d);
        if (w != null) {
            return w;
        }
        for (int y = d.getYear(); y <= d.getYear() + 1; y++) {
            try {
                LocalDate anchor = s.lunar ? LunarCalendar.toSolar(s.day, s.month, y) : LocalDate.of(y, s.month, s.day);
                LocalDate a = anchor.minusDays(s.daysBefore);
                if (a.isAfter(d)) {
                    return new LocalDate[]{a, anchor.plusDays(s.daysAfter)};
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean shouldRun(Season s, LocalDate d) {
        Boolean f = forced.get(s.id);
        if (f != null) {
            return f;
        }
        return s.enabled && window(s, d) != null;
    }

    public List<Season> activeSeasons() {
        List<Season> list = new ArrayList<>();
        for (Season s : seasons.values()) {
            if (s.active) {
                list.add(s);
            }
        }
        return list;
    }

    @Override
    public void run() {
        while (ServerManager.isRunning) {
            try {
                update();
            } catch (Exception e) {
                Logger.error("Lỗi cập nhật sự kiện theo mùa: " + e.getMessage() + "\n");
            }
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public synchronized void update() {
        LocalDate d = today();
        for (Season s : seasons.values()) {
            LocalDate[] w = window(s, d);
            s.start = w != null ? w[0] : null;
            s.end = w != null ? w[1] : null;
            boolean run = shouldRun(s, d);
            if (run && !s.active) {
                s.active = true;
                spawnBosses(s);
                Logger.success("Bắt đầu sự kiện " + s.name + "\n");
                ServerNotify.gI().notify("Sự kiện " + s.name + " đã bắt đầu! Gặp ông già Santa ở Đảo Kamê, Đảo Guru, Vách núi đen để đổi quà.");
            } else if (!run && s.active) {
                s.active = false;
                removeBosses(s);
                Logger.success("Kết thúc sự kiện " + s.name + "\n");
                ServerNotify.gI().notify("Sự kiện " + s.name + " đã kết thúc. Hẹn gặp lại ở mùa sau!");
            }
        }
    }

    // ------------------------------------------------------------------ boss
    private void spawnBosses(Season s) {
        for (int[] b : s.bosses) {
            for (int i = 0; i < b[1]; i++) {
                try {
                    Boss boss = BossManager.gI().createBoss(b[0]);
                    if (boss != null) {
                        s.spawned.add(boss);
                    }
                } catch (Exception e) {
                    Logger.error("Không gọi được boss sự kiện " + b[0] + ": " + e.getMessage() + "\n");
                }
            }
        }
    }

    private void removeBosses(Season s) {
        BossManager[] managers = {BossManager.gI(), LunarNewYearEventManager.gI(), HungVuongEventManager.gI(),
            TrungThuEventManager.gI(), HalloweenEventManager.gI(), ChristmasEventManager.gI()};
        for (Boss boss : s.spawned) {
            for (BossManager m : managers) {
                m.removeBoss(boss);
            }
            try {
                if (boss.zone != null) {
                    ChangeMapService.gI().exitMap(boss);
                }
            } catch (Exception ignored) {
            }
        }
        s.spawned.clear();
    }

    // ------------------------------------------------------------------ vật phẩm rơi
    /** Vật phẩm sự kiện rơi khi đánh quái. */
    public void addMobDrops(List<ItemMap> list, Player pl, Zone zone, int x, int y) {
        if (pl == null || !pl.isPl()) {
            return;
        }
        for (Season s : seasons.values()) {
            if (!s.active) {
                continue;
            }
            for (Drop d : s.drops) {
                if (!d.fromBoss && Util.isTrue(d.rate, 10000)) {
                    list.add(new ItemMap(zone, d.itemId, Util.nextInt(d.qtyMin, d.qtyMax), x, y, pl.id));
                }
            }
        }
    }

    /** Vật phẩm sự kiện rơi khi hạ boss (mọi boss). */
    public void onBossKilled(Boss boss, Player pl) {
        if (pl == null || boss == null || boss.zone == null || !pl.isPl()) {
            return;
        }
        for (Season s : seasons.values()) {
            if (!s.active) {
                continue;
            }
            for (Drop d : s.drops) {
                if (d.fromBoss && Util.isTrue(d.rate, 10000)) {
                    int x = boss.location.x + Util.nextInt(-20, 20);
                    ItemMap it = new ItemMap(boss.zone, d.itemId, Util.nextInt(d.qtyMin, d.qtyMax), x,
                            boss.zone.map.yPhysicInTop(x, boss.location.y - 24), pl.id);
                    Service.gI().dropItemMap(boss.zone, it);
                }
            }
        }
    }

    // ------------------------------------------------------------------ NPC
    public boolean hasActive() {
        for (Season s : seasons.values()) {
            if (s.active) {
                return true;
            }
        }
        return false;
    }

    public String menuLabel() {
        List<Season> list = activeSeasons();
        return list.size() == 1 ? "Sự kiện\n" + list.get(0).name : "Sự kiện\n(" + list.size() + ")";
    }

    public void openMenu(Npc npc, Player pl) {
        List<Season> list = activeSeasons();
        if (list.isEmpty()) {
            npc.createOtherMenu(pl, MENU_LIST, "Hiện không có sự kiện nào.\n" + upcomingText(), "Đóng");
        } else if (list.size() == 1) {
            openSeason(npc, pl, list.get(0));
        } else {
            String[] opts = new String[list.size()];
            for (int i = 0; i < opts.length; i++) {
                opts[i] = list.get(i).name;
            }
            npc.createOtherMenu(pl, MENU_LIST, "Đang có " + list.size() + " sự kiện, chọn sự kiện:", opts);
        }
    }

    private void openSeason(Npc npc, Player pl, Season s) {
        StringBuilder sb = new StringBuilder();
        sb.append(ConstFont.BOLD_RED).append("Sự kiện ").append(s.name).append("\n");
        if (s.end != null) {
            long left = ChronoUnit.DAYS.between(today(), s.end);
            sb.append(ConstFont.BOLD_BLUE).append("Đến hết ngày ").append(fmt(s.end))
                    .append(left > 0 ? " (còn " + left + " ngày)" : " (ngày cuối)").append("\n");
        }
        sb.append(ConstFont.BOLD_DARK).append("Điểm sự kiện: ").append(pl.event.getEventPoint()).append("\n");
        for (int id : materialIds(s)) {
            sb.append(ItemService.gI().getTemplate(id).name).append(": ").append(count(pl, id)).append("\n");
        }
        List<String> opts = new ArrayList<>();
        for (Exchange e : s.exchanges) {
            opts.add("Đổi\n" + e.name);
        }
        opts.add("Hướng\ndẫn");
        npc.createOtherMenu(pl, MENU_EVENT + s.id, sb.toString(), opts.toArray(new String[0]));
    }

    private List<Integer> materialIds(Season s) {
        List<Integer> ids = new ArrayList<>();
        for (Exchange e : s.exchanges) {
            for (int[] n : e.need) {
                if (!ids.contains(n[0])) {
                    ids.add(n[0]);
                }
            }
        }
        return ids;
    }

    private void openExchange(Npc npc, Player pl, Exchange e) {
        StringBuilder sb = new StringBuilder();
        sb.append(ConstFont.BOLD_GREEN).append("Đổi ").append(e.name).append("\n");
        sb.append(ConstFont.BOLD_DARK).append("Cần cho 1 lần đổi:\n");
        for (int[] n : e.need) {
            int have = count(pl, n[0]);
            sb.append(have >= n[1] ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED)
                    .append(ItemService.gI().getTemplate(n[0]).name).append(": ").append(have).append("/").append(n[1]).append("\n");
        }
        if (e.gold > 0) {
            sb.append(pl.inventory.gold >= e.gold ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED).append("Vàng: ").append(Util.numberToMoney(e.gold)).append("\n");
        }
        if (e.gem > 0) {
            sb.append(pl.inventory.gem >= e.gem ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED).append("Ngọc xanh: ").append(e.gem).append("\n");
        }
        if (e.ruby > 0) {
            sb.append(pl.inventory.ruby >= e.ruby ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED).append("Hồng ngọc: ").append(e.ruby).append("\n");
        }
        sb.append(ConstFont.BOLD_DARK).append("Nhận: x").append(e.rewardQty).append(" ").append(ItemService.gI().getTemplate(e.rewardItem).name);
        if (e.eventPoint > 0) {
            sb.append(" và ").append(e.eventPoint).append(" điểm sự kiện");
        }
        int max = maxTimes(pl, e);
        sb.append("\n").append(ConstFont.BOLD_GREEN).append("Có thể đổi: ").append(max).append(" lần");
        npc.createOtherMenu(pl, MENU_EXCHANGE + e.id, sb.toString(), "Đổi\nx1", "Đổi\nx10", "Đổi\nhết", "Từ chối");
    }

    /** Xử lý chọn menu của sự kiện; trả về true nếu đúng menu sự kiện. */
    public boolean confirmMenu(Npc npc, Player pl, int menu, int select) {
        if (menu == MENU_LIST) {
            List<Season> list = activeSeasons();
            if (select >= 0 && select < list.size()) {
                openSeason(npc, pl, list.get(select));
            }
            return true;
        }
        if (menu >= MENU_EVENT && menu < MENU_EXCHANGE) {
            Season s = seasons.get(menu - MENU_EVENT);
            if (s == null || !s.active) {
                return true;
            }
            if (select >= 0 && select < s.exchanges.size()) {
                openExchange(npc, pl, s.exchanges.get(select));
            } else if (select == s.exchanges.size()) {
                NpcService.gI().createTutorial(pl, npc.tempId, npc.avartar, guide(s));
            }
            return true;
        }
        if (menu >= MENU_EXCHANGE && menu < MENU_EXCHANGE + 100000) {
            Exchange e = null;
            Season owner = null;
            for (Season s : seasons.values()) {
                for (Exchange x : s.exchanges) {
                    if (x.id == menu - MENU_EXCHANGE) {
                        e = x;
                        owner = s;
                    }
                }
            }
            if (e == null || !owner.active) {
                Service.gI().sendThongBao(pl, "Sự kiện đã kết thúc");
                return true;
            }
            int times = switch (select) {
                case 0 -> 1;
                case 1 -> 10;
                case 2 -> maxTimes(pl, e);
                default -> 0;
            };
            if (times > 0) {
                exchange(pl, e, times);
                openExchange(npc, pl, e);
            }
            return true;
        }
        return false;
    }

    private String guide(Season s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Sự kiện ").append(s.name);
        if (s.start != null) {
            sb.append(" từ ").append(fmt(s.start)).append(" đến hết ").append(fmt(s.end));
        }
        sb.append("\n");
        if (s.description != null && !s.description.isBlank()) {
            sb.append(s.description.replace("\\n", "\n")).append("\n");
        }
        for (Drop d : s.drops) {
            sb.append("- ").append(ItemService.gI().getTemplate(d.itemId).name)
                    .append(d.fromBoss ? ": rơi khi hạ boss" : ": rơi khi đánh quái").append("\n");
        }
        for (Exchange e : s.exchanges) {
            sb.append("- ").append(e.name).append(": ");
            List<String> parts = new ArrayList<>();
            for (int[] n : e.need) {
                parts.add(n[1] + " " + ItemService.gI().getTemplate(n[0]).name);
            }
            if (e.gold > 0) {
                parts.add(Util.numberToMoney(e.gold) + " vàng");
            }
            if (e.gem > 0) {
                parts.add(e.gem + " ngọc xanh");
            }
            if (e.ruby > 0) {
                parts.add(e.ruby + " hồng ngọc");
            }
            sb.append(String.join(", ", parts)).append("\n");
        }
        sb.append("Điểm sự kiện dùng để đổi đồ ở cửa hàng sự kiện.");
        return sb.toString();
    }

    // ------------------------------------------------------------------ đổi quà
    private static int count(Player pl, int id) {
        int n = 0;
        for (Item it : pl.inventory.itemsBag) {
            if (it.isNotNullItem() && it.template.id == id) {
                n += it.quantity;
            }
        }
        return n;
    }

    private static void take(Player pl, int id, int qty) {
        for (Item it : new ArrayList<>(pl.inventory.itemsBag)) {
            if (qty <= 0) {
                return;
            }
            if (it.isNotNullItem() && it.template.id == id) {
                int n = Math.min(qty, it.quantity);
                InventoryService.gI().subQuantityItemsBag(pl, it, n);
                qty -= n;
            }
        }
    }

    private static int maxTimes(Player pl, Exchange e) {
        long max = 9999;
        for (int[] n : e.need) {
            max = Math.min(max, count(pl, n[0]) / Math.max(1, n[1]));
        }
        if (e.gold > 0) {
            max = Math.min(max, pl.inventory.gold / e.gold);
        }
        if (e.gem > 0) {
            max = Math.min(max, pl.inventory.gem / e.gem);
        }
        if (e.ruby > 0) {
            max = Math.min(max, pl.inventory.ruby / e.ruby);
        }
        return (int) Math.max(0, max);
    }

    private synchronized void exchange(Player pl, Exchange e, int times) {
        synchronized (pl) {
            if (maxTimes(pl, e) < times) {
                Service.gI().sendThongBao(pl, "Không đủ nguyên liệu để đổi " + times + " lần");
                return;
            }
            Item sample = ItemService.gI().createNewItem((short) e.rewardItem, 1);
            boolean stack = sample.template.isUpToUp;
            int slots = stack ? (InventoryService.gI().findItemBag(pl, e.rewardItem) != null ? 0 : 1) : e.rewardQty * times;
            if (InventoryService.gI().getCountEmptyBag(pl) < slots) {
                Service.gI().sendThongBao(pl, "Hành trang cần " + slots + " ô trống");
                return;
            }
            for (int[] n : e.need) {
                take(pl, n[0], n[1] * times);
            }
            pl.inventory.gold -= e.gold * times;
            pl.inventory.gem -= e.gem * times;
            pl.inventory.ruby -= e.ruby * times;
            if (stack) {
                InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(e.rewardItem, e.rewardQty * times, 0));
            } else {
                for (int i = 0; i < e.rewardQty * times; i++) {
                    InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(e.rewardItem, 1, 0));
                }
            }
            if (e.eventPoint > 0) {
                pl.event.addEventPoint(e.eventPoint * times);
            }
            InventoryService.gI().sendItemBags(pl);
            Service.gI().sendMoney(pl);
            Service.gI().sendThongBao(pl, "Đổi thành công x" + (e.rewardQty * times) + " " + sample.template.name
                    + (e.eventPoint > 0 ? ", +" + (e.eventPoint * times) + " điểm sự kiện" : ""));
        }
    }

    // ------------------------------------------------------------------ admin
    public synchronized String admin(String args) {
        String[] a = args.trim().split("\\s+");
        String cmd = a.length > 0 ? a[0] : "";
        try {
            switch (cmd) {
                case "bat", "tat", "tudong" -> {
                    int id = Integer.parseInt(a[1]);
                    if (!seasons.containsKey(id)) {
                        return "Không có sự kiện id " + id;
                    }
                    if (cmd.equals("bat")) {
                        forced.put(id, true);
                    } else if (cmd.equals("tat")) {
                        forced.put(id, false);
                    } else {
                        forced.remove(id);
                    }
                    update();
                    return seasons.get(id).name + ": " + (seasons.get(id).active ? "đang chạy" : "đã tắt");
                }
                case "reload" -> {
                    load();
                    Event2026Service.gI().reloadBoxes();
                    update();
                    return "Đã nạp lại " + seasons.size() + " sự kiện và hộp quà";
                }
                case "ngay" -> {
                    testDate = a.length > 1 ? LocalDate.parse(a[1]) : null;
                    update();
                    return "Ngày sự kiện: " + today() + (testDate != null ? " (giả lập)" : "");
                }
                default -> {
                }
            }
        } catch (Exception e) {
            return "Sai lệnh: " + e.getMessage();
        }
        return statusText();
    }

    public String statusText() {
        StringBuilder sb = new StringBuilder("Sự kiện theo mùa (hôm nay " + fmt(today()) + ")\n");
        for (Season s : seasons.values()) {
            sb.append(s.id).append(". ").append(s.name).append(": ");
            if (s.active) {
                sb.append("ĐANG CHẠY");
                if (s.end != null) {
                    sb.append(" đến ").append(fmt(s.end));
                }
            } else {
                LocalDate[] w = next(s, today());
                sb.append(s.enabled ? "tắt" : "đã tắt trong DB");
                if (w != null) {
                    sb.append(", lần tới ").append(fmt(w[0])).append(" - ").append(fmt(w[1]));
                }
            }
            Boolean f = forced.get(s.id);
            if (f != null) {
                sb.append(f ? " (bật tay)" : " (tắt tay)");
            }
            if (!s.spawned.isEmpty()) {
                long inMap = s.spawned.stream().filter(b -> b.zone != null).count();
                sb.append(", ").append(s.spawned.size()).append(" boss (").append(inMap).append(" đang ở bản đồ)");
            }
            sb.append("\n");
        }
        sb.append("Lệnh: sukien bat|tat|tudong <id>, sukien reload, sukien ngay yyyy-mm-dd");
        return sb.toString();
    }

    private String upcomingText() {
        LocalDate d = today();
        Season best = null;
        LocalDate[] bw = null;
        for (Season s : seasons.values()) {
            LocalDate[] w = s.enabled ? next(s, d) : null;
            if (w != null && (bw == null || w[0].isBefore(bw[0]))) {
                best = s;
                bw = w;
            }
        }
        return best == null ? "" : "Sự kiện tiếp theo: " + best.name + " từ " + fmt(bw[0]) + " đến " + fmt(bw[1]);
    }

    private static String fmt(LocalDate d) {
        return String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    public List<Season> all() {
        return Collections.unmodifiableList(new ArrayList<>(seasons.values()));
    }
}
