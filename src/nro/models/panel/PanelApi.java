package nro.models.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.boss.Boss_Manager.BrolyManager;
import nro.models.data.LocalManager;
import nro.models.database.PlayerDAO;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.Client;
import nro.models.server.Maintenance;
import nro.models.server.Manager;
import nro.models.server.ServerManager;
import nro.models.server.ServerNotify;
import nro.models.services.ChatGlobalService;
import nro.models.services.Event2026Service;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.SeasonEventService;
import nro.models.services.Service;

/**
 * Các API của panel web. Mỗi hàm trả về một đối tượng (Map / List) để chuyển thành JSON.
 * Người chơi đang online: sửa trực tiếp trên bộ nhớ (lưu khi thoát); offline: sửa thẳng database.
 */
class PanelApi {

    private final PanelServer server;
    private final long startedAt = System.currentTimeMillis();

    PanelApi(PanelServer server) {
        this.server = server;
    }

    Object route(String r, String method, Map<String, String> q, Map<String, Object> b) throws Exception {
        return switch (r) {
            case "me" -> Map.of("ok", true);
            case "stats" -> stats();
            case "players" -> players(q.getOrDefault("q", ""), "1".equals(q.get("online")));
            case "player" -> player(lng(q.get("id")));
            case "player/money" -> playerMoney(b);
            case "player/give" -> playerGive(b);
            case "player/kick" -> playerKick(b);
            case "player/save" -> playerSave(b);
            case "accounts" -> accounts(q.getOrDefault("q", ""));
            case "account/update" -> accountUpdate(b);
            case "items" -> items(q.getOrDefault("q", ""), q.getOrDefault("type", ""));
            case "item" -> item(intv(q.get("id")));
            case "options" -> options(q.getOrDefault("q", ""));
            case "giftcodes" -> rows("select id, code, count_left, detail, datecreate, expired from giftcode order by id desc");
            case "giftcode/save" -> giftcodeSave(b);
            case "giftcode/delete" -> giftcodeDelete(b);
            case "boxes" -> rows("select b.box_id, t.name, count(*) n, sum(b.weight) w from item_box_reward b left join item_template t on t.id=b.box_id group by b.box_id, t.name order by b.box_id");
            case "box" -> boxRows(intv(q.get("id")));
            case "box/save" -> boxSave(b);
            case "box/delete" -> boxDelete(b);
            case "shops" -> rows("select s.id shop_id, s.npc_id, s.tag_name, t.id tab_id, t.NAME tab_name, (select count(*) from item_shop i where i.tab_id=t.id) n from shop s join tab_shop t on t.shop_id=s.id order by s.id, t.id");
            case "shop/items" -> rows("select i.id, i.temp_id, it.name, it.icon_id, i.cost, i.type_sell, i.is_sell, i.is_new from item_shop i left join item_template it on it.id=i.temp_id where i.tab_id=? order by i.id", intv(q.get("tab")));
            case "shop/save" -> shopSave(b);
            case "shop/delete" -> shopDelete(b);
            case "shop/reload" -> shopReload();
            case "seasons" -> seasons();
            case "season/cmd" -> Map.of("result", SeasonEventService.gI().admin(" " + str(b.get("cmd")).trim()));
            case "bosses" -> bosses();
            case "boss/spawn" -> bossSpawn(b);
            case "boss/remove" -> bossRemove(b);
            case "notify" -> notify(b);
            case "maintenance" -> maintenance(b);
            case "logs" -> PanelServer.LOG.since(lng(q.getOrDefault("since", "0")));
            case "audit" -> audit();
            case "sql" -> sql(str(b.get("query")));
            case "napthe" -> rows("select * from napthe order by id desc limit 300");
            case "config" -> config();
            case "config/save" -> configSave(b);
            case "maps" -> maps();
            case "map" -> map(intv(q.get("id")));
            case "map/npc/add" -> mapNpcAdd(b);
            case "map/npc/remove" -> mapNpcRemove(b);
            case "npcs" -> rows("select id, NAME name, head, avatar from npc_template order by id");
            case "mobs" -> rows("select id, NAME name, type, hp from mob_template order by id");
            case "player/teleport" -> playerTeleport(b);
            case "giveall" -> giveAll(b);
            case "exprate" -> expRate(b);
            case "tops" -> tops(str(q.getOrDefault("kind", "power")));
            case "clans" -> rows("select id, NAME name, slogan, power_point, max_member, clan_point, LEVEL level, create_time from clan order by power_point desc limit 300");
            case "kygui" -> rows("select k.id, k.player_id, p.name player, k.item_id, t.name, t.icon_id, k.gold, k.gem, k.quantity, k.isBuy from shop_ky_gui k left join player p on p.id=k.player_id left join item_template t on t.id=k.item_id order by k.id desc limit 300");
            default -> throw new IllegalArgumentException("Không có API " + r);
        };
    }

    // ------------------------------------------------------------------ tổng quan

    private Object stats() throws Exception {
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("server", ServerManager.NAME);
        m.put("startedAt", ServerManager.timeStart);
        m.put("uptimeSec", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        m.put("online", onlinePlayers().size());
        m.put("maxPlayer", Manager.MAX_PLAYER);
        m.put("memUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1048576);
        m.put("memMaxMb", rt.maxMemory() / 1048576);
        m.put("threads", Thread.activeCount());
        m.put("cpuLoad", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
        m.put("cpus", rt.availableProcessors());
        m.put("maintenance", Maintenance.isRunning);
        m.put("bosses", allBosses().stream().filter(x -> x.zone != null && !x.isDie()).count());
        m.put("accounts", scalar("select count(*) from account"));
        m.put("players", scalar("select count(*) from player"));
        m.put("seasons", SeasonEventService.gI().activeSeasons().stream().map(s -> s.name).toList());
        return m;
    }

    // ------------------------------------------------------------------ người chơi

    static List<Player> onlinePlayers() {
        for (int i = 0; i < 3; i++) {
            try {
                List<Player> l = new ArrayList<>();
                for (Player p : new ArrayList<>(Client.gI().getPlayers())) {
                    if (p != null && p.isPl()) {
                        l.add(p);
                    }
                }
                return l;
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
    }

    private Object players(String kw, boolean onlineOnly) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        if (onlineOnly) {
            for (Player p : onlinePlayers()) {
                if (kw.isEmpty() || p.name.toLowerCase().contains(kw.toLowerCase())) {
                    out.add(onlineRow(p));
                }
            }
            return out;
        }
        for (Map<String, Object> r : rows("select p.id, p.name, p.gender, a.username, a.ban, a.is_admin, p.data_point, p.data_inventory, p.data_location from player p left join account a on a.id=p.account_id "
                + "where p.name like ? or a.username like ? or p.id = ? order by p.id desc limit 200", "%" + kw + "%", "%" + kw + "%", kw.matches("\\d+") ? Long.parseLong(kw) : -1)) {
            long id = ((Number) r.get("id")).longValue();
            Player on = Client.gI().getPlayerByID((int) id);
            if (on != null) {
                Map<String, Object> o = onlineRow(on);
                o.put("username", r.get("username"));
                o.put("ban", r.get("ban"));
                out.add(o);
                continue;
            }
            JsonArray pt = arr(r.get("data_point")), inv = arr(r.get("data_inventory")), loc = arr(r.get("data_location"));
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", id);
            o.put("name", r.get("name"));
            o.put("username", r.get("username"));
            o.put("gender", r.get("gender"));
            o.put("power", pt != null && pt.size() > 1 ? pt.get(1).getAsLong() : 0);
            o.put("gold", inv != null && inv.size() > 0 ? inv.get(0).getAsLong() : 0);
            o.put("gem", inv != null && inv.size() > 1 ? inv.get(1).getAsLong() : 0);
            o.put("ruby", inv != null && inv.size() > 2 ? inv.get(2).getAsLong() : 0);
            o.put("map", loc != null && loc.size() > 0 ? mapName(loc.get(0).getAsInt()) : "");
            o.put("online", false);
            o.put("ban", r.get("ban"));
            out.add(o);
        }
        return out;
    }

    private Map<String, Object> onlineRow(Player p) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", p.id);
        o.put("name", p.name);
        o.put("gender", (int) p.gender);
        o.put("power", p.nPoint.power);
        o.put("gold", p.inventory.gold);
        o.put("gem", p.inventory.gem);
        o.put("ruby", p.inventory.ruby);
        o.put("map", p.zone != null ? p.zone.map.mapName + " (khu " + p.zone.zoneId + ")" : "");
        o.put("ip", p.session != null ? p.session.ipAddress : "");
        o.put("online", true);
        return o;
    }

    private Object player(long id) throws Exception {
        Map<String, Object> o = new LinkedHashMap<>();
        Player on = Client.gI().getPlayerByID((int) id);
        List<Map<String, Object>> rs = rows("select p.id, p.name, p.gender, p.account_id, a.username, a.ban, a.is_admin, a.vnd, a.tongnap, a.last_time_login, a.ip_address, p.data_point, p.data_inventory, p.data_location, p.items_bag, p.items_body, p.items_box from player p left join account a on a.id=p.account_id where p.id=?", id);
        if (rs.isEmpty()) {
            throw new IllegalArgumentException("Không có nhân vật " + id);
        }
        Map<String, Object> r = rs.get(0);
        o.putAll(on != null ? onlineRow(on) : ((List<Map<String, Object>>) players(String.valueOf(id), false)).stream().filter(x -> ((Number) x.get("id")).longValue() == id).findFirst().orElse(Map.of()));
        for (String k : new String[]{"account_id", "username", "ban", "is_admin", "vnd", "tongnap", "last_time_login", "ip_address"}) {
            o.put(k, r.get(k));
        }
        if (on != null) {
            o.put("body", items(on.inventory.itemsBody));
            o.put("bag", items(on.inventory.itemsBag));
            o.put("box", items(on.inventory.itemsBox));
        } else {
            o.put("body", dbItems(r.get("items_body")));
            o.put("bag", dbItems(r.get("items_bag")));
            o.put("box", dbItems(r.get("items_box")));
        }
        return o;
    }

    private List<Map<String, Object>> items(List<Item> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Item it = list.get(i);
            if (it == null || !it.isNotNullItem()) {
                continue;
            }
            List<String> ops = new ArrayList<>();
            for (Item.ItemOption io : it.itemOptions) {
                ops.add(io.getOptionString());
            }
            out.add(itemRow(i, it.template, it.quantity, ops));
        }
        return out;
    }

    private List<Map<String, Object>> dbItems(Object json) {
        List<Map<String, Object>> out = new ArrayList<>();
        JsonArray a = arr(json);
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.size(); i++) {
            try {
                JsonArray it = parse(a.get(i).getAsString()).getAsJsonArray();
                int tid = it.get(0).getAsInt();
                if (tid < 0 || tid >= Manager.ITEM_TEMPLATES.size()) {
                    continue;
                }
                List<String> ops = new ArrayList<>();
                for (JsonElement e : parse(it.get(2).getAsString()).getAsJsonArray()) {
                    JsonArray op = parse(e.getAsString()).getAsJsonArray();
                    int oid = op.get(0).getAsInt();
                    ItemOptionTemplate t = oid >= 0 && oid < Manager.ITEM_OPTION_TEMPLATES.size() ? Manager.ITEM_OPTION_TEMPLATES.get(oid) : null;
                    ops.add(t == null ? "#" + oid : t.name.replace("#", op.get(1).getAsString()));
                }
                out.add(itemRow(i, ItemService.gI().getTemplate(tid), it.get(1).getAsInt(), ops));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private Map<String, Object> itemRow(int slot, ItemTemplate t, int qty, List<String> ops) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("slot", slot);
        o.put("id", t.id);
        o.put("name", t.name);
        o.put("icon", t.iconID);
        o.put("qty", qty);
        o.put("options", ops);
        return o;
    }

    private Object playerMoney(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        long gold = lng(b.get("gold")), gem = lng(b.get("gem")), ruby = lng(b.get("ruby"));
        Player on = Client.gI().getPlayerByID((int) id);
        if (on != null) {
            on.inventory.gold = Math.max(0, Math.min(nro.models.player.Inventory.LIMIT_GOLD, on.inventory.gold + gold));
            on.inventory.gem = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) on.inventory.gem + gem));
            on.inventory.ruby = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) on.inventory.ruby + ruby));
            Service.gI().sendMoney(on);
            Service.gI().sendThongBao(on, "Bạn nhận được từ quản trị: " + money(gold, gem, ruby));
            return Map.of("ok", true, "online", true);
        }
        List<Map<String, Object>> rs = rows("select data_inventory from player where id=?", id);
        if (rs.isEmpty()) {
            throw new IllegalArgumentException("Không có nhân vật");
        }
        JsonArray inv = arr(rs.get(0).get("data_inventory"));
        inv.set(0, new com.google.gson.JsonPrimitive(Math.max(0, inv.get(0).getAsLong() + gold)));
        inv.set(1, new com.google.gson.JsonPrimitive(Math.max(0, inv.get(1).getAsLong() + gem)));
        inv.set(2, new com.google.gson.JsonPrimitive(Math.max(0, inv.get(2).getAsLong() + ruby)));
        update("update player set data_inventory=? where id=?", inv.toString(), id);
        return Map.of("ok", true, "online", false);
    }

    private static String money(long gold, long gem, long ruby) {
        List<String> s = new ArrayList<>();
        if (gold != 0) {
            s.add(gold + " vàng");
        }
        if (gem != 0) {
            s.add(gem + " ngọc xanh");
        }
        if (ruby != 0) {
            s.add(ruby + " hồng ngọc");
        }
        return String.join(", ", s);
    }

    private Object playerGive(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        int itemId = intv(b.get("itemId"));
        int qty = Math.max(1, intv(b.getOrDefault("qty", 1)));
        if (itemId < 0 || itemId >= Manager.ITEM_TEMPLATES.size()) {
            throw new IllegalArgumentException("Không có item " + itemId);
        }
        List<int[]> ops = new ArrayList<>();
        boolean shopOptions = !Boolean.FALSE.equals(b.get("shopOptions"));
        if (shopOptions) {
            for (Item.ItemOption io : ItemService.gI().getListOptionItemShop((short) itemId)) {
                ops.add(new int[]{io.optionTemplate.id, io.param});
            }
        }
        Object extra = b.get("options");
        if (extra instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof List<?> p && p.size() >= 2) {
                    int oid = intv(p.get(0));
                    ops.removeIf(x -> x[0] == oid);
                    ops.add(new int[]{oid, intv(p.get(1))});
                }
            }
        }
        Player on = Client.gI().getPlayerByID((int) id);
        ItemTemplate t = ItemService.gI().getTemplate(itemId);
        if (on != null) {
            Item it = ItemService.gI().createNewItem((short) itemId, qty);
            it.itemOptions.clear();
            for (int[] o : ops) {
                it.itemOptions.add(new Item.ItemOption(o[0], o[1]));
            }
            if (!InventoryService.gI().addItemBag(on, it)) {
                throw new IllegalStateException("Hành trang người chơi đã đầy");
            }
            InventoryService.gI().sendItemBags(on);
            Service.gI().sendThongBao(on, "Bạn nhận được từ quản trị: x" + qty + " " + t.name);
            return Map.of("ok", true, "online", true);
        }
        List<Map<String, Object>> rs = rows("select items_bag from player where id=?", id);
        if (rs.isEmpty()) {
            throw new IllegalArgumentException("Không có nhân vật");
        }
        JsonArray bag = arr(rs.get(0).get("items_bag"));
        JsonArray opsJson = new JsonArray();
        for (int[] o : ops) {
            opsJson.add("[" + o[0] + "," + o[1] + "]");
        }
        JsonArray it = new JsonArray();
        it.add(itemId);
        it.add(qty);
        it.add(opsJson.toString());
        it.add(System.currentTimeMillis());
        for (int i = 0; i < bag.size(); i++) {
            JsonArray slot = parse(bag.get(i).getAsString()).getAsJsonArray();
            if (slot.get(0).getAsInt() == -1) {
                bag.set(i, new com.google.gson.JsonPrimitive(it.toString()));
                update("update player set items_bag=? where id=?", bag.toString(), id);
                return Map.of("ok", true, "online", false);
            }
        }
        throw new IllegalStateException("Hành trang người chơi đã đầy");
    }

    private Object playerKick(Map<String, Object> b) {
        Player on = Client.gI().getPlayerByID(intv(b.get("id")));
        if (on == null || on.session == null) {
            throw new IllegalStateException("Người chơi không online");
        }
        Client.gI().kickSession(on.session);
        return Map.of("ok", true);
    }

    private Object playerSave(Map<String, Object> b) {
        int n = 0;
        for (Player p : onlinePlayers()) {
            if (b.get("id") == null || p.id == lng(b.get("id"))) {
                PlayerDAO.updatePlayer(p);
                n++;
            }
        }
        return Map.of("ok", true, "saved", n);
    }

    // ------------------------------------------------------------------ tài khoản

    private Object accounts(String kw) throws Exception {
        return rows("select a.id, a.username, a.ban, a.is_admin, a.vnd, a.tongnap, a.create_time, a.last_time_login, a.ip_address, "
                + "(select group_concat(p.name) from player p where p.account_id=a.id) players from account a "
                + "where a.username like ? or a.id = ? or exists (select 1 from player p where p.account_id=a.id and p.name like ?) order by a.id desc limit 200",
                "%" + kw + "%", kw.matches("\\d+") ? Long.parseLong(kw) : -1, "%" + kw + "%");
    }

    private Object accountUpdate(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        if (b.containsKey("ban")) {
            update("update account set ban=? where id=?", intv(b.get("ban")), id);
            if (intv(b.get("ban")) == 1) {
                for (Player p : onlinePlayers()) {
                    if (p.session != null && p.session.userId == id) {
                        Client.gI().kickSession(p.session);
                    }
                }
            }
        }
        if (b.containsKey("is_admin")) {
            update("update account set is_admin=? where id=?", intv(b.get("is_admin")), id);
        }
        if (b.containsKey("vnd")) {
            update("update account set vnd=? where id=?", intv(b.get("vnd")), id);
        }
        if (b.containsKey("tongnap")) {
            update("update account set tongnap=? where id=?", intv(b.get("tongnap")), id);
        }
        String pw = str(b.get("password"));
        if (!pw.isEmpty()) {
            update("update account set password=? where id=?", pw, id);
        }
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------ item

    private Object items(String kw, String type) {
        List<Map<String, Object>> out = new ArrayList<>();
        String k = kw.toLowerCase();
        for (ItemTemplate t : Manager.ITEM_TEMPLATES) {
            if (t == null || t.name == null) {
                continue;
            }
            if (!type.isEmpty() && !String.valueOf(t.type).equals(type)) {
                continue;
            }
            if (!k.isEmpty() && !t.name.toLowerCase().contains(k) && !String.valueOf(t.id).equals(k)) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", t.id);
            o.put("name", t.name);
            o.put("type", t.type);
            o.put("gender", t.gender);
            o.put("level", t.level);
            o.put("icon", t.iconID);
            o.put("desc", t.description);
            out.add(o);
            if (out.size() >= 300) {
                break;
            }
        }
        return out;
    }

    private Object item(int id) {
        ItemTemplate t = ItemService.gI().getTemplate(id);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", t.id);
        o.put("name", t.name);
        o.put("type", t.type);
        o.put("gender", t.gender);
        o.put("level", t.level);
        o.put("icon", t.iconID);
        o.put("part", t.part);
        o.put("strRequire", t.strRequire);
        o.put("desc", t.description);
        List<Map<String, Object>> ops = new ArrayList<>();
        for (Item.ItemOption io : ItemService.gI().getListOptionItemShop((short) id)) {
            ops.add(Map.of("id", io.optionTemplate.id, "param", io.param, "text", io.getOptionString()));
        }
        o.put("options", ops);
        return o;
    }

    private Object options(String kw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ItemOptionTemplate t : Manager.ITEM_OPTION_TEMPLATES) {
            if (t != null && (kw.isEmpty() || t.name.toLowerCase().contains(kw.toLowerCase()) || String.valueOf(t.id).equals(kw))) {
                out.add(Map.of("id", t.id, "name", t.name));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ giftcode

    private Object giftcodeSave(Map<String, Object> b) throws Exception {
        String code = str(b.get("code")).trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Thiếu mã code");
        }
        String detail = str(b.get("detail"));
        parse(detail).getAsJsonArray(); // kiểm tra JSON hợp lệ
        int count = intv(b.getOrDefault("count", 1));
        String expired = str(b.get("expired")).replace('T', ' ');
        if (expired.length() == 16) {
            expired += ":00";
        }
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update giftcode set code=?, count_left=?, detail=?, expired=? where id=?", code, count, detail, expired, lng(b.get("id")));
        } else {
            update("insert into giftcode (code, count_left, detail, datecreate, expired) values (?, ?, ?, now(), ?)", code, count, detail, expired);
        }
        reloadGiftcodes();
        return Map.of("ok", true);
    }

    private Object giftcodeDelete(Map<String, Object> b) throws Exception {
        update("delete from giftcode where id=?", lng(b.get("id")));
        reloadGiftcodes();
        return Map.of("ok", true);
    }

    private void reloadGiftcodes() throws Exception {
        List<nro.models.player_system.GiftCode> list = new ArrayList<>();
        for (Map<String, Object> r : rows("select * from giftcode")) {
            nro.models.player_system.GiftCode g = new nro.models.player_system.GiftCode();
            g.id = intv(r.get("id"));
            g.code = str(r.get("code"));
            g.countLeft = intv(r.get("count_left"));
            if (g.countLeft == -1) {
                g.countLeft = 999999999;
            }
            g.datecreate = (java.sql.Timestamp) r.get("datecreate");
            g.dateexpired = (java.sql.Timestamp) r.get("expired");
            try {
                for (JsonElement e : parse(str(r.get("detail"))).getAsJsonArray()) {
                    var o = e.getAsJsonObject();
                    int id = o.get("id").getAsInt();
                    ArrayList<Item.ItemOption> ops = new ArrayList<>();
                    if (o.has("options")) {
                        for (JsonElement op : o.getAsJsonArray("options")) {
                            ops.add(new Item.ItemOption(op.getAsJsonObject().get("id").getAsInt(), op.getAsJsonObject().get("param").getAsInt()));
                        }
                    }
                    g.option.put(id, ops);
                    g.detail.put(id, o.get("quantity").getAsInt());
                }
            } catch (Exception ignored) {
            }
            list.add(g);
        }
        synchronized (nro.models.managers.GiftCodeManager.gI()) {
            nro.models.managers.GiftCodeManager.gI().listGiftCode.clear();
            nro.models.managers.GiftCodeManager.gI().listGiftCode.addAll(list);
        }
    }

    // ------------------------------------------------------------------ hộp quà

    private Object boxRows(int boxId) throws Exception {
        return rows("select b.id, b.box_id, b.item_id, t.name, t.icon_id, b.qty_min, b.qty_max, b.weight, b.hsd_days, b.perm_rate from item_box_reward b left join item_template t on t.id=b.item_id where b.box_id=? order by b.id", boxId);
    }

    private Object boxSave(Map<String, Object> b) throws Exception {
        Object[] v = {intv(b.get("box_id")), intv(b.get("item_id")), Math.max(1, intv(b.getOrDefault("qty_min", 1))), Math.max(1, intv(b.getOrDefault("qty_max", 1))),
            intv(b.getOrDefault("weight", 1)), str(b.get("hsd_days")), intv(b.getOrDefault("perm_rate", 0))};
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update item_box_reward set box_id=?, item_id=?, qty_min=?, qty_max=?, weight=?, hsd_days=?, perm_rate=? where id=?",
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], lng(b.get("id")));
        } else {
            update("insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate) values (?, ?, ?, ?, ?, ?, ?)", v);
        }
        Event2026Service.gI().reloadBoxes();
        return Map.of("ok", true);
    }

    private Object boxDelete(Map<String, Object> b) throws Exception {
        update("delete from item_box_reward where id=?", lng(b.get("id")));
        Event2026Service.gI().reloadBoxes();
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------ shop

    private Object shopSave(Map<String, Object> b) throws Exception {
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update item_shop set temp_id=?, cost=?, type_sell=?, is_sell=?, is_new=? where id=?",
                    intv(b.get("temp_id")), intv(b.get("cost")), intv(b.get("type_sell")), intv(b.get("is_sell")), intv(b.get("is_new")), lng(b.get("id")));
        } else {
            update("insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, create_time) values (?, ?, ?, ?, ?, ?, 0, now())",
                    intv(b.get("tab_id")), intv(b.get("temp_id")), intv(b.get("is_new")), intv(b.getOrDefault("is_sell", 1)), intv(b.get("type_sell")), intv(b.get("cost")));
        }
        return shopReload();
    }

    private Object shopDelete(Map<String, Object> b) throws Exception {
        update("delete from item_shop_option where item_shop_id=?", lng(b.get("id")));
        update("delete from item_shop where id=?", lng(b.get("id")));
        return shopReload();
    }

    private Object shopReload() throws Exception {
        try (Connection con = LocalManager.getConnection()) {
            Manager.SHOPS = nro.models.database.ShopDAO.getShops(con);
        }
        return Map.of("ok", true, "shops", Manager.SHOPS.size());
    }

    // ------------------------------------------------------------------ sự kiện mùa

    private Object seasons() {
        return SeasonEventService.gI().panelList();
    }

    // ------------------------------------------------------------------ boss

    private static List<Boss> allBosses() {
        List<Boss> l = new ArrayList<>();
        try {
            l.addAll(new ArrayList<>(BossManager.gI().getBosses()));
            l.addAll(new ArrayList<>(BrolyManager.gI().getBosses()));
        } catch (Exception ignored) {
        }
        return l;
    }

    private Object bosses() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Boss b : allBosses()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", b.id);
            o.put("name", b.name);
            o.put("map", b.zone != null ? b.zone.map.mapName + " (khu " + b.zone.zoneId + ")" : "");
            o.put("hp", b.nPoint.hp);
            o.put("hpMax", b.nPoint.hpMax);
            o.put("alive", b.zone != null && !b.isDie());
            list.add(o);
        }
        List<Map<String, Object>> ids = new ArrayList<>();
        for (Field f : BossID.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                try {
                    ids.add(Map.of("key", f.getName(), "id", f.getInt(null)));
                } catch (Exception ignored) {
                }
            }
        }
        return Map.of("bosses", list, "ids", ids);
    }

    private Object bossSpawn(Map<String, Object> b) {
        Boss boss = BossManager.gI().createBoss(intv(b.get("bossId")));
        if (boss == null) {
            throw new IllegalStateException("Không gọi được boss (BossManager chưa hỗ trợ ID này)");
        }
        return Map.of("ok", true, "name", str(boss.name), "note", "Đã gọi boss " + str(boss.name));
    }

    private Object bossRemove(Map<String, Object> b) {
        long id = lng(b.get("id"));
        for (Boss boss : allBosses()) {
            if (boss.id == id && boss.zone != null) {
                boss.leaveMap();
                return Map.of("ok", true);
            }
        }
        throw new IllegalStateException("Không thấy boss đang ở trên bản đồ");
    }

    // ------------------------------------------------------------------ thông báo, bảo trì

    private Object notify(Map<String, Object> b) {
        String text = str(b.get("text")).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Thiếu nội dung");
        }
        switch (str(b.get("type"))) {
            case "popup" ->
                Service.gI().sendThongBaoAllPlayer(text);
            case "world" -> {
                List<Player> on = onlinePlayers();
                if (on.isEmpty()) {
                    throw new IllegalStateException("Không có ai online để gửi chat thế giới");
                }
                ChatGlobalService.gI().chatVip(on.get(0), "[Hệ thống] " + text);
            }
            default ->
                ServerNotify.gI().notify(text);
        }
        return Map.of("ok", true);
    }

    private Object maintenance(Map<String, Object> b) {
        if (Maintenance.isRunning) {
            throw new IllegalStateException("Server đang bảo trì");
        }
        int sec = Math.max(10, intv(b.getOrDefault("seconds", 60)));
        playerSave(Map.of());
        Maintenance.gI().startSeconds(sec);
        return Map.of("ok", true, "seconds", sec);
    }

    private Object audit() throws Exception {
        File f = new File("logs/panel_actions.log");
        if (!f.exists()) {
            return List.of();
        }
        List<String> all = java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>(all.subList(Math.max(0, all.size() - 300), all.size()));
        java.util.Collections.reverse(out);
        return out;
    }

    // ------------------------------------------------------------------ SQL, cấu hình

    private Object sql(String query) throws Exception {
        String s = query.trim().toLowerCase();
        if (!(s.startsWith("select") || s.startsWith("show") || s.startsWith("describe") || s.startsWith("desc ") || s.startsWith("explain"))) {
            throw new IllegalArgumentException("Chỉ cho phép câu lệnh đọc (SELECT, SHOW, DESCRIBE, EXPLAIN)");
        }
        if (query.contains(";") && query.indexOf(';') < query.trim().length() - 1) {
            throw new IllegalArgumentException("Chỉ chạy 1 câu lệnh");
        }
        List<Map<String, Object>> r = rows(query.replaceAll(";\\s*$", ""));
        return r.size() > 500 ? r.subList(0, 500) : r;
    }

    private static final List<String> EDITABLE = List.of("server.name", "server.expserver", "server.maxplayer", "server.maxperip", "server.waitlogin",
            "panel.port", "panel.host", "panel.user", "panel.pass", "panel.enable");

    private Object config() throws Exception {
        Properties p = loadConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : p.stringPropertyNames().stream().sorted().toList()) {
            String v = p.getProperty(k);
            m.put(k, k.contains("pass") ? (v.isEmpty() ? "" : "******") : v);
        }
        return Map.of("values", m, "editable", EDITABLE);
    }

    private Object configSave(Map<String, Object> b) throws Exception {
        File f = new File("Config.properties");
        List<String> lines = java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        Map<String, String> changes = new LinkedHashMap<>();
        Object v = b.get("values");
        if (v instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                String val = String.valueOf(e.getValue()).replace("\n", "").trim();
                if (EDITABLE.contains(k) && !val.equals("******")) {
                    changes.put(k, val);
                }
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int eq = l.indexOf('=');
            if (eq > 0 && !l.trim().startsWith("#")) {
                String k = l.substring(0, eq).trim();
                if (changes.containsKey(k)) {
                    lines.set(i, k + "=" + changes.remove(k));
                }
            }
        }
        for (Map.Entry<String, String> e : changes.entrySet()) {
            lines.add(e.getKey() + "=" + e.getValue());
        }
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(String.join("\n", lines) + "\n");
        }
        return Map.of("ok", true, "note", "Đã lưu Config.properties, khởi động lại server để áp dụng");
    }

    private static Properties loadConfig() throws Exception {
        Properties p = new Properties();
        try (InputStreamReader r = new InputStreamReader(new FileInputStream("Config.properties"), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    // ------------------------------------------------------------------ bản đồ & NPC

    private Object maps() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows("select id, NAME name, planet_id, zones, max_player from map_template order by id")) {
            nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(intv(r.get("id")));
            r.put("loaded", m != null);
            r.put("npcCount", m != null ? m.npcs.size() : 0);
            int online = 0;
            if (m != null) {
                for (nro.models.map.Zone z : m.zones) {
                    online += z.getNumOfPlayers();
                }
            }
            r.put("online", online);
            out.add(r);
        }
        return out;
    }

    private Object map(int id) throws Exception {
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(id);
        if (m == null) {
            throw new IllegalArgumentException("Bản đồ " + id + " chưa được tải");
        }
        List<Map<String, Object>> npcs = new ArrayList<>();
        for (nro.models.npc.Npc n : m.npcs) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("tempId", n.tempId);
            o.put("name", npcName(n.tempId));
            o.put("x", n.cx);
            o.put("y", n.cy);
            npcs.add(o);
        }
        List<Map<String, Object>> db = rows("select npcs from map_template where id=?", id);
        return Map.of("id", id, "name", m.mapName, "width", m.mapWidth, "zones", m.zones.size(), "npcs", npcs,
                "dbNpcs", db.isEmpty() ? "[]" : str(db.get(0).get("npcs")));
    }

    private static String npcName(int tempId) {
        try {
            return Manager.NPC_TEMPLATES.get(tempId).name;
        } catch (Exception e) {
            return "NPC " + tempId;
        }
    }

    /** Đọc cột npcs của map_template (dạng [[id,x,y],...] hoặc ["[id,x,y]",...]). */
    private static List<int[]> dbNpcs(int mapId) throws Exception {
        List<int[]> out = new ArrayList<>();
        List<Map<String, Object>> r = rows("select npcs from map_template where id=?", mapId);
        JsonArray a = r.isEmpty() ? null : arr(r.get(0).get("npcs"));
        if (a != null) {
            for (JsonElement e : a) {
                JsonArray n = e.isJsonArray() ? e.getAsJsonArray() : parse(e.getAsString()).getAsJsonArray();
                out.add(new int[]{n.get(0).getAsInt(), n.get(1).getAsInt(), n.get(2).getAsInt()});
            }
        }
        return out;
    }

    private static void saveDbNpcs(int mapId, List<int[]> list) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            int[] n = list.get(i);
            sb.append(i > 0 ? "," : "").append("[").append(n[0]).append(",").append(n[1]).append(",").append(n[2]).append("]");
        }
        update("update map_template set npcs=? where id=?", sb.append("]").toString(), mapId);
    }

    private Object mapNpcAdd(Map<String, Object> b) throws Exception {
        int mapId = intv(b.get("mapId")), tempId = intv(b.get("tempId"));
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(mapId);
        if (m == null) {
            throw new IllegalArgumentException("Bản đồ " + mapId + " chưa được tải");
        }
        if (tempId < 0 || tempId >= Manager.NPC_TEMPLATES.size()) {
            throw new IllegalArgumentException("Không có NPC " + tempId);
        }
        int x = intv(b.getOrDefault("x", 0));
        if (x <= 0) {
            x = m.mapWidth / 2;
        }
        int y = intv(b.getOrDefault("y", 0));
        if (y <= 0) {
            y = m.yPhysicInTop(x, 0);
        }
        nro.models.npc.Npc npc = nro.models.npc.NpcFactory.createNPC(mapId, 1, x, y, tempId);
        if (npc == null) {
            throw new IllegalStateException("Không tạo được NPC");
        }
        npc.map = m;
        m.npcs.add(npc);
        if (!Boolean.FALSE.equals(b.get("persist"))) {
            List<int[]> list = dbNpcs(mapId);
            list.add(new int[]{tempId, x, y});
            saveDbNpcs(mapId, list);
        }
        return Map.of("ok", true, "note", "Người chơi vào lại bản đồ sẽ thấy NPC");
    }

    private Object mapNpcRemove(Map<String, Object> b) throws Exception {
        int mapId = intv(b.get("mapId")), tempId = intv(b.get("tempId")), x = intv(b.getOrDefault("x", -1));
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(mapId);
        int removed = 0;
        if (m != null) {
            for (nro.models.npc.Npc n : new ArrayList<>(m.npcs)) {
                if (n.tempId == tempId && (x < 0 || n.cx == x)) {
                    m.npcs.remove(n);
                    Manager.NPCS.remove(n);
                    removed++;
                }
            }
        }
        if (!Boolean.FALSE.equals(b.get("persist"))) {
            List<int[]> list = dbNpcs(mapId);
            list.removeIf(n -> n[0] == tempId && (x < 0 || n[1] == x));
            saveDbNpcs(mapId, list);
        }
        return Map.of("ok", true, "removed", removed, "note", "Người chơi vào lại bản đồ sẽ không còn thấy NPC");
    }

    // ------------------------------------------------------------------ chức năng thêm

    private Object playerTeleport(Map<String, Object> b) {
        Player on = Client.gI().getPlayerByID(intv(b.get("id")));
        if (on == null) {
            throw new IllegalStateException("Người chơi không online");
        }
        int mapId = intv(b.get("mapId"));
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(mapId);
        if (m == null) {
            throw new IllegalArgumentException("Không có bản đồ " + mapId);
        }
        int x = intv(b.getOrDefault("x", 0));
        if (x <= 0) {
            x = m.mapWidth / 2;
        }
        nro.models.map.service.ChangeMapService.gI().changeMapBySpaceShip(on, mapId, -1, x);
        return Map.of("ok", true);
    }

    private Object giveAll(Map<String, Object> b) throws Exception {
        int n = 0, full = 0;
        for (Player p : onlinePlayers()) {
            Map<String, Object> one = new LinkedHashMap<>(b);
            one.put("id", p.id);
            try {
                if (intv(b.getOrDefault("itemId", -1)) >= 0) {
                    playerGive(one);
                }
                if (lng(b.get("gold")) != 0 || lng(b.get("gem")) != 0 || lng(b.get("ruby")) != 0) {
                    playerMoney(one);
                }
                n++;
            } catch (IllegalStateException e) {
                full++;
            }
        }
        return Map.of("ok", true, "given", n, "bagFull", full);
    }

    private Object expRate(Map<String, Object> b) {
        if (b.get("rate") != null) {
            int r = Math.max(1, Math.min(100, intv(b.get("rate"))));
            Manager.RATE_EXP_SERVER = (byte) r;
        }
        return Map.of("ok", true, "rate", Manager.RATE_EXP_SERVER);
    }

    private Object tops(String kind) throws Exception {
        return switch (kind) {
            case "sukien" -> rows("select id, name, point_sukien point from player order by point_sukien desc limit 100");
            case "sukien1" -> rows("select id, name, point_sukien1 point from player order by point_sukien1 desc limit 100");
            case "sukien2" -> rows("select id, name, point_sukien2 point from player order by point_sukien2 desc limit 100");
            case "hopden" -> rows("select e.player_id id, p.name, e.hdba_count point from player_extra e join player p on p.id=e.player_id order by e.hdba_count desc limit 100");
            case "naptien" -> rows("select a.id, a.username name, a.tongnap point from account a order by a.tongnap desc limit 100");
            default -> {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Map<String, Object> r : rows("select id, name, data_point from player")) {
                    JsonArray pt = arr(r.get("data_point"));
                    long power = pt != null && pt.size() > 1 ? pt.get(1).getAsLong() : 0;
                    out.add(new LinkedHashMap<>(Map.of("id", r.get("id"), "name", r.get("name"), "point", power)));
                }
                out.sort((x, y) -> Long.compare(((Number) y.get("point")).longValue(), ((Number) x.get("point")).longValue()));
                yield out.subList(0, Math.min(100, out.size()));
            }
        };
    }

    // ------------------------------------------------------------------ database

    static List<Map<String, Object>> rows(String sql, Object... args) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection con = LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int c = 1; c <= md.getColumnCount(); c++) {
                        Object o = rs.getObject(c);
                        if (o instanceof byte[] bytes) {
                            o = new String(bytes, StandardCharsets.UTF_8);
                        } else if (o instanceof java.sql.Timestamp) {
                            // giữ nguyên để đọc giftcode; Gson tự chuyển thành chuỗi
                        }
                        m.put(md.getColumnLabel(c), o);
                    }
                    out.add(m);
                }
            }
        }
        return out;
    }

    static int update(String sql, Object... args) throws Exception {
        try (Connection con = LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    private static Object scalar(String sql) throws Exception {
        List<Map<String, Object>> r = rows(sql);
        return r.isEmpty() ? 0 : r.get(0).values().iterator().next();
    }

    @SuppressWarnings("deprecation")
    private static JsonElement parse(String s) {
        return new JsonParser().parse(s);
    }

    private static JsonArray arr(Object json) {
        try {
            return json == null ? null : parse(String.valueOf(json)).getAsJsonArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String mapName(int mapId) {
        try {
            nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(mapId);
            return m != null ? m.mapName : "Map " + mapId;
        } catch (Exception e) {
            return "Map " + mapId;
        }
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static long lng(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return 0;
        }
        return o instanceof Number n ? n.longValue() : (long) Double.parseDouble(String.valueOf(o).trim());
    }

    static int intv(Object o) {
        return (int) lng(o);
    }
}
