package nro.models.services;

import java.util.List;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.matches.TOP;
import nro.models.player.Player;
import nro.models.server.Manager;
import nro.models.server.ServerNotify;

/**
 * Thông báo toàn server:
 * - Top 1 các sự kiện ở Chi Chi vừa đăng nhập (chat thế giới).
 * - Đánh quái nhặt được trang bị set kích hoạt riêng (Thần Vũ Trụ Kaio, Nail chiến binh Namếc, Cađic M).
 * - Mở hộp quà nhận được cải trang, pet, đeo lưng, thú cưỡi.
 */
public class AnnounceService {

    /** Set kích hoạt riêng rơi từ quái: Nail chiến binh Namếc, Cađic M, Thần Vũ Trụ Kaio */
    private static final int[] SKH_RIENG = {237, 241, 245};

    private static AnnounceService instance;

    public static AnnounceService gI() {
        if (instance == null) {
            instance = new AnnounceService();
        }
        return instance;
    }

    // ------------------------------------------------------------------ top 1 đăng nhập

    public void onLogin(Player pl) {
        if (pl == null || pl.isBot || pl.loginAnnounced) {
            return;
        }
        pl.loginAnnounced = true;
        StringBuilder tops = new StringBuilder();
        addTop(tops, pl, Manager.Topsukien, "Hộp quà thiếu nhi");
        addTop(tops, pl, Manager.Topsukien1, "Nước mía");
        addTop(tops, pl, Manager.Topsukien2, "Kem trái cây");
        try {
            List<Object[]> hd = nro.models.database.PlayerExtraDAO.top("hdba_count", 1);
            if (!hd.isEmpty() && ((Number) hd.get(0)[0]).longValue() == pl.id && ((Number) hd.get(0)[2]).longValue() > 0) {
                append(tops, "Hộp đen bí ẩn");
            }
        } catch (Exception ignored) {
        }
        if (tops.length() > 0) {
            ChatGlobalService.gI().chatVip(pl, "Top 1 sự kiện " + tops + " vừa đăng nhập!");
        }
    }

    private void addTop(StringBuilder sb, Player pl, List<TOP> list, String name) {
        if (list == null || list.isEmpty()) {
            return;
        }
        TOP t = list.get(0);
        String info = String.valueOf(t.getInfo1());
        if (t.getId_player() == pl.id && !info.startsWith("0 ")) {
            append(sb, name);
        }
    }

    private void append(StringBuilder sb, String name) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(name);
    }

    // ------------------------------------------------------------------ nhặt set kích hoạt riêng từ quái

    public void onPickItem(Player pl, ItemMap itemMap, Item item) {
        if (pl == null || itemMap == null || !itemMap.fromMob || item == null || item.template == null || item.template.type > 4) {
            return;
        }
        for (Item.ItemOption io : item.itemOptions) {
            for (int id : SKH_RIENG) {
                if (io.optionTemplate.id == id) {
                    ServerNotify.gI().notify(pl.name + " vừa đánh quái may mắn nhận được 1 trang bị Set kích hoạt "
                            + io.optionTemplate.name);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ mở hộp

    /** Cải trang, đeo lưng có hình, thú cưỡi, pet có hình. */
    public static boolean isRare(Item it) {
        if (it == null || it.template == null) {
            return false;
        }
        int type = it.template.type;
        return type == 5 || type == 23 || type == 24 || ((type == 11 || type == 27) && it.template.part >= 0);
    }

    public void onOpenBox(Player pl, int boxId, Item it) {
        if (pl == null || !isRare(it)) {
            return;
        }
        int days = 0;
        for (Item.ItemOption io : it.itemOptions) {
            if (io.optionTemplate.id == 93) {
                days = io.param;
            }
        }
        String box = ItemService.gI().getTemplate(boxId) != null ? ItemService.gI().getTemplate(boxId).name : "hộp quà";
        ServerNotify.gI().notify("Chúc mừng " + pl.name + " đã may mắn mở " + box + " nhận được " + it.template.name
                + (days > 0 ? " hạn dùng " + days + " ngày" : " hạn dùng vĩnh viễn"));
    }
}
