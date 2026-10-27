package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.map.service.ChangeMapService;
import nro.models.npc.Npc;
import nro.models.player.Player;

/**
 *
 * @author By Mr Blue
 * 
 */

public class DaiThienSu extends Npc {

    public DaiThienSu(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        // trước đây để trống: bấm vào không có gì, người ở Võ Đài Siêu Cấp (không có cổng) bị kẹt
        if (canOpenNpc(player)) {
            createOtherMenu(player, ConstNpc.BASE_MENU, "Ta có thể giúp gì cho ngươi?", "Về nhà", "Từ chối");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player) && player.idMark.isBaseMenu() && select == 0) {
            ChangeMapService.gI().changeMapInYard(player, 21 + player.gender, -1, 300);
        }
    }
}
