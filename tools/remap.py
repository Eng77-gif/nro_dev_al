"""Re-number item templates to match the official DataNRO (TeaMobi) ItemTemplates.json.

Usage: remap.py <official.json> <db name> <out dir> <code ref ids file>
Writes <out>/map.json (old -> new), <out>/remap.sql and <out>/report.txt.
"""
import json, os, re, subprocess, sys, unicodedata, collections

OFF, DB, OUT, CODE_IDS = sys.argv[1:5]
REPO = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
FIRST_CUSTOM = 2103          # món riêng của server (không có trong DataNRO) nhưng code/DB vẫn dùng
FILLER = (75, 3, '', '', 0, 0, -1, 0, 0, 0, 0, -1, -1, -1)  # dòng trống giữ chỗ (type 75)

off = {x['id']: x for x in json.load(open(OFF, encoding='utf-8'))}


def q(sql):
    return subprocess.run(['mysql', DB, '--default-character-set=utf8mb4', '-N', '-B', '--raw', '-e', sql],
                          capture_output=True, text=True, check=True).stdout


COLS = ['id', 'TYPE', 'gender', 'NAME', 'description', 'level', 'icon_id', 'part', 'is_up_to_up', 'power_require', 'gold', 'gem', 'head', 'body', 'leg']
ours = {}
for line in q('select json_object(' + ','.join(f"'{c}', `{c}`" for c in COLS) + ') from item_template').splitlines():
    r = json.loads(line)
    ours[r['id']] = r


def N(s):
    s = unicodedata.normalize('NFC', s).lower().strip()
    s = re.sub(r'^(cải trang|ct)\s+', '', s)
    return re.sub(r'\s+', ' ', s)


# ---------------------------------------------------------------- ghép tự động
by_name, by_icon = collections.defaultdict(list), collections.defaultdict(list)
for i, o in off.items():
    by_name[N(o['name'])].append(i)
    by_icon[o['icon']].append(i)

# chỉnh tay: món mượn icon hoặc ghép icon sai (đã xem hình)
MANUAL = {
    758: 758,    # Capsule Tết (DataNRO đổi tên thành 2026)
    1860: 1957,  # Cải trang Gốc 1 (16520) = Goku GT
    1942: None,  # Capsule vỡ: không có trong DataNRO
    1943: 1909,  # Mảnh capsule vỡ
    1945: 2045,  # Trứng Xên = Trứng ve sầu
    1946: None,  # Thiệp chúc VIP: không có trong DataNRO
    1947: 2079,  # Hộp quà TT 2026 VIP = Hộp bánh Trung Thu Vip 2026
    1948: 2078,
    1949: 2080,  # Hộp bánh TT chay 2026
    1950: 2088,  # Quà Trăng Tròn = Trăng tròn
    1951: 2087,  # Quà Trăng Khuyết = Trăng khuyết
    1967: 2048, 1968: 2049, 1969: 2050, 1970: 2051, 1971: 2052,  # 5 "Cải trang N"
    1975: 2019,  # "Pet Kuma" = Pet Cửu Vĩ Hồ
    1976: 2038,  # "Pet Robot Đỏ" = Pet Xên bọ hung MAX
    1977: 2077,  # Pet Cừu Bông = Pet Cừu không mập
    1978: 2059,  # "Pet Hề Súng Nước" = Pet Minion
    1987: 2060,  # Ván bay Trung Thu 2026
    1990: 2037, 1991: 2036,
    1993: 1885,  # Danh hiệu Phá cỗ trung thu
    1994: 2076,  # Danh hiệu Phá cỗ trung thu VIP
    1995: 2081,  # Danh hiệu Perfect
    1792: None, 1793: None,  # Thẻ Oozaru 1, 2
}
# món trùng: tham chiếu đổi sang món chính, dòng của nó bị bỏ
ALIAS = {1989: 1978, 1992: 1976, 1536: 1774, 1537: 1775}
# giữ tên của server khi tên DataNRO chỉ là tên tạm
KEEP_OUR_NAME = {2048, 2049, 2050, 2051, 2052}

mp = {}
for i, u in ours.items():
    if not u['NAME'] or i in MANUAL or i in ALIAS:
        continue
    o = off.get(i)
    if o and (N(o['name']) == N(u['NAME']) or o['icon'] == u['icon_id']):
        mp[i] = i
for i, j in MANUAL.items():
    if j is not None:
        mp[i] = j
used = set(mp.values())
for i, u in sorted(ours.items()):
    if i in mp or i in MANUAL or i in ALIAS or not u['NAME']:
        continue
    c = [j for j in by_icon.get(u['icon_id'], []) if j not in used]
    cn = [j for j in by_name.get(N(u['NAME']), []) if j not in used]
    both = [j for j in c if j in cn]
    pick = both or (c if len(c) == 1 else []) or (cn if len(cn) == 1 else [])
    if pick:
        mp[i] = pick[0]
        used.add(pick[0])
assert len(set(mp.values())) == len(mp), 'hai món cùng ghép vào một id'

# ---------------------------------------------------------------- món không có trong DataNRO
code_ids = set(int(x) for x in open(CODE_IDS).read().split())
db_refs = set()
for sql in ('select temp_id from item_shop', 'select template_id from item_template_option', 'select box_id from item_box_reward',
            'select item_id from item_box_reward', 'select idItem from data_badges', 'select id from radar', 'select item_id from shop_ky_gui'):
    db_refs |= {int(x) for x in q(sql).split()}
for d in q('select detail from giftcode').splitlines():
    db_refs |= {int(x) for x in re.findall(r'"id":(\d+)', d)}
FORCE_DROP = {1946}
unmatched = [i for i, u in ours.items() if u['NAME'] and i not in mp and i not in ALIAS]
relocate, drop = {}, []
nxt = FIRST_CUSTOM
for i in sorted(unmatched):
    if i not in FORCE_DROP and (i in code_ids or i in db_refs):
        relocate[i] = nxt
        nxt += 1
    else:
        drop.append(i)
mp.update(relocate)
for a, b in ALIAS.items():
    if b in mp:
        mp[a] = mp[b]
final_max = max(max(off), nxt - 1)

# ---------------------------------------------------------------- bảng item_template mới
TYPE_FIX = {18: 27, 19: 27, 38: 27}   # loại mới của client chính thức mà server/client này chưa có
icons = {int(f[:-4]) for f in os.listdir(os.path.join(REPO, 'data/icon/x4')) if f[:-4].isdigit()}
rows = {}
for old, new in mp.items():
    if old in ALIAS:
        continue
    r = dict(ours[old])
    r['id'] = new
    o = off.get(new)
    if o and new < FIRST_CUSTOM:
        if new not in KEEP_OUR_NAME:
            r['NAME'] = o['name']
        if old != new:
            r['description'] = o['description']
        if o['icon'] in icons:
            r['icon_id'] = o['icon']
    rows[new] = r
added = []
for i, o in off.items():
    if i in rows:
        continue
    t = TYPE_FIX.get(o['type'], o['type'])
    part = o['part'] if t in (0, 1, 2, 3, 4, 23, 24) else -1
    rows[i] = dict(id=i, TYPE=t, gender=o['gender'], NAME=o['name'], description=o['description'], level=o['level'],
                   icon_id=o['icon'], part=part, is_up_to_up=int(o['isUpToUp']), power_require=o['strRequire'],
                   gold=0, gem=0, head=-1, body=-1, leg=-1)
    added.append(i)
for r in rows.values():
    if len(r['description']) > 75:  # cột description là varchar(75)
        r['description'] = r['description'][:72].rstrip() + '...'
for i in range(final_max + 1):
    if i not in rows:
        rows[i] = dict(zip(COLS, (i,) + FILLER))


def sq(v):
    if isinstance(v, int):
        return str(v)
    return "'" + v.replace('\\', '\\\\').replace("'", "''") + "'"


moved = {o: n for o, n in mp.items() if o != n}
L = ["-- Xếp lại id vật phẩm theo DataNRO (TeaMobi). Sinh bởi remap.py", "SET FOREIGN_KEY_CHECKS = 0;", "DELETE FROM `item_template`;"]
ids = sorted(rows)
for k in range(0, len(ids), 200):
    L.append("INSERT INTO `item_template` (" + ", ".join(f"`{c}`" for c in COLS) + ") VALUES\n" + ",\n".join(
        "(" + ", ".join(sq(rows[i][c]) for c in COLS) + ")" for i in ids[k:k + 200]) + ";")

# đổi tham chiếu: qua id tạm để không đè nhau
dropped_set = set(drop)


def remap_col(table, col, extra_where=''):
    out = []
    for i in sorted(dropped_set):
        out.append(f"DELETE FROM `{table}` WHERE `{col}` = {i}{extra_where};")
    for o, n in moved.items():
        out.append(f"UPDATE `{table}` SET `{col}` = {n + 1000000} WHERE `{col}` = {o}{extra_where};")
    out.append(f"UPDATE `{table}` SET `{col}` = `{col}` - 1000000 WHERE `{col}` >= 1000000;")
    return out


for t, c in (('item_template_option', 'template_id'), ('item_box_reward', 'box_id'), ('item_box_reward', 'item_id'),
             ('item_shop', 'temp_id'), ('data_badges', 'idItem'), ('shop_ky_gui', 'item_id'), ('radar', 'id')):
    if t == 'item_shop':
        L.append("DELETE FROM `item_shop_option` WHERE `item_shop_id` IN (SELECT `id` FROM `item_shop` WHERE `temp_id` IN (" + ",".join(map(str, sorted(dropped_set) or [-9])) + "));")
    L += remap_col(t, c)
L.append("SET FOREIGN_KEY_CHECKS = 1;")
os.makedirs(OUT, exist_ok=True)
open(os.path.join(OUT, 'remap.sql'), 'w', encoding='utf-8').write("\n".join(L) + "\n")
json.dump({'map': {str(k): v for k, v in mp.items()}, 'drop': drop, 'relocate': relocate, 'added': added, 'max': final_max},
          open(os.path.join(OUT, 'map.json'), 'w'), ensure_ascii=False)

R = []
R.append(f"Tổng: {len(rows)} dòng (0..{final_max}); giữ nguyên id {sum(1 for o, n in mp.items() if o == n)}, "
         f"đổi id {len(moved)}, thêm từ DataNRO {len(added)}, món riêng dời ra sau {len(relocate)}, bỏ {len(drop)}")
R.append("\n== Đổi id (cũ -> mới) ==")
for o, n in sorted(moved.items()):
    nm = rows[n]['NAME'] if n in rows else '?'
    R.append(f"{o} -> {n}  {ours[o]['NAME']}  =>  {nm}")
R.append("\n== Món riêng của server (không có trong DataNRO, code/DB còn dùng) dời ra sau ==")
for o, n in sorted(relocate.items()):
    R.append(f"{o} -> {n}  {ours[o]['NAME']}")
R.append("\n== Bỏ (không có trong DataNRO, không nơi nào dùng) ==")
for o in drop:
    R.append(f"{o}  {ours[o]['NAME']}")
R.append("\n== Thêm mới từ DataNRO ==")
for i in added:
    R.append(f"{i}  {off[i]['name']}  (type {off[i]['type']})")
open(os.path.join(OUT, 'report.txt'), 'w', encoding='utf-8').write("\n".join(R) + "\n")
print(R[0])
