package nro.models.boss.event.Halloween;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import nro.models.boss.Boss_Manager.HalloweenEventManager;
import nro.models.consts.BossStatus;
import static nro.models.consts.BossType.HALLOWEEN_EVENT;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.map.service.NpcService;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.services.Event2026Service;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.server.ServerNotify;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Đại chiến Zombie (sự kiện Halloween): từ 21h00 đến 22h00 mỗi ngày zombie lang thang ở khu 2 trở lên,
 * cắn ai thì người đó hóa bộ xương trong 5 phút. Cần hợp lực để hạ (mỗi đòn chỉ trừ tối đa 1% máu),
 * người hạ nhận 1 phần quà ngẫu nhiên (có Cá Koi zombie, cải trang bộ xương hạn sử dụng hoặc vĩnh viễn).
 */
public class Zombie extends Boss {

    public static final int START_HOUR = 21, END_HOUR = 22;
    public static final int REWARD_POOL = 100703; // box_id trong item_box_reward
    public static final int RADA = 1737;
    private static final int INFECT_MS = 300_000;
    private static volatile long forceUntil;
    private static volatile boolean announced;

    private long lastTimeAtt;

    public Zombie() throws Exception {
        super(HALLOWEEN_EVENT, BossID.ZOMBIE - Util.nextInt(1000000), true, true, BossesData.ZOMBIE);
    }

    /** Đang trong giờ zombie (hoặc admin gọi thử). */
    public static boolean isZombieTime() {
        if (System.currentTimeMillis() < forceUntil) {
            return true;
        }
        int h = LocalTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).getHour();
        return h >= START_HOUR && h < END_HOUR;
    }

    /** Admin cho zombie xuất hiện ngay trong số phút. */
    public static void force(int minutes) {
        forceUntil = System.currentTimeMillis() + minutes * 60_000L;
    }

    @Override
    public void rest() {
        if (isZombieTime()) {
            if (!announced) {
                announced = true;
                ServerNotify.gI().notify("Zombie đã xuất hiện khắp nơi! Các cư dân hãy hợp lực tiêu diệt chúng (khu 2 trở lên). Rađa dò Zombie bán ở Santa.");
            }
            if (Util.canDoWithTime(lastTimeRest, secondsRest * 1000L)) {
                this.changeStatus(BossStatus.RESPAWN);
            }
        } else {
            announced = false;
        }
    }

    @Override
    public void respawn() {
        this.currentLevel = 0;
        this.initBase();
    }

    @Override
    public void joinMap() {
        try {
            Zone z = null;
            for (int i = 0; i < 10 && z == null; i++) {
                int[] maps = this.data[0].getMapJoin();
                nro.models.map.Map map = MapService.gI().getMapById(maps[Util.nextInt(0, maps.length - 1)]);
                if (map != null && map.zones.size() > 2) {
                    z = map.zones.get(Util.nextInt(2, map.zones.size() - 1)); // khu 2 trở lên
                }
            }
            if (z == null) {
                this.changeStatus(BossStatus.REST);
                return;
            }
            this.zone = z;
            this.name = "Zombie " + Util.nextInt(10, 99);
            ChangeMapService.gI().changeMap(this, z, Util.nextInt(100, Math.max(150, z.map.mapWidth - 100)),
                    z.map.yPhysicInTop(this.location.x, 100));
            this.changeToTypePK();
            this.changeStatus(BossStatus.ACTIVE);
        } catch (Exception e) {
            Logger.error("Zombie: lỗi vào bản đồ\n");
            this.changeStatus(BossStatus.REST);
        }
    }

    @Override
    public void autoLeaveMap() {
        if (!isZombieTime()) {
            this.leaveMapNew();
        }
    }

    @Override
    public void leaveMap() {
        ChangeMapService.gI().exitMap(this);
        this.lastZone = null;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
    }

    @Override
    public void active() {
        this.attack();
    }

    private static void infect(Player pl) {
        if (pl != null && pl.effectSkill != null && !pl.isBoss && !pl.effectSkill.isHalloween) {
            EffectSkillService.gI().setIsHalloween(pl, 0, INFECT_MS);
            Service.gI().sendThongBao(pl, "Bạn đã bị Zombie cắn, hóa bộ xương trong 5 phút!");
        }
    }

    @Override
    public void attack() {
        if (!Util.canDoWithTime(this.lastTimeAttack, 500)) {
            return;
        }
        this.lastTimeAttack = System.currentTimeMillis();
        try {
            Player pl = getPlayerAttack();
            if (pl == null || pl.isDie()) {
                if (Util.isTrue(1, 4)) {
                    int x = Math.max(60, Math.min(this.zone.map.mapWidth - 60, this.location.x + Util.nextInt(-100, 100)));
                    this.moveTo(x, this.location.y);
                }
                return;
            }
            this.nPoint.dame = Math.max(1, pl.nPoint.hpMax / Util.nextInt(30, 50));
            this.playerSkill.skillSelect = this.playerSkill.skills.get(Util.nextInt(0, this.playerSkill.skills.size() - 1));
            if (Util.getDistance(this, pl) <= this.getRangeCanAttackWithSkillSelect()) {
                if (Util.isTrue(5, 20)) {
                    this.moveTo(pl.location.x + (Util.getOne(-1, 1) * Util.nextInt(10, 40)), pl.location.y);
                }
                if (Util.canDoWithTime(lastTimeAtt, 3000)) {
                    lastTimeAtt = System.currentTimeMillis();
                    infect(pl);
                }
                SkillService.gI().useSkill(this, pl, null, -1, null);
                checkPlayerDie(pl);
            } else if (Util.isTrue(1, 2)) {
                this.moveToPlayer(pl);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (this.isDie()) {
            return 0;
        }
        if (!piercing && Util.isTrue(10, 1000)) {
            this.chat("Grừừ...");
            return 0;
        }
        damage = this.nPoint.subDameInjureWithDeff(damage / 5);
        if (!piercing && effectSkill.isShielding && damage > nPoint.hpMax) {
            EffectSkillService.gI().breakShield(this);
        }
        damage = Math.min(damage, this.nPoint.hpMax / 100); // hợp lực: mỗi đòn tối đa 1% máu
        this.nPoint.subHP(damage);
        if (isDie()) {
            this.setDie(plAtt);
            die(plAtt);
        }
        return (int) damage;
    }

    @Override
    public void reward(Player plKill) {
        if (plKill == null || !plKill.isPl()) {
            return;
        }
        ItemMap it = new ItemMap(this.zone, 901, Util.nextInt(3, 5), this.location.x,
                this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id);
        Service.gI().dropItemMap(this.zone, it);
        if (InventoryService.gI().getCountEmptyBag(plKill) > 0) {
            String got = Event2026Service.gI().openBoxById(plKill, REWARD_POOL);
            if (got != null) {
                InventoryService.gI().sendItemBags(plKill);
                Service.gI().sendThongBao(plKill, "Hạ " + this.name + " nhận được " + got);
            }
        } else {
            Service.gI().sendThongBao(plKill, "Hành trang đầy, không nhận được quà Zombie");
        }
    }

    /** Dùng Rađa dò Zombie: hiện vị trí các zombie đang lang thang. */
    public static void radar(Player pl, Item rada) {
        List<String> lines = new ArrayList<>();
        for (Boss b : HalloweenEventManager.gI().getBosses()) {
            if (b instanceof Zombie z && z.zone != null && !z.isDie()) {
                lines.add(z.name + ": " + z.zone.map.mapName + " khu " + z.zone.zoneId);
            }
        }
        if (lines.isEmpty()) {
            Service.gI().sendThongBao(pl, isZombieTime() ? "Không dò thấy Zombie nào" : "Zombie chỉ xuất hiện từ " + START_HOUR + "h đến " + END_HOUR + "h");
            return;
        }
        InventoryService.gI().subQuantityItemsBag(pl, rada, 1);
        InventoryService.gI().sendItemBags(pl);
        NpcService.gI().createTutorial(pl, -1, "Rađa dò Zombie (" + lines.size() + " con)\n" + String.join("\n", lines.subList(0, Math.min(20, lines.size()))));
    }
}
