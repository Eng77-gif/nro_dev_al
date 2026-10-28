package nro.models.combine;

import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.utils.Util;

/**
 *
 * @author By Mr Blue
 */
public class CombineSystem {

    public static boolean isTrangBiPhaLeHoa(Item item) {
        if (item != null && item.isNotNullItem()) {
            return item.template.type < 5 || item.template.type == 32;
        } else {
            return false;
        }
    }

    /** Ngọc bất kỳ: tổng ngọc xanh + hồng ngọc. */
    public static boolean hasNgocBatKy(Player player, int n) {
        return (long) player.inventory.gem + player.inventory.ruby >= n;
    }

    /** Trừ ngọc bất kỳ: ngọc xanh trước, thiếu thì trừ hồng ngọc. */
    public static void subNgocBatKy(Player player, int n) {
        int g = Math.min(Math.max(player.inventory.gem, 0), n);
        player.inventory.gem -= g;
        player.inventory.ruby -= n - g;
    }

    public static boolean isDaPhaLe(Item item) {
        return item != null && item.template != null && (item.template.type == 30 || (item.template.id >= 14 && item.template.id <= 20));
    }

    public static int getGemEpSao(int star) {
        switch (star) {
            case 0 -> {
                return 10;
            }
            case 1 -> {
                return 10;
            }
            case 2 -> {
                return 10;
            }
            case 3 -> {
                return 10;
            }
            case 4 -> {
                return 10;
            }
            case 5 -> {
                return 10;
            }
            case 6 -> {
                return 10;

            }
            case 7 -> {
                return 10;

            }
            case 8, 9 -> { // ép vào lỗ thứ 9, 10
                return 10;

            }
        }
        return 0;
    }

    public static int getOptionDaPhaLe(Item daPhaLe) {
        if (daPhaLe.template.type == 30) {
            return daPhaLe.itemOptions.get(0).optionTemplate.id;
        }
        return switch (daPhaLe.template.id) {
            case 20 ->
                77;
            case 19 ->
                103;
            case 18 ->
                80;
            case 17 ->
                81;
            case 16 ->
                50;
            case 15 ->
                94;
            case 14 ->
                108;
            case 441 ->
                95;
            case 442 ->
                96;
            case 443 ->
                97;
            case 444 ->
                98;
            case 445 ->
                99;
            case 446 ->
                100;
            case 447 ->
                101;
            default ->
                -1;
        };
    }

    public static int getParamDaPhaLe(Item daPhaLe) {
        if (daPhaLe.template.type == 30) {
            return daPhaLe.itemOptions.get(0).param;
        }
        return switch (daPhaLe.template.id) {
            case 20 ->
                5;
            case 19 ->
                5;
            case 18 ->
                5;
            case 17 ->
                5;
            case 16 ->
                3;
            case 15 ->
                2;
            case 14 ->
                2;
            case 441 ->
                5;
            case 442 ->
                5;
            case 443 ->
                5;
            case 444 ->
                3;
            case 445 ->
                3;
            case 446 ->
                5;
            case 447 ->
                5;
            default ->
                -1;
        };
    }

    public static int getGoldPhaLeHoa(int star) {
        switch (star) {
            case 0:
                return 5000000;
            case 1:
                return 10000000;
            case 2:
                return 20000000;
            case 3:
                return 40000000;
            case 4:
                return 60000000;
            case 5:
                return 90000000;
            case 6:
                return 120000000;
            case 7:
                return 200000000;
            case 8:
                return 300000000;
            case 9:
                return 500000000;
        }
        return 0;
    }

    public static float getRatioPhaLeHoa(int star) {
        switch (star) {
            case 0:
                return 50f;
            case 1:
                return 20f;
            case 2:
                return 10f;
            case 3:
                return 5f;
            case 4:
                return 2f;
            case 5:
                return 1f;
            case 6:
                return 1f;
            case 7:
                return 0.5f;
            case 8:
                return 0.3f;
            case 9:
                return 0.5f;
            case 10:
                return 0.2f;
            case 11:
                return 0.1f;
            case 12:
                return 0.1f;
        }

        return 0;
    }

    public static int getGemPhaLeHoa(int star) {
        switch (star) {
            case 0:
                return 1;
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 4;
            case 4:
                return 5;
            case 5:
                return 6;
            case 6:
                return 7;
            case 7:
                return 8;
            case 8:
                return 9;
            case 9:
                return 50; // sao 10: 50 ngọc bất kỳ
            case 10:
                return 560;
            case 11:
                return 1120;
            case 12:
                return 2240;
        }
        return 0;
    }

    /** Đá cấp 9 cho từng loại đồ (áo, quần, găng, giày, rada) khi nâng từ +8 lên +9. */
    public static final int[] DA_NANG_CAP_9 = {2001, 2003, 2002, 2004, 2000};

    public static int getLevelNangCap(Item trangBi) {
        for (Item.ItemOption io : trangBi.itemOptions) {
            if (io.optionTemplate.id == 72) {
                return io.param;
            }
        }
        return 0;
    }

    public static boolean isCoupleItemNangCapCheck(Item trangBi, Item daNangCap) {
        if (trangBi != null && daNangCap != null && trangBi.template.type < 5) {
            // +8 -> +9 chỉ dùng đá cấp 9, các cấp khác dùng đá thường
            if (getLevelNangCap(trangBi) == 8) {
                return daNangCap.template.id == DA_NANG_CAP_9[trangBi.template.type];
            }
            if (trangBi.template.type == 0 && daNangCap.template.id == 223) {
                return true;
            } else if (trangBi.template.type == 1 && daNangCap.template.id == 222) {
                return true;
            } else if (trangBi.template.type == 2 && daNangCap.template.id == 224) {
                return true;
            } else if (trangBi.template.type == 3 && daNangCap.template.id == 221) {
                return true;
            } else {
                return trangBi.template.type == 4 && daNangCap.template.id == 220;
            }
        } else {
            return false;
        }
    }

    public static int getCountDaNangCapDo(int level) {
        switch (level) {
            case 0:
                return 3;
            case 1:
                return 7;
            case 2:
                return 11;
            case 3:
                return 17;
            case 4:
                return 23;
            case 5:
                return 35;
            case 6:
                return 50;
            case 7:
                return 70;
            case 8:
                return 10; // +8 -> +9: 10 đá cấp 9
            case 9:
                return 110;
            case 10:
                return 130;
            case 11:
                return 160;
            case 12:
                return 200;
        }
        return 0;
    }

    public static int getCountDaBaoVe(int level) {
        return 1;
    }

    public static int getGoldNangCapDo(int level) {
        switch (level) {
            case 0:
                return 10000;
            case 1:
                return 70000;
            case 2:
                return 300000;
            case 3:
                return 1500000;
            case 4:
                return 7000000;
            case 5:
                return 23000000;
            case 6:
                return 100000000;
            case 7:
                return 250000000;
            case 8:
                return 300000000;
            case 9:
                return 400000000;
            case 10:
                return 500000000;
            case 11:
                return 700000000;
            case 12:
                return 900000000;
        }
        return 0;
    }

    public static double getTileNangCapDo(int level) {
        switch (level) {
            case 0:
                return 80;
            case 1:
                return 50;
            case 2:
                return 20;
            case 3:
                return 10;
            case 4:
                return 7;
            case 5:
                return 5;
            case 6:
                return 1;
            case 7: // 7 sao
                return 0.3;
            case 8:
                return 0.3;
            case 9:
                return 0.25;
            case 10:
                return 0.2;
            case 11:
                return 0.15;
            case 12:
                return 0.1;
        }
        return 0;
    }

    private static boolean rollSuccess(Player player, Item.ItemOption optionStar) {
        int ratio = 1;
        boolean successCondition = true;

        if (optionStar != null) {
            switch (optionStar.param) {
                case 6 ->
                    ratio *= 1;
                case 7 ->
                    ratio *= 1;
                case 8 ->
                    ratio *= 1;
                case 9 ->
                    ratio *= 1;
                case 10 ->
                    ratio *= 1;
                case 11 ->
                    ratio *= 1;
                case 12 ->
                    ratio *= 1;
            }
            if (optionStar.param > 10) {
                successCondition = Util.isTrue(1, 100000000);
            }
        }

        return Util.isTrue(player.combineNew.ratioCombine, 100 * ratio) && successCondition;
    }

}
