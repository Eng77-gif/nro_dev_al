package nro.models.services;

import java.util.ArrayList;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.utils.Util;

/**
 * Rương sưu tập (NPC Rương Sưu Tầm ở Nhà Bunma): rương riêng chỉ cất cải trang, đeo lưng, pet, thú cưỡi.
 * Dùng chung giao diện rương đồ của client: khi đang mở rương sưu tập (player.collectionOpen), các thao tác
 * cất / lấy đồ của rương được áp lên player.itemsCollection thay cho rương đồ.
 */
public class CollectionService {

    public static final int DEFAULT_SLOTS = 20;
    public static final int MAX_SLOTS = 100;
    /** Giá mở ô thứ 21; mỗi ô sau đắt thêm 10% giá gốc. */
    public static final long BASE_PRICE = 1_500_000_000L;

    private static CollectionService instance;

    public static CollectionService gI() {
        if (instance == null) {
            instance = new CollectionService();
        }
        return instance;
    }

    public void ensure(Player player) {
        if (player.itemsCollection == null) {
            player.itemsCollection = new ArrayList<>();
        }
        while (player.itemsCollection.size() < DEFAULT_SLOTS) {
            player.itemsCollection.add(ItemService.gI().createItemNull());
        }
    }

    /** Cải trang, đeo lưng, thú cưỡi, pet (pet là loại 27 có hình đi theo). */
    public boolean canStore(Item item) {
        if (item == null || !item.isNotNullItem()) {
            return false;
        }
        int type = item.template.type;
        return type == 5 || type == 11 || type == 23 || type == 24 || (type == 27 && item.template.part >= 0);
    }

    public void open(Player player) {
        ensure(player);
        player.collectionOpen = true;
        InventoryService.gI().sendItemBags(player);
        InventoryService.gI().sendItemBox(player);
    }

    /** Giá mở ô kế tiếp (ô số size + 1). */
    public long nextSlotPrice(Player player) {
        ensure(player);
        int next = player.itemsCollection.size() + 1;
        return BASE_PRICE + BASE_PRICE * Math.max(0, next - (DEFAULT_SLOTS + 1)) / 10;
    }

    public boolean canUpgrade(Player player) {
        ensure(player);
        return player.itemsCollection.size() < MAX_SLOTS;
    }

    public void upgrade(Player player) {
        ensure(player);
        if (!canUpgrade(player)) {
            Service.gI().sendThongBao(player, "Rương sưu tập đã đạt tối đa " + MAX_SLOTS + " ô");
            return;
        }
        long price = nextSlotPrice(player);
        if (player.inventory.gold < price) {
            Service.gI().sendThongBao(player, "Bạn không đủ vàng, còn thiếu " + Util.numberToMoney(price - player.inventory.gold) + " vàng");
            return;
        }
        player.inventory.gold -= price;
        player.itemsCollection.add(ItemService.gI().createItemNull());
        Service.gI().sendMoney(player);
        Service.gI().sendThongBao(player, "Rương sưu tập đã mở thêm ô " + player.itemsCollection.size());
    }

    private void refuse(Player player) {
        Service.gI().sendThongBao(player, "Rương sưu tập chỉ cất được cải trang, đeo lưng, pet và thú cưỡi");
    }

    public void bagToCollection(Player player, int index) {
        ensure(player);
        if (index < 0 || index >= player.inventory.itemsBag.size()) {
            return;
        }
        Item item = player.inventory.itemsBag.get(index);
        if (!canStore(item)) {
            refuse(player);
            return;
        }
        if (InventoryService.gI().addItemList(player.itemsCollection, item)) {
            if (item.quantity == 0) {
                player.inventory.itemsBag.set(index, ItemService.gI().createItemNull());
            }
            InventoryService.gI().sortItems(player.inventory.itemsBag);
            InventoryService.gI().sendItemBags(player);
            InventoryService.gI().sendItemBox(player);
        } else {
            Service.gI().sendThongBao(player, "Rương sưu tập đã đầy, hãy nâng cấp rương");
        }
    }

    public void bodyToCollection(Player player, int index) {
        ensure(player);
        if (index < 0 || index >= player.inventory.itemsBody.size()) {
            return;
        }
        Item item = player.inventory.itemsBody.get(index);
        if (!item.isNotNullItem()) {
            return;
        }
        if (!canStore(item)) {
            refuse(player);
            return;
        }
        if (InventoryService.gI().addItemList(player.itemsCollection, item)) {
            player.inventory.itemsBody.set(index, ItemService.gI().createItemNull());
            InventoryService.gI().sendItemBody(player);
            InventoryService.gI().sendItemBox(player);
            Service.gI().point(player);
            Service.gI().Send_Caitrang(player);
        } else {
            Service.gI().sendThongBao(player, "Rương sưu tập đã đầy, hãy nâng cấp rương");
        }
    }

    public void collectionToBag(Player player, int index) {
        ensure(player);
        if (index < 0 || index >= player.itemsCollection.size()) {
            return;
        }
        Item item = player.itemsCollection.get(index);
        if (!item.isNotNullItem()) {
            return;
        }
        if (InventoryService.gI().addItemBag(player, item)) {
            if (item.quantity == 0) {
                player.itemsCollection.set(index, ItemService.gI().createItemNull());
            }
            InventoryService.gI().sendItemBags(player);
            InventoryService.gI().sendItemBox(player);
        } else {
            Service.gI().sendThongBao(player, "Hành trang không còn chỗ trống");
        }
    }
}
