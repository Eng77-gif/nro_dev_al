package nro.models.services.shenron;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.consts.ConstPlayer;
import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.map.service.NpcService;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.Event2026Service;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.ItemTimeService;
import nro.models.services.SeasonEventService;
import nro.models.services.Service;
import nro.models.skill.Skill;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

/**
 * Rồng Xương (sự kiện Halloween): đủ 7 viên Bí ngô 1-7 sao thì gọi được rồng, ban 1 trong 9 điều ước
 * (theo video hướng dẫn sự kiện Halloween chính thức).
 */
public class RongXuong {

    public static final int BI_NGO_1_SAO = 702, BI_NGO_7_SAO = 708;
    public static final int PET_CERBERUS = 1654, QUA_TRUNG_MABU = 568, RUONG_SPL_VIP = 1453;
    public static final int MENU_SUMMON = 31720300, MENU_P1 = 31720301, MENU_P2 = 31720302, MENU_CONFIRM = 31720303;
    private static final int HALLOWEEN = 7;
    private static final long WAIT_MS = 5 * 60_000L, RESUMMON_MS = 60_000L, BUFF_MS = 30 * 60_000L;
    /** loại rồng gửi cho client (-83): 0 Rồng thần, 1 Rồng Namek/sự kiện; client khác có thể dùng số khác cho Rồng Xương */
    public static volatile int dragonType = 1;

    private static final String SAY_1 = "Ta sẽ ban cho ngươi một điều ước, ngươi có 5 phút, hãy chọn đi:\n"
            + "1) Đổi cả 3 kỹ năng đầu của đệ tử\n(Lưu ý: Kỹ năng mới có cấp 1 và vẫn có thể trùng lại với kỹ năng vốn có).\n"
            + "2) X3 tiềm năng sức mạnh đệ tử 30 phút.\n"
            + "3) Tăng thêm 15% HP cho sư phụ khi sử dụng bông tai Porata 30 phút.\n"
            + "4) Tăng thêm 15% KI cho sư phụ khi sử dụng bông tai Porata 30 phút.\n"
            + "5) Tăng thêm 15% sức đánh cho sư phụ khi sử dụng bông tai Porata 30 phút.";
    private static final String SAY_2 = "Ta sẽ ban cho ngươi một điều ước, ngươi có 5 phút, hãy chọn đi:\n"
            + "6) Tiềm năng sức mạnh X3 cho bản thân 30 phút.\n"
            + "7) Giảm 100 triệu sức mạnh bản thân (yêu cầu trên 40 tỷ sức mạnh).\n"
            + "8) Pet chó Địa ngục hạn sử dụng từ 15 tới 90 ngày.\n"
            + "9) 2% cơ hội ra trứng Majin Bư (nếu hụt sẽ ra hộp sao pha lê VIP).";

    private static class State {

        Zone zone;
        int x, y;
        long appear;
        int wish = -1;
    }

    private static final Map<Long, State> dragons = new ConcurrentHashMap<>();
    private static final Map<Long, Long> lastSummon = new ConcurrentHashMap<>();

    public static boolean isBiNgo(int id) {
        return id >= BI_NGO_1_SAO && id <= BI_NGO_7_SAO;
    }

    /** Dùng một viên Bí ngô: hỏi gọi Rồng Xương. */
    public static void openSummon(Player pl) {
        NpcService.gI().createMenuConMeo(pl, MENU_SUMMON, -1, "Bạn có muốn gọi Rồng Xương không?", "Đồng ý", "Từ chối");
    }

    public static void summon(Player pl) {
        if (!SeasonEventService.gI().isActive(HALLOWEEN)) {
            Service.gI().sendThongBao(pl, "Chỉ gọi được Rồng Xương trong sự kiện Halloween");
            return;
        }
        int map = pl.zone.map.mapId;
        if (map == 0 || map == 7 || map == 14 || map == 21 || map == 22 || map == 23) {
            Service.gI().sendThongBao(pl, "Không thể gọi rồng ở đây");
            return;
        }
        if (dragons.containsKey(pl.id) || pl.isShenronAppear || pl.shenronEvent != null) {
            Service.gI().sendThongBao(pl, "Không thể thực hiện");
            return;
        }
        Long last = lastSummon.get(pl.id);
        if (last != null && System.currentTimeMillis() - last < RESUMMON_MS) {
            Service.gI().sendThongBao(pl, "Vui lòng đợi " + ((RESUMMON_MS - (System.currentTimeMillis() - last)) / 1000) + " giây nữa");
            return;
        }
        for (int i = BI_NGO_1_SAO; i <= BI_NGO_7_SAO; i++) {
            if (InventoryService.gI().findItemBag(pl, i) == null) {
                Service.gI().sendThongBao(pl, "Bạn còn thiếu 1 viên " + ItemService.gI().getTemplate(i).name);
                return;
            }
        }
        for (int i = BI_NGO_1_SAO; i <= BI_NGO_7_SAO; i++) {
            InventoryService.gI().subQuantityItemsBag(pl, InventoryService.gI().findItemBag(pl, i), 1);
        }
        InventoryService.gI().sendItemBags(pl);
        State st = new State();
        st.zone = pl.zone;
        st.x = pl.location.x;
        st.y = pl.location.y;
        st.appear = System.currentTimeMillis();
        dragons.put(pl.id, st);
        lastSummon.put(pl.id, st.appear);
        sendDragon(pl, st, true);
        page(pl, 1);
    }

    private static void sendDragon(Player pl, State st, boolean appear) {
        try {
            Message msg = new Message(-83);
            msg.writer().writeByte(appear ? 0 : 1);
            if (appear) {
                msg.writer().writeShort(st.zone.map.mapId);
                msg.writer().writeShort(st.zone.map.bgId);
                msg.writer().writeByte(st.zone.zoneId);
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeUTF("null");
                msg.writer().writeShort(st.x);
                msg.writer().writeShort(st.y);
                msg.writer().writeByte(dragonType);
                pl.isShenronAppear = true;
            }
            Service.gI().sendMessAllPlayerInMap(pl, msg);
            msg.cleanup();
        } catch (Exception ignored) {
        }
    }

    private static void page(Player pl, int p) {
        if (p == 1) {
            NpcService.gI().createMenuRongThieng(pl, MENU_P1, SAY_1, "Điều\nước 1", "Điều\nước 2", "Điều\nước 3", "Điều\nước 4", "Điều\nước 5", "->");
        } else {
            NpcService.gI().createMenuRongThieng(pl, MENU_P2, SAY_2, "<-", "Điều\nước 6", "Điều\nước 7", "Điều\nước 8", "Điều\nước 9");
        }
    }

    /** Xử lý menu (con mèo: gọi rồng; rồng thiêng: chọn điều ước). Trả về true nếu đúng menu Rồng Xương. */
    public static boolean confirmMenu(Player pl, int menu, int select) {
        switch (menu) {
            case MENU_SUMMON -> {
                if (select == 0) {
                    summon(pl);
                }
                return true;
            }
            case MENU_P1, MENU_P2, MENU_CONFIRM -> {
                State st = dragons.get(pl.id);
                if (st == null) {
                    return true;
                }
                if (System.currentTimeMillis() - st.appear > WAIT_MS) {
                    leave(pl, "Hết giờ rồi, ta đi đây...");
                    return true;
                }
                if (menu == MENU_P1) {
                    if (select == 5) {
                        page(pl, 2);
                    } else if (select >= 0 && select < 5) {
                        confirm(pl, st, select + 1);
                    }
                } else if (menu == MENU_P2) {
                    if (select == 0) {
                        page(pl, 1);
                    } else if (select >= 1 && select <= 4) {
                        confirm(pl, st, select + 5);
                    }
                } else if (select == 0) {
                    doWish(pl, st);
                } else {
                    page(pl, st.wish <= 5 ? 1 : 2);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void confirm(Player pl, State st, int wish) {
        st.wish = wish;
        NpcService.gI().createMenuRongThieng(pl, MENU_CONFIRM, "Ngươi có chắc muốn ước điều ước " + wish + "?", "Điều\nước " + wish, "Từ chối");
    }

    private static void doWish(Player pl, State st) {
        long now = System.currentTimeMillis();
        switch (st.wish) {
            case 1 -> {
                if (pl.pet == null) {
                    Service.gI().sendThongBao(pl, "Ngươi làm gì có đệ tử?");
                    page(pl, 1);
                    return;
                }
                rerollPetSkills(pl);
            }
            case 2 ->
                pl.hlwPetX3Until = Math.max(now, pl.hlwPetX3Until) + BUFF_MS;
            case 3 ->
                pl.hlwHpUntil = Math.max(now, pl.hlwHpUntil) + BUFF_MS;
            case 4 ->
                pl.hlwKiUntil = Math.max(now, pl.hlwKiUntil) + BUFF_MS;
            case 5 ->
                pl.hlwDameUntil = Math.max(now, pl.hlwDameUntil) + BUFF_MS;
            case 6 ->
                pl.hlwSelfX3Until = Math.max(now, pl.hlwSelfX3Until) + BUFF_MS;
            case 7 -> {
                if (pl.nPoint.power <= 40_000_000_000L) {
                    Service.gI().sendThongBao(pl, "Cần trên 40 tỷ sức mạnh");
                    page(pl, 2);
                    return;
                }
                pl.nPoint.power -= 100_000_000L;
                Service.gI().point(pl);
            }
            case 8, 9 -> {
                if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
                    Service.gI().sendThongBao(pl, "Hành trang đã đầy");
                    page(pl, 2);
                    return;
                }
                Item it = st.wish == 8
                        ? Event2026Service.gI().createItem(PET_CERBERUS, 1, Util.nextInt(15, 90))
                        : Event2026Service.gI().createItem(Util.isTrue(2, 100) ? QUA_TRUNG_MABU : RUONG_SPL_VIP, 1, 0);
                InventoryService.gI().addItemBag(pl, it);
                InventoryService.gI().sendItemBags(pl);
                Service.gI().sendThongBao(pl, "Bạn nhận được " + it.template.name);
            }
            default -> {
                return;
            }
        }
        if (st.wish >= 2 && st.wish <= 6) {
            sendBuffTimes(pl);
            pl.nPoint.calPoint();
            Service.gI().point(pl);
        }
        leave(pl, "Điều ước " + st.wish + " của các con đã được thực hiện...tạm biệt");
    }

    /** Đổi 3 kỹ năng đầu của đệ tử: kỹ năng mới cấp 1, có thể trùng kỹ năng cũ. */
    private static void rerollPetSkills(Player pl) {
        byte[][] pools = {
            {Skill.DRAGON, Skill.DEMON, Skill.GALICK},
            {Skill.KAMEJOKO, Skill.MASENKO, Skill.ANTOMIC},
            {Skill.THAI_DUONG_HA_SAN, Skill.TAI_TAO_NANG_LUONG, Skill.KAIOKEN}};
        for (int i = 0; i < 3 && i < pl.pet.playerSkill.skills.size(); i++) {
            Skill old = pl.pet.playerSkill.skills.get(i);
            if (old == null || old.skillId == -1) {
                continue;
            }
            Skill sk = SkillUtil.createSkill(pools[i][Util.nextInt(0, pools[i].length - 1)], 1);
            if (sk != null) {
                pl.pet.playerSkill.skills.set(i, sk);
            }
        }
        Service.gI().sendThongBao(pl, "Đệ tử đã được đổi kỹ năng");
    }

    /** Hiện thời gian còn lại của các điều ước có thời hạn. */
    public static void sendBuffTimes(Player pl) {
        long now = System.currentTimeMillis();
        long[] until = {pl.hlwPetX3Until, pl.hlwHpUntil, pl.hlwKiUntil, pl.hlwDameUntil, pl.hlwSelfX3Until};
        for (long u : until) {
            if (u > now) {
                ItemTimeService.gI().sendItemTime(pl, ItemService.gI().getTemplate(BI_NGO_1_SAO).iconID, (int) ((u - now) / 1000));
            }
        }
    }

    private static void leave(Player pl, String text) {
        State st = dragons.remove(pl.id);
        if (st == null) {
            return;
        }
        NpcService.gI().createTutorial(pl, 0, text);
        sendDragon(pl, st, false);
        pl.isShenronAppear = false;
    }

    /** Gọi định kỳ: rồng tự đi sau 5 phút không ước. */
    public static void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, State> e : dragons.entrySet()) {
            if (now - e.getValue().appear > WAIT_MS) {
                Player pl = Client.gI().getPlayer(e.getKey());
                if (pl != null) {
                    leave(pl, "Có không ước mất đừng tìm...");
                } else {
                    dragons.remove(e.getKey());
                }
            }
        }
    }

    /** Có đang hợp thể Porata (mọi loại bông tai) không. */
    public static boolean isPorata(Player pl) {
        return pl.fusion != null && (pl.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA
                || pl.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || pl.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3);
    }
}
