"""Vá client J2ME Dragonboy247 (mod Hùng Hero) để vào server riêng.

Cách dùng:
    python patch_client.py <client gốc .jar hoặc .jar.zip> <jar đầu ra> <ip> [port] [tên server]
Ví dụ:
    python patch_client.py Dragonboy247_v7.0.jar NRO_J2ME.jar 36.50.135.16 14445 "NRO"

Client mã hóa mọi chuỗi bằng khóa "hunghero" (ký tự - i%9 - khóa[i%8]). Công cụ tìm các chuỗi danh sách
server / IP mặc định trong constant pool, thay bằng server của bạn (mã hóa lại đúng cách) và ghi jar mới.
Không dịch ngược hay biên dịch lại class nào nên mọi tính năng của bản mod giữ nguyên.
"""
import re
import struct
import sys
import zipfile

KEY = [ord(c) for c in 'hunghero']


def dec(s):
    return ''.join(chr(ord(ch) - i % 9 - KEY[i % 8]) for i, ch in enumerate(s))


def enc(s):
    return ''.join(chr(ord(ch) + i % 9 + KEY[i % 8]) for i, ch in enumerate(s))


def mutf8_decode(b):
    return b.decode('utf-8', 'surrogatepass').replace('\u0000', '\0') if b'\xc0\x80' not in b else b.replace(b'\xc0\x80', b'\x00').decode('utf-8', 'surrogatepass')


def mutf8_encode(s):
    return s.encode('utf-8', 'surrogatepass').replace(b'\x00', b'\xc0\x80')


def patch_class(data, replace):
    """replace(chuỗi đã giải mã) -> chuỗi mới hoặc None. Trả về (bytes mới, số chuỗi đã thay)."""
    n = struct.unpack('>H', data[8:10])[0]
    p, i, out, changed = 10, 1, bytearray(data[:10]), 0
    while i < n:
        t = data[p]
        if t == 1:
            ln = struct.unpack('>H', data[p + 1:p + 3])[0]
            raw = data[p + 3:p + 3 + ln]
            new = None
            try:
                new = replace(dec(mutf8_decode(raw)))
            except Exception:
                pass
            if new is not None:
                raw = mutf8_encode(enc(new))
                changed += 1
            out += bytes([1]) + struct.pack('>H', len(raw)) + raw
            p += 3 + ln
        else:
            size = {3: 5, 4: 5, 5: 9, 6: 9, 7: 3, 8: 3, 16: 3, 19: 3, 20: 3, 9: 5, 10: 5, 11: 5, 12: 5, 17: 5, 18: 5, 15: 4}[t]
            out += data[p:p + size]
            p += size
            if t in (5, 6):
                i += 1
        i += 1
    out += data[p:]
    return bytes(out), changed


OFFICIAL_IPS = {'112.213.94.23', '210.211.109.199', '112.213.85.88', '52.74.230.22'}


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        sys.exit(1)
    src, dst, ip = sys.argv[1], sys.argv[2], sys.argv[3]
    port = sys.argv[4] if len(sys.argv) > 4 else '14445'
    name = sys.argv[5] if len(sys.argv) > 5 else 'NRO'
    entry = '%s:%s:%s:0:0:0' % (name, ip, port)

    def replace(s):
        # danh sách server: "Tên:host:port:lang:0:0,...[,lang,index]"
        if re.match(r'^[^:,]+:(dragon\d*\.teamobi\.com|dragon\.indonaga\.com|[\d.]+):\d{4,5}:\d:\d:\d', s) and ('teamobi' in s or 'indonaga' in s or any(x in s for x in OFFICIAL_IPS)):
            lang = re.match(r'^[^:,]+:[^:]+:\d+:(\d)', s).group(1)
            tail = ',%s,0' % lang if re.search(r',\d,\d$', s) else ''
            return entry.replace(':0:0:0', ':%s:0:0' % lang) + tail
        if s in OFFICIAL_IPS:  # IP mặc định
            return ip
        return None

    zin = zipfile.ZipFile(src)
    zout = zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED)
    total = 0
    for info in zin.infolist():
        data = zin.read(info.filename)
        if info.filename.endswith('.class'):
            data, c = patch_class(data, replace)
            total += c
        if info.filename == 'META-INF/MANIFEST.MF':
            data = data.replace(b'MIDlet-Name: Dragonboy247 v7.0', ('MIDlet-Name: ' + name).encode('utf-8'))
        zout.writestr(info, data)
    zout.close()
    print('Đã thay %d chuỗi server -> %s' % (total, entry))


if __name__ == '__main__':
    main()
