"""Đổi id vật phẩm trong dữ liệu JSON của người chơi theo map.json của remap.py."""
import json, re, subprocess, sys
DB, MAP = sys.argv[1], sys.argv[2]
m = json.load(open(MAP))
moved = {int(k): v for k, v in m['map'].items() if int(k) != v}
drop = set(m['drop'])
COLS = ['items_body', 'items_bag', 'items_box', 'items_box_lucky_round', 'items_daban', 'pet']
q = lambda sql: subprocess.run(['mysql', DB, '--default-character-set=utf8mb4', '-N', '-B', '--raw', '-e', sql], capture_output=True, text=True, check=True).stdout
pat = re.compile(r'((?:\\*")\[)(-?\d+),(-?\d+),')
n = 0
for pid in q('select id from player').split():
    sets = []
    for c in COLS:
        v = q(f"select json_object('v', `{c}`) from player where id={pid}").strip()
        if not v:
            continue
        val = json.loads(v)['v']
        if val is None:
            continue

        def rep(mo):
            global n
            i = int(mo.group(2))
            if i in drop:
                n += 1
                return f"{mo.group(1)}-1,0,"
            if i in moved:
                n += 1
                return f"{mo.group(1)}{moved[i]},{mo.group(3)},"
            return mo.group(0)
        new = pat.sub(rep, val)
        if new != val:
            sets.append(f"`{c}` = 0x{new.encode('utf-8').hex()}")
    if sets:
        q(f"update player set {', '.join(sets)} where id={pid}")
for row in q("select player_id, item_foot from player_extra").splitlines():
    pid, foot = row.split('\t', 1)
    try:
        d = json.loads(foot)
    except Exception:
        continue
    if d and d[0] in moved:
        d[0] = moved[d[0]]; n += 1
        q(f"update player_extra set item_foot = 0x{json.dumps(d).encode().hex()} where player_id={pid}")
print('đổi', n, 'vật phẩm trong dữ liệu người chơi')
