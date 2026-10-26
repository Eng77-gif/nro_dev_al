#!/usr/bin/env python3
"""Giao diện web cho res_download.py và res_import.py.

Chạy:   python tools/res_web.py            rồi mở http://127.0.0.1:8765
        python tools/res_web.py --port 9000 --bind 0.0.0.0   (cho máy khác trong mạng LAN mở được)

Trang web gồm 3 phần:
  - Tải res: điền host/port/tài khoản, chọn loại res cần tải.
  - Nhập res: chọn thư mục gói (hoặc tải file .zip lên), chọn database rồi bấm Nhập.
  - Xem: duyệt icon / mount / aura / effect trong một thư mục.
Mỗi lần chạy là một tiến trình riêng, nhật ký hiện trực tiếp trên trang.
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile
import threading
import time
import uuid
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

TOOLS = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(TOOLS, '..'))
UPLOADS = os.path.join(tempfile.gettempdir(), 'nro_res_web_uploads')
JOBS = {}
LOCK = threading.Lock()


class Job:
    def __init__(self, title, cmd):
        self.id = uuid.uuid4().hex[:8]
        self.title = title
        self.cmd = cmd
        self.lines = []
        self.status = 'running'
        self.started = time.time()
        self.proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, cwd=REPO,
                                     env={**os.environ, 'PYTHONUNBUFFERED': '1', 'PYTHONIOENCODING': 'utf-8'})
        threading.Thread(target=self._pump, daemon=True).start()

    def _pump(self):
        for raw in self.proc.stdout:
            self.lines.append(raw.decode('utf-8', 'replace').rstrip('\n'))
        self.proc.wait()
        self.status = 'done' if self.proc.returncode == 0 else f'lỗi (mã {self.proc.returncode})'

    def info(self, since=0):
        return {'id': self.id, 'title': self.title, 'status': self.status, 'lines': self.lines[since:],
                'total': len(self.lines), 'started': self.started}


def start(title, cmd):
    job = Job(title, cmd)
    with LOCK:
        JOBS[job.id] = job
    return job


def build_download(f):
    cmd = [sys.executable, os.path.join(TOOLS, 'res_download.py'), '--host', f['host'], '--port', str(int(f.get('port') or 14445)),
           '--zoom', str(int(f.get('zoom') or 4)), '--out', f.get('out') or 'res_out']
    if f.get('user'):
        cmd += ['--user', f['user'], '--password', f.get('password', '')]
    for k in ('icons', 'mounts', 'auras', 'names', 'effects', 'platform', 'version'):
        if f.get(k):
            cmd += [f'--{k}', f[k]]
    for k in ('batch', 'wait', 'delay'):
        if f.get(k) not in (None, ''):
            cmd += [f'--{k}', str(float(f[k]) if k != 'batch' else int(f[k]))]
    if f.get('parts'):
        cmd.append('--parts')
    if f.get('res'):
        cmd.append('--res')
    if f.get('official'):
        cmd.append('--official')
    return cmd


def build_import(f):
    cmd = [sys.executable, os.path.join(TOOLS, 'res_import.py'), f['pack'], '--repo', f.get('repo') or REPO,
           '--db', f.get('db') or 'team2026', '--sql', f.get('sql') or os.path.join(REPO, 'res_import.sql')]
    for k in ('mysql_user', 'mysql_password', 'mysql_host', 'parts', 'first_part'):
        if f.get(k):
            cmd += ['--' + k.replace('_', '-'), str(f[k])]
    for k in ('res', 'overwrite', 'apply'):
        if f.get(k):
            cmd.append('--' + k)
    return cmd


def list_images(root, kind, offset, limit):
    """Trả về danh sách ảnh trong thư mục gói/thư mục server để xem trước."""
    sub = {'icon': 'icon', 'img_by_name': 'img_by_name', 'effect': 'effect'}.get(kind, 'icon')
    base = None
    for cand in (os.path.join(root, sub, 'x4'), os.path.join(root, 'data', sub, 'x4'), os.path.join(root, sub), root):
        if os.path.isdir(cand):
            base = cand
            break
    if base is None:
        return {'base': None, 'items': [], 'total': 0}
    names = [n for n in os.listdir(base) if n.lower().endswith('.png')]

    def key(n):
        d = ''.join(ch for ch in n if ch.isdigit())
        return (0, int(d), n) if d else (1, 0, n)
    names.sort(key=key)
    return {'base': base, 'total': len(names), 'items': names[offset:offset + limit]}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, code, body, ctype='application/json; charset=utf-8'):
        if isinstance(body, (dict, list)):
            body = json.dumps(body, ensure_ascii=False).encode('utf-8')
        elif isinstance(body, str):
            body = body.encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        self.wfile.write(body)

    def body_json(self):
        n = int(self.headers.get('Content-Length') or 0)
        return json.loads(self.rfile.read(n) or b'{}')

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        if u.path == '/':
            return self.send(200, PAGE.replace('__REPO__', json.dumps(REPO)), 'text/html; charset=utf-8')
        if u.path == '/api/jobs':
            with LOCK:
                return self.send(200, [j.info(len(j.lines)) for j in JOBS.values()])
        if u.path.startswith('/api/job/'):
            job = JOBS.get(u.path.rsplit('/', 1)[1])
            if not job:
                return self.send(404, {'error': 'không có'})
            return self.send(200, job.info(int(q.get('since', 0))))
        if u.path == '/api/images':
            return self.send(200, list_images(q.get('root', ''), q.get('kind', 'icon'), int(q.get('offset', 0)), int(q.get('limit', 300))))
        if u.path == '/img':
            path = os.path.abspath(q.get('p', ''))
            if not path.lower().endswith('.png') or not os.path.isfile(path):
                return self.send(404, b'', 'text/plain')
            with open(path, 'rb') as fh:
                return self.send(200, fh.read(), 'image/png')
        self.send(404, {'error': 'không có'})

    def do_POST(self):
        u = urlparse(self.path)
        try:
            if u.path == '/api/download':
                f = self.body_json()
                if not f.get('host'):
                    return self.send(400, {'error': 'Thiếu host'})
                job = start(f"Tải từ {f['host']}:{f.get('port') or 14445}", build_download(f))
                return self.send(200, {'id': job.id})
            if u.path == '/api/import':
                f = self.body_json()
                if not f.get('pack') or not os.path.isdir(f['pack']):
                    return self.send(400, {'error': 'Thư mục gói không tồn tại'})
                job = start(f"Nhập {os.path.basename(os.path.normpath(f['pack']))}", build_import(f))
                return self.send(200, {'id': job.id})
            if u.path == '/api/upload':
                name = os.path.basename(unquote(self.headers.get('X-Filename', 'pack.zip'))) or 'pack.zip'
                n = int(self.headers.get('Content-Length') or 0)
                dest = os.path.join(UPLOADS, uuid.uuid4().hex[:8])
                os.makedirs(dest, exist_ok=True)
                path = os.path.join(dest, name)
                with open(path, 'wb') as fh:
                    left = n
                    while left > 0:
                        chunk = self.rfile.read(min(left, 1 << 20))
                        if not chunk:
                            break
                        fh.write(chunk)
                        left -= len(chunk)
                if not zipfile.is_zipfile(path):
                    return self.send(400, {'error': 'Chỉ nhận file .zip (giải nén .rar/.7z trước rồi chọn thư mục)'})
                out = os.path.join(dest, 'pack')
                with zipfile.ZipFile(path) as z:
                    for m in z.infolist():
                        target = os.path.abspath(os.path.join(out, m.filename))
                        if not target.startswith(os.path.abspath(out) + os.sep):
                            continue  # bỏ đường dẫn thoát ra ngoài thư mục giải nén
                        z.extract(m, out)
                os.remove(path)
                return self.send(200, {'pack': out})
            if u.path.startswith('/api/stop/'):
                job = JOBS.get(u.path.rsplit('/', 1)[1])
                if job and job.status == 'running':
                    job.proc.terminate()
                return self.send(200, {'ok': True})
        except (ValueError, KeyError) as e:
            return self.send(400, {'error': str(e)})
        self.send(404, {'error': 'không có'})


PAGE = r'''<!doctype html>
<html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>NRO Res Tool</title>
<style>
:root{--bg:#f6f7f9;--card:#fff;--text:#1d2330;--muted:#6b7280;--line:#e3e6eb;--acc:#e8771a;--acc2:#c75f0c;--ok:#1f9d55;--err:#d64545;--log:#11151c;--logtext:#d7dde8}
@media (prefers-color-scheme:dark){:root{--bg:#12151b;--card:#1a1f27;--text:#e6e9ef;--muted:#9aa3b2;--line:#2a313c;--log:#0b0e13;--logtext:#cfd6e2}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:14px/1.45 system-ui,-apple-system,Segoe UI,Roboto,sans-serif}
header{display:flex;align-items:center;gap:12px;padding:14px 20px;border-bottom:1px solid var(--line);background:var(--card);position:sticky;top:0;z-index:2}
header h1{font-size:17px;margin:0}header .dot{width:12px;height:12px;border-radius:50%;background:var(--acc)}
nav{display:flex;gap:4px;margin-left:auto}nav button{border:0;background:none;color:var(--muted);padding:8px 12px;border-radius:8px;cursor:pointer;font-weight:600}
nav button.on{background:var(--acc);color:#fff}
main{max-width:1100px;margin:0 auto;padding:20px 16px;display:grid;gap:16px}
.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:16px}
.card h2{font-size:15px;margin:0 0 12px}.hint{color:var(--muted);font-size:12.5px;margin:4px 0 0}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:12px}
label{display:flex;flex-direction:column;gap:4px;font-size:12.5px;color:var(--muted)}
input[type=text],input[type=number],input[type=password],select{padding:8px 10px;border:1px solid var(--line);border-radius:8px;background:var(--bg);color:var(--text);font:inherit}
.checks{display:flex;flex-wrap:wrap;gap:14px;margin-top:12px}.checks label{flex-direction:row;align-items:center;color:var(--text);font-size:13.5px}
.row{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-top:14px}
button.main{background:var(--acc);color:#fff;border:0;padding:9px 18px;border-radius:8px;font-weight:700;cursor:pointer}button.main:hover{background:var(--acc2)}
button.sec{background:none;border:1px solid var(--line);color:var(--text);padding:8px 14px;border-radius:8px;cursor:pointer}
details summary{cursor:pointer;color:var(--muted);margin-top:12px}
.jobs{display:grid;gap:10px}.job{border:1px solid var(--line);border-radius:10px;overflow:hidden}
.job .top{display:flex;gap:10px;align-items:center;padding:8px 12px;background:var(--bg)}.job .top b{flex:1}
.badge{font-size:12px;padding:2px 8px;border-radius:99px;background:var(--line)}.badge.running{background:#fde7d2;color:#9a4a07}.badge.done{background:#d5f2e1;color:#14683a}.badge.err{background:#fbdada;color:#9b2323}
pre{margin:0;background:var(--log);color:var(--logtext);padding:10px 12px;max-height:280px;overflow:auto;font:12px/1.45 ui-monospace,Consolas,monospace;white-space:pre-wrap}
.gallery{display:grid;grid-template-columns:repeat(auto-fill,minmax(92px,1fr));gap:8px;margin-top:12px}
.tile{background:repeating-conic-gradient(#8881 0 25%,transparent 0 50%) 0 0/14px 14px;border:1px solid var(--line);border-radius:8px;padding:6px;text-align:center}
.tile img{max-width:100%;height:72px;object-fit:contain;image-rendering:pixelated}.tile div{font-size:11px;color:var(--muted);word-break:break-all}
.drop{border:2px dashed var(--line);border-radius:10px;padding:14px;text-align:center;color:var(--muted);margin-top:12px}.drop.over{border-color:var(--acc);color:var(--acc)}
.hide{display:none}
@media (max-width:600px){header{flex-wrap:wrap}nav{margin-left:0;width:100%}}
</style></head><body>
<header><span class="dot"></span><h1>NRO Res Tool</h1>
<nav><button data-tab="dl" class="on">Tải res</button><button data-tab="imp">Nhập res</button><button data-tab="view">Xem</button></nav></header>
<main>
<section id="tab-dl" class="card">
  <h2>Tải res từ server</h2>
  <div class="grid">
    <label>Host<input type="text" id="d_host" value="127.0.0.1"></label>
    <label>Port<input type="number" id="d_port" value="14445"></label>
    <label>Tài khoản (cần cho effect)<input type="text" id="d_user"></label>
    <label>Mật khẩu<input type="password" id="d_password"></label>
    <label>Thư mục lưu<input type="text" id="d_out" value="res_out"></label>
    <label>Cỡ ảnh<select id="d_zoom"><option>4</option><option>3</option><option>2</option><option>1</option></select></label>
  </div>
  <div class="grid" style="margin-top:12px">
    <label>Icon (id)<input type="text" id="d_icons" placeholder="0-20000"></label>
    <label>Mount (id)<input type="text" id="d_mounts" placeholder="0-120"></label>
    <label>Aura (id)<input type="text" id="d_auras" placeholder="0-130"></label>
    <label>Effect (id)<input type="text" id="d_effects" placeholder="0-1200"></label>
    <label>Ảnh khác theo tên<input type="text" id="d_names" placeholder="ten_anh_0,ten_anh_1"></label>
  </div>
  <div class="checks"><label><input type="checkbox" id="d_parts" checked> Toàn bộ part</label><label><input type="checkbox" id="d_res"> File res (data/res)</label><label><input type="checkbox" id="d_official"> Server TeaMobi chính thức</label></div>
  <details><summary>Nâng cao</summary><div class="grid" style="margin-top:10px">
    <label>Platform|phiên bản<input type="text" id="d_platform" value="pc|2.3.3"></label>
    <label>Phiên bản đăng nhập<input type="text" id="d_version" value="2.3.3"></label>
    <label>Số yêu cầu mỗi đợt<input type="number" id="d_batch" value="50"></label>
    <label>Chờ sau mỗi đợt (giây)<input type="number" step="0.1" id="d_wait" value="1.5"></label>
    <label>Nghỉ giữa yêu cầu (giây)<input type="number" step="0.01" id="d_delay" value="0"></label>
  </div></details>
  <div class="row"><button class="main" id="btn_dl">Bắt đầu tải</button><span class="hint">Chỉ dùng với server bạn có quyền tải dữ liệu.</span></div>
</section>

<section id="tab-imp" class="card hide">
  <h2>Nhập gói res vào server</h2>
  <div class="grid">
    <label style="grid-column:span 2">Thư mục gói res<input type="text" id="i_pack" placeholder="D:\res team new 16th10  hoặc  res_out"></label>
    <label>Thư mục server<input type="text" id="i_repo"></label>
    <label>Database<input type="text" id="i_db" value="team2026"></label>
    <label>MySQL user<input type="text" id="i_mysql_user"></label>
    <label>MySQL mật khẩu<input type="password" id="i_mysql_password"></label>
    <label>MySQL host<input type="text" id="i_mysql_host"></label>
    <label>Chỉ nhập part (tuỳ chọn)<input type="text" id="i_parts" placeholder="2064-2072 hoặc all"></label>
    <label>Id part bắt đầu (tuỳ chọn)<input type="number" id="i_first_part"></label>
  </div>
  <div class="drop" id="drop">Kéo thả file <b>.zip</b> gói res vào đây (hoặc <a href="#" id="pick">chọn file</a>) để tải lên rồi nhập<input type="file" id="file" accept=".zip" class="hide"></div>
  <div class="checks"><label><input type="checkbox" id="i_apply" checked> Chạy SQL vào database</label><label><input type="checkbox" id="i_overwrite"> Ghi đè file đã có</label><label><input type="checkbox" id="i_res"> Chép file res</label></div>
  <div class="row"><button class="main" id="btn_imp">Nhập</button><span class="hint">Nhập xong nhớ tăng <code>vsData</code> trong DataGame.java để client tải lại part.</span></div>
</section>

<section id="tab-view" class="card hide">
  <h2>Xem ảnh</h2>
  <div class="grid">
    <label style="grid-column:span 2">Thư mục (gói đã tải hoặc thư mục server)<input type="text" id="v_root" value="res_out"></label>
    <label>Loại<select id="v_kind"><option value="icon">Icon</option><option value="img_by_name">Mount / aura</option><option value="effect">Effect</option></select></label>
    <label>Lọc theo tên/id<input type="text" id="v_filter" placeholder="vd 176"></label>
  </div>
  <div class="row"><button class="main" id="btn_view">Xem</button><button class="sec" id="btn_more">Thêm 300</button><span class="hint" id="v_info"></span></div>
  <div class="gallery" id="gallery"></div>
</section>

<section class="card"><h2>Tiến trình</h2><div class="jobs" id="jobs"><span class="hint">Chưa có tiến trình nào.</span></div></section>
</main>
<script>
const REPO = __REPO__;
const $ = id => document.getElementById(id);
$('i_repo').value = REPO;
document.querySelectorAll('nav button').forEach(b => b.onclick = () => {
  document.querySelectorAll('nav button').forEach(x => x.classList.toggle('on', x === b));
  ['dl','imp','view'].forEach(t => $('tab-'+t).classList.toggle('hide', t !== b.dataset.tab));
});
function form(prefix, keys){const o={};keys.forEach(k=>{const e=$(prefix+k);if(!e)return;o[k]=e.type==='checkbox'?e.checked:e.value.trim()});return o}
async function post(url, body, headers){const r=await fetch(url,{method:'POST',body,headers});const j=await r.json();if(!r.ok)throw new Error(j.error||r.status);return j}
const jobs = {};
async function track(id){
  jobs[id] = {since:0}; renderJob(id, {title:'...', status:'running', lines:[]});
  const tick = async () => {
    const r = await fetch(`/api/job/${id}?since=${jobs[id].since}`); const j = await r.json();
    jobs[id].since = j.total; renderJob(id, j);
    if (j.status === 'running') setTimeout(tick, 800);
  }; tick();
}
function renderJob(id, j){
  let el = $('job-'+id);
  if (!el){ if ($('jobs').querySelector('.hint')) $('jobs').innerHTML='';
    el = document.createElement('div'); el.className='job'; el.id='job-'+id;
    el.innerHTML = `<div class="top"><b></b><span class="badge"></span><button class="sec">Dừng</button></div><pre></pre>`;
    el.querySelector('button').onclick = () => post('/api/stop/'+id);
    $('jobs').prepend(el); }
  el.querySelector('b').textContent = j.title;
  const bd = el.querySelector('.badge'); bd.textContent = j.status === 'running' ? 'đang chạy' : j.status === 'done' ? 'xong' : j.status;
  bd.className = 'badge ' + (j.status === 'running' ? 'running' : j.status === 'done' ? 'done' : 'err');
  el.querySelector('button').classList.toggle('hide', j.status !== 'running');
  if (j.lines && j.lines.length){ const pre = el.querySelector('pre'); pre.textContent += j.lines.join('\n') + '\n'; pre.scrollTop = pre.scrollHeight; }
}
$('btn_dl').onclick = async () => {
  try { const f = form('d_', ['host','port','user','password','out','zoom','icons','mounts','auras','effects','names','parts','res','official','platform','version','batch','wait','delay']);
    const r = await post('/api/download', JSON.stringify(f), {'Content-Type':'application/json'}); track(r.id);
    $('v_root').value = f.out; } catch(e){ alert(e.message) }
};
$('btn_imp').onclick = async () => {
  try { const f = form('i_', ['pack','repo','db','mysql_user','mysql_password','mysql_host','parts','first_part','apply','overwrite','res']);
    const r = await post('/api/import', JSON.stringify(f), {'Content-Type':'application/json'}); track(r.id); } catch(e){ alert(e.message) }
};
async function upload(file){
  $('drop').textContent = 'Đang tải lên ' + file.name + '...';
  try { const r = await post('/api/upload', file, {'X-Filename': encodeURIComponent(file.name)});
    $('i_pack').value = r.pack; $('drop').textContent = 'Đã giải nén vào ' + r.pack + '. Bấm Nhập để tiếp tục.'; }
  catch(e){ $('drop').textContent = 'Lỗi: ' + e.message }
}
$('pick').onclick = e => { e.preventDefault(); $('file').click() };
$('file').onchange = () => $('file').files[0] && upload($('file').files[0]);
['dragover','dragenter'].forEach(ev => $('drop').addEventListener(ev, e => { e.preventDefault(); $('drop').classList.add('over') }));
['dragleave','drop'].forEach(ev => $('drop').addEventListener(ev, e => { e.preventDefault(); $('drop').classList.remove('over') }));
$('drop').addEventListener('drop', e => e.dataTransfer.files[0] && upload(e.dataTransfer.files[0]));
let vOffset = 0;
async function view(more){
  if (!more){ vOffset = 0; $('gallery').innerHTML=''; }
  const root = $('v_root').value.trim(), kind = $('v_kind').value, flt = $('v_filter').value.trim().toLowerCase();
  const r = await (await fetch(`/api/images?root=${encodeURIComponent(root)}&kind=${kind}&offset=${vOffset}&limit=300`)).json();
  if (!r.base){ $('v_info').textContent = 'Không thấy thư mục ảnh'; return }
  $('v_info').textContent = `${r.base} · ${r.total} ảnh`;
  r.items.filter(n => !flt || n.toLowerCase().includes(flt)).forEach(n => {
    const t = document.createElement('div'); t.className='tile';
    t.innerHTML = `<img loading="lazy" src="/img?p=${encodeURIComponent(r.base + '/' + n)}"><div>${n.replace('.png','')}</div>`;
    $('gallery').append(t); });
  vOffset += r.items.length;
}
$('btn_view').onclick = () => view(false); $('btn_more').onclick = () => view(true);
fetch('/api/jobs').then(r=>r.json()).then(list => list.forEach(j => track(j.id)));
</script></body></html>'''


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('--port', type=int, default=8765)
    p.add_argument('--bind', default='127.0.0.1', help='0.0.0.0 để máy khác trong mạng LAN truy cập')
    a = p.parse_args()
    srv = ThreadingHTTPServer((a.bind, a.port), Handler)
    print(f'NRO Res Tool đang chạy: http://{"127.0.0.1" if a.bind == "0.0.0.0" else a.bind}:{a.port}  (Ctrl+C để tắt)')
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
