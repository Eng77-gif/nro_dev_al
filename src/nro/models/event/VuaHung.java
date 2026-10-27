package nro.models.event;

import java.util.ArrayList;
import java.util.List;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import java.util.Random;
import nro.models.consts.ConstFont;
import nro.models.npc.DuaHauEgg;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;

/**
 *
 * @author By Mr Blue
 */
public class VuaHung extends Npc {

    private static final int NGA_VOI = 1220;
    private static final int CUA_GA = 1221;
    private static final int HONG_MAO = 1222;
    private static final int HOP_QUA_THUONG = 1823;
    private static final int HOP_QUA_VIP = 1824;
    private static final int BANH_DAY = 1542;
    private static final int BANH_CHUNG = 1556;
    private static final int DUA_HAU = 569;
    private static final int TEM = 1558;
    private static final int THOI_VANG = 457;
    private static final int LENH_BAI_THANH_CO = 2018;
    public static final int MAP_THANH_CO_1 = 183, MAP_THANH_CO_2 = 184, MAP_DAU_TRUONG = 185;
    /** người đã trồng dưa trong khung giờ hiện tại: playerId -> mã khung giờ */
    private static final java.util.Map<Long, Long> daTrong = new java.util.concurrent.ConcurrentHashMap<>();
    /** các nút đang hiện ở menu của từng người */
    private static final java.util.Map<Long, List<String>> actions = new java.util.concurrent.ConcurrentHashMap<>();

    private final Random random = new Random();

    public VuaHung(int mapId, int status, int cx, int cy, int tempId, int avatar) {
        super(mapId, status, cx, cy, tempId, avatar);
    }

    /** Trồng dưa vào phút 25-29 và 55-59 mỗi giờ, dưa chín đúng phút 00 / 30. */
    private static boolean isPlantTime() {
        return java.time.LocalTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).getMinute() % 30 >= 25;
    }

    private static long plantWindow() {
        return System.currentTimeMillis() / (30 * 60_000L);
    }

    private static long msToHarvest() {
        long half = 30 * 60_000L;
        long now = System.currentTimeMillis();
        return (now / half + 1) * half - now;
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }
        List<String> act = new ArrayList<>();
        List<String> label = new ArrayList<>();
        if (isPlantTime() && player.DuaHauEgg == null && !Long.valueOf(plantWindow()).equals(daTrong.get(player.id))) {
            act.add("PLANT");
            label.add("Trồng\ndưa");
        }
        act.add("DOI");
        label.add("Đổi\ndưa hấu");
        List<String> ex = nro.models.services.SeasonEventService.gI().npcExchangeNames(tempId);
        if (!ex.isEmpty()) {
            for (int i = 0; i < ex.size(); i++) {
                act.add("EX" + i);
                label.add(ex.get(i));
            }
        } else {
            String[] old = {"Dâng\nsính lễ", "Dâng\nsính lễ\nxịn", "Dâng\nbánh dầy", "Dâng\nbánh chưng"};
            for (int i = 0; i < old.length; i++) {
                act.add("OLD" + (i + 1));
                label.add(old[i]);
            }
        }
        int map = player.zone.map.mapId;
        if (map == MAP_THANH_CO_1 || map == MAP_THANH_CO_2) {
            act.add(0, "NEXT");
            label.add(0, "Đi tiếp");
            act.add("HOME");
            label.add("Về làng");
        } else if (map == MAP_DAU_TRUONG) {
            act.add(0, "FIGHT");
            label.add(0, "Lên\nsàn đấu");
            act.add("HOME");
            label.add("Về làng");
        } else {
            act.add(0, "THANHCO");
            label.add(0, "Đến\ncổ thành");
        }
        actions.put(player.id, act);
        String say = "Ngươi tìm ta có việc gì ?";
        if (player.DuaHauEgg != null) {
            say += player.DuaHauEgg.getSecondDone() == 0 ? "\nDưa của ngươi đã chín, về nhà thu hoạch rồi mang đến đây!" : "\nDưa đang lớn, chín lúc phút 00 hoặc 30.";
        } else if (!isPlantTime()) {
            say += "\nTrồng dưa vào phút 25 và 55 mỗi giờ.";
        }
        createOtherMenu(player, 0, say, label.toArray(new String[0]));
    }

    @Override
    public void confirmMenu(Player pl, int select) {
        if (canOpenNpc(pl)) {
            switch (pl.idMark.getIndexMenu()) {
                case 0 -> {
                    List<String> act = actions.get(pl.id);
                    if (act == null || select < 0 || select >= act.size()) {
                        return;
                    }
                    String a = act.get(select);
                    if (a.startsWith("EX")) {
                        nro.models.services.SeasonEventService.gI().openNpcExchange(this, pl, Integer.parseInt(a.substring(2)));
                    } else if (a.startsWith("OLD")) {
                        legacy(pl, Integer.parseInt(a.substring(3)));
                    } else {
                        switch (a) {
                            case "PLANT" -> plant(pl);
                            case "DOI" -> legacy(pl, 0);
                            case "THANHCO" -> ThanhCo.enter(pl);
                            case "NEXT" -> ThanhCo.next(pl);
                            case "FIGHT" -> ThanhCo.fight(pl);
                            case "HOME" -> ThanhCo.home(pl);
                        }
                    }
                }
                case 10 -> {
                    if (select >= 0 && select <= 4) {
                        DoiDuaHau(pl, select);
                    }
                }
                case 1 -> {
                    if (select == 0) {
                        NhanQuaThuong(pl);
                    }
                }
                case 2 -> {
                    if (select == 0) {
                        NhanQuaVip(pl);
                    }
                }
                case 3 -> {
                    if (select == 0) {
                        BanhDay(pl);
                    }
                }
                case 4 -> {
                    if (select == 0) {
                        BanhChung(pl);
                    }
                }
            }
        }
    }

    private void legacy(Player pl, int select) {
        switch (select) {
            case 0 ->
                createOtherMenu(pl, 10, "Mang Dưa Hấu và Tem chứng nhận Mai An Tiêm đến đây đổi hồng ngọc",
                        "5 hồng ngọc\n1 Quả\n1 tem",
                        "40 hồng ngọc\n10 Quả\n2 tem",
                        "120 hồng ngọc\n20 Quả\n3 tem",
                        "185 hồng ngọc\n25 Quả\n4 tem",
                        "250 hồng ngọc\n30 Quả\n5 tem");
                        case 1 -> {
                            int q1 = getItemQuantity(pl, NGA_VOI);
                            int q2 = getItemQuantity(pl, CUA_GA);
                            int q3 = getItemQuantity(pl, HONG_MAO);
                            boolean enoughItem = q1 >= 9 && q2 >= 9 && q3 >= 9;
                            boolean enoughGold = pl.inventory.gold >= 1_000_000;

                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Ngươi muốn dâng sính lễ?\n")
                                    .append(formatRequirement("Ngà Voi", q1, 9))
                                    .append(formatRequirement("Của Gà", q2, 9))
                                    .append(formatRequirement("Hồng Mao", q3, 9))
                                    .append(ConstFont.BOLD_RED).append("Giá vàng: 1.000.000\n");

                            if (enoughItem && enoughGold) {
                                createOtherMenu(pl, 1, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                        case 2 -> {
                            int q1 = getItemQuantity(pl, NGA_VOI);
                            int q2 = getItemQuantity(pl, CUA_GA);
                            int q3 = getItemQuantity(pl, HONG_MAO);
                            boolean enoughItem = q1 >= 9 && q2 >= 9 && q3 >= 9;
                            boolean enoughGem = pl.inventory.gem >= 10;

                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Ngươi muốn dâng sính lễ xịn?\n")
                                    .append(formatRequirement("Ngà Voi", q1, 9))
                                    .append(formatRequirement("Của Gà", q2, 9))
                                    .append(formatRequirement("Hồng Mao", q3, 9))
                                    .append(ConstFont.BOLD_RED).append("Giá ngọc: 10\n");

                            if (enoughItem && enoughGem) {
                                createOtherMenu(pl, 2, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                        case 3 -> {
                            int quantity = getItemQuantity(pl, BANH_DAY);
                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Ngươi muốn dâng Bánh Dầy?\n")
                                    .append(formatRequirement("Bánh Dầy", quantity, 1));
                            if (quantity >= 1) {
                                createOtherMenu(pl, 3, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
                        case 4 -> {
                            int quantity = getItemQuantity(pl, BANH_CHUNG);
                            StringBuilder sb = new StringBuilder()
                                    .append(ConstFont.BOLD_GREEN).append("Ngươi muốn dâng Bánh chưng?\n")
                                    .append(formatRequirement("Bánh chưng lang liêu", quantity, 1));
                            if (quantity >= 1) {
                                createOtherMenu(pl, 4, sb.toString(), "Đồng ý", "Từ chối");
                            } else {
                                createOtherMenu(pl, 123, sb.toString(), "Từ chối");
                            }
                        }
        }
    }

    private void plant(Player pl) {
        if (!isPlantTime() || pl.DuaHauEgg != null || Long.valueOf(plantWindow()).equals(daTrong.get(pl.id))) {
            Service.gI().sendThongBao(pl, "Chưa đến giờ trồng dưa hoặc ngươi đã trồng rồi");
            return;
        }
        DuaHauEgg.createDuaHauEgg(pl, msToHarvest());
        daTrong.put(pl.id, plantWindow());
        Service.gI().sendThongBao(pl, "Đã trồng 1 cây dưa hấu ở nhà, dưa chín lúc phút 00 hoặc 30");
    }

    private void NhanQuaThuong(Player player) {
        Item ngaVoi = InventoryService.gI().findItemBag(player, NGA_VOI);
        Item cuaGa = InventoryService.gI().findItemBag(player, CUA_GA);
        Item hongMao = InventoryService.gI().findItemBag(player, HONG_MAO);

        if (ngaVoi != null && cuaGa != null && hongMao != null
                && ngaVoi.quantity >= 9 && cuaGa.quantity >= 9 && hongMao.quantity >= 9
                && player.inventory.gold >= 1_000_000) {

            InventoryService.gI().subQuantityItemsBag(player, ngaVoi, 9);
            InventoryService.gI().subQuantityItemsBag(player, cuaGa, 9);
            InventoryService.gI().subQuantityItemsBag(player, hongMao, 9);
            player.inventory.gold -= 1_000_000;
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);

            Item hopQua = ItemService.gI().createNewItem((short) HOP_QUA_THUONG);
            InventoryService.gI().addItemBag(player, hopQua);
            InventoryService.gI().sendItemBags(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được 1 Hộp Quà Thường!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu hoặc vàng!");
        }
    }

    private void NhanQuaVip(Player player) {
        Item ngaVoi = InventoryService.gI().findItemBag(player, NGA_VOI);
        Item cuaGa = InventoryService.gI().findItemBag(player, CUA_GA);
        Item hongMao = InventoryService.gI().findItemBag(player, HONG_MAO);

        if (ngaVoi != null && cuaGa != null && hongMao != null
                && ngaVoi.quantity >= 9 && cuaGa.quantity >= 9 && hongMao.quantity >= 9
                && player.inventory.gem >= 10) {

            InventoryService.gI().subQuantityItemsBag(player, ngaVoi, 9);
            InventoryService.gI().subQuantityItemsBag(player, cuaGa, 9);
            InventoryService.gI().subQuantityItemsBag(player, hongMao, 9);
            player.inventory.gem -= 10;
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);

            Item hopQua = ItemService.gI().createNewItem((short) HOP_QUA_VIP);
            InventoryService.gI().addItemBag(player, hopQua);
            InventoryService.gI().sendItemBags(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được 1 Hộp Quà VIP!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu hoặc ngọc!");
        }
    }

    private void BanhDay(Player player) {
        Item banhDay = InventoryService.gI().findItemBag(player, BANH_DAY);
        if (banhDay != null && banhDay.quantity >= 1) {
            InventoryService.gI().subQuantityItemsBag(player, banhDay, 1);
            InventoryService.gI().sendItemBags(player);

            int randomItemId = randomBanhDay();
            Item randomItem = ItemService.gI().createNewItem((short) randomItemId);

            InventoryService.gI().addItemBag(player, randomItem);
            InventoryService.gI().sendItemBags(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được một món quà ngẫu nhiên từ Bánh Dầy!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ Bánh Dầy!");
        }
    }

    private void BanhChung(Player player) {
        Item banhChung = InventoryService.gI().findItemBag(player, BANH_CHUNG);
        if (banhChung != null && banhChung.quantity >= 1) {
            InventoryService.gI().subQuantityItemsBag(player, banhChung, 1);
            InventoryService.gI().sendItemBags(player);

            int randomItemId = randomBanhChung();
            Item randomItem = ItemService.gI().createNewItem((short) randomItemId);

            InventoryService.gI().addItemBag(player, randomItem);
            InventoryService.gI().sendItemBags(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được một món quà ngẫu nhiên từ Bánh Chưng!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ Bánh Chưng!");
        }
    }

    private int randomBanhDay() {
        int[] possibleItems = {381, 382, 383, 384, 385, 1635};
        return possibleItems[random.nextInt(possibleItems.length)];
    }

    private int randomBanhChung() {
        int[] possibleItems = {1150, 1151, 1152, 1153, 1635, 1154, 1423, 1438, 1634};
        return possibleItems[random.nextInt(possibleItems.length)];
    }

    private void DoiDuaHau(Player player, int select) {
        Item DuaHau = InventoryService.gI().findItemBag(player, DUA_HAU);
        Item tem = InventoryService.gI().findItemBag(player, TEM);

        int[] gems = {5, 40, 120, 185, 250};
        int[] duahau = {1, 10, 20, 25, 30};
        int[] temNeeded = {1, 2, 3, 4, 5};

        if (DuaHau != null && tem != null
                && DuaHau.quantity >= duahau[select]
                && tem.quantity >= temNeeded[select]) {

            InventoryService.gI().subQuantityItemsBag(player, DuaHau, duahau[select]);
            InventoryService.gI().subQuantityItemsBag(player, tem, temNeeded[select]);
            InventoryService.gI().sendItemBags(player);

            player.inventory.ruby += gems[select];
            Service.gI().sendMoney(player);

            Service.gI().sendThongBao(player, "Bạn đã nhận được " + gems[select] + " hồng ngọc!");
        } else {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu để đổi Ngọc!");
        }
    }

    private static int getItemQuantity(Player player, int itemId) {
        Item item = InventoryService.gI().findItemBag(player, itemId);
        return item != null ? item.quantity : 0;
    }

    private static String formatRequirement(String itemName, int currentQuantity, int requiredQuantity) {
        String color = currentQuantity >= requiredQuantity ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED;
        return String.format("%s%s %d/%d\n", color, itemName, currentQuantity, requiredQuantity);
    }
}
