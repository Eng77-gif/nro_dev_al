package nro.models.combine;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */

public class PhanRaTrangBiKichHoat {

    public class PhanRaTrangBi {

        private static final int GOLD_PHAN_RA = 2_000_000_000;  // Lượng vàng cần thiết
        private static final int RATIO_PHAN_RA = 100;  // Tỉ lệ thành công
        private static final int[][] optionIds = {
            {128, 129, 127, 233, 245, 130, 131, 132, 233, 237, 133, 135, 134, 233, 241}};

        /** Chọn một hay nhiều trang bị kích hoạt, mỗi món rã ra 1 Khoáng tái chế. */
        public static void showInfoCombine(Player player) {
            int n = player.combineNew.itemsCombine.size();
            if (n == 0) {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Cần đặt đúng vật phẩm!", "Đóng");
                return;
            }
            for (Item it : player.combineNew.itemsCombine) {
                if (!isValidItem(it)) {
                    CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU,
                            it.template.name + " không phải trang bị kích hoạt, không thể phân rã!", "Đóng");
                    return;
                }
            }
            long gold = (long) GOLD_PHAN_RA * n;
            player.combineNew.ratioCombine = RATIO_PHAN_RA;

            String npcSay = "|2|Phân rã " + n + " trang bị kích hoạt\n"
                    + "|2|Nhận " + n + " Khoáng tái chế\n"
                    + "|2|Tỉ lệ thành công: " + RATIO_PHAN_RA + "%\n"
                    + "|2|Cần: " + Util.numberToMoney(gold) + " vàng\n";

            if (player.inventory.gold < gold) {
                npcSay += "|7|Còn thiếu " + Util.powerToString(gold - player.inventory.gold) + " vàng\n";
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, npcSay, "Đóng");
            } else {
                CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay,
                        "Phân rã\n" + Util.numberToMoney(gold) + " vàng", "Từ chối");
            }
        }

        public static void ThucHienPhanRa(Player player) {
            java.util.List<Item> items = new java.util.ArrayList<>(player.combineNew.itemsCombine);
            if (items.isEmpty()) {
                Service.gI().sendThongBao(player, "Cần đặt trang bị kích hoạt!");
                return;
            }
            for (Item it : items) {
                if (!isValidItem(it) || it.quantity < 1) {
                    Service.gI().sendThongBao(player, "Vật phẩm không đủ điều kiện để phân rã!");
                    return;
                }
            }
            long gold = (long) GOLD_PHAN_RA * items.size();
            if (player.inventory.gold < gold) {
                Service.gI().sendThongBao(player, "Không đủ vàng để thực hiện!");
                return;
            }
            player.inventory.gold -= gold;
            int got = 0;
            for (Item it : items) {
                InventoryService.gI().subQuantityItemsBag(player, it, 1);
                if (Util.isTrue(RATIO_PHAN_RA, 100)) {
                    got++;
                }
            }
            if (got > 0) {
                InventoryService.gI().addItemBag(player, ItemService.gI().createNewItem((short) 1656, got));
                CombineService.gI().sendEffectSuccessCombine(player);
                Service.gI().sendThongBao(player, "Phân rã thành công, nhận " + got + " Khoáng tái chế!");
            } else {
                CombineService.gI().sendEffectFailCombine(player);
                Service.gI().sendThongBao(player, "Phân rã thất bại!");
            }
            player.combineNew.itemsCombine.clear();
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);
            CombineService.gI().reOpenItemCombine(player);
        }

        private static boolean isValidItem(Item item) {
            if (item == null || item.template == null || item.itemOptions == null || item.template.type > 4) {
                return false;
            }
            for (ItemOption option : item.itemOptions) {
                for (int[] optionsForRace : optionIds) {
                    for (int validOptionId : optionsForRace) {
                        if (option.optionTemplate.id == validOptionId) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

    }
}
