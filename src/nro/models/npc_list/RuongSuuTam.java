package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.CollectionService;
import nro.models.utils.Util;

/**
 * Rương sưu tập (Nhà Bunma): cất riêng cải trang, đeo lưng, pet, thú cưỡi; nâng cấp thêm ô bằng vàng.
 */
public class RuongSuuTam extends Npc {

    private static final int CONFIRM_UPGRADE = 1101;

    public RuongSuuTam(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            createOtherMenu(player, ConstNpc.BASE_MENU, "Vàng bạc châu báu gì cứ yên tâm giao hết cho tôi",
                    "Mở rương", "Nâng cấp\nrương", "Từ chối");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (player.idMark.isBaseMenu()) {
            switch (select) {
                case 0 ->
                    CollectionService.gI().open(player);
                case 1 -> {
                    if (!CollectionService.gI().canUpgrade(player)) {
                        createOtherMenu(player, ConstNpc.IGNORE_MENU, "Rương sưu tập đã đạt tối đa " + CollectionService.MAX_SLOTS + " ô", "Đóng");
                        return;
                    }
                    int next = player.itemsCollection.size() + 1;
                    createOtherMenu(player, CONFIRM_UPGRADE, "Bạn có chắc muốn mở thêm ô " + next + " với giá "
                            + Util.numberToMoney(CollectionService.gI().nextSlotPrice(player)) + " vàng ?", "Đồng ý", "Từ chối");
                }
            }
        } else if (player.idMark.getIndexMenu() == CONFIRM_UPGRADE && select == 0) {
            CollectionService.gI().upgrade(player);
        }
    }
}
