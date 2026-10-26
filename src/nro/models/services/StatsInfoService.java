package nro.models.services;

import java.util.ArrayList;
import java.util.List;
import nro.models.player.NPoint;
import nro.models.player.Player;
import nro.models.player.SetClothes;

/**
 * Bảng chỉ số chi tiết của nhân vật / đệ tử (chat "chiso" hoặc "chiso de").
 */
public class StatsInfoService {

    private static StatsInfoService instance;

    public static StatsInfoService gI() {
        if (instance == null) {
            instance = new StatsInfoService();
        }
        return instance;
    }

    public void show(Player player, boolean pet) {
        Player target = pet ? player.pet : player;
        if (target == null || target.nPoint == null) {
            Service.gI().sendThongBao(player, pet ? "Bạn chưa có đệ tử" : "Không xem được chỉ số");
            return;
        }
        nro.models.map.service.NpcService.gI().createTutorial(player, -1, build(target, pet));
    }

    public String build(Player p, boolean pet) {
        NPoint n = p.nPoint;
        StringBuilder sb = new StringBuilder();
        sb.append("|7|").append(pet ? "Chỉ số đệ tử" : "Chỉ số ").append(pet ? "" : p.name).append('\n');
        line(sb, "HP", num(n.hpMax));
        line(sb, "MP", num(n.mpMax));
        line(sb, "Sức đánh", num(n.dame));
        line(sb, "Chí mạng", n.crit + "%");
        line(sb, "STCM", n.tlSDCM + "%");
        line(sb, "Giáp", num(n.def));
        line(sb, "Giảm sát thương", n.tlGiap + "%");
        line(sb, "Sát thương chuẩn", n.tlSTChuan + "%");
        line(sb, "Sát thương cuối", n.tlSTCuoi + "%");
        line(sb, "Sát thương Boss", n.tlDameBoss + "%");
        line(sb, "Né đòn", n.tlNeDon + "%");
        line(sb, "Hút máu", n.tlHutHp + "%");
        line(sb, "Hút KI", n.tlHutMp + "%");
        int tnsm = 0;
        if (n.tlTNSM != null) {
            for (Integer t : n.tlTNSM) {
                tnsm += t == null ? 0 : t;
            }
        }
        line(sb, "TNSM", tnsm + "%");
        line(sb, "Vàng rơi từ quái", n.tlGold + "%");
        for (String s : activeSets(p)) {
            sb.append("|5|").append(s).append('\n');
        }
        return sb.toString();
    }

    private static String num(long v) {
        return String.format("%,d", v).replace(',', '.');
    }

    private static void line(StringBuilder sb, String name, String value) {
        sb.append("|1|").append(name).append(": |2|").append(value).append('\n');
    }

    private static List<String> activeSets(Player p) {
        List<String> l = new ArrayList<>();
        SetClothes s = p.setClothes;
        if (s != null) {
            add(l, "Sôngôku", s.songoku);
            add(l, "Thiên Xin Hăng", s.thienXinHang);
            add(l, "Kirin", s.kirin);
            add(l, "Kaioken", s.kaioken);
            add(l, "Thần Vũ Trụ Kaio", s.thanVuTruKaio);
            add(l, "Ốc Tiêu", s.ocTieu);
            add(l, "Pikkoro Đaimao", s.pikkoroDaimao);
            add(l, "Picolo", s.picolo);
            add(l, "Liên Hoàn", s.lienHoan);
            add(l, "Nail", s.nail);
            add(l, "Kakarot", s.kakarot);
            add(l, "Cađíc", s.cadic);
            add(l, "Nappa", s.nappa);
            add(l, "Cađíc M", s.cadicM);
        }
        if (p.nPoint.skhBeerus >= 2) {
            l.add("Set Thần hủy diệt Beerus: " + p.nPoint.skhBeerus + " món");
        }
        return l;
    }

    private static void add(List<String> l, String name, byte count) {
        if (count >= 5) {
            l.add("Set " + name + ": 5 món");
        }
    }
}
