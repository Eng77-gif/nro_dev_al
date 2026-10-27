package nro.models.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import nro.models.server.Client;
import nro.models.server.ServerManager;
import nro.models.server.ServerNotify;
import nro.models.utils.Logger;
import nro.models.utils.LunarCalendar;
import nro.models.utils.Util;

/**
 * Sự kiện theo mùa (Tết, Valentine, 8/3, Cá tháng 4, Giỗ tổ, Hè, Trung Thu, Halloween, 20/11, Giáng sinh...).
 * Lịch, vật phẩm rơi, công thức đổi quà và đua top nằm trong các bảng season_event*, quà trong hộp
 * nằm ở item_box_reward. Sự kiện tự bật/tắt theo ngày (dương lịch hoặc âm lịch), admin bật/tắt tay
 * bằng lệnh "sukien". Tất cả nằm ở NPC Santa (Đảo Kamê, Đảo Guru, Vách núi đen).
 */
public class SeasonEventService implements Runnable {

    private static SeasonEventService instance;

    public static SeasonEventService gI() {
        if (instance == null) {
            instance = new SeasonEventService();
        }
        return instance;
    }

    public static final int SUMMER = 5;

    // chỉ số menu ở NPC Santa
    public static final int MENU_LIST = 83000;
    public static final int MENU_EVENT = 83100; // + id sự kiện
    public static final int MENU_EXCHANGE = 84000; // + id công thức
    public static final int MENU_GROUP = 86000; // + id sự kiện * 50 + nhóm
    public static final int MENU_NPC = 97000; // menu sự kiện ở NPC khác Santa

    public static class Season {

        public int id;
        public String name, description;
        public boolean lunar, enabled;
        public int month, day, daysBefore, daysAfter;
        public int[][] bosses = new int[0][];
        public List<int[]> npcSpawns = new ArrayList<>(); // {npcTempId, mapId, x, y}; x = 0 tự chọn chỗ trống
        public List<int[]> mobSpawns = new ArrayList<>(); // {mobTempId, mapId, hp, respawnMinutes}
        public List<Drop> drops = new ArrayList<>();
        public List<Exchange> exchanges = new ArrayList<>();
        public List<String> groups = new ArrayList<>();
        public List<Top> tops = new ArrayList<>();
        public LocalDate start, end;
        volatile boolean active;
        final List<Boss> spawned = new CopyOnWriteArrayList<>();
    }

    public static class Drop {

        int itemId, itemTo, rate, qtyMin, qtyMax, dailyMax, needItem;
        boolean fromBoss;
        Set<Integer> mobs = new HashSet<>(), maps = new HashSet<>();
    }

    public static class Exchange {

        int id, gem, ruby, costPoint, rewardItem, rewardQty, rewardQtyMax, permRate, bonusItem, bonusRate, eventPoint, topId, topPoint;
        String name, group;
        int[][] need;
        int[] rewardDays, npcs;
        long gold;
        boolean openNow;
        Season season;
    }

    public static class Top {

        int id;
        String name;
        Season season;
        List<int[]> rewards = new ArrayList<>(); // {rankFrom, rankTo, itemId, qty}
        List<int[]> rewardDays = new ArrayList<>();
    }

    private final Map<Integer, Season> seasons = new LinkedHashMap<>();
    private final Map<Integer, Exchange> exchanges = new ConcurrentHashMap<>();
    private final Map<Integer, Top> tops = new ConcurrentHashMap<>();
    /** id sự kiện -> true bật tay / false tắt tay; không có = theo lịch */
    private final Map<Integer, Boolean> forced = new ConcurrentHashMap<>();
    /** số vật phẩm đã nhặt trong ngày: "playerId:itemId" */
    private final Map<String, Integer> daily = new ConcurrentHashMap<>();
    /** NPC sự kiện đang được đặt thêm vào bản đồ: "mapId:tempId" -> npc */
    private final Map<String, Npc> eventNpcs = new ConcurrentHashMap<>();
    /** quái to sự kiện đang được thả: "mapId:mobTempId" -> các con (mỗi khu một con) */
    private final Map<String, List<nro.models.mob.Mob>> eventMobs = new ConcurrentHashMap<>();
    private final Map<String, int[]> eventMobCfg = new ConcurrentHashMap<>();
    private volatile LocalDate dailyDate;
    private volatile LocalDate testDate;

    // ------------------------------------------------------------------ nạp dữ liệu
    public synchronized void load() {
        Map<Integer, Season> map = new LinkedHashMap<>();
        Map<Integer, Exchange> exMap = new LinkedHashMap<>();
        Map<Integer, Top> topMap = new LinkedHashMap<>();
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
                    d.dailyMax = rs.getInt("daily_max");
                    d.itemTo = rs.getInt("item_to");
                    d.needItem = rs.getInt("need_item");
                    d.mobs = parseSet(rs.getString("mobs"));
                    d.maps = parseSet(rs.getString("maps"));
                    if (s != null && validItem(d.itemId)) {
                        s.drops.add(d);
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("select * from season_event_top order by id"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Season s = map.get(rs.getInt("event_id"));
                    Top t = new Top();
                    t.id = rs.getInt("id");
                    t.name = rs.getString("name");
                    t.season = s;
                    if (s != null) {
                        s.tops.add(t);
                        topMap.put(t.id, t);
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("select * from season_event_top_reward order by id"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Top t = topMap.get(rs.getInt("top_id"));
                    int item = rs.getInt("item_id");
                    if (t != null && (item < 0 || validItem(item))) {
                        t.rewards.add(new int[]{rs.getInt("rank_from"), rs.getInt("rank_to"), item, Math.max(1, rs.getInt("qty"))});
                        t.rewardDays.add(parseInts(rs.getString("hsd_days")));
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("select * from season_event_exchange order by id"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Season s = map.get(rs.getInt("event_id"));
                    Exchange e = new Exchange();
                    e.id = rs.getInt("id");
                    e.name = rs.getString("name");
                    e.group = rs.getString("grp");
                    e.need = parsePairs(rs.getString("need"));
                    e.gold = rs.getLong("gold");
                    e.gem = rs.getInt("gem");
                    e.ruby = rs.getInt("ruby");
                    e.costPoint = rs.getInt("cost_point");
                    e.rewardItem = rs.getInt("reward_item");
                    e.rewardQty = Math.max(1, rs.getInt("reward_qty"));
                    e.rewardQtyMax = Math.max(e.rewardQty, rs.getInt("reward_qty_max"));
                    e.rewardDays = parseInts(rs.getString("reward_days"));
                    e.permRate = rs.getInt("perm_rate");
                    e.bonusItem = rs.getInt("bonus_item");
                    e.bonusRate = rs.getInt("bonus_rate");
                    e.openNow = rs.getInt("open_now") == 1;
                    e.eventPoint = rs.getInt("event_point");
                    e.topId = rs.getInt("top_id");
                    e.topPoint = rs.getInt("top_point");
                    e.npcs = parseInts(rs.getString("npc"));
                    e.season = s;
                    if (s != null && (e.openNow || validItem(e.rewardItem)) && (e.bonusItem <= 0 || validItem(e.bonusItem))) {
                        s.exchanges.add(e);
                        if (!s.groups.contains(e.group)) {
                            s.groups.add(e.group);
                        }
                        exMap.put(e.id, e);
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("select * from season_event_npc order by id"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Season s = map.get(rs.getInt("event_id"));
                    if (s != null) {
                        s.npcSpawns.add(new int[]{rs.getInt("npc_id"), rs.getInt("map_id"), rs.getInt("x"), rs.getInt("y")});
                    }
                }
            } catch (Exception e) {
                Logger.error("Không tải được season_event_npc: " + e.getMessage() + "\n");
            }
            try (PreparedStatement ps = con.prepareStatement("select * from season_event_mob order by id"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Season s = map.get(rs.getInt("event_id"));
                    if (s != null) {
                        s.mobSpawns.add(new int[]{rs.getInt("mob_id"), rs.getInt("map_id"), rs.getInt("hp"), rs.getInt("respawn_minutes")});
                    }
                }
            } catch (Exception e) {
                Logger.error("Không tải được season_event_mob: " + e.getMessage() + "\n");
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
                s.start = old.start;
                s.end = old.end;
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
        exchanges.clear();
        exchanges.putAll(exMap);
        tops.clear();
        tops.putAll(topMap);
        syncNpcs();
        syncMobs();
        Logger.success("Successfully loaded season event (" + seasons.size() + " sự kiện, " + exMap.size() + " công thức, " + topMap.size() + " đua top)\n");
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

    private static int[] parseInts(String s) {
        List<Integer> list = new ArrayList<>();
        if (s != null) {
            for (String p : s.split(",")) {
                try {
                    if (!p.isBlank()) {
                        list.add(Integer.parseInt(p.trim()));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Set<Integer> parseSet(String s) {
        Set<Integer> set = new HashSet<>();
        for (int v : parseInts(s)) {
            set.add(v);
        }
        return set;
    }

    // ------------------------------------------------------------------ lịch
    public LocalDate today() {
        return testDate != null ? testDate : LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    private static LocalDate anchor(Season s, int year) {
        return s.lunar ? LunarCalendar.toSolar(s.day, s.month, year) : LocalDate.of(year, s.month, s.day);
    }

    /** Khoảng ngày [bắt đầu, kết thúc] của đợt sự kiện chứa ngày d, hoặc null. */
    public static LocalDate[] window(Season s, LocalDate d) {
        for (int y = d.getYear() - 1; y <= d.getYear() + 1; y++) {
            try {
                LocalDate a = anchor(s, y);
                LocalDate from = a.minusDays(s.daysBefore), to = a.plusDays(s.daysAfter);
                if (!d.isBefore(from) && !d.isAfter(to)) {
                    return new LocalDate[]{from, to};
                }
            } catch (Exception ignored) {
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
        for (int y = d.getYear() - 1; y <= d.getYear() + 1; y++) {
            try {
                LocalDate a = anchor(s, y);
                if (a.minusDays(s.daysBefore).isAfter(d)) {
                    return new LocalDate[]{a.minusDays(s.daysBefore), a.plusDays(s.daysAfter)};
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

    public boolean isActive(int seasonId) {
        Season s = seasons.get(seasonId);
        return s != null && s.active;
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
                nro.models.services.shenron.RongXuong.tick();
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
                ServerNotify.gI().notify("Sự kiện " + s.name + " đã bắt đầu! Gặp ông già Santa ở Đảo Kamê, Đảo Guru, Vách núi đen để tham gia.");
            } else if (!run && s.active) {
                s.active = false;
                removeBosses(s);
                Logger.success("Kết thúc sự kiện " + s.name + "\n");
                ServerNotify.gI().notify("Sự kiện " + s.name + " đã kết thúc. Hẹn gặp lại ở mùa sau!");
                // hết sự kiện thật (không phải admin thử ngày / tắt tay) thì tự chốt top
                if (testDate == null && !forced.containsKey(s.id)) {
                    for (Top t : s.tops) {
                        closeTop(t);
                    }
                    clearCounters(s);
                }
            }
        }
        syncNpcs();
        syncMobs();
    }

    // ------------------------------------------------------------------ quái to sự kiện
    /** Thả / thu quái to (Voi Chín Ngà, Gà Chín Cựa...) theo sự kiện đang chạy; hồi sinh con đã chết sau vài phút. */
    private synchronized void syncMobs() {
        Map<String, int[]> want = new LinkedHashMap<>();
        for (Season s : seasons.values()) {
            if (s.active) {
                for (int[] m : s.mobSpawns) {
                    want.putIfAbsent(m[1] + ":" + m[0], m);
                }
            }
        }
        for (Map.Entry<String, List<nro.models.mob.Mob>> en : eventMobs.entrySet()) {
            if (!want.containsKey(en.getKey())) {
                for (nro.models.mob.Mob m : en.getValue()) {
                    synchronized (m.zone) {
                        m.zone.mobs.remove(m);
                    }
                }
                eventMobs.remove(en.getKey());
                eventMobCfg.remove(en.getKey());
            }
        }
        for (Map.Entry<String, int[]> en : want.entrySet()) {
            int[] cfg = en.getValue();
            eventMobCfg.put(en.getKey(), cfg);
            List<nro.models.mob.Mob> list = eventMobs.get(en.getKey());
            if (list != null) {
                long respawn = Math.max(1, cfg[3]) * 60_000L;
                for (nro.models.mob.Mob m : list) {
                    if (m.isDie() && Util.canDoWithTime(m.lastTimeDie, respawn)) {
                        m.hoiSinh();
                        m.sendMobHoiSinh();
                    }
                }
                continue;
            }
            nro.models.map.Map map = nro.models.map.service.MapService.gI().getMapById(cfg[1]);
            nro.models.player_system.Template.MobTemplate temp = nro.models.server.Manager.getMobTemplateByTemp(cfg[0]);
            if (map == null || temp == null) {
                continue;
            }
            list = new ArrayList<>();
            for (Zone z : map.zones) {
                try {
                    nro.models.mob.Mob m = createBigMob(z, cfg[0], cfg[2], freeMobX(map, list.size()), temp);
                    list.add(m);
                } catch (Exception e) {
                    Logger.error("Không thả được quái sự kiện " + cfg[0] + " ở map " + cfg[1] + ": " + e.getMessage() + "\n");
                }
            }
            eventMobs.put(en.getKey(), list);
        }
    }

    /** Tạo một quái (to) trong khu z và thêm vào khu. */
    public static nro.models.mob.Mob createBigMob(Zone z, int tempId, int hp, int x, nro.models.player_system.Template.MobTemplate temp) {
        if (temp == null) {
            temp = nro.models.server.Manager.getMobTemplateByTemp(tempId);
        }
        nro.models.mob.Mob base = new nro.models.mob.Mob();
        base.id = z.mobs.size();
        base.tempId = tempId;
        base.level = 1;
        base.point.setHpFull(hp);
        base.point.sethp(hp);
        base.location.x = x;
        base.location.y = groundY(z.map, x);
        base.pDame = temp.percentDame;
        base.pTiemNang = temp.percentTiemNang;
        base.type = temp.type;
        base.setTiemNang();
        nro.models.mob.Mob m = switch (tempId) {
            case nro.models.consts.ConstMob.VOI_CHIN_NGA -> new nro.models.mob_bigboss.VoiChinNga(base);
            case nro.models.consts.ConstMob.GA_CHIN_CUA -> new nro.models.mob_bigboss.GaChinCua(base);
            case nro.models.consts.ConstMob.NGUA_CHIN_LMAO -> new nro.models.mob_bigboss.NguaChinLmao(base);
            default -> new nro.models.mob.Mob(base);
        };
        m.zone = z;
        synchronized (z) {
            z.mobs.add(m);
        }
        return m;
    }

    private static int freeMobX(nro.models.map.Map m, int i) {
        return Math.max(150, Math.min(m.mapWidth - 150, m.mapWidth / 3 + (i % 2) * m.mapWidth / 3));
    }

    // ------------------------------------------------------------------ npc
    /** Đặt / gỡ NPC sự kiện trên các bản đồ cho khớp với các sự kiện đang chạy. */
    private synchronized void syncNpcs() {
        Map<String, int[]> want = new LinkedHashMap<>();
        for (Season s : seasons.values()) {
            if (s.active) {
                for (int[] n : s.npcSpawns) {
                    want.putIfAbsent(n[1] + ":" + n[0], n);
                }
            }
        }
        for (Map.Entry<String, Npc> en : eventNpcs.entrySet()) {
            if (!want.containsKey(en.getKey())) {
                Npc npc = en.getValue();
                if (npc.map != null) {
                    npc.map.npcs.remove(npc);
                }
                nro.models.server.Manager.NPCS.remove(npc);
                eventNpcs.remove(en.getKey());
            }
        }
        for (Map.Entry<String, int[]> en : want.entrySet()) {
            if (eventNpcs.containsKey(en.getKey())) {
                continue;
            }
            int[] n = en.getValue();
            nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(n[1]);
            if (m == null || m.npcs.stream().anyMatch(x -> x.tempId == n[0])) {
                continue; // bản đồ đã có sẵn NPC này
            }
            try {
                int x = n[2] > 0 ? n[2] : freeX(m);
                int y = n[3] > 0 ? n[3] : groundY(m, x);
                Npc npc = n[0] == nro.models.consts.ConstNpc.SANTA || n[0] == nro.models.consts.ConstNpc.HUNG_VUONG
                        ? nro.models.npc.NpcFactory.createNPC(m.mapId, 1, x, y, n[0])
                        : new EventNpc(m.mapId, x, y, n[0]);
                if (npc != null) {
                    npc.map = m;
                    m.npcs.add(npc);
                    eventNpcs.put(en.getKey(), npc);
                }
            } catch (Exception e) {
                Logger.error("Không đặt được NPC sự kiện " + n[0] + " ở map " + n[1] + ": " + e.getMessage() + "\n");
            }
        }
    }

    /** Chỗ trống gần giữa bản đồ, cách các NPC khác ít nhất 70px. */
    private static int freeX(nro.models.map.Map m) {
        int mid = m.mapWidth / 2;
        for (int d = 0; d < m.mapWidth; d += 40) {
            for (int x : new int[]{mid + d, mid - d}) {
                if (x >= 100 && x <= m.mapWidth - 100 && m.npcs.stream().allMatch(o -> Math.abs(o.cx - x) >= 70)
                        && Math.abs(groundY(m, x) - groundY(m, x - 30)) < 24 && Math.abs(groundY(m, x) - groundY(m, x + 30)) < 24) {
                    return x;
                }
            }
        }
        return mid;
    }

    private static int groundY(nro.models.map.Map m, int x) {
        return m.yPhysicInTop(x, 24);
    }

    /** NPC đặt thêm khi có sự kiện: chỉ mở các mục sự kiện của NPC đó. */
    private static class EventNpc extends Npc {

        EventNpc(int mapId, int cx, int cy, int tempId) {
            super(mapId, 1, cx, cy, tempId, nro.models.server.Manager.NPC_TEMPLATES.get(tempId).avatar);
        }

        @Override
        public void openBaseMenu(Player player) {
            if (canOpenNpc(player)) {
                if (gI().npcExchanges(tempId).isEmpty()) {
                    createOtherMenu(player, nro.models.consts.ConstNpc.IGNORE_MENU, "Sự kiện đã kết thúc", "Đóng");
                } else {
                    gI().openNpcEvent(this, player);
                }
            }
        }

        @Override
        public void confirmMenu(Player player, int select) {
            // các menu sự kiện đã được SeasonEventService.npcSelect xử lý trước
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
    /** Trả về số lượng được nhặt sau khi trừ giới hạn ngày (0 = đã đủ hôm nay). */
    private int takeDaily(Player pl, Drop d, int qty) {
        if (d.dailyMax <= 0) {
            return qty;
        }
        LocalDate t = today();
        if (!t.equals(dailyDate)) {
            daily.clear();
            dailyDate = t;
        }
        String key = pl.id + ":" + d.itemId;
        int got = daily.getOrDefault(key, 0);
        int n = Math.min(qty, d.dailyMax - got);
        if (n > 0) {
            daily.put(key, got + n);
        }
        return Math.max(0, n);
    }

    private boolean matches(Drop d, Player pl, int mobTemp) {
        if (!d.mobs.isEmpty() && !d.mobs.contains(mobTemp)) {
            return false;
        }
        return d.maps.isEmpty() || (pl.zone != null && d.maps.contains(pl.zone.map.mapId));
    }

    /** item_id..item_to: rơi ngẫu nhiên 1 món trong khoảng (vd Bí ngô 1-7 sao). */
    private static int pickItem(Drop d) {
        return d.itemTo > d.itemId ? Util.nextInt(d.itemId, d.itemTo) : d.itemId;
    }

    /** Vật phẩm cần mang theo để được rơi (vd Giỏ đựng ngọc bí), mất 1 cái mỗi lần rơi. */
    private boolean useNeed(Player pl, Drop d, int qty) {
        if (d.needItem <= 0) {
            return true;
        }
        Item need = InventoryService.gI().findItemBag(pl, d.needItem);
        if (need == null) {
            daily.computeIfPresent(pl.id + ":" + d.itemId, (k, v) -> Math.max(0, v - qty)); // không rơi thì không tính vào giới hạn ngày
            Service.gI().sendThongBao(pl, "Cần có " + ItemService.gI().getTemplate(d.needItem).name + " để nhặt " + ItemService.gI().getTemplate(d.itemId).name);
            return false;
        }
        InventoryService.gI().subQuantityItemsBag(pl, need, 1);
        InventoryService.gI().sendItemBags(pl);
        return true;
    }

    /** Vật phẩm sự kiện rơi khi đánh quái. */
    public void addMobDrops(List<ItemMap> list, Player pl, Zone zone, int x, int y, int mobTemp) {
        if (pl == null || !pl.isPl()) {
            return;
        }
        for (Season s : seasons.values()) {
            if (!s.active) {
                continue;
            }
            for (Drop d : s.drops) {
                if (!d.fromBoss && matches(d, pl, mobTemp) && Util.isTrue(d.rate, 10000)) {
                    int qty = takeDaily(pl, d, Util.nextInt(d.qtyMin, d.qtyMax));
                    if (qty > 0 && useNeed(pl, d, qty)) {
                        list.add(new ItemMap(zone, pickItem(d), qty, x, y, pl.id));
                    }
                }
            }
        }
    }

    /** Vật phẩm sự kiện rơi khi hạ boss (mọi boss, hoặc boss có id trong cột mobs). */
    public void onBossKilled(Boss boss, Player pl) {
        if (pl == null || boss == null || boss.zone == null || !pl.isPl()) {
            return;
        }
        for (Season s : seasons.values()) {
            if (!s.active) {
                continue;
            }
            for (Drop d : s.drops) {
                if (d.fromBoss && matches(d, pl, (int) boss.id) && Util.isTrue(d.rate, 10000)) {
                    int qty = takeDaily(pl, d, Util.nextInt(d.qtyMin, d.qtyMax));
                    if (qty <= 0 || !useNeed(pl, d, qty)) {
                        continue;
                    }
                    int x = boss.location.x + Util.nextInt(-20, 20);
                    ItemMap it = new ItemMap(boss.zone, pickItem(d), qty, x,
                            boss.zone.map.yPhysicInTop(x, boss.location.y - 24), pl.id);
                    Service.gI().dropItemMap(boss.zone, it);
                }
            }
        }
    }

    // ------------------------------------------------------------------ NPC
    public boolean hasActive() {
        return !activeSeasons().isEmpty();
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
        sb.append(ConstFont.BOLD_DARK).append("Điểm sự kiện: ").append(pl.event.getEventPoint());
        List<String> opts = new ArrayList<>(s.groups);
        for (Top t : s.tops) {
            opts.add("Top\n" + t.name);
        }
        opts.add("Hướng\ndẫn");
        npc.createOtherMenu(pl, MENU_EVENT + s.id, sb.toString(), opts.toArray(new String[0]));
    }

    private List<Exchange> groupExchanges(Season s, int g) {
        List<Exchange> list = new ArrayList<>();
        if (g >= 0 && g < s.groups.size()) {
            for (Exchange e : s.exchanges) {
                if (e.group.equals(s.groups.get(g))) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    private void openGroup(Npc npc, Player pl, Season s, int g) {
        List<Exchange> list = groupExchanges(s, g);
        StringBuilder sb = new StringBuilder(ConstFont.BOLD_RED + s.groups.get(g).replace("\n", " ") + "\n" + ConstFont.BOLD_DARK);
        List<Integer> shown = new ArrayList<>();
        for (Exchange e : list) {
            for (int[] n : e.need) {
                if (!shown.contains(n[0])) {
                    shown.add(n[0]);
                    sb.append(ItemService.gI().getTemplate(n[0]).name).append(": ").append(count(pl, n[0])).append("\n");
                }
            }
        }
        String[] opts = new String[list.size()];
        for (int i = 0; i < opts.length; i++) {
            opts[i] = list.get(i).name;
        }
        npc.createOtherMenu(pl, MENU_GROUP + s.id * 50 + g, sb.toString(), opts);
    }

    private void openExchange(Npc npc, Player pl, Exchange e) {
        StringBuilder sb = new StringBuilder();
        sb.append(ConstFont.BOLD_GREEN).append(e.name.replace("\n", " ")).append("\n");
        sb.append(ConstFont.BOLD_DARK).append("Cần cho 1 lần:\n");
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
        if (e.costPoint > 0) {
            sb.append(pl.event.getEventPoint() >= e.costPoint ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED).append("Điểm sự kiện: ").append(e.costPoint).append("\n");
        }
        sb.append(ConstFont.BOLD_DARK).append("Nhận: ").append(rewardText(e));
        int max = maxTimes(pl, e);
        sb.append("\n").append(ConstFont.BOLD_GREEN).append("Có thể đổi: ").append(max).append(" lần");
        npc.createOtherMenu(pl, MENU_EXCHANGE + e.id, sb.toString(), "Đổi\nx1", "Đổi\nx10", "Đổi\nhết", "Từ chối");
    }

    private String rewardText(Exchange e) {
        StringBuilder sb = new StringBuilder();
        if (e.openNow) {
            sb.append("1 phần quà ngẫu nhiên");
        } else {
            String name = ItemService.gI().getTemplate(e.rewardItem).name;
            sb.append("x").append(e.rewardQty).append(e.rewardQtyMax > e.rewardQty ? "-" + e.rewardQtyMax : "").append(" ").append(name);
            if (e.rewardDays.length > 0) {
                sb.append(" (HSD ").append(joinDays(e.rewardDays)).append(" ngày");
                if (e.permRate > 0) {
                    sb.append(", ").append(e.permRate).append("% vĩnh viễn");
                }
                sb.append(")");
            }
        }
        if (e.bonusItem > 0) {
            sb.append(", ").append(e.bonusRate).append("% thêm ").append(ItemService.gI().getTemplate(e.bonusItem).name);
        }
        if (e.eventPoint > 0) {
            sb.append(", ").append(e.eventPoint).append(" điểm sự kiện");
        }
        Top t = tops.get(e.topId);
        if (t != null && e.topPoint > 0) {
            sb.append(", ").append(e.topPoint).append(" điểm top ").append(t.name.replace("\n", " "));
        }
        return sb.toString();
    }

    private static String joinDays(int[] days) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < days.length; i++) {
            sb.append(i > 0 ? "/" : "").append(days[i]);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ mục sự kiện ở NPC khác (Bà Hạt Mít, Chi Chi...)
    /** playerId -> {tempId NPC, vị trí nút sự kiện trong menu chính} */
    private final Map<Long, int[]> npcOption = new ConcurrentHashMap<>();

    private List<Exchange> npcExchanges(int npcTemp) {
        List<Exchange> list = new ArrayList<>();
        for (Season s : seasons.values()) {
            if (s.active) {
                for (Exchange e : s.exchanges) {
                    for (int n : e.npcs) {
                        if (n == npcTemp) {
                            list.add(e);
                            break;
                        }
                    }
                }
            }
        }
        return list;
    }

    /** Thêm nút sự kiện vào cuối menu chính của NPC nếu NPC có mục sự kiện đang diễn ra. */
    public String[] withNpcOption(Npc npc, Player pl, String[] menu) {
        List<Exchange> list = npcExchanges(npc.tempId);
        if (list.isEmpty()) {
            npcOption.remove(pl.id);
            return menu;
        }
        Season s = list.get(0).season;
        boolean one = list.stream().allMatch(e -> e.season == s);
        String[] out = java.util.Arrays.copyOf(menu, menu.length + 1);
        out[menu.length] = one ? s.name.replace(" ", "\n") : "Sự kiện";
        npcOption.put(pl.id, new int[]{npc.tempId, menu.length});
        return out;
    }

    /** Gọi trước confirmMenu của mọi NPC: xử lý nút sự kiện và các menu sự kiện. */
    public boolean npcSelect(Npc npc, Player pl, int select) {
        int menu = pl.idMark.getIndexMenu();
        if (pl.idMark.isBaseMenu()) {
            int[] o = npcOption.get(pl.id);
            if (o != null && o[0] == npc.tempId && o[1] == select && npc.tempId != nro.models.consts.ConstNpc.SANTA) {
                openNpcEvent(npc, pl);
                return true;
            }
            return false;
        }
        if (menu == MENU_NPC) {
            List<Exchange> list = npcExchanges(npc.tempId);
            if (select >= 0 && select < list.size()) {
                openExchange(npc, pl, list.get(select));
            }
            return true;
        }
        return confirmMenu(npc, pl, menu, select);
    }

    /** Tên các mục sự kiện của NPC này (đang diễn ra), để NPC tự xếp vào menu của mình. */
    public List<String> npcExchangeNames(int npcTemp) {
        List<String> out = new ArrayList<>();
        for (Exchange e : npcExchanges(npcTemp)) {
            out.add(e.name);
        }
        return out;
    }

    /** Mở mục sự kiện thứ idx của NPC (theo thứ tự của npcExchangeNames). */
    public void openNpcExchange(Npc npc, Player pl, int idx) {
        List<Exchange> list = npcExchanges(npc.tempId);
        if (idx >= 0 && idx < list.size()) {
            openExchange(npc, pl, list.get(idx));
        }
    }

    private void openNpcEvent(Npc npc, Player pl) {
        List<Exchange> list = npcExchanges(npc.tempId);
        if (list.isEmpty()) {
            return;
        }
        Season s = list.get(0).season;
        String[] opts = new String[list.size()];
        for (int i = 0; i < opts.length; i++) {
            opts[i] = list.get(i).name;
        }
        npc.createOtherMenu(pl, MENU_NPC, s.name + " vui vẻ\nTa có thể giúp gì cho con?", opts);
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
            int g = s.groups.size(), t = s.tops.size();
            if (select >= 0 && select < g) {
                openGroup(npc, pl, s, select);
            } else if (select >= g && select < g + t) {
                NpcService.gI().createTutorial(pl, npc.tempId, npc.avartar, topText(s.tops.get(select - g), pl));
            } else if (select == g + t) {
                NpcService.gI().createTutorial(pl, npc.tempId, npc.avartar, guide(s));
            }
            return true;
        }
        if (menu >= MENU_GROUP && menu < MENU_GROUP + 10000) {
            Season s = seasons.get((menu - MENU_GROUP) / 50);
            if (s == null || !s.active) {
                return true;
            }
            List<Exchange> list = groupExchanges(s, (menu - MENU_GROUP) % 50);
            if (select >= 0 && select < list.size()) {
                openExchange(npc, pl, list.get(select));
            }
            return true;
        }
        if (menu >= MENU_EXCHANGE && menu < MENU_GROUP) {
            Exchange e = exchanges.get(menu - MENU_EXCHANGE);
            if (e == null || !e.season.active) {
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
            sb.append("- ").append(ItemService.gI().getTemplate(d.itemId).name).append(": ")
                    .append(d.fromBoss ? "rơi khi hạ boss" : "rơi khi đánh quái")
                    .append(d.dailyMax > 0 ? " (tối đa " + d.dailyMax + "/ngày)" : "").append("\n");
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
        long max = 999;
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
        if (e.costPoint > 0) {
            max = Math.min(max, pl.event.getEventPoint() / e.costPoint);
        }
        return (int) Math.max(0, max);
    }

    private void exchange(Player pl, Exchange e, int times) {
        synchronized (pl) {
            String rewardName = e.openNow ? "" : ItemService.gI().getTemplate(e.rewardItem).name;
            int empty = InventoryService.gI().getCountEmptyBag(pl);
            boolean stack = !e.openNow && ItemService.gI().getTemplate(e.rewardItem).isUpToUp && e.rewardDays.length == 0;
            if (e.openNow || !stack) {
                // mỗi lần đổi ra 1 món riêng: không đổi quá số ô trống
                int perTime = e.openNow ? 1 : e.rewardQtyMax;
                times = Math.min(times, (empty - (e.bonusItem > 0 ? 1 : 0)) / Math.max(1, perTime));
            } else if (empty < 1 + (e.bonusItem > 0 ? 1 : 0)) {
                times = 0;
            }
            if (times <= 0) {
                Service.gI().sendThongBao(pl, "Hành trang không đủ chỗ trống");
                return;
            }
            if (maxTimes(pl, e) < times) {
                Service.gI().sendThongBao(pl, "Không đủ nguyên liệu để đổi " + times + " lần");
                return;
            }
            for (int[] n : e.need) {
                take(pl, n[0], n[1] * times);
            }
            pl.inventory.gold -= e.gold * times;
            pl.inventory.gem -= e.gem * times;
            pl.inventory.ruby -= e.ruby * times;
            if (e.costPoint > 0) {
                pl.event.subEventPoint(e.costPoint * times);
            }
            List<String> got = new ArrayList<>();
            int total = 0, bonus = 0;
            for (int i = 0; i < times; i++) {
                if (e.openNow) {
                    String g = Event2026Service.gI().openBoxById(pl, e.rewardItem);
                    if (g != null) {
                        got.add(g);
                    }
                } else {
                    int qty = Util.nextInt(e.rewardQty, e.rewardQtyMax);
                    if (stack) {
                        total += qty;
                    } else {
                        for (int k = 0; k < qty; k++) {
                            int days = 0;
                            if (e.rewardDays.length > 0 && !Util.isTrue(e.permRate, 100)) {
                                days = e.rewardDays[Util.nextInt(0, e.rewardDays.length - 1)];
                            }
                            InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(e.rewardItem, 1, days));
                            got.add(rewardName + (e.rewardDays.length > 0 ? (days > 0 ? " (" + days + " ngày)" : " (vĩnh viễn)") : ""));
                        }
                    }
                }
                if (e.bonusItem > 0 && Util.isTrue(e.bonusRate, 100)) {
                    bonus++;
                }
            }
            if (total > 0) {
                InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(e.rewardItem, total, 0));
                got.add("x" + total + " " + rewardName);
            }
            if (bonus > 0) {
                InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(e.bonusItem, bonus, 0));
                got.add("x" + bonus + " " + ItemService.gI().getTemplate(e.bonusItem).name);
            }
            if (e.eventPoint > 0) {
                pl.event.addEventPoint(e.eventPoint * times);
                got.add(e.eventPoint * times + " điểm sự kiện");
            }
            Top t = tops.get(e.topId);
            if (t != null && e.topPoint > 0) {
                addTopPoint(t, pl, e.topPoint * times);
                got.add(e.topPoint * times + " điểm top");
            }
            InventoryService.gI().sendItemBags(pl);
            Service.gI().sendMoney(pl);
            String text = String.join(", ", got);
            Service.gI().sendThongBao(pl, "Nhận được " + (text.length() > 200 ? text.substring(0, 200) + "..." : text));
        }
    }

    // ------------------------------------------------------------------ bộ đếm theo mùa
    /** Bộ đếm riêng của người chơi trong một mùa sự kiện (lưu DB, xoá khi mùa kết thúc). Dùng top_id âm trong season_event_point. */
    private final Map<String, Integer> counters = new ConcurrentHashMap<>();

    private static int counterId(int seasonId, int key) {
        return -(seasonId * 100 + key);
    }

    public int getCounter(int seasonId, int key, Player pl) {
        int id = counterId(seasonId, key);
        return counters.computeIfAbsent(id + ":" + pl.id, k -> {
            try (Connection con = LocalManager.getConnection();
                    PreparedStatement ps = con.prepareStatement("select points from season_event_point where top_id = ? and player_id = ?")) {
                ps.setInt(1, id);
                ps.setLong(2, pl.id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (Exception e) {
                return 0;
            }
        });
    }

    public int addCounter(int seasonId, int key, Player pl, int n) {
        int id = counterId(seasonId, key);
        int v = getCounter(seasonId, key, pl) + n;
        counters.put(id + ":" + pl.id, v);
        try {
            LocalManager.executeUpdate("INSERT INTO season_event_point (top_id, player_id, name, points) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE points = points + VALUES(points), name = VALUES(name)", id, pl.id, pl.name, n);
        } catch (Exception e) {
            Logger.error("Không lưu được bộ đếm sự kiện: " + e.getMessage() + "\n");
        }
        return v;
    }

    private void clearCounters(Season s) {
        int hi = counterId(s.id, 0), lo = counterId(s.id, 99);
        counters.keySet().removeIf(k -> {
            int id = Integer.parseInt(k.substring(0, k.indexOf(':')));
            return id >= lo && id <= hi;
        });
        try {
            LocalManager.executeUpdate("DELETE FROM season_event_point WHERE top_id BETWEEN ? AND ?", lo, hi);
        } catch (Exception e) {
            Logger.error("Không xoá được bộ đếm sự kiện: " + e.getMessage() + "\n");
        }
    }

    // ------------------------------------------------------------------ đua top
    private void addTopPoint(Top t, Player pl, int pts) {
        try {
            LocalManager.executeUpdate("INSERT INTO season_event_point (top_id, player_id, name, points) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE points = points + VALUES(points), name = VALUES(name)", t.id, pl.id, pl.name, pts);
        } catch (Exception e) {
            Logger.error("Không lưu được điểm top: " + e.getMessage() + "\n");
        }
    }

    private String topText(Top t, Player pl) {
        StringBuilder sb = new StringBuilder("Đua top " + t.name.replace("\n", " ") + "\n");
        int mine = 0;
        try (Connection con = LocalManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("select name, points from season_event_point where top_id = ? order by points desc, player_id limit 10")) {
                ps.setInt(1, t.id);
                try (ResultSet rs = ps.executeQuery()) {
                    int rank = 0;
                    while (rs.next()) {
                        sb.append("Top ").append(++rank).append(": ").append(rs.getString("name")).append(" - ").append(rs.getInt("points")).append(" điểm\n");
                    }
                    if (rank == 0) {
                        sb.append("Chưa có ai tham gia\n");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("select points from season_event_point where top_id = ? and player_id = ?")) {
                ps.setInt(1, t.id);
                ps.setLong(2, pl.id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        mine = rs.getInt("points");
                    }
                }
            }
        } catch (Exception e) {
            return "Không xem được top: " + e.getMessage();
        }
        sb.append("Điểm của bạn: ").append(mine).append("\n");
        int lastFrom = -1;
        for (int i = 0; i < t.rewards.size(); i++) {
            int[] r = t.rewards.get(i);
            if (r[0] != lastFrom) {
                sb.append(lastFrom == -1 ? "" : "\n").append(r[0] == r[1] ? "Top " + r[0] : "Top " + r[0] + "-" + r[1]).append(":");
                lastFrom = r[0];
            } else {
                sb.append(",");
            }
            sb.append(" ").append(rewardName(r[2], r[3], t.rewardDays.get(i)));
        }
        sb.append("\nTop chốt khi hết sự kiện, quà nhận khi vào game.");
        return sb.toString();
    }

    private static String rewardName(int item, int qty, int[] days) {
        String name = switch (item) {
            case -1 -> Util.numberToMoney(qty) + " vàng";
            case -2 -> qty + " ngọc xanh";
            case -3 -> qty + " hồng ngọc";
            default -> (qty > 1 ? qty + " " : "") + ItemService.gI().getTemplate(item).name;
        };
        if (item >= 0 && days.length > 0) {
            name += " " + days[0] + " ngày";
        } else if (item >= 0 && Set.of(5, 11, 18, 21, 23, 36, 72).contains((int) ItemService.gI().getTemplate(item).type)) {
            name += " vĩnh viễn";
        }
        return name;
    }

    /** Chốt top: xếp hạng, lưu quà chờ nhận và xoá điểm cho mùa sau. */
    public synchronized int closeTop(Top t) {
        int maxRank = 0;
        for (int[] r : t.rewards) {
            maxRank = Math.max(maxRank, r[1]);
        }
        int n = 0;
        try (Connection con = LocalManager.getConnection()) {
            List<long[]> ranks = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement("select player_id from season_event_point where top_id = ? and points > 0 order by points desc, player_id limit ?")) {
                ps.setInt(1, t.id);
                ps.setInt(2, Math.max(1, maxRank));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        ranks.add(new long[]{rs.getLong("player_id"), ++n});
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("insert into season_event_reward_pending (player_id, top_id, rank_no) values (?, ?, ?)")) {
                for (long[] r : ranks) {
                    ps.setLong(1, r[0]);
                    ps.setInt(2, t.id);
                    ps.setInt(3, (int) r[1]);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            try (PreparedStatement ps = con.prepareStatement("delete from season_event_point where top_id = ?")) {
                ps.setInt(1, t.id);
                ps.executeUpdate();
            }
            for (long[] r : ranks) {
                Player pl = Client.gI().getPlayer(r[0]);
                if (pl != null) {
                    deliverTopRewards(pl);
                }
            }
        } catch (Exception e) {
            Logger.error("Lỗi chốt top " + t.name + ": " + e.getMessage() + "\n");
        }
        Logger.success("Chốt top " + t.name + ": " + n + " người\n");
        return n;
    }

    /** Phát quà top đang chờ khi người chơi vào game. */
    public void deliverTopRewards(Player pl) {
        if (pl == null || !pl.isPl()) {
            return;
        }
        synchronized (pl) {
            try (Connection con = LocalManager.getConnection()) {
                List<int[]> pend = new ArrayList<>();
                try (PreparedStatement ps = con.prepareStatement("select id, top_id, rank_no from season_event_reward_pending where player_id = ?")) {
                    ps.setLong(1, pl.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            pend.add(new int[]{rs.getInt("id"), rs.getInt("top_id"), rs.getInt("rank_no")});
                        }
                    }
                }
                for (int[] p : pend) {
                    Top t = tops.get(p[1]);
                    List<Integer> idx = new ArrayList<>();
                    if (t != null) {
                        for (int i = 0; i < t.rewards.size(); i++) {
                            int[] r = t.rewards.get(i);
                            if (p[2] >= r[0] && p[2] <= r[1]) {
                                idx.add(i);
                            }
                        }
                    }
                    int slots = (int) idx.stream().filter(i -> t.rewards.get(i)[2] >= 0).count();
                    if (InventoryService.gI().getCountEmptyBag(pl) < slots) {
                        Service.gI().sendThongBao(pl, "Bạn có quà đua top, cần " + slots + " ô trống trong hành trang để nhận");
                        return;
                    }
                    for (int i : idx) {
                        int[] r = t.rewards.get(i);
                        int[] days = t.rewardDays.get(i);
                        if (r[2] < 0) {
                            Event2026Service.gI().giveCurrency(pl, r[2], r[3]);
                        } else {
                            InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(r[2], r[3], days.length > 0 ? days[0] : 0));
                        }
                    }
                    try (PreparedStatement ps = con.prepareStatement("delete from season_event_reward_pending where id = ?")) {
                        ps.setInt(1, p[0]);
                        ps.executeUpdate();
                    }
                    InventoryService.gI().sendItemBags(pl);
                    Service.gI().sendMoney(pl);
                    if (t != null) {
                        Service.gI().sendThongBao(pl, "Bạn đạt Top " + p[2] + " " + t.name.replace("\n", " ") + ", quà đã vào hành trang");
                    }
                }
            } catch (Exception e) {
                Logger.error("Lỗi phát quà top: " + e.getMessage() + "\n");
            }
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
                case "rongtype" -> {
                    nro.models.services.shenron.RongXuong.dragonType = Integer.parseInt(a[1]);
                    return "Loại rồng gửi cho client khi gọi Rồng Xương: " + a[1];
                }
                case "zombie" -> {
                    int m = a.length > 1 ? Integer.parseInt(a[1]) : 15;
                    nro.models.boss.event.Halloween.Zombie.force(m);
                    return "Zombie xuất hiện trong " + m + " phút tới (cần sự kiện Halloween đang chạy)";
                }
                case "top" -> {
                    StringBuilder sb = new StringBuilder("Các đua top (sukien chottop <id>):\n");
                    for (Top t : tops.values()) {
                        sb.append(t.id).append(". ").append(t.season.name).append(" - ").append(t.name.replace("\n", " ")).append("\n");
                    }
                    return sb.toString();
                }
                case "chottop" -> {
                    Top t = tops.get(Integer.parseInt(a[1]));
                    if (t == null) {
                        return "Không có đua top id " + a[1];
                    }
                    return "Đã chốt top " + t.name.replace("\n", " ") + ": " + closeTop(t) + " người nhận quà";
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
        sb.append("Lệnh: sukien bat|tat|tudong <id>, sukien reload, sukien ngay yyyy-mm-dd, sukien top, sukien chottop <id>, sukien zombie [phút], sukien rongtype <số>");
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
}
