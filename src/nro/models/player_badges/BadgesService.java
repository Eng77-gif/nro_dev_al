package nro.models.player_badges;

import nro.models.player.Player;

/**
 *
 * @author By Mr Blue
 * 
 */

public class BadgesService {

    public static final int PERFECT_EFFECT = 302;
    public static final int[] XEN_PETS = {1990, 1991, 1992};

    /**
     * Thêm danh hiệu (hoặc gia hạn nếu đã có) rồi bật nó lên. days <= 0 là vĩnh viễn.
     */
    public static void giveBadge(Player player, int idEffect, int days) {
        if (player.dataBadges == null) {
            return;
        }
        long until = days <= 0 ? Long.MAX_VALUE / 2 : System.currentTimeMillis() + days * 24L * 60 * 60 * 1000;
        BadgesData owned = null;
        for (BadgesData d : player.dataBadges) {
            if (d.idBadGes == idEffect) {
                owned = d;
            }
        }
        if (owned == null) {
            owned = new BadgesData(player, idEffect, 0);
            player.dataBadges.add(owned);
        }
        owned.timeofUseBadges = Math.max(owned.timeofUseBadges, until);
        turnOnBadges(player, idEffect);
        player.nPoint.calPoint();
    }

    private static boolean isPermanent(nro.models.item.Item it) {
        for (nro.models.item.Item.ItemOption io : it.itemOptions) {
            if (io.optionTemplate.id == 93) {
                return false; // còn hạn sử dụng => không phải vĩnh viễn
            }
        }
        return true;
    }

    /**
     * Danh hiệu Perfect: sở hữu đủ 3 Pet Xên (con, hoàn thiện, Max) vĩnh viễn ở hành trang, rương hoặc trên người.
     */
    public static void checkPerfect(Player player) {
        if (player == null || player.inventory == null || player.dataBadges == null) {
            return;
        }
        for (BadgesData d : player.dataBadges) {
            if (d.idBadGes == PERFECT_EFFECT) {
                return;
            }
        }
        java.util.Set<Integer> found = new java.util.HashSet<>();
        for (java.util.List<nro.models.item.Item> list : java.util.List.of(player.inventory.itemsBag, player.inventory.itemsBox, player.inventory.itemsBody)) {
            for (nro.models.item.Item it : list) {
                if (it != null && it.isNotNullItem() && isPermanent(it)) {
                    for (int id : XEN_PETS) {
                        if (it.template.id == id) {
                            found.add(id);
                        }
                    }
                }
            }
        }
        if (found.size() == XEN_PETS.length) {
            int on = -1;
            for (BadgesData d : player.dataBadges) {
                if (d.isUse) {
                    on = d.idBadGes;
                }
            }
            giveBadge(player, PERFECT_EFFECT, 0);
            if (on != -1) {
                turnOnBadges(player, on); // giữ danh hiệu đang dùng, Perfect chỉ được thêm vào bộ sưu tập
            }
            nro.models.services.Service.gI().sendThongBao(player, "Sở hữu đủ 3 Pet Xên vĩnh viễn: nhận danh hiệu Perfect");
        }
    }

    public static void turnOnBadges(Player player, int id) {
        if (player.dataBadges != null) {
            for (BadgesData data : player.dataBadges) {
                if (data.idBadGes == id) {
                    data.isUse = true;
                } else {
                    data.isUse = false;
                }
            }
        }
    }

}
