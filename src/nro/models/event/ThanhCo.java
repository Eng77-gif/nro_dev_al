package nro.models.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.boss.BossData;
import nro.models.boss.vo_dai_hat_mit.DeathOrAliveArena;
import nro.models.consts.BossStatus;
import static nro.models.consts.BossType.PHOBAN;
import nro.models.consts.ConstPlayer;
import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.player.Player;
import nro.models.services.Event2026Service;
import nro.models.services.InventoryService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.skill.Skill;
import nro.models.utils.Util;

/**
 * Map Thành Cổ (sự kiện Giỗ tổ Hùng Vương): dùng Lệnh bài thành cổ nhờ Hùng Vương đưa đến Thành cổ 1,
 * đi qua Thành cổ 2 rồi lên sàn ở Đấu trường thành cổ đấu với Gohan chiến binh (máu bằng máu người chơi).
 * Thắng nhận Hộp quà thường Giỗ tổ.
 */
public class ThanhCo {

    public static final int LENH_BAI = 2018;
    public static final int REWARD_BOX = 1823;
    public static final int GOHAN_ID = -9501000;
    private static final Map<Zone, GohanChienBinh> fighting = new ConcurrentHashMap<>();

    private static final BossData GOHAN = new BossData(
            "Gohan chiến binh",
            ConstPlayer.TRAI_DAT,
            new short[]{1962, 1963, 1964, -1, -1, -1},
            1000,
            new int[]{1000},
            new int[]{VuaHung.MAP_DAU_TRUONG},
            new int[][]{
                {Skill.DRAGON, 7, 1000},
                {Skill.MASENKO, 7, 1500},
                {Skill.KAMEJOKO, 7, 3000}},
            new String[]{},
            new String[]{"|-1|Mù mắt chưa!", "|-1|Ngươi không qua được ta đâu"},
            new String[]{},
            1); // nghỉ 1 giây

    public static void enter(Player pl) {
        Item lb = InventoryService.gI().findItemBag(pl, LENH_BAI);
        if (lb == null) {
            Service.gI().sendThongBao(pl, "Cần 1 Lệnh bài thành cổ (mua ở Chi Chi) để đến cổ thành");
            return;
        }
        InventoryService.gI().subQuantityItemsBag(pl, lb, 1);
        InventoryService.gI().sendItemBags(pl);
        ChangeMapService.gI().changeMapBySpaceShip(pl, VuaHung.MAP_THANH_CO_1, -1, 100);
    }

    public static void next(Player pl) {
        int map = pl.zone.map.mapId == VuaHung.MAP_THANH_CO_1 ? VuaHung.MAP_THANH_CO_2 : VuaHung.MAP_DAU_TRUONG;
        ChangeMapService.gI().changeMapBySpaceShip(pl, map, -1, 100);
    }

    public static void home(Player pl) {
        ChangeMapService.gI().changeMapBySpaceShip(pl, 21 + pl.gender, -1, 250);
    }

    public static void fight(Player pl) {
        if (pl.zone == null || pl.zone.map.mapId != VuaHung.MAP_DAU_TRUONG) {
            return;
        }
        GohanChienBinh cur = fighting.get(pl.zone);
        if (cur != null && cur.zone != null && !cur.isDie()) {
            Service.gI().sendThongBao(pl, "Sàn đấu đang có người thi đấu, hãy chờ hoặc sang khu khác");
            return;
        }
        try {
            fighting.put(pl.zone, new GohanChienBinh(pl));
            Service.gI().sendThongBao(pl, "Gohan chiến binh đã lên sàn!");
        } catch (Exception e) {
            Service.gI().sendThongBao(pl, "Không gọi được đối thủ, thử lại sau");
        }
    }

    public static class GohanChienBinh extends DeathOrAliveArena {

        private final Zone arena;

        public GohanChienBinh(Player pl) throws Exception {
            super(PHOBAN, GOHAN_ID - Util.nextInt(1000000), GOHAN);
            this.playerAtt = pl;
            this.arena = pl.zone;
        }

        @Override
        public void joinMap() {
            if (playerAtt.zone == null || playerAtt.zone != arena) {
                return;
            }
            this.zone = arena;
            this.nPoint.hpMax = Math.max(1000, playerAtt.nPoint.hpMax);
            this.nPoint.hp = this.nPoint.hpMax;
            this.nPoint.dame = Math.max(1, playerAtt.nPoint.hpMax / 25);
            ChangeMapService.gI().changeMap(this, this.zone, Math.min(this.zone.map.mapWidth - 100, playerAtt.location.x + 150), playerAtt.location.y);
            PlayerService.gI().changeAndSendTypePK(playerAtt, ConstPlayer.PK_PVP);
        }

        @Override
        public void afk() {
            if (playerAtt.zone == arena) {
                this.changeStatus(BossStatus.ACTIVE);
            } else {
                end();
            }
        }

        @Override
        public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
            if (plAtt != playerAtt) {
                return 0; // chỉ người lên sàn được đánh
            }
            return super.injured(plAtt, damage, piercing, isMobAttack);
        }

        @Override
        public void attack() {
            if (playerAtt.isDie() || playerAtt.zone != arena) {
                this.chat("Về tập luyện thêm đi!");
                end();
                return;
            }
            super.attack();
        }

        @Override
        public void die(Player plKill) {
            this.changeStatus(BossStatus.DIE);
            Player pl = playerAtt;
            if (pl != null && pl.zone == arena) {
                this.chat("Chúc mừng bạn đã hoàn thành thử thách");
                if (InventoryService.gI().getCountEmptyBag(pl) > 0) {
                    InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(REWARD_BOX, 1, 0));
                    InventoryService.gI().sendItemBags(pl);
                    Service.gI().sendThongBao(pl, "Hoàn thành thử thách thành cổ, nhận 1 Hộp quà thường Giỗ tổ");
                } else {
                    Service.gI().sendThongBao(pl, "Hành trang đầy, không nhận được quà thành cổ");
                }
            }
            end();
        }

        private void end() {
            fighting.remove(arena, this);
            if (playerAtt != null && playerAtt.zone != null) {
                PlayerService.gI().changeAndSendTypePK(playerAtt, ConstPlayer.NON_PK);
            }
            this.leaveMap();
        }
    }
}
