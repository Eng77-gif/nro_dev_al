package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.server.Manager;
import nro.models.services.Service;
import nro.models.services_func.Input;
import nro.models.shop.ShopService;
import nro.models.services.Event2026Service;

/**
 *
 * @author By Mr Blue
 *
 */
public class ChiChi extends Npc {

    private static final int MENU_HOP_DEN = 2095;

    public ChiChi(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            List<String> menu = new ArrayList<>(Arrays.asList(
                    "Top\nHộp quà\nthiếu nhi\n2025",
                    "Top\nNước mía",
                    "Top\nKem trái cây",
                    "Cửa hàng",
                    "Hộp đen\nbí ẩn",
                    "Đóng"));

            String[] menus = menu.toArray(new String[0]);

            createOtherMenu(player, ConstNpc.BASE_MENU,
                    "Bạn muốn hỏi chi?", menus);
        }

    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            int soLuong = 0;
            if (this.mapId == 5) {
                if (player.idMark.isBaseMenu()) {
                    switch (select) {
                        case 3:
                            ShopService.gI().opendShop(player, "SHOP_CHI_CHI", false);
                            break;
                        case 4:
                            createOtherMenu(player, MENU_HOP_DEN,
                                    "Hộp đen bí ẩn giá " + Event2026Service.HOP_DEN_GIA_NGOC + " ngọc\n"
                                    + "Mở ra có cơ hội nhận Bóng WC 2026, Cải trang Hit, Mảnh thẻ VIP, Năng lượng tà ác, Pet rắn cute, "
                                    + "Phượng hoàng băng, Pet Pikachu, Cải trang Goku cận vô cực (có hạn sử dụng hoặc vĩnh viễn)\n"
                                    + "Đua top mua hộp: top 1 nhận Danh hiệu Đón Xuân 180 ngày, Đá bảo vệ x100, Capsule 1 món kích hoạt x3, "
                                    + "Capsule vỡ x3, Bông tai cấp 3, Giáp tập luyện cấp 5\n"
                                    + "Bạn đã mua: " + player.hdbaCount + " hộp",
                                    "Mua 1 hộp\n" + Event2026Service.HOP_DEN_GIA_NGOC + " ngọc",
                                    "Mua 10 hộp\n" + (Event2026Service.HOP_DEN_GIA_NGOC * 10) + " ngọc",
                                    "Top\nmua hộp", "Đóng");
                            break;
                        case 0:
                            createOtherMenu(player, ConstNpc.PHAO_BONG_VIP,
                                    "Sự kiện đua top Hộp quà thiếu nhi nhận quà khủng\n Kết thúc và trao giải sau (....)\nHạn chót nhận giải: (15 ngày nữa)\nĐến gặp ChiChi để nhận giải nhé\nChi tiết xem tại diễn đàn, Fanpage",
                                    "Top 100\nHộp quà\nthiếu nhi\n2025",
                                    "Xem điểm",
                                    "Đóng");
                            break;
                        case 1:
                            createOtherMenu(player, ConstNpc.PHAO_BONG,
                                    "Sự kiện đua top Nước mía nhận quà khủng\n Kết thúc và trao giải sau (....)\nHạn chót nhận giải: (15 ngày nữa)\nĐến gặp ChiChi để nhận giải nhé\nChi tiết xem tại diễn đàn, Fanpage",
                                    "Top 100\nNước mía",
                                    "Xem điểm",
                                    "Đóng");
                            break;
                        case 2:
                            createOtherMenu(player, ConstNpc.GOKU_DAY,
                                    "Sự kiện đua top Kem trái cây nhận quà khủng\n Kết thúc và trao giải sau (....)\nHạn chót nhận giải: (15 ngày nữa)\nĐến gặp ChiChi để nhận giải nhé\nChi tiết xem tại diễn đàn, Fanpage",
                                    "Top 100\nKem trái cây",
                                    "Xem điểm",
                                    "Đóng");
                            break;
                    }
                } else if (player.idMark.getIndexMenu() == MENU_HOP_DEN) {
                    switch (select) {
                        case 0 -> Event2026Service.gI().buyHopDen(player, 1);
                        case 1 -> Event2026Service.gI().buyHopDen(player, 10);
                        case 2 -> nro.models.map.service.NpcService.gI().createTutorial(player, tempId, this.avartar, Event2026Service.gI().topHopDenText());
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.PHAO_BONG_VIP) {
                    switch (select) {
                        case 0:
                            Service.gI().showListTop(player, Manager.Topsukien);
                            break;
                        case 1:
                            Service.gI().sendThongBao(player, "Bạn có " + player.point_sukien + " điểm Hộp quà thiếu nhi.");
                            break;
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.PHAO_BONG) {
                    switch (select) {
                        case 0:
                            Service.gI().showListTop(player, Manager.Topsukien1);
                            break;
                        case 1:
                            Service.gI().sendThongBao(player, "Bạn có " + player.point_sukien1 + " điểm Nước mía.");
                            break;
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.GOKU_DAY) {
                    switch (select) {
                        case 0:
                            Service.gI().showListTop(player, Manager.Topsukien2);
                            break;
                        case 1:
                            Service.gI().sendThongBao(player, "Bạn có " + player.point_sukien2 + " điểm Kem trái cây.");
                            break;
                    }
                }
            }
        }
    }
}
