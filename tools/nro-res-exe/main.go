// NroResTool: tải res (icon, part, effect, mount, aura, file res) từ server NRO.
// Chạy file exe là tự mở giao diện trên trình duyệt; kết quả lưu vào thư mục cạnh file exe.
package main

import (
	"bufio"
	"bytes"
	_ "embed"
	"encoding/binary"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"math"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

//go:embed index.html
var indexHTML []byte

var baseCmds = map[int8]bool{-32: true, -66: true, -74: true, 11: true, -67: true, -87: true, 66: true}
var partFrames = []int{3, 17, 14} // đầu / thân / chân

// ------------------------------------------------------------------ giao thức

type msg struct {
	cmd  int8
	data []byte
}

type client struct {
	conn       net.Conn
	r          *bufio.Reader
	key        []byte
	curR, curW int
	big        map[int8]bool
	qmu        sync.Mutex
	queue      []msg         // gói đã nhận, không giới hạn để luồng đọc không bao giờ bị chặn
	notify     chan struct{} // báo có gói mới
	closed     chan struct{}
	closeErr   error
	wmu        sync.Mutex
}

func dial(host string, port int, official bool) (*client, error) {
	conn, err := net.DialTimeout("tcp", net.JoinHostPort(host, strconv.Itoa(port)), 10*time.Second)
	if err != nil {
		return nil, err
	}
	big := map[int8]bool{}
	for k := range baseCmds {
		big[k] = true
	}
	if official {
		big[12] = true // server TeaMobi chính thức dùng độ dài 3 byte cho lệnh 12
	}
	return &client{conn: conn, r: bufio.NewReaderSize(conn, 1<<16), big: big, notify: make(chan struct{}, 1), closed: make(chan struct{})}, nil
}

func (c *client) enc(b byte) byte {
	r := c.key[c.curW] ^ b
	c.curW = (c.curW + 1) % len(c.key)
	return r
}

func (c *client) dec(b byte) byte {
	r := c.key[c.curR] ^ b
	c.curR = (c.curR + 1) % len(c.key)
	return r
}

func (c *client) send(cmd int8, data []byte) error {
	c.wmu.Lock()
	defer c.wmu.Unlock()
	var p []byte
	n := len(data)
	if c.key == nil {
		p = append([]byte{byte(cmd), byte(n >> 8), byte(n)}, data...)
	} else {
		p = []byte{c.enc(byte(cmd)), c.enc(byte(n >> 8)), c.enc(byte(n))}
		for _, b := range data {
			p = append(p, c.enc(b))
		}
	}
	// server ngừng nhận (bị chặn / treo) thì không treo theo: quá hạn coi như mất kết nối
	c.conn.SetWriteDeadline(time.Now().Add(20 * time.Second))
	_, err := c.conn.Write(p)
	if err != nil {
		c.conn.Close()
	}
	return err
}

func (c *client) read() (msg, error) {
	b, err := c.r.ReadByte()
	if err != nil {
		return msg{}, err
	}
	var n int
	if c.key == nil {
		var h [2]byte
		if _, err := io.ReadFull(c.r, h[:]); err != nil {
			return msg{}, err
		}
		n = int(h[0])<<8 | int(h[1])
		d := make([]byte, n)
		_, err = io.ReadFull(c.r, d)
		return msg{int8(b), d}, err
	}
	cmd := int8(c.dec(b))
	if c.big[cmd] {
		var h [3]byte
		if _, err := io.ReadFull(c.r, h[:]); err != nil {
			return msg{}, err
		}
		b1, b2, b3 := c.dec(h[0]+128), c.dec(h[1]+128), c.dec(h[2]+128)
		n = int(b1) | int(b2)<<8 | int(b3)<<16
	} else {
		var h [2]byte
		if _, err := io.ReadFull(c.r, h[:]); err != nil {
			return msg{}, err
		}
		n = int(c.dec(h[0]))<<8 | int(c.dec(h[1]))
	}
	if n > 64<<20 {
		return msg{}, fmt.Errorf("gói lệnh %d dài %d byte bất thường (lệch dữ liệu, thử bật/tắt ô Server TeaMobi chính thức)", cmd, n)
	}
	d := make([]byte, n)
	if _, err := io.ReadFull(c.r, d); err != nil {
		return msg{}, err
	}
	for i := range d {
		d[i] = c.dec(d[i])
	}
	return msg{cmd, d}, nil
}

func (c *client) handshake() error {
	if err := c.send(-27, nil); err != nil {
		return err
	}
	m, err := c.read()
	if err != nil {
		return err
	}
	if m.cmd != -27 || len(m.data) < 2 {
		return errors.New("server không trả khoá mã hoá")
	}
	n := int(m.data[0])
	key := make([]byte, n)
	key[0] = m.data[1]
	for i := 1; i < n; i++ {
		key[i] = m.data[1+i] ^ key[i-1]
	}
	c.key = key
	go func() {
		defer close(c.closed)
		for {
			m, err := c.read()
			if err != nil {
				c.closeErr = err
				return
			}
			c.qmu.Lock()
			c.queue = append(c.queue, m)
			c.qmu.Unlock()
			select {
			case c.notify <- struct{}{}:
			default:
			}
		}
	}()
	return nil
}

// bộ ghi gói dữ liệu kiểu Java DataOutputStream
type pkt struct{ bytes.Buffer }

func (p *pkt) b(v int8) *pkt { p.WriteByte(byte(v)); return p }
func (p *pkt) bo(v bool) *pkt {
	if v {
		p.WriteByte(1)
	} else {
		p.WriteByte(0)
	}
	return p
}
func (p *pkt) s(v int16) *pkt  { binary.Write(&p.Buffer, binary.BigEndian, v); return p }
func (p *pkt) i(v int32) *pkt  { binary.Write(&p.Buffer, binary.BigEndian, v); return p }
func (p *pkt) u(v string) *pkt { p.s(int16(len(v))); p.WriteString(v); return p }

type rd struct {
	d []byte
	p int
}

func (r *rd) take(n int) ([]byte, error) {
	if n < 0 || r.p+n > len(r.d) {
		return nil, io.ErrUnexpectedEOF
	}
	b := r.d[r.p : r.p+n]
	r.p += n
	return b, nil
}
func (r *rd) i8() (int8, error) {
	b, err := r.take(1)
	if err != nil {
		return 0, err
	}
	return int8(b[0]), nil
}
func (r *rd) i16() (int16, error) {
	b, err := r.take(2)
	if err != nil {
		return 0, err
	}
	return int16(binary.BigEndian.Uint16(b)), nil
}
func (r *rd) i32() (int32, error) {
	b, err := r.take(4)
	if err != nil {
		return 0, err
	}
	return int32(binary.BigEndian.Uint32(b)), nil
}
func (r *rd) utf() (string, error) {
	n, err := r.i16()
	if err != nil {
		return "", err
	}
	b, err := r.take(int(uint16(n)))
	return string(b), err
}
func (r *rd) blob() ([]byte, error) {
	n, err := r.i32()
	if err != nil {
		return nil, err
	}
	return r.take(int(n))
}

// ------------------------------------------------------------------ tải

type options struct {
	Host     string  `json:"host"`
	Port     int     `json:"port,string"`
	User     string  `json:"user"`
	Password string  `json:"password"`
	Zoom     int     `json:"zoom,string"`
	Out      string  `json:"out"`
	Icons    string  `json:"icons"`
	Mounts   string  `json:"mounts"`
	Auras    string  `json:"auras"`
	Names    string  `json:"names"`
	Effects  string  `json:"effects"`
	Parts    bool    `json:"parts"`
	Res      bool    `json:"res"`
	Official bool    `json:"official"`
	Debug    bool    `json:"debug"`
	Platform string  `json:"platform"`
	Version  string  `json:"version"`
	Batch    int     `json:"batch,string"`
	Wait     float64 `json:"wait,string"`
	Delay    float64 `json:"delay,string"`
	Skip     bool    `json:"skip"`         // bỏ qua id đã có file trong thư mục lưu
	Renew    int     `json:"renew,string"` // số yêu cầu tối đa mỗi phiên rồi kết nối lại (0 = không giới hạn)
}

type job struct {
	ID      string `json:"id"`
	Title   string `json:"title"`
	Status  string `json:"status"`
	mu      sync.Mutex
	lines   []string
	stop    chan struct{}
	stopped bool
	file    *os.File
}

func (j *job) log(f string, a ...any) {
	s := fmt.Sprintf(f, a...)
	j.mu.Lock()
	j.lines = append(j.lines, s)
	if j.file != nil {
		fmt.Fprintf(j.file, "%s  %s\n", time.Now().Format("15:04:05.000"), s)
	}
	j.mu.Unlock()
	fmt.Println("[" + j.Title + "] " + s)
}

func (j *job) isStopped() bool {
	select {
	case <-j.stop:
		return true
	default:
		return false
	}
}

type downloader struct {
	o         options
	j         *job
	c         *client
	stats     map[string]int
	frames    map[string]int
	parts     [][][3]int
	types     []int
	resEnd    bool
	got       map[string]bool // các yêu cầu đã nhận được (để không gửi lại / đếm trùng)
	hinted    bool
	waitLogin int    // server bảo chờ bao nhiêu giây mới cho đăng nhập (lệnh 122)
	inGame    bool   // đã vào bản đồ sau khi đăng nhập
	sessBytes int    // dung lượng ảnh nhận được trong phiên hiện tại
	recvCount int    // số gói ảnh/icon/effect nhận được (kể cả trùng)
	canary    []byte // yêu cầu một ảnh chắc chắn có, dùng để thử server còn trả lời không
}

// mark ghi nhận đã nhận key; trả về true nếu là lần đầu.
func (d *downloader) mark(key string) bool {
	if d.got[key] {
		return false
	}
	d.got[key] = true
	return true
}

func (d *downloader) write(rel string, data []byte) {
	p := filepath.Join(d.o.Out, filepath.FromSlash(rel))
	os.MkdirAll(filepath.Dir(p), 0o755)
	os.WriteFile(p, data, 0o644)
}

func (d *downloader) onMsg(m msg) {
	if d.o.Debug {
		head := m.data
		if len(head) > 16 {
			head = head[:16]
		}
		d.j.log("    [gói] lệnh %d, %d byte, đầu: % x", m.cmd, len(m.data), head)
	}
	r := &rd{d: m.data}
	z := strconv.Itoa(d.o.Zoom)
	switch m.cmd {
	case -67: // icon
		d.recvCount++
		id, err := r.i32()
		img, err2 := r.blob()
		if err == nil && err2 == nil {
			d.write("icon/x"+z+"/"+strconv.Itoa(int(id))+".png", img)
			if d.mark("icon:" + strconv.Itoa(int(id))) {
				d.stats["icon"]++
			}
		}
	case 66: // ảnh theo tên
		d.sessBytes += len(m.data)
		d.recvCount++
		name, err := r.utf()
		nf, _ := r.i8()
		img, err2 := r.blob()
		if err == nil && err2 == nil && len(img) > 0 {
			d.write("img_by_name/x"+z+"/"+filepath.Base(name)+".png", img)
			d.frames[name] = int(nf)
			if d.mark("img:" + name) {
				d.stats[nameLabel(name)]++
			}
		}
	case -66: // effect
		d.recvCount++
		id, err := r.i16()
		eff, err2 := r.blob()
		if err != nil || err2 != nil {
			return
		}
		if left := len(r.d) - r.p; left >= 4 {
			if int(binary.BigEndian.Uint32(r.d[r.p:]))+4 != left {
				r.p++ // client mới có thêm 1 byte loại effect
			}
		}
		img, err := r.blob()
		if err == nil {
			d.write("effdata/DataEffect_"+strconv.Itoa(int(id)), eff)
			d.write("effect/x"+z+"/ImgEffect_"+strconv.Itoa(int(id))+".png", img)
			if d.mark("eff:" + strconv.Itoa(int(id))) {
				d.stats["effect"]++
			}
		}
	case -87: // dart, arrow, effect, image, part, skill
		r.i8()
		var part []byte
		for i := 0; i < 6; i++ {
			b, err := r.blob()
			if err != nil {
				return
			}
			if i == 4 {
				part = b
			}
		}
		d.parseParts(part)
	case 122: // chờ N giây mới được đăng nhập (đăng nhập lại quá nhanh)
		if n, err := r.i16(); err == nil {
			d.waitLogin = int(n)
		}
	case -24: // thông tin bản đồ: đã vào game
		d.inGame = true
	case -26, -25:
		if s, err := r.utf(); err == nil && strings.TrimSpace(s) != "" {
			d.j.log("  Server: %s", strings.ReplaceAll(s, "\n", " "))
		}
	case -74:
		t, _ := r.i8()
		if t == 2 {
			name, err := r.utf()
			b, err2 := r.blob()
			if err == nil && err2 == nil {
				d.write("res/x"+z+"/"+filepath.Base(name), b)
				d.stats["res"]++
			}
		} else if t == 3 {
			d.resEnd = true
		}
	}
}

// nameLabel: nhóm thống kê của ảnh theo tên.
func nameLabel(name string) string {
	switch {
	case strings.HasPrefix(name, "mount_"):
		return "mount"
	case strings.HasPrefix(name, "aura_"):
		return "aura"
	}
	return "ảnh"
}

// have: file đã tải trước đó (khi bật bỏ qua file đã có).
func (d *downloader) have(rel string) bool {
	if !d.o.Skip {
		return false
	}
	st, err := os.Stat(filepath.Join(d.o.Out, filepath.FromSlash(rel)))
	return err == nil && st.Size() > 0
}

// fetch gửi các yêu cầu còn thiếu; rels là file kết quả tương ứng để bỏ qua cái đã có.
func (d *downloader) fetch(label string, reqs [][]byte, keys, rels []string, cmd int8) {
	var r2 [][]byte
	var k2 []string
	d.canary = nil
	best := int64(-1)
	for i := range reqs {
		if !d.have(rels[i]) {
			r2 = append(r2, reqs[i])
			k2 = append(k2, keys[i])
		} else if st, err := os.Stat(filepath.Join(d.o.Out, filepath.FromSlash(rels[i]))); err == nil && (best < 0 || st.Size() < best) {
			best, d.canary = st.Size(), reqs[i]
		}
	}
	if n := len(reqs) - len(r2); n > 0 {
		d.j.log("Bỏ qua %d %s đã có", n, label)
	}
	if len(r2) > 0 {
		d.batch(label, r2, k2, cmd)
	}
}

func (d *downloader) parseParts(blob []byte) {
	r := &rd{d: blob}
	n, err := r.i16()
	if err != nil {
		return
	}
	var parts [][][3]int
	var types []int
	for i := 0; i < int(n); i++ {
		t, err := r.i8()
		if err != nil || t < 0 || t > 2 {
			d.j.log("  part %d có loại lạ, dừng đọc part", i)
			return
		}
		fr := make([][3]int, partFrames[t])
		for k := range fr {
			ic, e1 := r.i16()
			dx, e2 := r.i8()
			dy, e3 := r.i8()
			if e1 != nil || e2 != nil || e3 != nil {
				return
			}
			fr[k] = [3]int{int(ic), int(dx), int(dy)}
		}
		parts = append(parts, fr)
		types = append(types, int(t))
	}
	d.parts, d.types = parts, types
}

// drain xử lý hết các gói đang chờ.
func (d *downloader) drain() {
	for {
		d.c.qmu.Lock()
		q := d.c.queue
		d.c.queue = nil
		d.c.qmu.Unlock()
		if len(q) == 0 {
			return
		}
		for _, m := range q {
			d.onMsg(m)
		}
	}
}

func (d *downloader) pump(sec float64) {
	deadline := time.After(time.Duration(sec * float64(time.Second)))
	for {
		d.drain()
		select {
		case <-d.c.notify:
		case <-deadline:
			d.drain()
			return
		case <-d.j.stop:
			return
		case <-d.c.closed:
			d.drain()
			return
		}
	}
}

func parseRange(s string) []int {
	var ids []int
	for _, p := range strings.Split(s, ",") {
		p = strings.TrimSpace(p)
		if p == "" {
			continue
		}
		if i := strings.Index(p[1:], "-"); i >= 0 {
			a, e1 := strconv.Atoi(strings.TrimSpace(p[:i+1]))
			b, e2 := strconv.Atoi(strings.TrimSpace(p[i+2:]))
			if e1 == nil && e2 == nil {
				for x := a; x <= b; x++ {
					ids = append(ids, x)
				}
			}
		} else if x, err := strconv.Atoi(p); err == nil {
			ids = append(ids, x)
		}
	}
	return ids
}

func (d *downloader) isClosed() bool {
	select {
	case <-d.c.closed:
		return true
	default:
		return false
	}
}

// reconnect: server ngắt kết nối (hết giờ chờ đăng nhập, chống spam...) thì kết nối lại.
// why khác rỗng là chủ động mở phiên mới (server ngừng trả lời / đủ giới hạn mỗi phiên).
func (d *downloader) reconnect(why string) bool {
	if why == "" {
		why = "không rõ lý do"
		if d.c.closeErr != nil {
			why = d.c.closeErr.Error()
		}
		d.j.log("  Mất kết nối (%s)", why)
		if d.o.User == "" && !d.hinted {
			d.hinted = true
			d.j.log("  Gợi ý: nhiều server ngắt kết nối nếu không đăng nhập, hãy điền tài khoản; hoặc tăng thời gian chờ / giảm số yêu cầu mỗi đợt ở mục Nâng cao")
		}
	} else {
		d.j.log("  %s, mở phiên mới (phiên vừa rồi nhận %.1f MB ảnh)...", why, float64(d.sessBytes)/1048576)
	}
	d.sessBytes = 0
	d.c.conn.Close()
	for try := 1; try <= 5 && !d.j.isStopped(); try++ {
		time.Sleep(time.Duration(try*2) * time.Second)
		d.j.log("  Kết nối lại lần %d...", try)
		if err := d.connect(); err == nil {
			return true
		} else {
			d.j.log("  Lỗi: %v", err)
		}
	}
	return false
}

func (d *downloader) batch(label string, reqs [][]byte, keys []string, cmd int8) {
	d.j.log("Tải %s: %d yêu cầu...", label, len(reqs))
	size, wait, delay := d.o.Batch, d.o.Wait, d.o.Delay
	if d.o.Official && cmd == 66 {
		// ảnh theo tên (mount, aura) rất nặng, server chính thức giới hạn lưu lượng: tải từng cái một
		size, wait, delay = 1, math.Max(wait, 8), math.Max(delay, 1.5)
		d.j.log("  Server chính thức: tải từng ảnh một, nghỉ %.1f giây giữa các ảnh", delay)
	}
	// Server chính thức chỉ trả một ít ảnh mỗi phiên rồi im (không ngắt kết nối).
	// Khi một đợt không nhận được gì: mở phiên mới (đăng nhập lại) rồi gửi lại đúng đợt đó;
	// nếu phiên mới vẫn im thì chờ lâu dần (1, 2, 5, 10 phút) trước khi mở phiên tiếp.
	// Hết các lần thử vẫn không có thì mới bỏ qua.
	// Một ảnh không có trả lời có thể do server không có ảnh đó: tạm bỏ qua, thử ảnh kế tiếp;
	// ảnh kế tiếp cũng im thì mới coi là bị giới hạn. Ảnh bỏ qua được thử lại ở cuối.
	cooldowns := []int{0, 60, 120, 300, 600}
	retries, cool, stalls, stallAt, renewedAt, sessionSent := 0, 0, 0, -1, -1, 0
	consec := 0
	var deferred []int
	var absent []string
	for k := 0; k < len(reqs) && !d.j.isStopped(); {
		end := k + size
		if end > len(reqs) {
			end = len(reqs)
		}
		if d.o.Renew > 0 && cmd == -67 && sessionSent >= d.o.Renew { // chỉ icon cần đổi phiên định kỳ
			if !d.reconnect(fmt.Sprintf("Đã gửi %d yêu cầu trong phiên này", sessionSent)) {
				d.j.log("  Dừng: không kết nối lại được server (đã tải %d/%d)", k, len(reqs))
				return
			}
			sessionSent = 0
		}
		before := len(d.got)
		sent := 0
		ok := true
		for i, p := range reqs[k:end] {
			if d.got[keys[k+i]] {
				continue // đã nhận trước khi mất kết nối
			}
			if d.c.send(cmd, p) != nil {
				ok = false
				break
			}
			sent++
			if delay > 0 {
				time.Sleep(time.Duration(delay * float64(time.Second)))
			}
		}
		sessionSent += sent
		if ok {
			d.waitKeys(keys[k:end], wait)
		}
		if !ok || d.isClosed() {
			retries++
			if retries > 20 || !d.reconnect("") {
				d.j.log("  Dừng: không kết nối lại được server (đã tải %d/%d)", k, len(reqs))
				return
			}
			sessionSent = 0
			continue // gửi lại đợt vừa mất
		}
		d.j.log("  %d/%d  đã nhận %d", end, len(reqs), d.stats[label])
		if sent == 0 || len(d.got) > before {
			cool, stalls, consec = 0, 0, 0
			if d.canary == nil {
				for i := k; i < end; i++ {
					if d.got[keys[i]] {
						d.canary = reqs[i]
						break
					}
				}
			}
			if d.o.Official && d.missing(keys[k:end]) > 0 && len(d.got) > before {
				continue // nhận được một phần: gửi lại phần còn thiếu
			}
			k = end
			continue
		}
		if d.o.Official && d.canary != nil {
			var miss []string
			for i := k; i < end; i++ {
				if !d.got[keys[i]] {
					miss = append(miss, keyName(keys[i]))
				}
			}
			d.j.log("  %s không có trả lời, thử lại một ảnh đã có để kiểm tra server...", strings.Join(miss, ", "))
			if d.alive(cmd, wait) {
				// server vẫn trả lời bình thường: các yêu cầu này không có trên server
				d.j.log("  Server vẫn trả lời: server không có %s, bỏ qua", strings.Join(miss, ", "))
				absent = append(absent, miss...)
				cool, consec = 0, 0
				k = end
				continue
			}
			d.j.log("  Server không trả lời cả ảnh kiểm tra: đang bị giới hạn")
			if cool < len(cooldowns) {
				sec := cooldowns[cool]
				cool++
				if sec > 0 {
					d.j.log("  Chờ %d giây rồi thử lại (lần %d/%d)...", sec, cool, len(cooldowns))
					d.pump(float64(sec))
				}
				if !d.reconnect(fmt.Sprintf("Bị giới hạn (lần %d/%d)", cool, len(cooldowns))) {
					d.j.log("  Dừng: không kết nối lại được server (đã tải %d/%d)", k, len(reqs))
					return
				}
				sessionSent = 0
				continue
			}
			d.j.log("  Vẫn bị giới hạn sau nhiều lần chờ, dừng. Để vài giờ sau chạy lại mục này (tích Bỏ qua file đã có).")
			return
		}
		if d.o.Official {
			consec++
			if consec == 1 && end < len(reqs) {
				d.j.log("  %s không có trả lời, tạm bỏ qua để thử cái tiếp theo", keyName(keys[k:end]...))
				for i := k; i < end; i++ {
					deferred = append(deferred, i)
				}
				k = end
				continue
			}
			if cool < len(cooldowns) {
				sec := cooldowns[cool]
				cool++
				if sec > 0 {
					d.j.log("  Phiên mới vẫn không trả lời, chờ %d giây rồi thử lại (lần %d/%d)...", sec, cool, len(cooldowns))
					d.pump(float64(sec))
				}
				if !d.reconnect(fmt.Sprintf("Server ngừng trả lời (lần %d/%d)", cool, len(cooldowns))) {
					d.j.log("  Dừng: không kết nối lại được server (đã tải %d/%d)", k, len(reqs))
					return
				}
				sessionSent = 0
				continue
			}
			d.j.log("  Bỏ qua %d yêu cầu không có trả lời", d.missing(keys[k:end]))
			for i := k; i < end; i++ {
				deferred = append(deferred, i)
			}
			cool, consec = 0, 0
			k = end
			continue
		}
		// server thường: 2 đợt liền không nhận được gì thì mở phiên mới và gửi lại;
		// tắc tiếp thì coi như các id đó không có dữ liệu
		if stalls == 0 {
			stallAt = k
		}
		stalls++
		if stalls >= 2 && stallAt != renewedAt {
			if !d.reconnect("Server ngừng trả lời") {
				d.j.log("  Dừng: không kết nối lại được server (đã tải %d/%d)", stallAt, len(reqs))
				return
			}
			renewedAt, stalls, sessionSent = stallAt, 0, 0
			k = stallAt
			continue
		}
		k = end
	}
	d.pump(d.o.Wait * 2)
	if len(absent) > 0 {
		f := "khong_co_tren_server_" + strings.ReplaceAll(label, "/", "_") + ".txt"
		d.write(f, []byte(strings.Join(absent, "\n")+"\n"))
		d.j.log("Server không có %d %s (danh sách ở %s)", len(absent), label, f)
	}
	if len(deferred) > 0 && !d.j.isStopped() {
		d.retryDeferred(label, reqs, keys, cmd, deferred, wait)
	}
}

// retryDeferred thử lại một lần các yêu cầu đã tạm bỏ qua (phiên mới), rồi ghi danh sách còn thiếu.
func (d *downloader) retryDeferred(label string, reqs [][]byte, keys []string, cmd int8, idx []int, wait float64) {
	var left []int
	for _, i := range idx {
		if !d.got[keys[i]] {
			left = append(left, i)
		}
	}
	if len(left) == 0 {
		return
	}
	d.j.log("Thử lại %d %s chưa tải được...", len(left), label)
	if !d.reconnect("Thử lại với phiên mới") {
		return
	}
	var miss []string
	silent := 0
	for _, i := range left {
		if d.j.isStopped() {
			return
		}
		if d.c.send(cmd, reqs[i]) != nil {
			return
		}
		d.waitKeys(keys[i:i+1], wait)
		if d.got[keys[i]] {
			silent = 0
			continue
		}
		miss = append(miss, keyName(keys[i]))
		if silent++; silent >= 3 {
			// 3 cái liền không có: có thể lại bị giới hạn, mở phiên mới
			if !d.reconnect("Server ngừng trả lời") {
				return
			}
			silent = 0
		}
	}
	if len(miss) > 0 {
		f := "chua_tai_duoc_" + strings.ReplaceAll(label, "/", "_") + ".txt"
		d.write(f, []byte(strings.Join(miss, "\n")+"\n"))
		d.j.log("  Còn %d %s không tải được (server không có hoặc đang giới hạn), danh sách ở %s. Để vài giờ sau tải lại mục này (tích Bỏ qua file đã có).", len(miss), label, f)
	}
}

// alive gửi lại ảnh kiểm tra (chắc chắn có) xem server còn trả lời không.
func (d *downloader) alive(cmd int8, wait float64) bool {
	before := d.recvCount
	if d.c.send(cmd, d.canary) != nil {
		return false
	}
	deadline := time.Now().Add(time.Duration(wait * float64(time.Second)))
	for time.Now().Before(deadline) && !d.j.isStopped() && !d.isClosed() {
		d.pump(0.3)
		if d.recvCount > before {
			return true
		}
	}
	return false
}

// keyName bỏ tiền tố loại (icon:, img:, eff:) cho dễ đọc.
func keyName(keys ...string) string {
	out := make([]string, len(keys))
	for i, k := range keys {
		if j := strings.Index(k, ":"); j >= 0 {
			k = k[j+1:]
		}
		out[i] = k
	}
	return strings.Join(out, ", ")
}

func (d *downloader) missing(keys []string) int {
	n := 0
	for _, k := range keys {
		if !d.got[k] {
			n++
		}
	}
	return n
}

// waitKeys chờ đến khi nhận đủ các key hoặc hết thời gian chờ.
func (d *downloader) waitKeys(keys []string, sec float64) {
	deadline := time.Now().Add(time.Duration(sec * float64(time.Second)))
	for time.Now().Before(deadline) && !d.j.isStopped() && !d.isClosed() {
		d.pump(0.3)
		if d.missing(keys) == 0 {
			return
		}
	}
}

// connect: kết nối, nhận khoá, chọn loại client và đăng nhập (nếu có tài khoản).
func (d *downloader) connect() error {
	o := &d.o
	c, err := dial(o.Host, o.Port, o.Official)
	if err != nil {
		return err
	}
	d.c = c
	if err := c.handshake(); err != nil {
		c.conn.Close()
		return err
	}
	ct := (&pkt{}).b(2).b(4).b(int8(o.Zoom)).b(0).i(int32(1280 / o.Zoom)).i(int32(720 / o.Zoom)).bo(true).bo(true).u(o.Platform)
	c.send(-29, ct.Bytes())
	d.pump(1.5)
	if o.User != "" {
		login := (&pkt{}).b(0).u(o.User).u(o.Password).u(o.Version).bo(false).Bytes()
		d.waitLogin, d.inGame = 0, false
		c.send(-29, login)
		d.pump(3)
		for try := 0; d.waitLogin > 0 && try < 3 && !d.j.isStopped() && !d.isClosed(); try++ {
			w := d.waitLogin
			d.waitLogin = 0
			d.j.log("  Server bảo chờ %d giây mới cho đăng nhập lại, đang chờ...", w)
			d.pump(float64(w + 2))
			c.send(-29, login)
			d.pump(4)
		}
		for _, sub := range []int8{6, 7, 8, 13} {
			c.send(-28, (&pkt{}).b(sub).Bytes())
		}
		c.send(-38, nil)
		d.pump(4)
		if !d.inGame && !d.isClosed() {
			d.j.log("  Cảnh báo: chưa thấy vào game sau khi đăng nhập (sai tài khoản, đang bị chờ hoặc nhân vật đang online ở máy khác)")
		}
	}
	if d.isClosed() {
		if c.closeErr != nil {
			return c.closeErr
		}
		return errors.New("server đóng kết nối")
	}
	return nil
}

func (d *downloader) run() error {
	o := &d.o
	d.j.log("Kết nối %s:%d%s...", o.Host, o.Port, map[bool]string{true: " và đăng nhập", false: ""}[o.User != ""])
	var err error
	for try := 1; try <= 3; try++ {
		if err = d.connect(); err == nil {
			break
		}
		d.j.log("  Kết nối thất bại (%v), thử lại...", err)
		time.Sleep(time.Duration(try*2) * time.Second)
	}
	if err != nil {
		return err
	}
	defer func() { d.c.conn.Close() }()
	if o.Res {
		d.j.log("Tải file res...")
		d.c.send(-74, (&pkt{}).b(1).Bytes())
		d.c.send(-74, (&pkt{}).b(2).Bytes())
		for end := time.Now().Add(10 * time.Minute); !d.resEnd && !d.j.isStopped() && time.Now().Before(end); {
			d.pump(2)
		}
		d.j.log("  %d file res", d.stats["res"])
	}
	if o.Parts {
		d.j.log("Tải part...")
		d.c.send(-87, nil)
		for end := time.Now().Add(30 * time.Second); d.parts == nil && !d.j.isStopped() && time.Now().Before(end); {
			d.pump(1)
		}
		if d.parts == nil {
			d.j.log("  không nhận được part")
		} else {
			var sb strings.Builder
			for i, fr := range d.parts {
				js, _ := json.Marshal(fr)
				fmt.Fprintf(&sb, "INSERT INTO `nr_part` (`id`, `type`, `part`) VALUES (%d, %d, '%s');\n", i, d.types[i], js)
			}
			d.write("Nr_Part.sql", []byte(sb.String()))
			d.j.log("  %d part → Nr_Part.sql", len(d.parts))
		}
	}
	z := strconv.Itoa(o.Zoom)
	if ids := parseRange(o.Icons); len(ids) > 0 {
		var reqs [][]byte
		var keys, rels []string
		for _, id := range ids {
			reqs = append(reqs, (&pkt{}).i(int32(id)).Bytes())
			keys = append(keys, "icon:"+strconv.Itoa(id))
			rels = append(rels, "icon/x"+z+"/"+strconv.Itoa(id)+".png")
		}
		d.fetch("icon", reqs, keys, rels, -67)
	}
	byName := func(label string, names []string) {
		if len(names) == 0 || d.j.isStopped() {
			return
		}
		var reqs [][]byte
		var keys, rels []string
		for _, n := range names {
			reqs = append(reqs, (&pkt{}).u(n).Bytes())
			keys = append(keys, "img:"+n)
			rels = append(rels, "img_by_name/x"+z+"/"+filepath.Base(n)+".png")
		}
		d.fetch(label, reqs, keys, rels, 66)
	}
	var mounts, auras, names []string
	for _, id := range parseRange(o.Mounts) {
		mounts = append(mounts, fmt.Sprintf("mount_%d_0", id), fmt.Sprintf("mount_%d_1", id))
	}
	for _, id := range parseRange(o.Auras) {
		auras = append(auras, fmt.Sprintf("aura_%d_0", id), fmt.Sprintf("aura_%d_1", id))
	}
	for _, n := range strings.Split(o.Names, ",") {
		if n = strings.TrimSpace(n); n != "" {
			names = append(names, n)
		}
	}
	byName("mount", mounts)
	byName("aura", auras)
	byName("ảnh", names)
	if len(d.frames) > 0 {
		jf := filepath.Join(o.Out, "img_by_name.json")
		all := map[string]int{}
		if b, err := os.ReadFile(jf); err == nil {
			json.Unmarshal(b, &all)
		}
		for k, v := range d.frames {
			all[k] = v
		}
		b, _ := json.MarshalIndent(all, "", " ")
		os.MkdirAll(o.Out, 0o755)
		os.WriteFile(jf, b, 0o644)
	}
	if ids := parseRange(o.Effects); len(ids) > 0 && !d.j.isStopped() {
		if o.User == "" {
			d.j.log("Bỏ qua effect: cần tài khoản để vào game")
		} else {
			var reqs [][]byte
			var keys, rels []string
			for _, id := range ids {
				reqs = append(reqs, (&pkt{}).s(int16(id)).Bytes())
				keys = append(keys, "eff:"+strconv.Itoa(id))
				rels = append(rels, "effect/x"+z+"/ImgEffect_"+strconv.Itoa(id)+".png")
			}
			d.fetch("effect", reqs, keys, rels, -66)
		}
	}
	keys := make([]string, 0, len(d.stats))
	for k := range d.stats {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var sum []string
	for _, k := range keys {
		sum = append(sum, fmt.Sprintf("%s: %d", k, d.stats[k]))
	}
	if len(sum) == 0 {
		sum = append(sum, "không nhận được dữ liệu mới")
	}
	d.j.log("Xong. %s", strings.Join(sum, ", "))
	d.j.log("Lưu tại: %s", o.Out)
	return nil
}

// ------------------------------------------------------------------ web

var (
	jobs   = map[string]*job{}
	order  []string
	jobsMu sync.Mutex
	exeDir string
)

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(v)
}

func resolve(p string) string {
	if p == "" {
		p = "res_out"
	}
	if !filepath.IsAbs(p) {
		p = filepath.Join(exeDir, p)
	}
	return filepath.Clean(p)
}

func startDownload(w http.ResponseWriter, r *http.Request) {
	o := options{Port: 14445, Zoom: 4, Batch: 50, Wait: 1.5, Platform: "pc|2.3.3", Version: "2.3.3"}
	if err := json.NewDecoder(r.Body).Decode(&o); err != nil {
		writeJSON(w, 400, map[string]string{"error": "Dữ liệu không hợp lệ: " + err.Error()})
		return
	}
	if strings.TrimSpace(o.Host) == "" {
		writeJSON(w, 400, map[string]string{"error": "Thiếu host"})
		return
	}
	if o.Zoom < 1 || o.Zoom > 4 {
		o.Zoom = 4
	}
	if o.Batch <= 0 {
		o.Batch = 50
	}
	if o.Wait <= 0 {
		o.Wait = 1.5
	}
	if o.Platform == "" {
		o.Platform = "pc|2.3.3"
	}
	if o.Version == "" {
		o.Version = "2.3.3"
	}
	o.Out = resolve(o.Out)
	var what []string
	for _, w := range []struct {
		on   bool
		name string
	}{{o.Res, "res"}, {o.Parts, "part"}, {strings.TrimSpace(o.Icons) != "", "icon"}, {strings.TrimSpace(o.Mounts) != "", "mount"},
		{strings.TrimSpace(o.Auras) != "", "aura"}, {strings.TrimSpace(o.Names) != "", "ảnh theo tên"}, {strings.TrimSpace(o.Effects) != "", "effect"}} {
		if w.on {
			what = append(what, w.name)
		}
	}
	if len(what) == 0 {
		writeJSON(w, 400, map[string]string{"error": "Chưa chọn mục nào để tải"})
		return
	}
	j := &job{ID: strconv.FormatInt(time.Now().UnixNano(), 36), Title: fmt.Sprintf("Tải %s từ %s:%d", strings.Join(what, ", "), o.Host, o.Port), Status: "running", stop: make(chan struct{})}
	jobsMu.Lock()
	// mỗi lần chỉ một tiến trình, tránh dồn yêu cầu làm server quá tải
	for _, id := range order {
		jobs[id].mu.Lock()
		busy := jobs[id].Status == "running"
		jobs[id].mu.Unlock()
		if busy {
			jobsMu.Unlock()
			writeJSON(w, 409, map[string]string{"error": "Đang có tiến trình tải (" + jobs[id].Title + "). Chờ xong hoặc bấm Dừng rồi tải mục khác."})
			return
		}
	}
	jobs[j.ID] = j
	order = append(order, j.ID)
	jobsMu.Unlock()
	os.MkdirAll(o.Out, 0o755)
	if f, err := os.OpenFile(filepath.Join(o.Out, "nhat_ky.txt"), os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644); err == nil {
		j.file = f
		fmt.Fprintf(f, "\n=== %s\nNroResTool "+version+"  %s  %s:%d  user=%v  official=%v  batch=%d  wait=%.1f  delay=%.2f  renew=%d  zoom=%d\n",
			j.Title, time.Now().Format("2006-01-02 15:04:05"), o.Host, o.Port, o.User != "", o.Official, o.Batch, o.Wait, o.Delay, o.Renew, o.Zoom)
	}
	go func() {
		defer func() {
			if j.file != nil {
				j.file.Close()
			}
		}()
		d := &downloader{o: o, j: j, stats: map[string]int{}, frames: map[string]int{}, got: map[string]bool{}}
		err := d.run()
		j.mu.Lock()
		defer j.mu.Unlock()
		switch {
		case err != nil:
			j.lines = append(j.lines, "Lỗi: "+err.Error())
			if j.file != nil {
				fmt.Fprintf(j.file, "Lỗi: %s\n", err.Error())
			}
			j.Status = "lỗi"
		case j.isStopped():
			j.Status = "đã dừng"
		default:
			j.Status = "done"
		}
	}()
	writeJSON(w, 200, map[string]string{"id": j.ID})
}

func jobInfo(j *job, since int) map[string]any {
	j.mu.Lock()
	defer j.mu.Unlock()
	if since > len(j.lines) || since < 0 {
		since = len(j.lines)
	}
	return map[string]any{"id": j.ID, "title": j.Title, "status": j.Status, "lines": append([]string{}, j.lines[since:]...), "total": len(j.lines)}
}

func listImages(root, kind string, offset, limit int) map[string]any {
	sub := map[string]string{"img_by_name": "img_by_name", "effect": "effect"}[kind]
	if sub == "" {
		sub = "icon"
	}
	base := ""
	for _, c := range []string{filepath.Join(root, sub, "x4"), filepath.Join(root, sub, "x3"), filepath.Join(root, sub, "x2"), filepath.Join(root, sub, "x1")} {
		if st, err := os.Stat(c); err == nil && st.IsDir() {
			base = c
			break
		}
	}
	if base == "" {
		return map[string]any{"base": nil, "items": []string{}, "total": 0}
	}
	ents, _ := os.ReadDir(base)
	var names []string
	for _, e := range ents {
		if strings.HasSuffix(strings.ToLower(e.Name()), ".png") {
			names = append(names, e.Name())
		}
	}
	num := func(s string) int {
		x, err := strconv.Atoi(strings.Map(func(r rune) rune {
			if r >= '0' && r <= '9' {
				return r
			}
			return -1
		}, s))
		if err != nil {
			return 1 << 30
		}
		return x
	}
	sort.Slice(names, func(a, b int) bool {
		if na, nb := num(names[a]), num(names[b]); na != nb {
			return na < nb
		}
		return names[a] < names[b]
	})
	if offset > len(names) {
		offset = len(names)
	}
	end := offset + limit
	if end > len(names) {
		end = len(names)
	}
	return map[string]any{"base": base, "items": names[offset:end], "total": len(names)}
}

func openBrowser(url string) {
	switch runtime.GOOS {
	case "windows":
		exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	case "darwin":
		exec.Command("open", url).Start()
	default:
		exec.Command("xdg-open", url).Start()
	}
}

// version: ghi vào nhật ký và giao diện để biết đang chạy bản nào
const version = "v6 (27/10 kiểm tra bằng ảnh có sẵn)"

func main() {
	port := flag.Int("port", 8765, "cổng giao diện web")
	bind := flag.String("bind", "127.0.0.1", "địa chỉ lắng nghe (0.0.0.0 để máy khác trong LAN mở được)")
	noBrowser := flag.Bool("no-browser", false, "không tự mở trình duyệt")
	flag.Parse()
	if exe, err := os.Executable(); err == nil {
		exeDir = filepath.Dir(exe)
	} else {
		exeDir, _ = os.Getwd()
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Write(indexHTML)
	})
	mux.HandleFunc("/api/download", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "POST", 405)
			return
		}
		startDownload(w, r)
	})
	mux.HandleFunc("/api/config", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]string{"dir": exeDir, "version": version})
	})
	mux.HandleFunc("/api/jobs", func(w http.ResponseWriter, r *http.Request) {
		jobsMu.Lock()
		var list []map[string]any
		for _, id := range order {
			j := jobs[id]
			info := jobInfo(j, 0)
			list = append(list, info)
		}
		jobsMu.Unlock()
		writeJSON(w, 200, list)
	})
	mux.HandleFunc("/api/job/", func(w http.ResponseWriter, r *http.Request) {
		jobsMu.Lock()
		j := jobs[strings.TrimPrefix(r.URL.Path, "/api/job/")]
		jobsMu.Unlock()
		if j == nil {
			writeJSON(w, 404, map[string]string{"error": "không có"})
			return
		}
		since, _ := strconv.Atoi(r.URL.Query().Get("since"))
		writeJSON(w, 200, jobInfo(j, since))
	})
	mux.HandleFunc("/api/stop/", func(w http.ResponseWriter, r *http.Request) {
		jobsMu.Lock()
		j := jobs[strings.TrimPrefix(r.URL.Path, "/api/stop/")]
		jobsMu.Unlock()
		if j != nil && !j.stopped {
			j.stopped = true
			close(j.stop)
		}
		writeJSON(w, 200, map[string]bool{"ok": true})
	})
	mux.HandleFunc("/api/images", func(w http.ResponseWriter, r *http.Request) {
		q := r.URL.Query()
		off, _ := strconv.Atoi(q.Get("offset"))
		lim, _ := strconv.Atoi(q.Get("limit"))
		if lim <= 0 {
			lim = 300
		}
		writeJSON(w, 200, listImages(resolve(q.Get("root")), q.Get("kind"), off, lim))
	})
	mux.HandleFunc("/img", func(w http.ResponseWriter, r *http.Request) {
		p := filepath.Clean(r.URL.Query().Get("p"))
		if !strings.HasSuffix(strings.ToLower(p), ".png") {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "image/png")
		http.ServeFile(w, r, p)
	})
	mux.HandleFunc("/api/open", func(w http.ResponseWriter, r *http.Request) {
		p := resolve(r.URL.Query().Get("p"))
		os.MkdirAll(p, 0o755)
		if runtime.GOOS == "windows" {
			exec.Command("explorer", p).Start()
		}
		writeJSON(w, 200, map[string]string{"path": p})
	})

	ln, err := net.Listen("tcp", fmt.Sprintf("%s:%d", *bind, *port))
	if err != nil {
		// cổng bận: chọn cổng trống
		ln, err = net.Listen("tcp", *bind+":0")
		if err != nil {
			fmt.Println("Không mở được cổng:", err)
			fmt.Scanln()
			return
		}
	}
	url := fmt.Sprintf("http://127.0.0.1:%d/", ln.Addr().(*net.TCPAddr).Port)
	fmt.Println("NRO Res Tool đang chạy:", url)
	fmt.Println("Kết quả lưu cạnh file exe:", exeDir)
	fmt.Println("Đóng cửa sổ này để tắt tool.")
	if !*noBrowser {
		openBrowser(url)
	}
	http.Serve(ln, mux)
}
