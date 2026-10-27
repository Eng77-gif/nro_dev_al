package nro.models.player;

import nro.models.radar.Card;
import nro.models.radar.OptionCard;
import nro.models.consts.ConstPlayer;
import nro.models.consts.ConstRatio;
import nro.models.intrinsic.Intrinsic;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.skill.Skill;
import nro.models.server.Manager;
import nro.models.services.EffectSkillService;
import nro.models.services.ItemService;
import nro.models.map.service.MapService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.Setter;
import nro.models.mob.Mob;
import nro.models.player_badges.BagesTemplate;
import static nro.models.player_badges.BagesTemplate.sendListItemOption;
import nro.models.utils.TimeUtil;

/**
 *
 * @author By Mr Blue
 *
 */
public class NPoint {

    public static final byte MAX_LIMIT = 9;

    @Setter
    private Player player;
    long timeXinbatoBuff;

    public NPoint(Player player) {
        this.player = player;
        this.tlHp = new ArrayList<>();
        this.tlMp = new ArrayList<>();
        this.tlDef = new ArrayList<>();
        this.tlDame = new ArrayList<>();
        this.tlDameAttMob = new ArrayList<>();
        this.tlTNSM = new ArrayList<>();
        this.tlDameCrit = new ArrayList<>();
    }

    public boolean isCrit;
    public boolean isCrit100;
    public boolean isCritTele;

    private Intrinsic intrinsic;
    private int percentDameIntrinsic;
    public int dameAfter;

    /*-----------------------Chỉ số cơ bản------------------------------------*/
    public byte numAttack;
    public short stamina, maxStamina;

    public byte limitPower;
    public long power;
    public long tiemNang;

    public int hp, hpMax, hpg;
    public int mp, mpMax, mpg;
    public int dame, dameg;
    public int def, defg;
    public int crit, critg, critdragon;
    public byte speed = 8;

    public boolean teleport;

    public boolean khangTDHS;

    /**
     * Chỉ số cộng thêm
     */
    public int hpAdd, mpAdd, dameAdd, defAdd, critAdd, hpHoiAdd, mpHoiAdd;

    /**
     * //+#% sức đánh chí mạng
     */
    public List<Integer> tlDameCrit;
    public int tlSDCM;

    /**
     * Tỉ lệ hp, mp cộng thêm
     */
    public List<Integer> tlHp, tlMp;

    /**
     * Tỉ lệ giáp cộng thêm
     */
    public List<Integer> tlDef;

    /**
     * Tỉ lệ sức đánh/ sức đánh khi đánh quái
     */
    public List<Integer> tlDame, tlDameAttMob;

    /**
     * Lượng hp, mp hồi mỗi 30s, mp hồi cho người khác
     */
    public int hpHoi, mpHoi, mpHoiCute;

    /**
     * Tỉ lệ hp, mp hồi cộng thêm
     */
    public short tlHpHoi, tlMpHoi;

    /**
     * Tỉ lệ hp, mp hồi bản thân và đồng đội cộng thêm
     */
    public short tlHpHoiBanThanVaDongDoi, tlMpHoiBanThanVaDongDoi;

    /**
     * Tỉ lệ hút hp, mp khi đánh, hp khi đánh quái
     */
    public short tlHutHp, tlHutMp, tlHutHpMob;

    /**
     * Tỉ lệ hút hp, mp xung quanh mỗi 5s
     */
    public short tlHutHpMpXQ;

    /**
     * Tỉ lệ phản sát thương
     */
    public short tlPST;

    /**
     * Tỉ lệ tiềm năng sức mạnh
     */
    public List<Integer> tlTNSM;

    /**
     * Tỉ lệ vàng cộng thêm
     */
    public short tlGold;

    /**
     * Tỉ lệ né đòn
     */
    public short tlNeDon;
    public short tlNeDonXinbato;

    public short tlBom;

    public short tlGiap;

    public short tlxgcc;

    public short tlxgc;

    public short tlchinhxac;

    public short tlTNSMPet;
    public short xChuong;

    public short setltdb;
    public short setTinhAn;
    public short setNhatAn;
    public short setNguyetAn;

    /**
     * Tỉ lệ sức đánh đẹp cộng thêm cho bản thân và người xung quanh
     */
    public int tlSexyDame;

    /**
     * Tỉ lệ giảm sức đánh
     */
    public short tlSubSD;

    public int voHieuChuong;

    /*------------------------Effect skin-------------------------------------*/
    public Item trainArmor;
    public boolean wearingTrainArmor;

    public boolean wearingVoHinh;
    public boolean isKhongLanh;
    public boolean islinhthuydanhbac;
    public boolean isTinhAn;
    public boolean isNhatAn;
    public boolean isNguyetAn;
    public boolean isTanHinh;
    public boolean isHoaDa;
    public boolean isLamCham;
    public boolean isDoSPL;
    public boolean isThoBulma;

    public short tlHpGiamODo;

    public int tlSTChuan; // option 10: % sát thương bỏ qua giáp/né/giảm sát thương
    public int tlDameBoss; // option 204: +% sát thương lên boss
    public int tlSTCuoi; // option 251: +% sát thương cuối
    public boolean isChongNLX; // option 252: chống năng lượng xấu (miễn hiệu ứng hôi/giảm HP xung quanh)
    public int tlTNSMDeTu; // option 253: +% TN, SM cho đệ tử khi sư phụ mặc
    public int tlAkazaRage; // option 254: % sức đánh khi HP dưới 50%
    public int tlAoThuat; // option 90: % sức đánh của Ảo Thuật
    public long rageUntil, lastTimeRage; // hiệu ứng Akaza
    public long aoThuatUntil, lastTimeAoThuat; // hiệu ứng Ảo Thuật
    private int setHpWithPet, setSTCuoiWithFlag, setCritWithFlag; // option 172, 39, 91
    private int setCritWithSkhBeerus, setSDCMWithXenHT, setGiapWithXenMax; // option 92, 169, 42
    private int setSDCMWithLuoiHaiHong; // option 255
    public int tlNeCrit; // option 191: né chí mạng
    public int tlGiamHoiKhien; // option 183: giảm % thời gian hồi Khiên năng lượng
    public int tlGiamKhongChe; // option 227: giảm % thời gian bị khống chế khi đang dùng khiên
    public int tlRareMob, tlRareBoss; // option 206 / 207: +#% vật phẩm hiếm rơi từ quái / boss
    public int giamSTTD, giamSTNM, giamSTXD; // option 198 / 199 / 200: giảm #% sát thương từ tộc Trái Đất / Namếc / Xayda
    public int kiHoiKhiBiDanh; // option 4: hồi #% KI khi bị đánh
    public int phanDonCanChien; // option 15: phản # sát thương khi bị đánh cận chiến
    public boolean isSocolaXq; // option 29 / 186: biến sôcôla (kẹo) người xung quanh mỗi 30 giây
    public int punchStackMax; // option 156: cộng dồn 1% SĐ mỗi đòn đấm liên tiếp, tối đa #%
    private int punchStack;
    public int giamSTKhiItKi; // option 157: giảm #% mọi sát thương khi KI dưới 20%
    private int flyMobCotNha, flyMobGheBay; // option 166 / 170: +#% SĐ lên quái khi bay cùng cải trang
    // hiệu ứng "ở gần cải trang khác loại" (145, 146, 150, 177, 179, 193, 196, 256), cập nhật mỗi 3 giây
    public int nearDame, nearHp, nearKi, nearSpeed;
    public int skhBeerus; // số món SKH Thần hủy diệt Beerus đang mặc (option 115)

    public boolean isGogeta;

    public int tlSpeed;

    public int levelBT;

    public int tlNeDonBuffXinbato = 0;

    /*-------------------------------------------------------------------------*/
    /**
     * Tính toán mọi chỉ số sau khi có thay đổi
     */
    public void calPoint() {
        if (this.player.pet != null) {
            this.player.pet.nPoint.setPointWhenWearClothes();
        }
        this.setPointWhenWearClothes();
    }

    public int getRealTlNeDon() {
        int total = this.tlNeDon;
        if (tlNeDonBuffXinbato > 0) {
            total += tlNeDonBuffXinbato;
        }
        return Math.min(total, 90);
    }

    private void setPointWhenWearClothes() {
        resetPoint();
        if (this.player.rewardBlackBall.timeOutOfDateReward[2] > System.currentTimeMillis()) {
            tlHutHp += RewardBlackBall.R3S_1;
        }
        if (this.player.rewardBlackBall.timeOutOfDateReward[3] > System.currentTimeMillis()) {
            tlPST += RewardBlackBall.R4S_2;
        }
        if (this.player.rewardBlackBall.timeOutOfDateReward[4] > System.currentTimeMillis()) {
            tlDameCrit.add(RewardBlackBall.R5S_1);
            tlSDCM += RewardBlackBall.R5S_1;
        }
        if (this.player.rewardBlackBall.timeOutOfDateReward[6] > System.currentTimeMillis()) {
            tlNeDon += RewardBlackBall.R7S_1;
        }
        // Lấy tất cả option danh hiệu
        List<Item.ItemOption> options = BagesTemplate.sendListItemOption(player);

        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 108) {
                tlNeDon += (tlNeDon * opt.param / 100L);
            }
        }
        // các option khác của danh hiệu (chí mạng, sát thương chuẩn...) trước đây bị bỏ qua
        for (Item.ItemOption opt : options) {
            switch (opt.optionTemplate.id) {
                case 50, 77, 103, 108, 93 -> {
                }
                default ->
                    addOption(opt);
            }
        }

        // thẻ radar: dòng "Mở khóa" (active 0) có tác dụng khi đã sưu tầm thẻ,
        // dòng "Sử dụng Lv.x" chỉ khi đang dùng thẻ ở đúng cấp x
        for (Card card : player.Cards) {
            if (card == null || card.Level == 0) {
                continue;
            }
            for (OptionCard io : card.Options) {
                if (io.active == 0 || (card.Used == 1 && io.active == card.Level)) {
                    switch (io.id) {
                        case 0: //Tấn công +#
                            this.dameAdd += io.param;
                            break;
                        case 2: //HP, KI+#000
                            this.hpAdd += io.param * 1000;
                            this.mpAdd += io.param * 1000;
                            break;
                        case 3:// vô hiệu chưởng
                            this.voHieuChuong += io.param;
                            break;
                        case 5: //+#% sức đánh chí mạng
                            this.tlDameCrit.add(io.param);
                            this.tlSDCM += io.param;
                            break;
                        case 6: //HP+#
                            this.hpAdd += io.param;
                            break;
                        case 7: //KI+#
                            this.mpAdd += io.param;
                            break;
                        case 8: //Hút #% HP, KI xung quanh mỗi 5 giây
                            this.tlHutHpMpXQ += io.param;
                            break;
                        case 14: //Chí mạng+#%
                            this.critAdd += io.param;
                            break;
                        case 16: // Speed
                        case 114:
                        case 148:
                            this.tlSpeed += io.param;
                            break;
                        case 18: //Chinh xac
                            this.tlchinhxac += io.param;
                            break;
                        case 19: //Tấn công+#% khi đánh quái
                            this.tlDameAttMob.add(io.param);
                            break;
                        case 22: //HP+#K
                            this.hpAdd += io.param * 1000;
                            break;
                        case 23: //MP+#K
                            this.mpAdd += io.param * 1000;
                            break;
                        case 27: //+# HP/30s
                            this.hpHoiAdd += io.param;
                            break;
                        case 28: //+# KI/30s
                            this.mpHoiAdd += io.param;
                            break;
                        case 33: //dịch chuyển tức thời
                            this.teleport = true;
                            break;
                        case 34:
                            this.setTinhAn += 1;
                            break;
                        case 35:
                            this.setNguyetAn += 1;
                            break;
                        case 36:
                            this.setNhatAn += 1;
                            break;
                        case 47: //Giáp+#
                            this.defAdd += io.param;
                            break;
                        case 48: //HP/KI+#
                            this.hpAdd += io.param;
                            this.mpAdd += io.param;
                            break;
                        case 49: //Tấn công+#%
                        case 50: //Sức đánh+#%
                            this.tlDame.add(io.param);
                            break;
                        case 77: //HP+#%
                            this.tlHp.add(io.param);
                            break;
                        case 80: //HP+#%/30s
                            this.tlHpHoi += io.param;
                            break;
                        case 81: //MP+#%/30s
                            this.tlMpHoi += io.param;
                            break;
                        case 88: //Cộng #% exp khi đánh quái
                            this.tlTNSM.add(io.param);
                            break;
                        case 94: //Giáp #%
                            this.tlGiap += io.param;
                            break;
                        case 95: //Biến #% tấn công thành HP
                            this.tlHutHp += io.param;
                            break;
                        case 96: //Biến #% tấn công thành MP
                            this.tlHutMp += io.param;
                            break;
                        case 97: //Phản #% sát thương
                            this.tlPST += io.param;
                            break;
                        case 98: //Xuyen giap chuong
                            this.tlxgc += io.param;
                            break;
                        case 99: //Xuyen giap can chien
                            this.tlxgcc += io.param;
                            break;
                        case 100: //+#% vàng từ quái
                            this.tlGold += io.param;
                            break;
                        case 101: //+#% TN,SM
                            this.tlTNSM.add(io.param);
                            break;
                        case 103: //KI +#%
                            this.tlMp.add(io.param);
                            break;
                        case 104: //Biến #% tấn công quái thành HP
                            this.tlHutHpMob += io.param;
                            break;
                        case 105: //Vô hình khi không đánh quái và boss
                            this.wearingVoHinh = true;
                            break;
                        case 106: //Không ảnh hưởng bởi cái lạnh
                            this.isKhongLanh = true;
                            break;
                        case 111:
                        case 108: //#% Né đòn
                            this.tlNeDon += io.param;
                            break;
                        case 109: //Hôi, giảm #% HP
                            this.tlHpGiamODo += io.param;
                            break;
                        case 116: //Kháng thái dương hạ san
                            this.khangTDHS = true;
                            break;
                        case 226:
                        case 117: //Đẹp +#% SĐ cho mình và người xung quanh
                            if (io.param > this.tlSexyDame) {
                                this.tlSexyDame = io.param;
                            }
                            break;
                        case 147: //+#% sức đánh
                            this.tlDame.add(io.param);
                            break;
                        case 155: //Giảm 50% sức đánh, HP, KI và +#% SM, TN, vàng từ quái (id theo item_option_template)
                            this.tlSubSD += 50;
                            this.tlTNSM.add(io.param);
                            this.tlGold += io.param;
                            break;
                        case 162: //Cute hồi #% KI/s bản thân và xung quanh
                            this.mpHoiCute += io.param;
                            break;
                        case 173: //Phục hồi #% HP và KI cho đồng đội
                            this.tlHpHoiBanThanVaDongDoi += io.param;
                            this.tlMpHoiBanThanVaDongDoi += io.param;
                            break;
                        case 211:
                            this.setltdb += 1;
                            break;
                        case 153: //% phát nổ sau khi chết
                            this.tlBom += io.param;
                            break;
                        default:
                            addOption(new ItemOption(io.id, io.param));
                            break;
                    }
                }
            }
        }

        // Bông tai cấp 2
        if (this.player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2) {
            this.player.inventory.itemsBag.stream()
                    .filter(it -> it.isNotNullItem() && it.template.id == 921)
                    .findFirst()
                    .ifPresent(btc2 -> {
                        for (ItemOption io : btc2.itemOptions) {
                            addOption(io);
                            if (io.optionTemplate.id == 72) {
                                this.levelBT = io.param;
                            }
                        }
                    });
        }
        if (this.player.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3) {
            this.player.inventory.itemsBag.stream()
                    .filter(it -> it.isNotNullItem() && it.template.id == 1884)
                    .findFirst()
                    .ifPresent(btc3 -> {
                        for (ItemOption io : btc3.itemOptions) {
                            addOption(io);
                            if (io.optionTemplate.id == 72) {
                                this.levelBT = io.param;
                            }
                        }
                    });
        }

        this.player.setClothes.worldcup = 0;
        for (Item item : this.player.inventory.itemsBody) {
            if (item.isNotNullItem()) {
                switch (item.template.id) {
                    case 966:
                    case 982:
                    case 983:
                    case 883:
                    case 904:
                        player.setClothes.worldcup++;
                }
                if (item.template.id >= 592 && item.template.id <= 594) {
                    teleport = true;
                }
                for (ItemOption io : item.itemOptions) {
                    if (io.optionTemplate.id == 115 && (this.player.inventory.itemsBody.indexOf(item) > 4)) {
                        continue; // set kích hoạt chỉ tính trên 5 món trang bị
                    }
                    addOption(io);
                }
            }
        }
        // ô bóng bước chân (lưu riêng ở server)
        if (!this.player.isPet && this.player.itemFoot != null && this.player.itemFoot.isNotNullItem()) {
            for (ItemOption io : this.player.itemFoot.itemOptions) {
                addOption(io);
            }
        }
        applySetBonus();
        setDameTrainArmor();
        setBasePoint();
        setOutfitFusion();
        setSpeed();
    }

    public static final int PET_KUMA = 2019;
    public static final int CO_VAN_HOA_DONG = 2030;
    public static final int CO_WORLD_CUP_2026 = 2031;

    private boolean isWearing(int templateId) {
        for (Item it : this.player.inventory.itemsBody) {
            if (it != null && it.isNotNullItem() && it.template.id == templateId) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hiệu ứng đi cùng: Naruto + Pet Kuma, Sasuke + Cờ Vạn Hoa Đồng, Ván bay WC 2026 + Cờ World Cup 2026.
     */
    private void applySetBonus() {
        if (setHpWithPet > 0 && isWearing(PET_KUMA)) {
            this.tlHp.add(setHpWithPet);
        }
        if (setSTCuoiWithFlag > 0 && isWearing(CO_VAN_HOA_DONG)) {
            this.tlSTCuoi += setSTCuoiWithFlag;
        }
        if (setCritWithFlag > 0 && isWearing(CO_WORLD_CUP_2026)) {
            this.critAdd += setCritWithFlag;
        }
        // SKH Thần hủy diệt Beerus: 2 món +10% SĐ +10% STCM, 4 món +15%/+20%, 5 món +30%/+30%
        int skhDame = skhBeerus >= 5 ? 30 : skhBeerus >= 4 ? 15 : skhBeerus >= 2 ? 10 : 0;
        int skhSDCM = skhBeerus >= 5 ? 30 : skhBeerus >= 4 ? 20 : skhBeerus >= 2 ? 10 : 0;
        if (skhDame > 0) {
            this.tlDame.add(skhDame);
            this.tlDameCrit.add(skhSDCM);
            this.tlSDCM += skhSDCM;
        }
        if (setCritWithSkhBeerus > 0 && skhBeerus >= 2) {
            this.critAdd += setCritWithSkhBeerus;
        }
        if (setSDCMWithXenHT > 0 && isWearing(PET_XEN_HOAN_THIEN)) {
            this.tlDameCrit.add(setSDCMWithXenHT);
            this.tlSDCM += setSDCMWithXenHT;
        }
        if (setGiapWithXenMax > 0 && isWearing(PET_XEN_MAX)) {
            this.tlGiap += setGiapWithXenMax;
        }
        if (nearDame > 0) {
            this.tlDame.add(nearDame);
        }
        if (nearHp > 0) {
            this.tlHp.add(nearHp);
        }
        if (nearKi > 0) {
            this.tlMp.add(nearKi);
        }
        this.tlSpeed += nearSpeed;
        if (setSDCMWithLuoiHaiHong > 0 && isWearing(LUOI_HAI_HONG)) {
            this.tlDameCrit.add(setSDCMWithLuoiHaiHong);
            this.tlSDCM += setSDCMWithLuoiHaiHong;
        }
    }

    public static final int LUOI_HAI_HONG = 1702;

    /** Thời gian bị khống chế sau khi trừ option 227 (chỉ khi đang bật khiên năng lượng). */
    public int controlTime(int ms) {
        if (tlGiamKhongChe > 0 && player.effectSkill != null && player.effectSkill.isShielding) {
            return ms * (100 - Math.min(90, tlGiamKhongChe)) / 100;
        }
        return ms;
    }
    /** Bộ Lý Tiểu Nương: hầu gái xanh, hầu gái hồng, bikini */
    public static final int[] LTN_HAU_GAI = {1503, 1504, 1512};

    private static int costumeId(Player p) {
        Item ct = p.inventory != null && p.inventory.itemsBody.size() > 5 ? p.inventory.itemsBody.get(5) : null;
        return ct != null && ct.isNotNullItem() ? ct.template.id : -1;
    }

    // các nhóm cải trang "khác loại" (mỗi mảng con là 1 loại, gồm các bản biến thể)
    private static final int[][] DR_SLUM = {{612, 914, 1982}, {613}, {614}};
    private static final int[][] POC_PIC_KONG = {{583, 913, 1235, 1886, 1952, 2086}, {633, 1234, 1396}, {634, 1236}};
    private static final int[] MABU_MAP = {937, 974, 1601};
    private static final int[] DEMONS_FROST = {629, 630, 631, 632, 878, 879, 1205, 1599, 1839, 1913};
    private static final int[][] CAU_THU = {{968}, {969}, {970}, {971}, {972}, {973}, {974}, {975}, {976}, {977}, {978}};
    private static final int[][] THO = {{1041}, {1042}, {1043}};
    private static final int[][] HAU_GAI = {{1503}, {1504}, {1512}};
    private static final int CT_TAU_PAY_PAY = 284, COT_NHA = 744, GHE_BAY = 795;
    private static final int[] NHA_FIDE = {629, 630, 631, 632, 878, 879, 1599, 1839, 1913};

    private static boolean in(int[] arr, int v) {
        for (int x : arr) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    private static int kindOf(int[][] groups, int ct) {
        for (int i = 0; i < groups.length; i++) {
            if (in(groups[i], ct)) {
                return i;
            }
        }
        return -1;
    }

    private int optParam(Item it, int opt) {
        if (it != null && it.isNotNullItem()) {
            for (ItemOption io : it.itemOptions) {
                if (io.optionTemplate.id == opt) {
                    return Math.max(1, io.param);
                }
            }
        }
        return 0;
    }

    /** Số loại khác loại của mình (theo nhóm) đang đứng gần trong 300px. */
    private int otherKindsNear(int[][] groups, java.util.List<Player> near) {
        int mine = kindOf(groups, costumeId(player));
        java.util.Set<Integer> kinds = new java.util.HashSet<>();
        for (Player o : near) {
            int k = kindOf(groups, costumeId(o));
            if (k >= 0 && k != mine) {
                kinds.add(k);
            }
        }
        return kinds.size();
    }

    /**
     * Cập nhật hiệu ứng "ở gần cải trang khác loại" (gọi định kỳ từ EffectSkin). Trả về true nếu chỉ số thay đổi.
     */
    public boolean updateNearSets() {
        int d = 0, h = 0, k = 0, sp = 0;
        Item ct = player.inventory != null && player.inventory.itemsBody.size() > 5 ? player.inventory.itemsBody.get(5) : null;
        if (ct != null && ct.isNotNullItem() && player.zone != null && !player.isPet) {
            java.util.List<Player> near = new java.util.ArrayList<>();
            for (Player o : player.zone.getNotBosses()) {
                if (o != player && !o.isPet && !o.isNewPet && !o.isDie() && Util.getDistance(player, o) <= 300) {
                    near.add(o);
                }
            }
            if (optParam(ct, 145) > 0 || optParam(ct, 146) > 0) { // Dr Slum: gần 1 loại +20% SĐ +66% tốc độ, gần 2 loại +30% +100%
                int n = otherKindsNear(DR_SLUM, near);
                d += n >= 2 ? 30 : n == 1 ? 20 : 0;
                sp += n >= 2 ? 100 : n == 1 ? 66 : 0;
            }
            if (optParam(ct, 150) > 0) { // Póc - Píc - King Kong: +10% SĐ, HP, KI mỗi loại khác ở gần
                int n = otherKindsNear(POC_PIC_KONG, near);
                d += 10 * n; h += 10 * n; k += 10 * n;
            }
            if (optParam(ct, 177) > 0) { // Gohan Bư: +2% tất cả khi ở gần Mabư mập
                for (Player o : near) {
                    if (in(MABU_MAP, costumeId(o))) {
                        d += 2; h += 2; k += 2;
                        break;
                    }
                }
            }
            if (optParam(ct, 179) > 0) { // tộc Demons Frost: +2% SĐ mỗi người, tối đa 10%
                int n = 0;
                for (Player o : near) {
                    if (in(DEMONS_FROST, costumeId(o))) {
                        n++;
                    }
                }
                d += Math.min(10, 2 * n);
            }
            int p193 = optParam(ct, 193);
            if (p193 > 0) {
                d += Math.min(11, p193 * otherKindsNear(CAU_THU, near));
            }
            int p196 = optParam(ct, 196);
            if (p196 > 0) {
                d += Math.min(18, p196 * otherKindsNear(THO, near));
            }
            int p256 = optParam(ct, 256);
            if (p256 > 0) {
                d += Math.min(12, p256 * otherKindsNear(HAU_GAI, near));
            }
        }
        if (d == nearDame && h == nearHp && k == nearKi && sp == nearSpeed) {
            return false;
        }
        nearDame = d; nearHp = h; nearKi = k; nearSpeed = sp;
        return true;
    }

    /** +% SĐ lên quái khi bay cùng cải trang (option 166 / 170): đang dùng ván bay đó và mặc đúng cải trang. */
    private int flyMobBonus() {
        int b = 0;
        int ct = costumeId(player);
        if (flyMobCotNha > 0 && ct == CT_TAU_PAY_PAY && isWearing(COT_NHA)) {
            b += flyMobCotNha;
        }
        if (flyMobGheBay > 0 && in(NHA_FIDE, ct) && isWearing(GHE_BAY)) {
            b += flyMobGheBay;
        }
        return b;
    }

    public static final int PET_XEN_HOAN_THIEN = 2036;
    public static final int PET_XEN_MAX = 2038;

    /**
     * Gọi khi bị đánh: kích hoạt Akaza (HP dưới 50%, hồi 180s) và Ảo Thuật (hồi 120s), mỗi hiệu ứng kéo dài 3 giây.
     */
    public void onInjured() {
        long now = System.currentTimeMillis();
        if (tlAkazaRage > 0 && this.hp < this.hpMax / 2 && now - lastTimeRage >= 180_000) {
            lastTimeRage = now;
            rageUntil = now + 3000;
            Service.gI().sendThongBao(this.player, "Nộ Akaza: +" + tlAkazaRage + "% sức đánh trong 3 giây");
        }
        if (tlAoThuat > 0 && now - lastTimeAoThuat >= 120_000) {
            lastTimeAoThuat = now;
            aoThuatUntil = now + 3000;
            Service.gI().sendThongBao(this.player, "Ảo Thuật: +" + tlAoThuat + "% sức đánh, giảm 50% sát thương nhận vào trong 3 giây");
        }
    }

    public boolean isAoThuat() {
        return System.currentTimeMillis() < aoThuatUntil;
    }

    private void addOption(ItemOption io) {
        switch (io.optionTemplate.id) {
            case 0: //Tấn công +#
                this.dameAdd += io.param;
                break;
            case 10: //Sát thương chuẩn #%
                this.tlSTChuan += io.param;
                break;
            case 204: //Tấn công+#% lên Boss
                this.tlDameBoss += io.param;
                break;
            case 251: //Sát thương cuối +#%
                this.tlSTCuoi += io.param;
                break;
            case 252: //Chống năng lượng xấu
                this.isChongNLX = true;
                break;
            case 253: //+#% TN, SM cho đệ tử khi sư phụ mặc
                this.tlTNSMDeTu += io.param;
                break;
            case 254: //Khi HP dưới 50% tăng #% sức đánh trong 3 giây
                this.tlAkazaRage = Math.max(this.tlAkazaRage, io.param);
                break;
            case 172: //Đi cùng Pet Cửu Vĩ Hồ tăng #% HP
                this.setHpWithPet += io.param;
                break;
            case 39: //Đi cùng Cờ Vạn Hoa Đồng tăng #% sát thương cuối
                this.setSTCuoiWithFlag += io.param;
                break;
            case 90: //Ảo Thuật
                this.tlAoThuat = Math.max(this.tlAoThuat, io.param);
                break;
            case 91: //Đi cùng Cờ World Cup 2026 tăng #% chí mạng
                this.setCritWithFlag += io.param;
                break;
            case 92: //Đi cùng SKH Beerus tăng #% chí mạng
                this.setCritWithSkhBeerus += io.param;
                break;
            case 115: //Set Thần hủy diệt Beerus
                this.skhBeerus++;
                break;
            case 169: //Đi cùng Pet Xên hoàn thiện tăng #% sát thương chí mạng
                this.setSDCMWithXenHT += io.param;
                break;
            case 206: //Vật phẩm hiếm rơi từ quái (+#%)
                this.tlRareMob += io.param;
                break;
            case 207: //Vật phẩm hiếm rơi từ boss (+#%)
                this.tlRareBoss += io.param;
                break;
            case 198: //Giảm #% sát thương từ tộc Trái Đất
                this.giamSTTD += io.param;
                break;
            case 199: //Giảm #% sát thương từ tộc Namếc
                this.giamSTNM += io.param;
                break;
            case 200: //Giảm #% sát thương từ tộc Xayda
                this.giamSTXD += io.param;
                break;
            case 4: //Hồi phục #% KI khi bị đánh
                this.kiHoiKhiBiDanh += Math.min(100, io.param);
                break;
            case 15: //Phản đòn cận chiến+#
                this.phanDonCanChien += io.param;
                break;
            case 29: //Biến Sôcôla mọi người xung quanh mỗi 30 giây
            case 186: //Biến kẹo 30 giây
                this.isSocolaXq = true;
                break;
            case 156: //Cộng dồn 1% sức đánh khi chỉ dùng chiêu đấm, tối đa #%
                this.punchStackMax = Math.max(this.punchStackMax, io.param);
                break;
            case 157: //Giảm #% mọi sát thương khi KI dưới 20%
                this.giamSTKhiItKi += io.param;
                break;
            case 166: //Ngầu +#% sức đánh lên quái khi bay với Cải Trang Tàu Pảy Pảy
                this.flyMobCotNha += io.param;
                break;
            case 170: //Ngầu +#% sức đánh lên quái khi bay với Cải Trang nhà Fide
                this.flyMobGheBay += io.param;
                break;
            case 191: //Né chí mạng+#%
                this.tlNeCrit += io.param;
                break;
            case 183: //Giảm #% thời gian hồi Khiên
                this.tlGiamHoiKhien += io.param;
                break;
            case 227: //Giảm #% tác dụng của các đòn khống chế khi dùng khiên năng lượng
                this.tlGiamKhongChe += io.param;
                break;
            case 255: //Đi cùng Lưỡi hái hồng tăng #% sát thương chí mạng
                this.setSDCMWithLuoiHaiHong += io.param;
                break;
            case 256: //+#% sức đánh, tối đa 12% khi ở gần Lý Tiểu Nương : Hầu Gái xanh, hồng, bikini (tính trong updateNearSets)
                break;
            case 42: //Đi cùng Pet Xên Max tăng #% giảm sát thương
                this.setGiapWithXenMax += io.param;
                break;
            case 2: //HP, KI+#000
                this.hpAdd += io.param * 1000;
                this.mpAdd += io.param * 1000;
                break;
            case 3:// vô hiệu chưởng
                this.voHieuChuong += io.param;
                break;
            case 5: //+#% sức đánh chí mạng
                this.tlDameCrit.add(io.param);
                this.tlSDCM += io.param;
                break;
            case 6: //HP+#
                this.hpAdd += io.param;
                break;
            case 7: //KI+#
                this.mpAdd += io.param;
                break;
            case 8: //Hút #% HP, KI xung quanh mỗi 5 giây
                this.tlHutHpMpXQ += io.param;
                break;
            case 14: //Chí mạng+#%
                this.critAdd += io.param;
                break;
            case 16: // Speed
            case 114:
            case 148:
                this.tlSpeed += io.param;
                break;
            case 18: //Chinh xac
                this.tlchinhxac += io.param;
                break;
            case 19: //Tấn công+#% khi đánh quái
                this.tlDameAttMob.add(io.param);
                break;
            case 22: //HP+#K
                this.hpAdd += io.param * 1000;
                break;
            case 23: //MP+#K
                this.mpAdd += io.param * 1000;
                break;
            case 24: //Làm chậm
                this.isLamCham = true;
                break;
            case 25: //Tàn hình
                this.isTanHinh = true;
                break;
            case 26: //Hóa đá
                this.isHoaDa = true;
                break;
            case 27: //+# HP/30s
                this.hpHoiAdd += io.param;
                break;
            case 28: //+# KI/30s
                this.mpHoiAdd += io.param;
                break;
            case 33: //dịch chuyển tức thời
                this.teleport = true;
                break;
            case 34:
                this.setTinhAn += 1;
                break;
            case 35:
                this.setNguyetAn += 1;
                break;
            case 36:
                this.setNhatAn += 1;
                break;
            case 47: //Giáp+#
                this.defAdd += io.param;
                break;
            case 48: //HP/KI+#
                this.hpAdd += io.param;
                this.mpAdd += io.param;
                break;
            case 49: //Tấn công+#%
            case 50: //Sức đánh+#%
                this.tlDame.add(io.param);
                break;
            case 77: //HP+#%
                this.tlHp.add(io.param);
                break;
            case 80: //HP+#%/30s
                this.tlHpHoi += io.param;
                break;
            case 81: //MP+#%/30s
                this.tlMpHoi += io.param;
                break;
            case 88: //Cộng #% exp khi đánh quái
                this.tlTNSM.add(io.param);
                break;
            case 94: //Giáp #%
                this.tlGiap += io.param;
                break;
            case 95: //Biến #% tấn công thành HP
                this.tlHutHp += io.param;
                break;
            case 96: //Biến #% tấn công thành MP
                this.tlHutMp += io.param;
                break;
            case 97: //Phản #% sát thương
                this.tlPST += io.param;
                break;
            case 98: //Xuyen giap chuong
                this.tlxgc += io.param;
                break;
            case 99: //Xuyen giap can chien
                this.tlxgcc += io.param;
                break;
            case 100: //+#% vàng từ quái
                this.tlGold += io.param;
                break;
            case 101: //+#% TN,SM
                this.tlTNSM.add(io.param);
                break;
            case 103: //KI +#%
                this.tlMp.add(io.param);
                break;
            case 104: //Biến #% tấn công quái thành HP
                this.tlHutHpMob += io.param;
                break;
            case 105: //Vô hình khi không đánh quái và boss
                this.wearingVoHinh = true;
                break;
            case 106: //Không ảnh hưởng bởi cái lạnh
                this.isKhongLanh = true;
                break;
            case 111:
            case 108: //#% Né đòn
                this.tlNeDon += io.param;
                break;
            case 109: //Hôi, giảm #% HP
                this.tlHpGiamODo += io.param;
                break;
            case 110: //Do spl
                this.isDoSPL = true;
                break;
            case 116: //Kháng thái dương hạ san
                this.khangTDHS = true;
                break;
            case 226:
            case 117: //Đẹp +#% SĐ cho mình và người xung quanh
                if (io.param > this.tlSexyDame) {
                    this.tlSexyDame = io.param;
                }
                break;
            case 147: //+#% sức đánh
                this.tlDame.add(io.param);
                break;
            case 155: //Giảm 50% sức đánh, HP, KI và +#% SM, TN, vàng từ quái (id theo item_option_template)
                this.tlSubSD += 50;
                this.tlTNSM.add(io.param);
                this.tlGold += io.param;
                break;
            case 162: //Cute hồi #% KI/s bản thân và xung quanh
                this.mpHoiCute += io.param;
                break;
            case 159: // x chưởng
                this.xChuong = (short) io.param;
                break;
            case 160: // TNSM PET;
                this.tlTNSMPet += io.param;
                break;
            case 173: //Phục hồi #% HP và KI cho đồng đội
                this.tlHpHoiBanThanVaDongDoi += io.param;
                this.tlMpHoiBanThanVaDongDoi += io.param;
                break;
            case 211:
                this.setltdb += 1;
                break;
            case 153: //% phát nổ sau khi chết
                this.tlBom += io.param;
                break;
        }
    }

    private void setSpeed() {
        if (player.isPl()) {
            speed = (byte) Math.min(127, 8 + 8 * tlSpeed / 100); // trước đây chia nguyên trước nên +% dưới 100 không có tác dụng
        }
    }

    private void setOutfitFusion() {
        if (this.player.inventory.itemsBody.size() < 6 || this.player.pet == null || this.player.pet.inventory.itemsBody.size() < 6) {
            return;
        }
        Item skin = this.player.inventory.itemsBody.get(5);
        Item pskin = this.player.pet.inventory.itemsBody.get(5);
        if (skin.isNotNullItem() && pskin.isNotNullItem()) {
            this.isGogeta = skin.template.id == 2133 && pskin.template.id == 2134 || skin.template.id == 2134 && pskin.template.id == 2133;
        } else {
            this.isGogeta = false;
        }
    }

    private void setDameTrainArmor() {
        if (!this.player.isPet && !this.player.isBot && !this.player.isBoss) {
            if (this.player.inventory.itemsBody.size() < 7) {
                return;
            }
            try {
                Item gtl = this.player.inventory.itemsBody.get(6);
                if (gtl.isNotNullItem()) {
                    this.wearingTrainArmor = true;
                    this.player.inventory.trainArmor = gtl;
                    this.tlSubSD += ItemService.gI().getPercentTrainArmor(gtl);
                } else {
                    if (this.player.inventory.trainArmor == null) {
                        gtl = this.player.inventory.itemsBag.stream().filter(item -> item.isNotNullItem() && item.template.type == 32 && item.itemOptions != null
                                && item.itemOptions.stream().filter(io -> io.optionTemplate.id == 9 && io.param > 0).findFirst().orElse(null) != null).findFirst().orElse(null);
                        if (gtl == null) {
                            return;
                        }
                        this.player.inventory.trainArmor = gtl;
                    }
                    this.wearingTrainArmor = false;
                    for (Item.ItemOption io : this.player.inventory.trainArmor.itemOptions) {
                        if (io.optionTemplate.id == 9 && io.param > 0) {
                            this.tlDame.add(ItemService.gI().getPercentTrainArmor(this.player.inventory.trainArmor));
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Logger.error("Lỗi get giáp tập luyện " + this.player.name + "\n" + e + "\n");
            }
        }
    }

    public void setBasePoint() {
        setHpMax();
        setHp();
        setMpMax();
        setMp();
        setDame();
        setDef();
        setCrit();
        setHpHoi();
        setMpHoi();
        setLtdb();
        setThoBulma();
        setTinhNhatNguyetAn();
    }

    private void setLtdb() {
        this.islinhthuydanhbac = this.setltdb >= 5;
    }

    private void setThoBulma() {
        this.isThoBulma = (this.player.inventory != null && this.player.inventory.itemsBody != null
                && this.player.inventory.itemsBody.size() >= 5 && this.player.inventory.itemsBody.get(5).isNotNullItem()
                && this.player.inventory.itemsBody.get(5).template.id == 584);
    }

    private void setTinhNhatNguyetAn() {
        this.isTinhAn = this.setTinhAn >= 5;
        this.isNhatAn = this.setNhatAn >= 5;
        this.isNguyetAn = this.setNguyetAn >= 5;
    }

    private void setHpHoi() {
        this.hpHoi = this.hpMax / 100;
        this.hpHoi += this.hpHoiAdd;

        if (this.tlHpHoi > 100) {
            this.tlHpHoi = 100;
        } else if (this.tlHpHoi < 0) {
            this.tlHpHoi = 0;
        }

        this.hpHoi += ((long) this.hpMax * this.tlHpHoi / 100);

        if (this.tlHpHoiBanThanVaDongDoi > 100) {
            this.tlHpHoiBanThanVaDongDoi = 100;
        } else if (this.tlHpHoiBanThanVaDongDoi < 0) {
            this.tlHpHoiBanThanVaDongDoi = 0;
        }

        this.hpHoi += ((long) this.hpMax * this.tlHpHoiBanThanVaDongDoi / 100);
    }

    private void setMpHoi() {
        this.mpHoi = this.mpMax / 100;
        this.mpHoi += this.mpHoiAdd;

        if (this.tlMpHoi > 100) {
            this.tlMpHoi = 100;
        } else if (this.tlMpHoi < 0) {
            this.tlMpHoi = 0;
        }

        this.mpHoi += ((long) this.mpMax * this.tlMpHoi / 100);

        if (this.tlMpHoiBanThanVaDongDoi > 100) {
            this.tlMpHoiBanThanVaDongDoi = 100;
        } else if (this.tlMpHoiBanThanVaDongDoi < 0) {
            this.tlMpHoiBanThanVaDongDoi = 0;
        }

        this.mpHoi += (((long) this.mpMax * this.tlMpHoiBanThanVaDongDoi) / 100);
    }

    private void setHpMax() {
        long hpMax = this.hpg + this.hpAdd;

        if (this.tlHp != null) {
            for (Integer tl : new ArrayList<>(this.tlHp)) {
                if (tl != null) {
                    hpMax += (hpMax * tl / 100L);
                }
            }
        }

        // Xử lý set nappa
        if (this.player.setClothes.nappa == 5) {
            hpMax += (hpMax * 80L / 100L);
        }

        if (this.player.setClothes.cadicM >= 2) {
            hpMax += (hpMax * 20L / 100L);

        }
        // Xử lý set worldcup
        if (this.player.setClothes.worldcup == 2) {
            hpMax += (hpMax * 10 / 100L);
        }

        // Xử lý rồng xương
        if (player.itemTime != null && player.itemTime.isUseRX) {
            hpMax += (hpMax * 10L / 100L);
        }

        // Xử lý set nhật ấn
        if (this.isNhatAn) {
            hpMax += (hpMax * 15L / 100L);
        }

        // Xử lý ngọc rồng đen 2 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[1] > System.currentTimeMillis()) {
            hpMax += (hpMax * RewardBlackBall.R2S_1 / 100L);
        }

        // Xử lý khỉ
        if (this.player.effectSkill.isMonkey) {
            if (!this.player.isPet || (this.player.isPet && ((Pet) this.player).status != Pet.FUSION)) {
                int percent = SkillUtil.getPercentHpMonkey(player.effectSkill.levelMonkey);
                hpMax += (hpMax * percent / 100L);
            }
        }
        // Xử lý pet mabư
        if (this.player.isPet && ((Pet) this.player).typePet == 1 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            hpMax += (hpMax * 0 / 100L);
        }

        // Xử lý pet Uub
        if (this.player.isPet && ((Pet) this.player).typePet == 2 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            hpMax += (hpMax * 20 / 100L);
        }
        // Xử lý pet vageta
        if (this.player.isPet && ((Pet) this.player).typePet == 3 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            hpMax += (hpMax * 20 / 100L);
        }
        // Xử lý pet jiren
        if (this.player.isPet && ((Pet) this.player).typePet == 4 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            hpMax += (hpMax * 20 / 100L);
        }

        // Xử lý phù
        if (this.player.zone != null && MapService.gI().isMapBlackBallWar(this.player.zone.map.mapId)) {
            hpMax *= this.player.effectSkin.xHPKI;
        }

        // Xử lý thức ăn 2
        if (this.player.itemTime != null && this.player.itemTime.isEatMeal2 && this.player.itemTime.iconMeal2 == 8062) {
            hpMax += (hpMax * 5 / 100L);
        }

        // Xử lý gogeta
        if (this.isGogeta) {
            hpMax += (hpMax * 10 / 100L);
        }

        // Phù map mabu
        if (this.player.isPhuHoMapMabu) {
            hpMax += 1_000_000;
        }

        // Xử lý +hp đệ
        if (this.player.fusion.typeFusion != ConstPlayer.NON_FUSION) {
            hpMax += this.player.pet.nPoint.hpMax;
        }
        // điều ước Rồng Xương: +15% HP khi hợp thể bông tai Porata
        if (this.player.hlwHpUntil > System.currentTimeMillis() && nro.models.services.shenron.RongXuong.isPorata(this.player)) {
            hpMax += hpMax * 15 / 100;
        }

        // Xử lý bổ huyết
        if (this.player.itemTime != null && this.player.itemTime.isUseBoHuyet && !this.player.itemTime.isUseBoHuyet2) {
            hpMax *= 2;
        }

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia3) {
            hpMax += hpMax / 10;  // Tăng 10%
        }

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia2) {
            hpMax += hpMax / 10;  // Tăng 10%
        }

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia1) {
            hpMax += hpMax / 10;  // Tăng 10%
        }

        // Xử lý item sieu cap
        if (this.player.itemTime != null && this.player.itemTime.isUseBoHuyet2) {
            hpMax *= 2.2;
        }

        // Xử lý huýt sáo
        if (!this.player.isPet || (this.player.isPet && ((Pet) this.player).status != Pet.FUSION)) {
            if (this.player.effectSkill.tiLeHPHuytSao != 0) {
                hpMax += (hpMax * this.player.effectSkill.tiLeHPHuytSao / 100L);
            }
        }

        // Xử lý chibi
        if (this.player.effectSkill != null && this.player.effectSkill.isChibi && this.player.typeChibi == 3) {
            hpMax *= 2;
        }

        // Xử lý map lạnh
        if (this.player.zone != null && MapService.gI().isMapCold(this.player.zone.map) && !this.isKhongLanh) {
            hpMax /= 2;
        }

        // Lấy tất cả option danh hiệu
        List<Item.ItemOption> options = BagesTemplate.sendListItemOption(player);

        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 77) {
                hpMax += (hpMax * opt.param / 100L);
            }
        }

        if (hpMax > 2_147_483_647) {
            hpMax = 2_147_483_647;
        }

        this.hpMax = (int) hpMax;
    }

    private void setHp() {
        this.hp = Math.min(this.hp, this.hpMax);
    }

    private void setMpMax() {
        // Tính toán giới hạn mpMax
        long mpMax = this.mpg + this.mpAdd;

        if (this.tlMp != null) {
            for (Integer tl : new ArrayList<>(this.tlMp)) {
                if (tl != null) {
                    mpMax += (mpMax * tl / 100L);
                }
            }
        }
        if (this.isNguyetAn) {
            mpMax += (mpMax * 15L / 100L);
        }

        // Xử lý ngọc rồng đen 6 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[5] > System.currentTimeMillis()) {
            mpMax += (mpMax * RewardBlackBall.R6S_1 / 100L);
        }

        // Xử lý set worldcup
        if (this.player.setClothes.worldcup
                == 2) {
            mpMax += (this.mpMax * 10 / 100L);
            // xử lý pet mabu
            if (this.player.isPet && ((Pet) this.player).typePet == 1
                    && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
                mpMax += (this.mpMax * 0 / 100L);
            }
        }

        // Xử lý pet Uub
        if (this.player.isPet && ((Pet) this.player).typePet == 2
                && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            mpMax += (this.mpMax * 20 / 100L);
        }
        // xử lý pet beer
        if (this.player.isPet && ((Pet) this.player).typePet == 3
                && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            mpMax += (this.mpMax * 20 / 100L);
        }
        // Xử lý pet jiren
        if (this.player.isPet && ((Pet) this.player).typePet == 4
                && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            mpMax += (this.mpMax * 20 / 100L);
        }

        // Xử lý phù
        if (this.player.zone
                != null && MapService.gI()
                        .isMapBlackBallWar(this.player.zone.map.mapId)) {
            mpMax *= this.player.effectSkin.xHPKI;
        }

        // Xử lý gogeta
        if (this.isGogeta) {
            mpMax += (mpMax * 10 / 100L);
        }

        // Phù map mabu
        if (this.player.isPhuHoMapMabu) {
            mpMax += 1_000_000;
        }

        // Xử lý rồng xương
        if (player.itemTime != null && player.itemTime.isUseRX) {
            mpMax += (mpMax * 10L / 100L);
        }

        // Xử lý hợp thể
        if (this.player.fusion.typeFusion
                != 0) {
            mpMax += this.player.pet.nPoint.mpMax;
        }
        // điều ước Rồng Xương: +15% KI khi hợp thể bông tai Porata
        if (this.player.hlwKiUntil > System.currentTimeMillis() && nro.models.services.shenron.RongXuong.isPorata(this.player)) {
            mpMax += mpMax * 15 / 100;
        }

        // Xử lý bổ khí
        if (this.player.itemTime != null && this.player.itemTime.isUseBoKhi && !this.player.itemTime.isUseBoKhi2) {
            mpMax *= 2;
        }

        // Xử lý item sieu cap
        if (this.player.itemTime != null && this.player.itemTime.isUseBoKhi2) {
            mpMax *= 2.2;
        }

        // Lấy tất cả option danh hiệu
        List<Item.ItemOption> options = BagesTemplate.sendListItemOption(player);

        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 103) {
                mpMax += (mpMax * opt.param / 100L);
            }
        }

        if (mpMax
                > 2_147_483_647) {
            mpMax = 2_147_483_647;
        }

        this.mpMax = (int) mpMax;
    }

    private void setMp() {
        this.mp = Math.min(this.mp, this.mpMax);
    }

    public int getHP() {
        return Math.min(this.hp, this.hpMax);
    }

    public void setHP(long hp) {
        if (hp > 0) {
            this.hp = (int) (hp <= this.hpMax ? hp : this.hpMax);
        } else {
            player.setDie();
        }
    }

    public int getMP() {
        return Math.min(this.mp, this.mpMax);
    }

    public void setMP(long mp) {
        if (mp > 0) {
            this.mp = (int) (mp <= this.mpMax ? mp : this.mpMax);
        } else {
            this.mp = 0;
        }
    }

    private void setDame() {
        long dame = this.dameg + this.dameAdd;

        if (this.tlDame != null) {
            for (Integer tl : new ArrayList<>(this.tlDame)) {
                if (tl != null) {
                    dame += (dame * tl / 100L);
                }
            }
        }
        // Xử lý pet pic
        if (this.player.isPet && ((Pet) this.player).typePet == 3 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            dame += (dame * 20 / 100L);
        }

        // Xử lý pet mabư
        if (this.player.isPet && ((Pet) this.player).typePet == 1 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            dame += (dame * 0 / 100L);
        }

        // Xử lý pet Uub
        if (this.player.isPet && ((Pet) this.player).typePet == 2 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            dame += (dame * 20 / 100L);
        }
        // Xử lý pet beer
        if (this.player.isPet && ((Pet) this.player).typePet == 3 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            dame += (dame * 20 / 100L);
        }
        // Xử lý pet jiren
        if (this.player.isPet && ((Pet) this.player).typePet == 4 && (((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA2 || ((Pet) this.player).master.fusion.typeFusion == ConstPlayer.HOP_THE_PORATA3)) {
            dame += (dame * 20 / 100L);
        }

        // Xử lý set tinh ấn
        if (this.isTinhAn) {
            dame += (dame * 15L / 100L);
        }

        // Xử lý thức ăn
        if (!this.player.isPet && this.player.itemTime != null && this.player.itemTime.isEatMeal || this.player.isPet && this.player.itemTime != null && ((Pet) this.player).master.itemTime.isEatMeal) {
            dame += (dame * 10 / 100L);
        }

        // Xử lý thức ăn 2
        if (this.player.itemTime != null && this.player.itemTime.isEatMeal2 && this.player.itemTime.iconMeal2 == 8060) {
            dame += (dame * 5 / 100L);
        }

        if (this.player.setClothes.nail >= 2) {
            this.tlDameCrit.add(10);
        }
        // Xử lý thức ăn 2
        if (this.player.itemTime != null && this.player.itemTime.isEatMeal2 && this.player.itemTime.iconMeal2 == 8061) {
            this.tlDameCrit.add(5);
            this.tlSDCM += 5;
        }

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia3) {
            this.tlDameCrit.add(10);
            this.tlSDCM += 10;
        }

        // Xử lý cuồng nộ
        if (this.player.itemTime != null && this.player.itemTime.isUseCuongNo && !this.player.itemTime.isUseCuongNo2) {
            dame *= 2;
        }
        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia3) {
            dame += dame / 10; // tăng 10%
        }
        if (this.player.itemTime != null && this.player.itemTime.isUseCuongNo2) {
            dame *= 2.2;
        }

        // Xử lý ngọc rồng đen 1 sao
        if (this.player.rewardBlackBall.timeOutOfDateReward[0] > System.currentTimeMillis()) {
            dame += (dame * RewardBlackBall.R1S_2 / 100L);
        }

        // Xử lý set worldcup
        if (this.player.setClothes.worldcup == 2) {
            dame += (dame * 10 / 100L);
        }

        // Xử lý gogeta
        if (this.isGogeta) {
            dame += (dame * 10 / 100L);
        }

        // Phù map mabu
        if (this.player.isPhuHoMapMabu) {
            dame += 10_000;
        }

        // Xử lý rồng xương
        if (player.itemTime != null && player.itemTime.isUseRX) {
            dame += (dame * 10L / 100L);
        }

        // Xử lý phù
        if (this.player.zone != null && MapService.gI().isMapBlackBallWar(this.player.zone.map.mapId)) {
            dame *= this.player.effectSkin.xDame;
        }

        // Xử lý hợp thể
        if (this.player.fusion.typeFusion != 0) {
            dame += this.player.pet.nPoint.dame;
        }
        // điều ước Rồng Xương: +15% sức đánh khi hợp thể bông tai Porata
        if (this.player.hlwDameUntil > System.currentTimeMillis() && nro.models.services.shenron.RongXuong.isPorata(this.player)) {
            dame += dame * 15 / 100;
        }

        // Lấy tất cả option danh hiệu
        List<Item.ItemOption> options = BagesTemplate.sendListItemOption(player);

        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 50) {
                dame += (dame * opt.param / 100L);
            }
        }

        // Xử lý khỉ
        if (this.player.effectSkill.isMonkey) {
            if (!this.player.isPet || (this.player.isPet && ((Pet) this.player).status != Pet.FUSION)) {
                int percent = SkillUtil.getPercentDameMonkey(player.effectSkill.levelMonkey);
                dame += (dame * percent / 100L);
            }
        }
        int totalPercent = 0;
        for (Item.ItemOption opt : options) {
            if (opt.optionTemplate.id == 117) {
                tlSexyDame += opt.param;
            }
        }

        dame += (dame * tlSexyDame / 100L);

        //Sức đánh đẹp
        dame += (dame * tlSexyDame / 100L);

        // Xử lý giảm dame
        dame -= (dame * tlSubSD / 100L);

        // Xử lý map cold
        if (this.player.zone != null && MapService.gI().isMapCold(this.player.zone.map) && !this.isKhongLanh) {
            dame /= 2;
        }

        if (dame > 2_147_483_647) {
            dame = 2_147_483_647;
        }

        this.dame = (int) dame;
    }

    public void setDame(long dame) {
        if (dame > 0) {
            this.dame = (int) (dame <= this.dame ? dame : this.dame);
        } else {
            this.dame = 0;
        }
    }

    private void setDef() {
        this.def = this.defg * 4;
        this.def += this.defAdd;

        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia3) {
            this.def += this.def * 10 / 100;
        }
    }

    private void setCrit() {
        this.crit = this.critg;
        this.crit += this.critAdd;
        this.crit += this.critdragon;
        //biến khỉ
        if (this.player.effectSkill.isMonkey) {
            this.crit = 110;
        }
        if (this.player.itemTime != null && this.player.itemTime.isUseNuocMia2) {
            this.crit += 10;
        }

        if (player.setClothes.thanVuTruKaio >= 1) {
            this.crit += 10 / 100;
        }
    }

    private void resetPoint() {
        this.voHieuChuong = 0;
        this.hpAdd = 0;
        this.mpAdd = 0;
        this.dameAdd = 0;
        this.defAdd = 0;
        this.critAdd = 0;
        this.tlHp.clear();
        this.tlMp.clear();
        this.tlDef.clear();
        this.tlDame.clear();
        this.tlDameCrit.clear();
        this.tlDameAttMob.clear();
        this.tlSDCM = 0;
        this.tlHpHoiBanThanVaDongDoi = 0;
        this.tlMpHoiBanThanVaDongDoi = 0;
        this.hpHoi = 0;
        this.mpHoi = 0;
        this.mpHoiCute = 0;
        this.tlHpHoi = 0;
        this.tlMpHoi = 0;
        this.tlHutHp = 0;
        this.tlHutMp = 0;
        this.tlHutHpMob = 0;
        this.tlHutHpMpXQ = 0;
        this.tlPST = 0;
        this.tlTNSM.clear();
        this.tlDameAttMob.clear();
        this.tlGold = 0;
        this.tlNeDon = 0;
        this.tlNeDonXinbato = 0;
        this.tlBom = 0;
        this.tlGiap = 0;
        this.tlxgcc = 0;
        this.tlxgc = 0;
        this.tlchinhxac = 0;
        this.tlTNSMPet = 0;
        this.xChuong = 0;
        this.setltdb = 0;
        this.setTinhAn = 0;
        this.setNhatAn = 0;
        this.setNguyetAn = 0;
        this.tlSexyDame = 0;
        this.tlSubSD = 0;
        this.tlHpGiamODo = 0;
        this.tlSTChuan = 0;
        this.tlDameBoss = 0;
        this.tlSTCuoi = 0;
        this.isChongNLX = false;
        this.tlTNSMDeTu = 0;
        this.tlAkazaRage = 0;
        this.tlAoThuat = 0;
        this.setHpWithPet = 0;
        this.setSTCuoiWithFlag = 0;
        this.setCritWithFlag = 0;
        this.setCritWithSkhBeerus = 0;
        this.setSDCMWithXenHT = 0;
        this.setSDCMWithLuoiHaiHong = 0;
        this.tlNeCrit = 0;
        this.kiHoiKhiBiDanh = 0;
        this.tlRareMob = 0;
        this.tlRareBoss = 0;
        this.giamSTTD = 0;
        this.giamSTNM = 0;
        this.giamSTXD = 0;
        this.phanDonCanChien = 0;
        this.isSocolaXq = false;
        this.punchStackMax = 0;
        this.giamSTKhiItKi = 0;
        this.flyMobCotNha = 0;
        this.flyMobGheBay = 0;
        this.tlGiamHoiKhien = 0;
        this.tlGiamKhongChe = 0;

        this.setGiapWithXenMax = 0;
        this.skhBeerus = 0;
        this.tlSpeed = 0;
        this.teleport = false;
        this.wearingVoHinh = false;
        this.isKhongLanh = false;
        this.khangTDHS = false;
        this.isTanHinh = false;
        this.isHoaDa = false;
        this.isLamCham = false;
        this.isDoSPL = false;
        this.isThoBulma = false;
    }

    public void addHp(long hp) {
        if (hp > 0) {
            long potentialHp = (long) this.hp + hp;
            if (potentialHp > this.hpMax) {
                this.hp = this.hpMax;
            } else {
                this.hp = (int) Math.min(potentialHp, 2_147_483_647);
            }
        }
    }

    public void addMp(long mp) {
        long potentialMp = this.mp + mp;

        if (potentialMp > this.mpMax) {
            this.mp = this.mpMax;
        } else if (potentialMp < 0) {
            this.mp = 0;
        } else {
            this.mp = (int) potentialMp;
        }
    }

    public void setHp(long hp) {
        if (hp < 0) {
            this.hp = 0;
        } else {
            this.hp = (int) Math.min(hp, 2_147_483_647);
        }
    }

    public void setMp(long mp) {
        if (mp < 0) {
            this.mp = 0;
        } else {
            this.mp = (int) Math.min(mp, 2_147_483_647);
        }
    }

    private void setIsCrit() {
        // lấy nội tại hiện tại (trước dùng bản của đòn đánh trước, đòn đầu tiên / vừa đổi nội tại bị sai)
        intrinsic = this.player.playerIntrinsic != null ? this.player.playerIntrinsic.intrinsic : null;
        if (intrinsic != null && intrinsic.id == 25
                && this.getCurrPercentHP() <= intrinsic.param1) {
            isCrit = true;
        } else if (isCrit100) {
            isCrit100 = false;
            isCrit = true;
        } else {
            isCrit = Util.isTrue(this.crit, ConstRatio.PER100);
        }
    }

    public int getDameAttack(boolean isAttackMob) {
        setIsCrit();
        long dameAttack = this.dame;
        intrinsic = this.player.playerIntrinsic.intrinsic;
        percentDameIntrinsic = 0;
        int percentDameSkill = 0;
        byte percentXDame = 0;
        Skill skillSelect = player.playerSkill.skillSelect;
        if (skillSelect.template.id != Skill.DICH_CHUYEN_TUC_THOI && isCritTele) {
            isCrit = true;
            isCritTele = false;
        }
        switch (skillSelect.template.id) {
            case Skill.DRAGON:
                if (intrinsic.id == 1) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.KAMEJOKO:
                if (intrinsic.id == 2) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.songoku == 5) {
                    percentXDame = 100;
                }
                break;
            case Skill.GALICK:
                if (intrinsic.id == 16) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.kakarot == 5) {
                    percentXDame = 100;
                }
                break;
            case Skill.ANTOMIC:
                if (intrinsic.id == 17) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.TU_SAT:
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.cadicM == 4) {
                    percentXDame = 20;
                } else if (this.player.setClothes.cadicM == 5) {
                    percentXDame = 50;
                }
                break;
            case Skill.DEMON:
                if (intrinsic.id == 8) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                break;
            case Skill.MASENKO:
                // trước đây cộng % nội tại / set Nail vào % chiêu rồi lại nhân thêm lần nữa ở dưới (tính 2 lần)
                if (intrinsic != null && intrinsic.id == 9) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                if (this.player.setClothes.nail == 5) {
                    percentXDame = 80;
                }
                percentDameSkill = skillSelect.damage;
                break;

            case Skill.LIEN_HOAN:
                if (intrinsic.id == 13) {
                    percentDameIntrinsic = intrinsic.param1;
                }
                percentDameSkill = skillSelect.damage;
                if (this.player.setClothes.ocTieu == 5) {
                    percentXDame = 100;
                }
                break;
            case Skill.KAIOKEN:
                percentDameSkill = skillSelect.damage;
                if (player.setClothes.thanVuTruKaio == 5) {
                    percentXDame = 30;
                }
                break;
            case Skill.DICH_CHUYEN_TUC_THOI:
                isCrit = true;
                isCritTele = true;
                dameAttack = Util.nextInt((int) (int) Math.min(2_147_483_647L, (dameAttack - (dameAttack / 100 * 5))),
                        (int) (int) Math.min(2_147_483_647L, (dameAttack + (dameAttack / 100 * 5))));
                break;
            case Skill.MAKANKOSAPPO:
                percentDameSkill = skillSelect.damage;
                int dameSkill = (int) Math.min(2_147_483_647L, (long) this.mpMax * percentDameSkill / 100);
                if (this.player.setClothes.picolo == 5) {
                    dameSkill = (int) Math.min(2_147_483_647L, (long) dameSkill * 3 / 2); // trước là *= 3 / 2 = nhân 1
                }
                return dameSkill;
            case Skill.QUA_CAU_KENH_KHI:
                long hpmob = 0;
                long hppl = 0;
                for (Mob mob : this.player.zone.mobs) {
                    if (!mob.isDie()
                            && Util.getDistance(this.player, mob) <= SkillUtil.getRangeQCKK(this.player.playerSkill.skillSelect.point)) {
                        hpmob += mob.point.hp;
                    }
                }

                for (Player pl : this.player.zone.getHumanoids()) {
                    if (!pl.isDie() && this.player.id != pl.id
                            && Util.getDistance(this.player, pl) <= SkillUtil.getRangeQCKK(this.player.playerSkill.skillSelect.point)) {
                        hppl += pl.nPoint.hp;
                    }
                }

                long dameqckk = (hpmob * 10 / 100) + (hppl * 10 / 100) + this.dame * 10L;

                if (this.player.setClothes.kirin == 5) {
                    dameqckk *= 2;
                }

                dameqckk = dameqckk + (Util.nextInt(-5, 5) * dameqckk / 100);
                if (dameqckk > 2_147_483_647) {
                    dameqckk = 2_147_483_647;
                }
                return (int) dameqckk;
            case Skill.DE_TRUNG:
                if (player.setClothes.pikkoroDaimao == 5) {
                    dameAttack *= 4;
                }
                if (dameAttack > 2_147_483_647) {
                    dameAttack = 2_147_483_647;
                }
                return (int) dameAttack;
        }

        if (intrinsic.id == 18 && this.player.effectSkill.isMonkey) {
            percentDameIntrinsic = intrinsic.param1;
        }

        if (percentDameSkill
                != 0) {
            dameAttack = dameAttack * percentDameSkill / 100;
        }

        dameAttack += (dameAttack * percentDameIntrinsic / 100);
        dameAttack += (dameAttack * dameAfter / 100);
        if (this.player.effectSkill != null && this.player.effectSkill.isDameBuff && tlSexyDame
                == 0) {
            int tiLeDame = this.player.effectSkill.tileDameBuff;
            dameAttack += (dameAttack * tiLeDame / 100L);
        }
        if (isAttackMob) {
            for (Integer tl : this.tlDameAttMob) {
                dameAttack += (dameAttack * tl / 100);
            }
            if (this.player.isPet && ((Pet) this.player).master.charms.tdDeTu > System.currentTimeMillis()) {
                dameAttack *= 2;
            }
        }

        dameAfter = 0;

        if (isCrit) {
            dameAttack *= 2;
            dameAttack += (dameAttack * tlSDCM / 100);
        }

        dameAttack += ((long) dameAttack * percentXDame / 100);
        // option 156: chỉ dùng chiêu đấm thì mỗi đòn cộng dồn 1% sức đánh, tối đa #%
        if (punchStackMax > 0) {
            int sk = skillSelect.template.id;
            if (sk == Skill.DRAGON || sk == Skill.DEMON || sk == Skill.GALICK) {
                punchStack = Math.min(punchStackMax, punchStack + 1);
                dameAttack += dameAttack * punchStack / 100;
            } else {
                punchStack = 0;
            }
        }
        // option 166 / 170: bay cùng cải trang thì +% sức đánh lên quái
        if (isAttackMob) {
            int fly = flyMobBonus();
            if (fly > 0) {
                dameAttack += dameAttack * fly / 100;
            }
        }

        long tempDameAttack = (long) (dameAttack / 100L * 5L);
        if (tempDameAttack
                <= 0) {
            tempDameAttack = 1;
        }
        dameAttack += (long) (Util.getOne(
                -1, 1) * Util.nextInt((int) tempDameAttack
                ) + 1);

        if (player.effectSkin != null && player.effectSkin.isXChuong && (player.playerSkill.skillSelect.template.id == Skill.KAMEJOKO || player.playerSkill.skillSelect.template.id == Skill.ANTOMIC || player.playerSkill.skillSelect.template.id == Skill.MASENKO)) {
            dameAttack *= xChuong;
            player.effectSkin.isXDame = true;
            player.effectSkin.isXChuong = false;
            player.effectSkin.lastTimeXChuong = System.currentTimeMillis();
        }

        if (tlSTCuoi > 0) {
            dameAttack += (long) dameAttack * tlSTCuoi / 100;
        }
        long nowBuff = System.currentTimeMillis();
        if (tlAkazaRage > 0 && nowBuff < rageUntil) {
            dameAttack += (long) dameAttack * tlAkazaRage / 100;
        }
        if (tlAoThuat > 0 && nowBuff < aoThuatUntil) {
            dameAttack += (long) dameAttack * tlAoThuat / 100;
        }

        if (dameAttack
                > 2_147_483_647) {
            dameAttack = 2_147_483_647;
        }
        return (int) dameAttack;
    }

    public int getCurrPercentHP() {
        if (this.hpMax == 0) {
            return 100;
        }
        return (int) ((long) this.hp * 100 / this.hpMax);
    }

    public int getCurrPercentMP() {
        return (int) ((long) this.mp * 100 / this.mpMax);
    }

    public void setFullHpMp() {
        this.hp = this.hpMax;
        this.mp = this.mpMax;
    }

    public void subHP(long sub) {
        this.hp -= sub;
        if (this.hp <= 0) {
            this.hp = 0;
            this.setHp(0);
        }
    }

    public void subMP(long sub) {
        this.mp -= sub;
        if (this.mp <= 0) {
            this.mp = 0;
        }

        if (this.mp > 2_147_483_647) {
            this.mp = 2_147_483_647;
        }
    }

    public long calSucManhTiemNang(long tiemNang) {
        if (power < getPowerLimit()) {
            if (this.tlTNSM != null) {
                for (Integer tl : this.tlTNSM) {
                    if (tl != null) {
                        tiemNang += ((long) tiemNang * tl / 100);
                    }
                }
            }
            long tn = tiemNang;
            if (this.player.charms.tdTriTue > System.currentTimeMillis()) {
                tiemNang += tn;
            }
            if (this.player.charms.tdTriTue3 > System.currentTimeMillis()) {
                tiemNang += tn * 3;
            }
            if (this.player.charms.tdTriTue4 > System.currentTimeMillis()) {
                tiemNang += tn * 4;
            }
            if (this.player.timevip > System.currentTimeMillis()) {
                tiemNang += tn * 3;
            }
            if (this.player.effectSkill.isChibi && this.player.typeChibi == 2) {
                tiemNang += tn * 2;
            }
            if (this.player.getSession() != null && this.player.getSession().vip > 0 || this.player.isPet
                    && ((Pet) this.player).master.getSession() != null && ((Pet) this.player).master.getSession().vip > 0) {
                tiemNang += tn * 3;
            }
            if (this.player.itemTime != null && this.player.itemTime.isUseDK) {
                tiemNang += tn * 2;
            }
            // điều ước Rồng Xương: x3 tiềm năng sức mạnh 30 phút (bản thân / đệ tử)
            long now = System.currentTimeMillis();
            if (!this.player.isPet && this.player.hlwSelfX3Until > now
                    || this.player.isPet && ((Pet) this.player).master != null && ((Pet) this.player).master.hlwPetX3Until > now) {
                tiemNang += tn * 2;
            }
            if (player.zone.map.mapId >= 135 && player.zone.map.mapId <= 138) {
                if (this.player.itemTime != null && this.player.itemTime.isUseKhoBauX2) {
                    tiemNang += tn * 2;
                }
            }
            if (this.player.satellite != null && this.player.satellite.isIntelligent) {
                tiemNang += tn / 5;
            }
            this.intrinsic = this.player.playerIntrinsic != null ? this.player.playerIntrinsic.intrinsic : null;
            if (this.intrinsic != null && this.intrinsic.id == 24) {
                tiemNang += ((long) tiemNang * this.intrinsic.param1 / 100);
            }
            if (this.power >= 60_000_000_000L) {
                tiemNang -= ((long) tiemNang * 80 / 100);
            }
            if (this.player.isPet) {
                if (((Pet) this.player).master.itemTime.isUseBuaSanta) {
                    tiemNang += tn * 2;
                }
                if (((Pet) this.player).master.nPoint != null && ((Pet) this.player).master.nPoint.tlTNSMPet > 0) {
                    tiemNang += tn / 100 * (((Pet) this.player).master.nPoint.tlTNSMPet + 100);
                }
            }
            if (MapService.gI().isMapNguHanhSon(this.player.zone.map.mapId)) {
                tiemNang *= 1;
            }
            if (MapService.gI().AllMap(this.player.zone.map.mapId)) {
                //  tiemNang *= 10; //x x3 tiềm năng toàn server
            }
            if (MapService.gI().isMapBanDoKhoBau(this.player.zone.map.mapId)) {
                tiemNang *= 1.5;
            }
            if (this.player.cFlag != 0) {
                if (this.player.cFlag == 8) {
                    tiemNang += ((long) tiemNang * 10 / 100);
                } else {
                    tiemNang += ((long) tiemNang * 5 / 100);
                }
            }
            tiemNang *= Manager.RATE_EXP_SERVER;
            tiemNang = calSubTNSM(tiemNang);
            if (tiemNang <= 0) {
                tiemNang = 1;
            }
        } else {
            tiemNang = 10;
        }
        return tiemNang;
    }

    public long calSubTNSM(long tiemNang) {
        if (power >= 90_000_000_000L) {
            tiemNang /= 100;
        } else if (power >= 80_000_000_000L) {
            tiemNang /= 90;
        } else if (power >= 60_000_000_000L) {
            tiemNang /= 50;
        } else if (power >= 50_000_000_000L) {
            tiemNang /= 40;
        } else if (power >= 40_000_000_000L) {
            tiemNang /= 30;
        }
        return tiemNang;
    }

    public short getTileHutHp(boolean isMob) {
        if (isMob) {
            return (short) (this.tlHutHp + this.tlHutHpMob);
        } else {
            return this.tlHutHp;
        }
    }

    public short getTiLeHutMp() {
        return this.tlHutMp;
    }

    public int subDameInjureWithDeff(long dame) {
        long def = this.def;
        dame -= def;
        if (dame < 0) {
            dame = 1;
        }
        return (int) dame;
    }

    /*------------------------------------------------------------------------*/
    public boolean canOpenPower() {
        return this.power >= getPowerLimit();
    }

    public long getPowerLimit() {
        switch (limitPower) {
            case 0:
                return 17999999999L;
            case 1:
                return 19999999999L;
            case 2:
                return 24999999999L;
            case 3:
                return 29999999999L;
            case 4:
                return 39999999999L;
            case 5:
                return 50010000000L;
            case 6:
                return 60010000000L;
            case 7:
                return 70010000000L;
            case 8:
                return 80010000000L;
            case 9:
                return 90010000000L;
            default:
                return 0;
        }
    }

    public long getPowerNextLimit() {
        switch (limitPower + 1) {
            case 0:
                return 17999999999L;
            case 1:
                return 19999999999L;
            case 2:
                return 24999999999L;
            case 3:
                return 29999999999L;
            case 4:
                return 39999999999L;
            case 5:
                return 50010000000L;
            case 6:
                return 60010000000L;
            case 7:
                return 70010000000L;
            case 8:
                return 80010000000L;
            case 9:
                return 90010000000L;
            default:
                return 0;
        }
    }

    public int getHpMpLimit() {
        switch (limitPower) {
            case 0:
                return 220000;
            case 1:
                return 240000;
            case 2:
                return 300000;
            case 3:
                return 350000;
            case 4:
                return 400000;
            case 5:
                return 450000;
            case 6:
                return 500000;
            case 7:
                return 525000;
            case 8:
                return 550000;
            case 9:
                return 575000;
            default:
                return 0;
        }
    }

    public int getDameLimit() {
        switch (limitPower) {
            case 0:
                return 11000;
            case 1:
                return 12000;
            case 2:
                return 15000;
            case 3:
                return 18000;
            case 4:
                return 20000;
            case 5:
                return 22000;
            case 6:
                return 24000;
            case 7:
                return 24500;
            case 8:
                return 25000;
            case 9:
                return 26000;
            default:
                return 0;
        }
    }

    public short getDefLimit() {
        switch (limitPower) {
            case 0:
                return 550;
            case 1:
                return 600;
            case 2:
                return 700;
            case 3:
                return 800;
            case 4:
                return 1000;
            case 5:
                return 1200;
            case 6:
                return 1400;
            case 7:
                return 1500;
            case 8:
                return 1600;
            case 9:
                return 1800;
            default:
                return 0;
        }
    }

    public byte getCritLimit() {
        switch (limitPower) {
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
                return 10;
            default:
                return 0;
        }
    }

    public void powerUp(long power) {
        this.power += power;
        TaskService.gI().checkDoneTaskPower(player, this.power);
    }

    public void tiemNangUp(long tiemNang) {
        this.tiemNang += tiemNang;
    }

    public void increasePoint(byte type, short point) {
        if (point <= 0 || point > 1000) {
            return;
        }
        long tiemNangUse;
        if (type == 0) {
            int pointHp = point * 20;
            tiemNangUse = (long) point * (2L * (this.hpg + 1000L) + pointHp - 20) / 2; // long: tránh tràn số int
            if ((this.hpg + pointHp) <= getHpMpLimit()) {
                if (doUseTiemNang(tiemNangUse)) {
                    hpg += pointHp;
                }
            } else {
                Service.gI().sendThongBaoOK(player, "Vui lòng mở giới hạn sức mạnh");
                return;
            }
        }
        if (type == 1) {
            int pointMp = point * 20;
            tiemNangUse = (long) point * (2L * (this.mpg + 1000L) + pointMp - 20) / 2;
            if ((this.mpg + pointMp) <= getHpMpLimit()) {
                if (doUseTiemNang(tiemNangUse)) {
                    mpg += pointMp;
                }
            } else {
                Service.gI().sendThongBaoOK(player, "Vui lòng mở giới hạn sức mạnh");
                return;
            }
        }
        if (type == 2) {
            TaskService.gI().checkDoneTaskNangCS(player);
            tiemNangUse = (long) point * (2L * this.dameg + point - 1) / 2 * 100;
            if ((this.dameg + point) <= getDameLimit()) {
                if (doUseTiemNang(tiemNangUse)) {
                    dameg += point;
                }
                TaskService.gI().checkDoneTaskNangCS(player);
            } else {
                Service.gI().sendThongBaoOK(player, "Vui lòng mở giới hạn sức mạnh");
                return;
            }
        }
        if (type == 3) {
            point = 1; // giá chỉ tính cho 1 điểm giáp
            tiemNangUse = 2L * (this.defg + 5) / 2 * 100000;
            if ((this.defg + point) <= getDefLimit()) {
                if (doUseTiemNang(tiemNangUse)) {
                    defg += point;
                }
            } else {
                Service.gI().sendThongBaoOK(player, "Vui lòng mở giới hạn sức mạnh");
                return;
            }
        }
        if (type == 4) {
            point = 1; // giá chỉ tính cho 1 điểm chí mạng
            tiemNangUse = 50000000L;
            for (int i = 0; i < this.critg; i++) {
                tiemNangUse *= 5L;
            }
            if ((this.critg + point) <= getCritLimit()) {
                if (doUseTiemNang(tiemNangUse)) {
                    critg += point;
                }
            } else {
                Service.gI().sendThongBaoOK(player, "Vui lòng mở giới hạn sức mạnh");
                return;
            }
        }
        Service.gI().point(player);
    }

    private boolean doUseTiemNang(long tiemNang) {
        if (this.tiemNang < tiemNang) {
            Service.gI().sendThongBaoOK(player, "Bạn không đủ tiềm năng");
            return false;
        }
        if (this.tiemNang >= tiemNang && this.tiemNang - tiemNang >= 0) {
            this.tiemNang -= tiemNang;
            TaskService.gI().checkDoneTaskUseTiemNang(player);
            return true;
        }
        return false;
    }

    public long getFullTN() {
        long tnhp = 0, tnki = 0, tnsd = 0, tng = 0, tncm = 0;

        if (hpg > 0) {
            tnhp = (((hpg / 20L) * (50L + (50L + (hpg / 20L) - 1L)) / 2L) * 20L);
        }
        if (mpg > 0) {
            tnki = (((mpg / 20L) * (50L + (50L + (mpg / 20L) - 1L)) / 2L) * 20L);
        }
        if (dameg > 0) {
            tnsd = ((dameg * (dameg - 1L) * 100L) / 2L);
        }
        if (defg > 0) {
            tng = ((defg * (500000L + (500000L + (defg - 1L) * 100000L))) / 2L);
        }
        if (critg > 0) {
            tncm = ((50L * (((long) Math.pow(5L, critg) - 1L)) / (5L - 1L) * 1000000L));
        }
        return tnhp + tnki + tnsd + tng + tncm;
    }

    //--------------------------------------------------------------------------
    private long lastTimeHoiPhuc;
    private long lastTimeHoiStamina;

    public void update() {
        if (player != null && player.effectSkill != null) {
            if (player.effectSkill.isCharging && player.effectSkill.countCharging < 10) {
                int tiLeHoiPhuc = SkillUtil.getPercentCharge(player.playerSkill.skillSelect.point);
                if (player.effectSkill.isCharging && !player.isDie() && !player.effectSkill.isHaveEffectSkill()
                        && (hp < hpMax || mp < mpMax)) {
                    long hpRecovered = hpMax / 100 * tiLeHoiPhuc;
                    long mpRecovered = mpMax / 100 * tiLeHoiPhuc;

                    if (hp + hpRecovered > 2_147_483_647) {
                        hpRecovered = 2_147_483_647 - hp;
                    }
                    if (mp + mpRecovered > 2_147_483_647) {
                        mpRecovered = 2_147_483_647 - mp;
                    }

                    PlayerService.gI().hoiPhuc(player, hpRecovered, mpRecovered);

                    if (player.effectSkill.countCharging % 3 == 0) {
                        Service.gI().chat(player, "Phục hồi năng lượng " + getCurrPercentHP() + "%");
                    }
                } else {
                    EffectSkillService.gI().stopCharge(player);
                }
                if (++player.effectSkill.countCharging >= 10) {
                    EffectSkillService.gI().stopCharge(player);
                }
            }

            if (Util.canDoWithTime(lastTimeHoiPhuc, 30000)) {
                PlayerService.gI().hoiPhuc(this.player, hpHoi, mpHoi);
                this.lastTimeHoiPhuc = System.currentTimeMillis();
            }

            if (Util.canDoWithTime(lastTimeHoiStamina, 60000) && this.stamina < this.maxStamina) {
                this.stamina++;
                this.lastTimeHoiStamina = System.currentTimeMillis();

                if (!this.player.isBoss && !this.player.isPet) {
                    PlayerService.gI().sendCurrentStamina(this.player);
                }
            }
        }
    }

    public void dispose() {
        this.intrinsic = null;
        this.player = null;
        this.tlHp = null;
        this.tlMp = null;
        this.tlDef = null;
        this.tlDame = null;
        this.tlDameAttMob = null;
        this.tlTNSM = null;
    }

    public long calPercent(long param, int percent) {
        return param * percent / 100;
    }

}
