package nro.models.boss.Broly;

import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.ConstPlayer;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.skill.Skill;
import nro.models.utils.Util;

/**
 * Broly Super (SSJ Broly tóc xanh lá): boss riêng ở vùng tuyết của Cooler (Cánh đồng tuyết -> Hang băng), HP 2 tỷ.
 * Khác Super Broly (hóa từ Broly, giết nhận đệ tử). Phần thưởng theo bài cập nhật boss SSJ Broly.
 */
public class BrolySuper extends Boss {

    private static final int HP = 2_000_000_000;

    public BrolySuper() throws Exception {
        super(BossID.BROLY_SUPER, new BossData(
                "Broly Super",
                ConstPlayer.XAYDA,
                new short[]{2099, 2100, 2101, -1, -1, -1}, // Broly tóc xanh lá
                1_000_000,
                new int[]{HP},
                new int[]{105, 106, 107, 108, 109, 110},
                new int[][]{
                    {Skill.DRAGON, 7, 1000}, {Skill.DEMON, 7, 1000}, {Skill.GALICK, 7, 1000},
                    {Skill.KAMEJOKO, 7, 1000}, {Skill.MASENKO, 7, 1000}, {Skill.ANTOMIC, 7, 1000},
                    {Skill.TAI_TAO_NANG_LUONG, 7, 15000}},
                new String[]{},
                new String[]{"|-1|Mất của ta", "|-1|Chói mắt quá", "|-1|Ta sẽ nghiền nát các ngươi!"},
                new String[]{"|-1|Các ngươi... sẽ phải trả giá!"},
                900));
    }

    @Override
    public synchronized int injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (this.isDie()) {
            return 0;
        }
        if (!piercing && Util.isTrue(this.nPoint.tlNeDon, 1000)) {
            this.chat("Xí hụt");
            return 0;
        }
        damage = this.nPoint.subDameInjureWithDeff(damage);
        if (!piercing) {
            damage = damage * 35 / 100; // giáp boss 65%
        }
        if (!piercing && plAtt != null && plAtt.playerSkill.skillSelect != null
                && plAtt.playerSkill.skillSelect.template.id != Skill.TU_SAT && damage > this.nPoint.hpMax / 100) {
            damage = this.nPoint.hpMax / 100;
        }
        this.nPoint.subHP(damage);
        if (isDie()) {
            this.setDie(plAtt);
            die(plAtt);
        }
        return (int) damage;
    }

    @Override
    public void reward(Player plKill) {
        super.reward(plKill);
        if (this.zone == null) {
            return;
        }
        int x = this.location.x;
        int y = this.zone.map.yPhysicInTop(x, this.location.y - 24);
        // Hộp đá cấp 9 (Hộp đá nâng cấp +9)
        if (Util.isTrue(30, 100)) {
            drop(plKill, nro.models.services.SaoPhaLe10Service.HOP_DA_9, 1, x, y);
        }
        // Item Cuồng bạo (tăng chí mạng)
        if (Util.isTrue(10, 100)) {
            drop(plKill, 2055, 1, x, y);
        }
        // Trang bị thần linh (cả găng)
        if (Util.isTrue(10, 100)) {
            ItemMap tl = ItemService.gI().randDoTLBoss(this.zone, 1, x, y, plKill.id);
            if (tl != null) {
                Service.gI().dropItemMap(this.zone, tl);
            }
        }
        // Ngọc rồng 1 - 4 sao
        if (Util.isTrue(50, 100)) {
            drop(plKill, Util.nextInt(14, 17), 1, x, y);
        }
        // Item cấp 2 (Cuồng nộ 2, Bổ khí 2, Bổ huyết 2, Giáp Xên 2, Ẩn danh 2)
        if (Util.isTrue(30, 100)) {
            drop(plKill, Util.nextInt(1150, 1154), Util.nextInt(1, 3), x, y);
        }
        // Trang bị sao pha lê 1 - 3 sao
        if (Util.isTrue(30, 100)) {
            dropEquip(plKill, 1, 7, Util.nextInt(1, 3), x, y);
        }
        // Trang bị cấp 8 - 11
        if (Util.isTrue(30, 100)) {
            dropEquip(plKill, 8, 11, 0, x, y);
        }
        // Vàng ngẫu nhiên
        Service.gI().dropItemMap(this.zone, new ItemMap(this.zone, 190, Util.nextInt(100_000, 2_000_000), x, y, plKill.id));
        // Mảnh khỉ Oozaru
        if (Util.isTrue(30, 100)) {
            drop(plKill, 1771, Util.nextInt(1, 5), x, y);
        }
        // Trang bị cấp 11 - 12 từ 5 - 8 sao
        if (Util.isTrue(10, 100)) {
            dropEquip(plKill, 11, 12, Util.isTrue(10, 100) ? 8 : Util.nextInt(5, 7), x, y);
        }
        // Set kích hoạt Thần hủy diệt Beerus (đồ cấp 12)
        if (Util.isTrue(1, 100)) {
            ItemMap skh = dropEquip(plKill, 12, 12, 0, x, y);
            if (skh != null) {
                nro.models.services.Event2026Service.addSkhBeerusOptions(skh.options);
            }
        }
    }

    private void drop(Player plKill, int tempId, int quantity, int x, int y) {
        ItemMap it = new ItemMap(this.zone, tempId, quantity, x + Util.nextInt(-30, 30), y, plKill.id);
        it.options.addAll(ItemService.gI().getListOptionItemShop((short) tempId));
        Service.gI().dropItemMap(this.zone, it);
    }

    private ItemMap dropEquip(Player plKill, int minLevel, int maxLevel, int stars, int x, int y) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (nro.models.player_system.Template.ItemTemplate t : nro.models.server.Manager.ITEM_TEMPLATES) {
            if (t.type >= 0 && t.type <= 4 && t.level >= minLevel && t.level <= maxLevel) {
                ids.add((int) t.id);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        int tempId = ids.get(Util.nextInt(0, ids.size() - 1));
        ItemMap it = new ItemMap(this.zone, tempId, 1, x + Util.nextInt(-30, 30), y, plKill.id);
        it.options.addAll(ItemService.gI().getListOptionItemShop((short) tempId));
        if (stars > 0) {
            it.options.add(new Item.ItemOption(107, stars));
        }
        Service.gI().dropItemMap(this.zone, it);
        return it;
    }
}
