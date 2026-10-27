"""Gắn hình (part) chính thức cho các pet đang thiếu: đầu trống + thân + chân lấy từ Nr_Part.sql.
Icon thiếu thì chép từ gói icon chính thức; icon trùng số mà khác hình thì chép sang số mới."""
import re, json, os, subprocess, sys
from PIL import Image

S = '/tmp/claude-0/-home-user-nro-dev-al/4776a0c8-09c8-50fc-8fdb-c3cbfa750be2/scratchpad/'
PK = S + 'iconpack/x/x4/'
R = '/home/user/nro_dev_al/data/icon/'
off = {}
for m in re.finditer(r"VALUES \((\d+), (\d+), '([^']*)'\)", open(S + 'iconpack/x/Nr_Part.sql').read()):
    off[int(m[1])] = (int(m[2]), json.loads(m[3]))

# vật phẩm pet -> part thân chính thức (part chân = thân + 1)
PETS = {1114: 1159, 1207: 1078, 1243: 1246, 1244: 1249, 1256: 1268, 1414: 1342, 1458: 1369, 1452: 1366,
        1550: 1429, 1551: 1426, 1564: 1438, 1568: 1444, 1596: 1471, 1597: 1474, 1611: 1494,
        1620: 1497, 1621: 1497, 1622: 1497, 1629: 1506, 1630: 1509, 1631: 1516, 1633: 1524,
        1668: 1551, 1682: 1559, 1683: 1562, 1714: 1601, 1729: 1622, 1748: 1651, 1774: 1666,
        1786: 1690, 1791: 1696, 1792: 1699, 1793: 1702, 1794: 1705, 1801: 1714, 1802: 1717,
        1830: 1768, 1845: 1785, 1846: 1788, 1870: 1821, 1872: 1827, 1873: 1830, 1874: 1833,
        1881: 1842, 1914: 1877, 1945: 1910, 1953: 1922, 1956: 1931, 1962: 1937, 2014: 1984,
        2015: 1981, 2016: 1987, 2025: 1998, 1935: 1901}
START = 2193


def q(sql):
    return subprocess.run(['mysql', '-N', 'team2026', '-e', sql], capture_output=True, text=True, check=True).stdout


def same(a, b):
    A, B = Image.open(a).convert('RGBA'), Image.open(b).convert('RGBA')
    return A.size == B.size and A.tobytes() == B.tobytes()


have = set(int(f[:-4]) for f in os.listdir(R + 'x4') if f.endswith('.png'))
next_icon = max(have) + 1
remap, copied = {}, []


def put(src, dst_id):
    im = Image.open(src); im.load()
    for z in (1, 2, 3):
        w, h = im.size
        im.convert('RGBA').resize((max(1, round(w * z / 4)), max(1, round(h * z / 4))), Image.LANCZOS).save(f'{R}x{z}/{dst_id}.png', optimize=True)
    open(f'{R}x4/{dst_id}.png', 'wb').write(open(src, 'rb').read())


def icon(i):
    global next_icon
    if i in (2954, 2955) or i <= 0:
        return i
    if i in remap:
        return remap[i]
    src = f'{PK}{i}.png'
    if not os.path.exists(src):
        remap[i] = i
        return i
    if i not in have:
        put(src, i); have.add(i); copied.append(i); remap[i] = i
    elif same(f'{R}x4/{i}.png', src):
        remap[i] = i
    else:
        put(src, next_icon); copied.append(next_icon); remap[i] = next_icon; next_icon += 1
    return remap[i]


def conv(data):
    return json.dumps([[icon(e[0]), e[1], e[2]] for e in data], separators=(',', ':'))


out = [f"DELETE FROM part WHERE id >= {START};"]
pid = START
made = {}
for item, body in PETS.items():
    if body not in made:
        bt, bd = off[body]
        lt, ld = off[body + 1]
        head = pid
        out.append(f"INSERT INTO part (id, TYPE, DATA) VALUES ({pid}, 0, '[[2955,0,0],[2955,0,0],[2955,0,0]]'), "
                   f"({pid + 1}, 1, '{conv(bd)}'), ({pid + 2}, 2, '{conv(ld)}');")
        made[body] = head
        pid += 3
    h = made[body]
    out.append(f"UPDATE item_template SET part = {h}, head = {h}, body = {h + 1}, leg = {h + 2} WHERE id = {item};")
open(S + 'pets_import.sql', 'w').write('\n'.join(out) + '\n')
print('parts', pid - START, 'icons copied', len(copied), 'remapped', sum(1 for k, v in remap.items() if k != v))
