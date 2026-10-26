package nro.models.matches;
import nro.models.consts.ConstAchievement;
import nro.models.matches.PVP;
import nro.models.matches.TYPE_LOSE_PVP;
import nro.models.matches.TYPE_PVP;
import nro.models.services.AchievementService;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.Service;
import nro.models.utils.Util;

public class ThachDau extends PVP {

    private int goldThachDau;
    private long goldReward;
    private boolean settled;

    public ThachDau(Player p1, Player p2, int goldThachDau) {
        super(TYPE_PVP.THACH_DAU, p1, p2);
        // PVP() gọi start() trước khi các field ở đây được gán, nên giữ tiền cược tại đây
        this.goldThachDau = Math.max(0, goldThachDau);
        this.goldReward = this.goldThachDau / 100 * 80;
        this.p1.inventory.gold -= this.goldThachDau;
        this.p2.inventory.gold -= this.goldThachDau;
        Service.gI().sendMoney(this.p1);
        Service.gI().sendMoney(this.p2);
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void finish() {

    }

    @Override
    public void dispose() {
        if (!settled) {
            // trận bị hủy giữa chừng: hoàn tiền cược
            settled = true;
            if (this.p1 != null) {
                this.p1.inventory.gold += this.goldThachDau;
                Service.gI().sendMoney(this.p1);
            }
            if (this.p2 != null) {
                this.p2.inventory.gold += this.goldThachDau;
                Service.gI().sendMoney(this.p2);
            }
        }
        super.dispose();
    }

    @Override
    public void update() {
    }

    @Override
    public void reward(Player plWin) {
        if (settled) {
            return;
        }
        settled = true;
        // trả lại tiền cược của người thắng + 80% tiền cược của người thua
        plWin.inventory.gold += this.goldThachDau + this.goldReward;
        Service.gI().sendMoney(plWin);
    }

    @Override
    public void sendResult(Player plLose, TYPE_LOSE_PVP typeLose) {
        if (typeLose == TYPE_LOSE_PVP.RUNS_AWAY) {
            Player plL = Client.gI().getPlayer(plLose.id);
            if (plL == null) {
                Service.gI().sendThongBao(p1.equals(plLose) ? p2 : p1, "Đối thủ rời game, bạn thắng được " + Util.numberToMoney(this.goldReward) + " vàng");
            } else {
                Service.gI().sendThongBao(p1.equals(plLose) ? p2 : p1, "Đối thủ sợ quá bỏ chạy, bạn thắng được " + Util.numberToMoney(this.goldReward) + " vàng");
            }
            Service.gI().sendThongBao(p1.equals(plLose) ? p1 : p2, "Bạn bị xử thua vì đã bỏ chạy");
        } else if (typeLose == TYPE_LOSE_PVP.DEAD) {
            Service.gI().sendThongBao(p1.equals(plLose) ? p2 : p1, "Đối thủ đã kiệt sức, bạn thắng được " + Util.numberToMoney(this.goldReward) + " vàng");
            Service.gI().sendThongBao(p1.equals(plLose) ? p1 : p2, "Bạn đã thua vì đã kiệt sức");
        }
        Service.gI().sendMoney(p1.equals(plLose) ? p1 : p2);
        if (!p1.equals(plLose)) {
            AchievementService.gI().checkDoneTask(p1, ConstAchievement.TRAM_TRAN_TRAM_THANG);
        }
    }

}
