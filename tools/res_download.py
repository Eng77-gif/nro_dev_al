#!/usr/bin/env python3
"""Tải res (icon, part, mount, aura, effect, file res) từ một server NRO qua giao thức client.

Ví dụ:
    python tools/res_download.py --host 127.0.0.1 --port 14445 --user 1 --password 1 \\
        --icons 0-20000 --parts --mounts 0-120 --auras 0-130 --effects 0-1200 --out res_out

Kết quả (đúng định dạng mà tools/res_import.py nhập được):
    res_out/icon/x<zoom>/<id>.png
    res_out/Nr_Part.sql                      INSERT INTO `nr_part` (`id`, `type`, `part`) ...
    res_out/img_by_name/x<zoom>/<tên>.png     mount_<id>_0/1, aura_<id>_0/1 ...
    res_out/img_by_name.json                 {tên: số khung}
    res_out/effect/x<zoom>/ImgEffect_<id>.png, res_out/effdata/DataEffect_<id>
    res_out/res/x<zoom>/<tên file>           (khi dùng --res)

Effect chỉ tải được khi đã đăng nhập vào game (cần --user/--password của một nhân vật có sẵn).
Chỉ dùng với server bạn có quyền tải dữ liệu.
"""
import argparse
import json
import os
import socket
import struct
import sys
import time

# lệnh có độ dài gói 3 byte
BIG_SIZE_CMDS = {-32, -66, -74, 11, -67, -87, 66, 12}
PART_FRAMES = {0: 3, 1: 17, 2: 14}  # đầu / thân / chân


def s8(v):
    v &= 0xFF
    return v - 256 if v > 127 else v


def utf(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


class Reader:
    def __init__(self, data):
        self.d, self.p = data, 0

    def take(self, n):
        if self.p + n > len(self.d):
            raise EOFError
        out = self.d[self.p:self.p + n]
        self.p += n
        return out

    def byte(self):
        return struct.unpack('>b', self.take(1))[0]

    def short(self):
        return struct.unpack('>h', self.take(2))[0]

    def int(self):
        return struct.unpack('>i', self.take(4))[0]

    def utf(self):
        n = struct.unpack('>H', self.take(2))[0]
        return self.take(n).decode('utf-8', 'replace')

    def left(self):
        return len(self.d) - self.p


class Client:
    def __init__(self, host, port, timeout=10):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.key = None
        self.cur_r = self.cur_w = 0
        self.buf = b''

    def _recv(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(1 << 20)
            if not chunk:
                raise EOFError('server đóng kết nối')
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out

    def _dec(self, b):
        r = (self.key[self.cur_r] ^ b) & 0xFF
        self.cur_r = (self.cur_r + 1) % len(self.key)
        return r

    def _enc(self, b):
        r = (self.key[self.cur_w] ^ b) & 0xFF
        self.cur_w = (self.cur_w + 1) % len(self.key)
        return r

    def send(self, cmd, data=b''):
        if self.key is None:
            pkt = struct.pack('>bH', cmd, len(data)) + data
        else:
            n = len(data)
            pkt = bytes([self._enc(cmd & 0xFF), self._enc((n >> 8) & 0xFF), self._enc(n & 0xFF)]) + bytes(self._enc(x) for x in data)
        self.sock.sendall(pkt)

    def read(self):
        cmd = self._recv(1)[0]
        if self.key is None:
            n = struct.unpack('>H', self._recv(2))[0]
            return s8(cmd), self._recv(n)
        cmd = s8(self._dec(cmd))
        if cmd in BIG_SIZE_CMDS:
            b = [self._dec((x + 128) & 0xFF) for x in self._recv(3)]
            n = b[0] | (b[1] << 8) | (b[2] << 16)
        else:
            hi, lo = self._recv(2)
            n = (self._dec(hi) << 8) | self._dec(lo)
        return cmd, bytes(self._dec(x) for x in self._recv(n))

    def handshake(self):
        self.send(-27)
        cmd, data = self.read()
        if cmd != -27:
            raise RuntimeError('server không trả khoá mã hoá')
        n = data[0]
        key = [data[1]]
        for i in range(1, n):
            key.append((data[1 + i] ^ key[i - 1]) & 0xFF)
        self.key = key

    def pump(self, seconds, on_msg):
        """Đọc gói trong `seconds` giây, gọi on_msg(cmd, data) cho mỗi gói."""
        end = time.time() + seconds
        self.sock.settimeout(0.25)
        try:
            while time.time() < end:
                try:
                    cmd, data = self.read()
                except socket.timeout:
                    continue
                on_msg(cmd, data)
        finally:
            self.sock.settimeout(10)


def parse_range(s):
    ids = []
    for part in s.split(','):
        if '-' in part:
            a, b = part.split('-')
            ids += range(int(a), int(b) + 1)
        elif part.strip():
            ids.append(int(part))
    return ids


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(data)


class Downloader:
    def __init__(self, args):
        self.a = args
        self.out = args.out
        self.z = args.zoom
        self.c = Client(args.host, args.port)
        self.stats = {}
        self.frames = {}
        self.parts = None
        self.res_done = False

    def count(self, k):
        self.stats[k] = self.stats.get(k, 0) + 1

    def on_msg(self, cmd, data):
        r = Reader(data)
        try:
            if cmd == -67:  # icon
                i, n = r.int(), r.int()
                write(f'{self.out}/icon/x{self.z}/{i}.png', r.take(n))
                self.count('icon')
            elif cmd == 66:  # ảnh theo tên (mount/aura...)
                name, nf, n = r.utf(), r.byte(), r.int()
                if n > 0:
                    write(f'{self.out}/img_by_name/x{self.z}/{name}.png', r.take(n))
                    self.frames[name] = nf
                    self.count('img_by_name')
            elif cmd == -66:  # effect
                i, n = r.short(), r.int()
                eff = r.take(n)
                if r.left() >= 4 and struct.unpack('>i', r.d[r.p:r.p + 4])[0] + 4 != r.left():
                    r.take(1)  # client mới có thêm 1 byte loại effect
                img = r.take(r.int())
                write(f'{self.out}/effdata/DataEffect_{i}', eff)
                write(f'{self.out}/effect/x{self.z}/ImgEffect_{i}.png', img)
                self.count('effect')
            elif cmd == -87:  # dart, arrow, effect, image, part, skill
                r.byte()
                blobs = [r.take(r.int()) for _ in range(6)]
                self.parts = parse_parts(blobs[4])
            elif cmd == -74:
                t = r.byte()
                if t == 2:
                    name, n = r.utf(), r.int()
                    write(f'{self.out}/res/x{self.z}/{os.path.basename(name)}', r.take(n))
                    self.count('res')
                elif t == 3:
                    self.res_done = True
        except EOFError:
            pass

    def run(self):
        a, c = self.a, self.c
        c.handshake()
        c.send(-29, struct.pack('>b', 2) + struct.pack('>bbbii??', 4, self.z, 0, 1280 // self.z, 720 // self.z, True, True) + utf(a.platform))
        c.pump(1.5, self.on_msg)
        if a.res:
            print('Tải file res...')
            c.send(-74, struct.pack('>b', 1))
            c.send(-74, struct.pack('>b', 2))
            end = time.time() + a.res_timeout
            while not self.res_done and time.time() < end:
                c.pump(2, self.on_msg)
        if a.user:
            print('Đăng nhập...')
            c.send(-29, struct.pack('>b', 0) + utf(a.user) + utf(a.password) + utf(a.version) + struct.pack('>?', False))
            c.pump(3, self.on_msg)
            for sub in (6, 7, 8):
                c.send(-28, struct.pack('>b', sub))
            c.send(-28, struct.pack('>b', 13))
            c.send(-38)
            c.pump(4, self.on_msg)
        if a.parts:
            print('Tải part...')
            c.send(-87)
            end = time.time() + 30
            while self.parts is None and time.time() < end:
                c.pump(1, self.on_msg)
            if self.parts is None:
                print('  không nhận được part')
            else:
                save_parts(self.parts, f'{self.out}/Nr_Part.sql')
                print(f'  {len(self.parts)} part')
        if a.icons:
            self.batch('icon', [struct.pack('>i', i) for i in parse_range(a.icons)], -67)
        names = []
        for kind in ('mounts', 'auras'):
            rng = getattr(a, kind)
            if rng:
                prefix = 'mount' if kind == 'mounts' else 'aura'
                names += [f'{prefix}_{i}_{k}' for i in parse_range(rng) for k in (0, 1)]
        names += [n for n in (a.names or '').split(',') if n]
        if names:
            self.batch('img_by_name', [utf(n) for n in names], 66)
            path = f'{self.out}/img_by_name.json'
            old = json.load(open(path)) if os.path.exists(path) else {}
            old.update(self.frames)
            os.makedirs(self.out, exist_ok=True)
            json.dump(old, open(path, 'w'), indent=1, ensure_ascii=False)
        if a.effects:
            if not a.user:
                print('Bỏ qua effect: cần --user/--password để vào game')
            else:
                self.batch('effect', [struct.pack('>h', i) for i in parse_range(a.effects)], -66)
        c.sock.close()
        print('Xong:', self.stats)

    def batch(self, label, payloads, cmd):
        print(f'Tải {label}: {len(payloads)} yêu cầu...')
        for k in range(0, len(payloads), self.a.batch):
            for p in payloads[k:k + self.a.batch]:
                self.c.send(cmd, p)
                if self.a.delay:
                    time.sleep(self.a.delay)
            self.c.pump(self.a.wait, self.on_msg)
            print(f'  {min(k + self.a.batch, len(payloads))}/{len(payloads)}  nhận {self.stats.get(label, 0)}', flush=True)
        self.c.pump(self.a.wait * 2, self.on_msg)


def parse_parts(blob):
    r = Reader(blob)
    parts = []
    for _ in range(r.short()):
        t = r.byte()
        if t not in PART_FRAMES:
            raise ValueError(f'loại part lạ: {t}')
        parts.append((t, [[r.short(), r.byte(), r.byte()] for _ in range(PART_FRAMES[t])]))
    return parts


def save_parts(parts, path):
    os.makedirs(os.path.dirname(path) or '.', exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        for i, (t, fr) in enumerate(parts):
            f.write(f"INSERT INTO `nr_part` (`id`, `type`, `part`) VALUES ({i}, {t}, '{json.dumps(fr, separators=(',', ':'))}');\n")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--host', required=True)
    p.add_argument('--port', type=int, default=14445)
    p.add_argument('--user', help='tài khoản (cần cho effect)')
    p.add_argument('--password', default='')
    p.add_argument('--zoom', type=int, default=4, choices=(1, 2, 3, 4))
    p.add_argument('--out', default='res_out')
    p.add_argument('--icons', help='vd 0-20000 hoặc 17577-17741,2955')
    p.add_argument('--parts', action='store_true', help='tải toàn bộ part (Nr_Part.sql)')
    p.add_argument('--mounts', help='id mount, vd 0-120 (ảnh mount_<id>_0/1)')
    p.add_argument('--auras', help='id aura, vd 0-130 (ảnh aura_<id>_0/1)')
    p.add_argument('--names', help='tên ảnh khác theo img_by_name, cách nhau bởi dấu phẩy')
    p.add_argument('--effects', help='id effect, vd 0-1200')
    p.add_argument('--res', action='store_true', help='tải các file trong data/res')
    p.add_argument('--res-timeout', type=int, default=600)
    p.add_argument('--platform', default='pc|2.3.3', help='chuỗi nền tảng|phiên bản gửi khi chọn loại client')
    p.add_argument('--version', default='2.3.3', help='phiên bản gửi khi đăng nhập')
    p.add_argument('--batch', type=int, default=50, help='số yêu cầu gửi mỗi đợt')
    p.add_argument('--wait', type=float, default=1.5, help='giây chờ nhận sau mỗi đợt')
    p.add_argument('--delay', type=float, default=0.0, help='giây nghỉ giữa các yêu cầu')
    args = p.parse_args()
    try:
        Downloader(args).run()
    except (OSError, EOFError, RuntimeError) as e:
        print('Lỗi:', e)
        sys.exit(1)


if __name__ == '__main__':
    main()
