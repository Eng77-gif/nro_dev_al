package nro.models.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import nro.models.consts.ConstMob;
import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.mob.Mob;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.server.Manager;
import nro.models.services.InventoryService;
import nro.models.services.SeasonEventService;
import nro.models.services.Service;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Bản đồ truyền thuyết (sự kiện Giỗ tổ Hùng Vương): dùng để vào một khu riêng của Rừng nguyên sinh
 * (bản sao map 17) có 1 trong 3 quái to Voi Chín Ngà, Gà Chín Cựa, Ngựa Chín Hồng Mao.
 * Hết giờ hoặc hạ xong quái một lúc thì tự đưa về nhà.
 */
public class BanDoTruyenThuyet {

    public static final int ITEM = 1565;
    public static final int MAP_ID = 186;
    private static final long TIME_LIMIT = 10 * 60_000L;
    private static final long AFTER_KILL = 30_000L;
    private static final int[] BOSSES = {ConstMob.VOI_CHIN_NGA, ConstMob.GA_CHIN_CUA, ConstMob.NGUA_CHIN_LMAO};

    private static class Run {

        long playerId;
        Zone zone;
        Mob mob;
        long end;
        long killedAt;
        boolean warned;
    }

    private static final Map<Zone, Run> runs = new ConcurrentHashMap<>();
    private static ScheduledExecutorService timer;

    public static void use(Player pl, Item item) {
        nro.models.map.Map map = MapService.gI().getMapById(MAP_ID);
        if (map == null) {
            Service.gI().sendThongBao(pl, "Chưa có bản đồ Rừng nguyên sinh");
            return;
        }
        if (pl.zone != null && pl.zone.map.mapId == MAP_ID) {
            Service.gI().sendThongBao(pl, "Ngươi đang ở trong bản đồ truyền thuyết rồi");
            return;
        }
        Zone free = null;
        for (Zone z : map.zones) {
            if (!runs.containsKey(z) && z.getNumOfPlayers() == 0) {
                free = z;
                break;
            }
        }
        if (free == null) {
            Service.gI().sendThongBao(pl, "Rừng nguyên sinh đang kín chỗ, thử lại sau ít phút");
            return;
        }
        InventoryService.gI().subQuantityItemsBag(pl, item, 1);
        InventoryService.gI().sendItemBags(pl);
        int temp = BOSSES[Util.nextInt(0, BOSSES.length - 1)];
        int hp = Math.max(1, Manager.getMobTemplateByTemp(temp).hp);
        Run r = new Run();
        r.playerId = pl.id;
        r.zone = free;
        synchronized (free) {
            free.mobs.clear(); // khu riêng: chỉ có quái truyền thuyết
        }
        r.mob = SeasonEventService.createBigMob(free, temp, hp, map.mapWidth / 2, null);
        r.end = System.currentTimeMillis() + TIME_LIMIT;
        runs.put(free, r);
        startTimer();
        ChangeMapService.gI().changeMap(pl, free, 120, map.yPhysicInTop(120, 100));
        Service.gI().sendThongBao(pl, "Bản đồ truyền thuyết dẫn ngươi tới Rừng nguyên sinh! Có 10 phút để hạ "
                + Manager.getMobTemplateByTemp(temp).name);
    }

    private static synchronized void startTimer() {
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor();
            timer.scheduleAtFixedRate(BanDoTruyenThuyet::tick, 5, 5, TimeUnit.SECONDS);
        }
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        for (Run r : runs.values()) {
            try {
                Player pl = Client.gI().getPlayer(r.playerId);
                boolean inside = pl != null && pl.zone == r.zone;
                if (r.mob.isDie() && r.killedAt == 0) {
                    r.killedAt = now;
                    if (inside) {
                        Service.gI().sendThongBao(pl, "Đã hạ quái truyền thuyết! 30 giây nữa sẽ được đưa về nhà, nhặt đồ nhanh nhé");
                    }
                }
                if (inside && !r.warned && r.end - now <= 60_000L) {
                    r.warned = true;
                    Service.gI().sendThongBao(pl, "Còn 1 phút trong Rừng nguyên sinh");
                }
                boolean over = now >= r.end || (r.killedAt > 0 && now - r.killedAt >= AFTER_KILL);
                if (over || !inside) {
                    if (inside) {
                        ChangeMapService.gI().changeMapBySpaceShip(pl, 21 + pl.gender, -1, 250);
                    }
                    synchronized (r.zone) {
                        r.zone.mobs.remove(r.mob);
                    }
                    runs.remove(r.zone);
                }
            } catch (Exception e) {
                Logger.error("Bản đồ truyền thuyết: " + e.getMessage() + "\n");
            }
        }
    }
}
