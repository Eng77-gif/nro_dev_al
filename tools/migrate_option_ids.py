#!/usr/bin/env python3
"""Dời các dòng chỉ số (item_option_template) id 255 - 265 về id trống dưới 255.

Client chỉ đọc được tối đa 255 dòng chỉ số (số lượng gửi bằng 1 byte), nên các id >= 255 làm client đọc sai
toàn bộ danh sách. Script đổi id trong: item_option_template, item_template_option, item_shop_option,
data_badges, radar, đồ của người chơi (hành trang, rương, trên người, đệ tử, ký gửi) và player_extra.

Dùng:  python tools/migrate_option_ids.py --db team2026            (chạy bằng lệnh mysql)
       python tools/migrate_option_ids.py --db team2026 --dry-run  (chỉ in số thay đổi)
Chạy nhiều lần không sao: id đã dời rồi thì bỏ qua.
"""
import argparse
import json
import re
import subprocess

MAP = {255: 172, 256: 39, 257: 90, 258: 91, 259: 92, 260: 115, 261: 163, 262: 167, 263: 168, 264: 169, 265: 42}
PLAYER_COLS = ['items_body', 'items_bag', 'items_box', 'items_box_lucky_round', 'items_daban', 'pet']


def run(args, sql, fetch=True):
    cmd = ['mysql', args.db, '--default-character-set=utf8mb4', '-N', '-B', '--raw']
    if args.user:
        cmd += ['-u', args.user]
    if args.password:
        cmd += [f'-p{args.password}']
    if args.host:
        cmd += ['-h', args.host]
    return subprocess.run(cmd + ['-e', sql], capture_output=True, text=True, check=True).stdout


def sub_item_options(text):
    """Đổi id trong chuỗi item JSON dạng [id,param] (có thể bị escape nhiều lớp)."""
    return re.sub(r'(\[)(25[5-9]|26[0-5])(,-?\d+\])', lambda m: m.group(1) + str(MAP[int(m.group(2))]) + m.group(3), text)


def sub_json_ids(text):
    return re.sub(r'("id"\s*:\s*"?)(25[5-9]|26[0-5])\b', lambda m: m.group(1) + str(MAP[int(m.group(2))]), text)


def hexs(s):
    return '0x' + s.encode('utf-8').hex()


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--db', default='team2026')
    p.add_argument('--user')
    p.add_argument('--password')
    p.add_argument('--host')
    p.add_argument('--dry-run', action='store_true')
    a = p.parse_args()

    names = {}
    for line in run(a, 'select json_object(\'id\', id, \'n\', NAME) from item_option_template where id >= 255').splitlines():
        r = json.loads(line)
        names[r['id']] = r['n']
    sql = []
    for old, new in MAP.items():
        if old in names:
            sql.append(f"UPDATE `item_option_template` SET `NAME` = {hexs(names[old])} WHERE `id` = {new};")
    if names:
        sql.append('DELETE FROM `item_option_template` WHERE `id` >= 255;')
    for table in ('item_template_option', 'item_shop_option'):
        for old, new in MAP.items():
            sql.append(f'UPDATE `{table}` SET `option_id` = {new} WHERE `option_id` = {old};')
    changed = 0
    for table, key, col, fn in (('data_badges', 'id', 'Options', sub_json_ids), ('radar', 'id', 'options', sub_json_ids),
                                ('player_extra', 'player_id', 'item_foot', sub_item_options)):
        for line in run(a, f'select json_object(\'k\', `{key}`, \'v\', `{col}`) from `{table}`').splitlines():
            r = json.loads(line)
            if r['v'] and fn(r['v']) != r['v']:
                sql.append(f'UPDATE `{table}` SET `{col}` = {hexs(fn(r["v"]))} WHERE `{key}` = {r["k"]};')
                changed += 1
    for pid in run(a, 'select id from player').split():
        sets = []
        for col in PLAYER_COLS:
            v = json.loads(run(a, f'select json_object(\'v\', `{col}`) from player where id = {pid}'))['v']
            if v and sub_item_options(v) != v:
                sets.append(f'`{col}` = {hexs(sub_item_options(v))}')
        if sets:
            sql.append(f'UPDATE `player` SET {", ".join(sets)} WHERE `id` = {pid};')
            changed += 1
    for line in run(a, 'select json_object(\'k\', id, \'v\', itemOption) from shop_ky_gui').splitlines():
        r = json.loads(line)
        if r['v'] and sub_json_ids(sub_item_options(r['v'])) != r['v']:
            sql.append(f'UPDATE `shop_ky_gui` SET `itemOption` = {hexs(sub_json_ids(sub_item_options(r["v"])))} WHERE `id` = {r["k"]};')
            changed += 1
    print(f'Dòng chỉ số cần dời: {len(names)}, dòng dữ liệu JSON thay đổi: {changed}')
    if a.dry_run:
        return
    for k in range(0, len(sql), 200):
        run(a, '\n'.join(sql[k:k + 200]))
    print('Xong. Số dòng chỉ số hiện có:', run(a, 'select count(*) from item_option_template').strip())


if __name__ == '__main__':
    main()
