package nro.models.services;

import nro.models.item.Item;
import nro.models.map.service.NpcService;
import nro.models.player.Player;

/**
 * Đổi vật phẩm sưu tầm: 999 Mảnh Cápsule Vỡ -> 1 Cápsule Vỡ, 99 Phiếu đổi Cápsule -> 1 Cápsule kích hoạt 1 món tự chọn.
 * Cápsule Vỡ + Khoáng tái chế (phân rã đồ kích hoạt) tái tạo thành Cápsule kích hoạt ở Bà Hạt Mít.
 */
public class CapsuleVoService {

    public static final int MANH_CAPSULE_VO = 1909;
    public static final int PHIEU_DOI_CAPSULE = 1690;
    private static final int CAPSULE_VO = 1634;
    private static final int CAPSULE_KICH_HOAT = 1655;
    private static final int CAN_MANH = 999;
    private static final int CAN_PHIEU = 99;

    private static CapsuleVoService instance;

    public static CapsuleVoService gI() {
        if (instance == null) {
            instance = new CapsuleVoService();
        }
        return instance;
    }

    public boolean isExchangeItem(Item item) {
        return item != null && item.template != null
                && (item.template.id == MANH_CAPSULE_VO || item.template.id == PHIEU_DOI_CAPSULE);
    }

    private int need(int id) {
        return id == MANH_CAPSULE_VO ? CAN_MANH : CAN_PHIEU;
    }

    private int reward(int id) {
        return id == MANH_CAPSULE_VO ? CAPSULE_VO : CAPSULE_KICH_HOAT;
    }

    public void use(Player pl, Item item) {
        int id = item.template.id;
        int have = InventoryService.gI().findItemBag(pl, id) == null ? 0 : InventoryService.gI().findItemBag(pl, id).quantity;
        String rewardName = ItemService.gI().getTemplate(reward(id)).name;
        if (have < need(id)) {
            Service.gI().sendThongBao(pl, "Cần " + need(id) + " " + item.template.name + " để đổi " + rewardName + " (đang có " + have + ")");
            return;
        }
        NpcService.gI().createMenuConMeo(pl, id, -1, "Ngươi có muốn đổi " + need(id) + " " + item.template.name
                + " lấy 1 " + rewardName + " không?", "Đồng ý", "Từ chối");
    }

    public void confirm(Player pl, int id, int select) {
        if (select != 0) {
            return;
        }
        Item item = InventoryService.gI().findItemBag(pl, id);
        if (item == null || item.quantity < need(id)) {
            Service.gI().sendThongBao(pl, "Không đủ vật phẩm để đổi");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0 && InventoryService.gI().findItemBag(pl, reward(id)) == null) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        InventoryService.gI().subQuantityItemsBag(pl, item, need(id));
        Item it = ItemService.gI().createNewItem((short) reward(id));
        InventoryService.gI().addItemBag(pl, it);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Bạn nhận được " + it.template.name);
    }
}
