#!/usr/bin/env python3
"""Nhập một gói res vào server: icon, part, mount/aura (img_by_name), effect, file res.

Ví dụ:
    python tools/res_import.py "D:/res team new 16th10" --db team2026
    python tools/res_import.py res_out --db team2026 --apply      # gói do res_download.py tải về

Gói có thể là thư mục bất kỳ, tool tự tìm:
    <id>.png hoặc Small<id>.png         icon (coi là cỡ x4, hoặc nằm trong thư mục x1..x4)
    Nr_Part.sql                          part (INSERT INTO `nr_part` (`id`, `type`, `part`) ...)
    mount_<id>_<k>.png, aura_<id>_<k>.png, <tên>_<k>.png trong thư mục img_by_name   ảnh theo tên
    img_by_name.json                     số khung của ảnh theo tên (nếu không có thì tự đoán)
    ImgEffect_<id>.png + DataEffect_<id> effect
    thư mục res/x1..x4                   file res (chỉ chép khi có --res)

Ảnh chỉ có cỡ x4 sẽ được tự thu nhỏ ra x1, x2, x3.
Part: mặc định chỉ thêm những part dùng ít nhất 1 icon có trong gói và chưa có y hệt trong database;
part mới được đánh id nối tiếp id lớn nhất trong bảng `part`. Bảng đổi id thư viện -> id mới ghi ở đầu file SQL.
Tool ghi file SQL (mặc định res_import.sql); thêm --apply để chạy luôn bằng lệnh mysql.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys

try:
    from PIL import Image
except ImportError:
    sys.exit('Cần cài Pillow: pip install pillow')

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
ZOOMS = (1, 2, 3, 4)
PART_RE = re.compile(r"INSERT INTO `nr_part` \(`id`, `type`, `part`\) VALUES \((\d+), (\d+), '(.*)'\);")


def mysql(args, sql):
    cmd = ['mysql', '--default-character-set=utf8mb4', '-N', '-B', '--raw']
    if args.mysql_user:
        cmd += ['-u', args.mysql_user]
    if args.mysql_password:
        cmd += [f'-p{args.mysql_password}']
    if args.mysql_host:
        cmd += ['-h', args.mysql_host]
    cmd += [args.db, '-e', sql]
    return subprocess.run(cmd, capture_output=True, text=True, check=True).stdout


def zoom_of(path):
    m = re.search(r'[\\/]x([1-4])[\\/]', path)
    return int(m.group(1)) if m else 4


def put_image(src, dst_dir, name, src_zoom, overwrite, stats, key):
    """Chép ảnh vào dst_dir/x<z>/name ở mọi cỡ; cỡ nào thiếu thì co giãn từ ảnh nguồn."""
    im = None
    for z in ZOOMS:
        dst = os.path.join(dst_dir, f'x{z}', name)
        if os.path.exists(dst) and not overwrite:
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if z == src_zoom:
            shutil.copyfile(src, dst)
        else:
            if im is None:
                im = Image.open(src).convert('RGBA')
            w, h = im.size
            im.resize((max(1, round(w * z / src_zoom)), max(1, round(h * z / src_zoom))), Image.LANCZOS).save(dst)
        stats[key] = stats.get(key, 0) + 1


def guess_frames(path):
    """Đoán số khung của ảnh dọc (mount/aura): đếm các dải có hình cách nhau bởi hàng trong suốt."""
    im = Image.open(path).convert('RGBA')
    w, h = im.size
    alpha = im.split()[3]
    rows = [alpha.crop((0, y, w, y + 1)).getbbox() is not None for y in range(h)]
    for n in range(8, 1, -1):
        if h % n == 0:
            step = h // n
            if all(any(rows[k * step:(k + 1) * step]) for k in range(n)):
                return n
    return 1


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('pack', help='thư mục gói res')
    p.add_argument('--repo', default=REPO, help='thư mục server (chứa data/)')
    p.add_argument('--db', default='team2026')
    p.add_argument('--mysql-user')
    p.add_argument('--mysql-password')
    p.add_argument('--mysql-host')
    p.add_argument('--first-part', type=int, help='id part mới bắt đầu (mặc định: lớn nhất trong DB + 1)')
    p.add_argument('--parts', help='chỉ nhập các part thư viện này, vd 2064-2072,2100; "all" = tất cả')
    p.add_argument('--res', action='store_true', help='chép cả file res (data/res)')
    p.add_argument('--overwrite', action='store_true', help='ghi đè file đã có')
    p.add_argument('--sql', default='res_import.sql')
    p.add_argument('--apply', action='store_true', help='chạy file SQL vào database bằng lệnh mysql')
    args = p.parse_args()

    data = os.path.join(args.repo, 'data')
    if not os.path.isdir(os.path.join(data, 'icon')):
        sys.exit(f'Không thấy {data}/icon, kiểm tra --repo')
    stats, icons, part_files, imgs, effects, res_files = {}, {}, [], {}, {}, []
    frames_json = {}
    for root, _, files in os.walk(args.pack):
        for fn in files:
            path = os.path.join(root, fn)
            low = fn.lower()
            if low == 'nr_part.sql':
                part_files.append(path)
            elif low == 'img_by_name.json':
                frames_json.update(json.load(open(path, encoding='utf-8')))
            elif re.fullmatch(r'(small)?\d+\.png', low) and not re.search(r'[\\/]res[\\/]', path):
                icons[int(re.sub(r'\D', '', fn))] = path
            elif re.fullmatch(r'imgeffect_\d+\.png', low):
                effects.setdefault(int(re.sub(r'\D', '', fn)), {})['img'] = path
            elif re.fullmatch(r'dataeffect_\d+', low):
                effects.setdefault(int(re.sub(r'\D', '', fn)), {})['data'] = path
            elif re.search(r'[\\/]res[\\/]x[1-4][\\/]', path):
                res_files.append(path)
            elif re.fullmatch(r'[a-z0-9_]+_\d+\.png', low) and (low.startswith(('mount_', 'aura_')) or 'img_by_name' in path.lower()):
                imgs[fn[:-4]] = path

    # icon
    for i, path in sorted(icons.items()):
        put_image(path, os.path.join(data, 'icon'), f'{i}.png', zoom_of(path), args.overwrite, stats, 'icon')
    # effect
    for i, e in sorted(effects.items()):
        if 'img' in e:
            put_image(e['img'], os.path.join(data, 'effect'), f'ImgEffect_{i}.png', zoom_of(e['img']), args.overwrite, stats, 'effect')
        if 'data' in e:
            dst = os.path.join(data, 'effdata', f'DataEffect_{i}')
            if args.overwrite or not os.path.exists(dst):
                shutil.copyfile(e['data'], dst)
                stats['effdata'] = stats.get('effdata', 0) + 1
    # res
    if args.res:
        for path in res_files:
            dst = os.path.join(data, 'res', f'x{zoom_of(path)}', os.path.basename(path))
            if args.overwrite or not os.path.exists(dst):
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copyfile(path, dst)
                stats['res'] = stats.get('res', 0) + 1
    # ảnh theo tên
    img_rows = []
    for name, path in sorted(imgs.items()):
        put_image(path, os.path.join(data, 'img_by_name'), f'{name}.png', zoom_of(path), args.overwrite, stats, 'img_by_name')
        if frames_json.get(name) == 0:
            continue  # server gốc không khai báo số khung cho ảnh này: chỉ chép file
        img_rows.append((name, frames_json.get(name) or guess_frames(path)))

    sql = ['-- Sinh bởi tools/res_import.py từ ' + os.path.abspath(args.pack)]
    if img_rows:
        sql.append('INSERT INTO `img_by_name` (`NAME`, `n_frame`) VALUES\n' + ',\n'.join(f"('{n}', {f})" for n, f in img_rows)
                   + '\nON DUPLICATE KEY UPDATE `n_frame` = VALUES(`n_frame`);')

    # part
    lib = {}
    for path in part_files:
        for line in open(path, encoding='utf-8', errors='replace'):
            m = PART_RE.match(line.strip())
            if m:
                lib[int(m.group(1))] = (int(m.group(2)), json.loads(m.group(3)))
    new_parts = []
    if lib:
        existing = set()
        try:
            for line in mysql(args, 'select type, replace(replace(data, char(10), \'\'), char(13), \'\') from part').splitlines():
                t, d = line.split('\t', 1)
                existing.add((int(t), json.dumps(json.loads(d.replace('\\"', '')), separators=(',', ':'))))
            first = args.first_part or int(mysql(args, 'select coalesce(max(id), -1) + 1 from part').strip())
        except (subprocess.CalledProcessError, FileNotFoundError) as e:
            if not args.first_part:
                sys.exit(f'Không đọc được bảng part ({e}); hãy truyền --first-part')
            first = args.first_part
        if args.parts == 'all':
            want = sorted(lib)
        elif args.parts:
            want = []
            for seg in args.parts.split(','):
                a, _, b = seg.partition('-')
                want += range(int(a), int(b or a) + 1)
        else:
            pack_icons = set(icons)
            want = [i for i in sorted(lib) if any(f[0] in pack_icons for f in lib[i][1])]
        nid = first
        for i in want:
            if i not in lib:
                print(f'Không có part {i} trong Nr_Part.sql')
                continue
            t, fr = lib[i]
            key = (t, json.dumps(fr, separators=(',', ':')))
            if key in existing:
                continue
            existing.add(key)
            new_parts.append((i, nid, t, fr))
            nid += 1
        if new_parts:
            sql.append('-- part thư viện -> id mới: ' + ', '.join(f'{i}->{n}' for i, n, _, _ in new_parts))
            sql.append('INSERT INTO `part` (`id`, `type`, `data`) VALUES\n' + ',\n'.join(
                f"({n}, {t}, '{json.dumps(fr, separators=(',', ':'))}')" for _, n, t, fr in new_parts) + ';')
    stats['part'] = len(new_parts)

    with open(args.sql, 'w', encoding='utf-8') as f:
        f.write('\n\n'.join(sql) + '\n')
    print('Đã nhập:', stats)
    for i, n, t, fr in new_parts:
        print(f'  part {i} -> {n} ({["đầu", "thân", "chân"][t] if t < 3 else t}, icon {fr[0][0]})')
    print('File SQL:', os.path.abspath(args.sql))
    if args.apply and len(sql) > 1:
        mysql(args, f'source {os.path.abspath(args.sql)}')
        print('Đã chạy SQL vào database', args.db)
    if new_parts or img_rows:
        print('Nhớ tăng DataGame.vsData (và vsItem nếu đổi vật phẩm) để client tải lại part/ảnh mới.')


if __name__ == '__main__':
    main()
