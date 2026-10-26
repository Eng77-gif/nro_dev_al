package models;

import nro.models.item.Item.ItemOption;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import nro.models.player.Player;

public class GiftCode {

    public String code;
    public int countLeft;
    public int id;
    public HashMap<Integer, Integer> detail = new HashMap<>();
    public HashMap<Integer, ArrayList<ItemOption>> option = new HashMap<>();
    public Timestamp datecreate;
    public Timestamp dateexpired;

    public boolean isUsedGiftCode(Player player) {
        return player.giftCode.isUsedGiftCode(code);
    }

    public boolean timeCode() {
        // hết hạn khi thời điểm hiện tại đã vượt quá ngày hết hạn (trước đây so sánh ngày tạo với ngày hết hạn nên code không bao giờ hết hạn)
        return this.dateexpired != null && System.currentTimeMillis() > this.dateexpired.getTime();
    }
}
