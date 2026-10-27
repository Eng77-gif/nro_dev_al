package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import java.util.ArrayList;
import java.util.List;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.shop.ShopService;
import nro.models.services_func.Input;
import nro.models.services.InventoryService;
import nro.models.services.SeasonEventService;

public class Santa extends Npc {

    public Santa(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    private static final String EVENT = "event", GIAM_GIA = "SANTA_GIAM_GIA_1", GIFT_CODE = "giftcode";

    /** Danh sách chức năng theo đúng thứ tự nút trong menu. */
    private List<String[]> menu(Player player) {
        List<String[]> menu = new ArrayList<>();
        if (SeasonEventService.gI().hasActive()) {
            menu.add(new String[]{SeasonEventService.gI().menuLabel(), EVENT});
        }
        menu.add(new String[]{"Cửa hàng", "SANTA"});
        Item pGG = InventoryService.gI().findItem(player.inventory.itemsBag, 459);
        if (pGG != null && pGG.quantity >= 1) {
            menu.add(new String[]{"Giảm giá\n80%", GIAM_GIA});
        }
        menu.add(new String[]{"Mở rộng\nHành trang\nRương đồ", "SANTA_MO_RONG_HANH_TRANG"});
        menu.add(new String[]{"Nhập mã\nquà tặng", GIFT_CODE});
        menu.add(new String[]{"Cửa hàng\nHạn sử dụng", "SANTA_HAN_SU_DUNG"});
        menu.add(new String[]{"Tiệm\nHớt tóc", "SANTA_HEAD"});
        menu.add(new String[]{"Danh\nhiệu", "SANTA_DANH_HIEU"});
        return menu;
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            List<String[]> menu = menu(player);
            String[] labels = new String[menu.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = menu.get(i)[0];
            }
            createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Xin chào, ta có một số vật phẩm đặc biệt cậu có muốn xem không?", labels);
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (SeasonEventService.gI().confirmMenu(this, player, player.idMark.getIndexMenu(), select)) {
            return;
        }
        if (player.idMark.isBaseMenu()) {
            List<String[]> menu = menu(player);
            if (select < 0 || select >= menu.size()) {
                return;
            }
            switch (menu.get(select)[1]) {
                case EVENT ->
                    SeasonEventService.gI().openMenu(this, player);
                case GIFT_CODE ->
                    Input.gI().createFormGiftCode(player);
                default ->
                    ShopService.gI().opendShop(player, menu.get(select)[1], false);
            }
        }
    }
}
