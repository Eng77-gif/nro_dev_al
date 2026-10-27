package nro.models.boss.event_tet;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import nro.models.boss.Boss_Manager.LunarNewYearEventManager;
import nro.models.consts.BossStatus;
import static nro.models.consts.BossType.TET_EVENT;
import nro.models.item.Item;
import nro.models.map.service.ChangeMapService;
import nro.models.player.Player;
import nro.models.services.Event2026Service;
import nro.models.services.InventoryService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.server.ServerNotify;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Ngựa ăn cỏ (sự kiện Tết): ba ngựa con lang thang các bản đồ, không đánh ai và không bị đánh chết.
 * Người chơi đứng gần và dùng Bó rơm để cho ngựa ăn, nhận 1 Capsule Tết 2026; gặp Ngựa Đỏ có cơ hội
 * nhận Pet Ngựa Đỏ vĩnh viễn. Mỗi con ăn đủ số bó rơm thì chạy sang bản đồ khác.
 */
public class NguaCon extends Boss {

    public static final int BO_ROM = 1989;
    public static final int CAPSULE_TET = 758;
    public static final int PET_NGUA_DO = 1973;
    public static final int PET_NGUA_DO_RATE = 1; // %
    private static final int FEED_PER_VISIT = 30;
    private static final Map<Long, Long> lastFeed = new ConcurrentHashMap<>();

    private final boolean red;
    private int fed;
    private long st, lastMove;
    private int timeLeave;

    public NguaCon(int bossId, BossData data) throws Exception {
        super(TET_EVENT, bossId - Util.nextInt(1000000), true, false, data);
        this.red = bossId == BossID.NGUA_DO;
    }

    @Override
    public void joinMap() {
        this.zone = getMapJoin();
        if (this.zone == null) {
            this.changeStatus(BossStatus.REST);
            return;
        }
        try {
            fed = 0;
            ChangeMapService.gI().changeMap(this, this.zone, Util.nextInt(100, Math.max(150, this.zone.map.mapWidth - 100)),
                    this.zone.map.yPhysicInTop(this.location.x, 100));
            this.changeStatus(BossStatus.ACTIVE);
            st = System.currentTimeMillis();
            timeLeave = Util.nextInt(600000, 900000);
            ServerNotify.gI().notify(this.name + " đang ăn cỏ ở " + this.zone.map.mapName + " khu " + this.zone.zoneId + ", mang Bó rơm đến cho ngựa ăn nhé!");
        } catch (Exception e) {
            Logger.error(this.name + ": lỗi vào bản đồ\n");
            this.changeStatus(BossStatus.REST);
        }
    }

    @Override
    public void respawn() {
        this.currentLevel = 0;
        this.initBase();
    }

    @Override
    public void active() {
        // đi lững thững, không tấn công
        if (Util.canDoWithTime(lastMove, Util.nextInt(4000, 8000))) {
            lastMove = System.currentTimeMillis();
            int x = Math.max(60, Math.min(this.zone.map.mapWidth - 60, this.location.x + Util.nextInt(-80, 80)));
            PlayerService.gI().playerMove(this, x, this.zone.map.yPhysicInTop(x, this.location.y - 24));
        }
        if (Util.canDoWithTime(this.lastTimeChatM, 20000)) {
            this.lastTimeChatM = System.currentTimeMillis();
            this.chat(Util.isTrue(1, 2) ? "Hí hí hí..." : "Đói quá, ai cho ta ít rơm đi!");
        }
    }

    @Override
    public void attack() {
    }

    @Override
    public void autoLeaveMap() {
        if (fed >= FEED_PER_VISIT || Util.canDoWithTime(st, timeLeave)) {
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
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (plAtt != null && Util.isTrue(1, 5)) {
            this.chat("Đừng đánh ta, cho ta ăn rơm đi mà!");
        }
        return 0;
    }

    @Override
    public void reward(Player plKill) {
    }

    /** Dùng Bó rơm: cho ngựa con gần nhất trong khu ăn. Trả về true nếu đã xử lý. */
    public static void feed(Player pl, Item rom) {
        if (pl.zone == null) {
            return;
        }
        NguaCon horse = null;
        for (Boss b : LunarNewYearEventManager.gI().getBosses()) {
            if (b instanceof NguaCon h && h.zone != null && h.zone.equals(pl.zone) 
                    && Util.getDistance(h, pl) <= 150 && h.bossStatus == BossStatus.ACTIVE) {
                horse = h;
                break;
            }
        }
        if (horse == null) {
            Service.gI().sendThongBao(pl, "Hãy đến gần ngựa con để cho ngựa ăn");
            return;
        }
        Long last = lastFeed.get(pl.id);
        if (last != null && System.currentTimeMillis() - last < 2000) {
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        synchronized (horse) {
            if (horse.fed >= FEED_PER_VISIT) {
                Service.gI().sendThongBao(pl, horse.name + " đã no rồi");
                return;
            }
            horse.fed++;
        }
        lastFeed.put(pl.id, System.currentTimeMillis());
        InventoryService.gI().subQuantityItemsBag(pl, rom, 1);
        String got;
        if (horse.red && Util.isTrue(PET_NGUA_DO_RATE, 100)) {
            InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(PET_NGUA_DO, 1, 0));
            got = "Pet Ngựa Đỏ vĩnh viễn";
            ServerNotify.gI().notify(pl.name + " cho Ngựa Đỏ ăn và nhận được Pet Ngựa Đỏ vĩnh viễn!");
        } else {
            InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(CAPSULE_TET, 1, 0));
            got = "1 Capsule Tết 2026";
        }
        InventoryService.gI().sendItemBags(pl);
        horse.chat("Hí hí, cảm ơn " + pl.name + "!");
        Service.gI().sendThongBao(pl, "Cho " + horse.name + " ăn, nhận được " + got);
    }

    public static BossData data(int id) {
        return switch (id) {
            case BossID.NGUA_DO -> BossesData.NGUA_DO;
            case BossID.NGUA_BE -> BossesData.NGUA_BE;
            default -> BossesData.NGUA_TRANG;
        };
    }
}
