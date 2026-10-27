// NroRepoDownloader: tải toàn bộ file server từ GitHub về máy, bỏ qua các thư mục không cần (mặc định tools/ và client/).
// Lần đầu tải 1 file zip của nhánh rồi giải nén; các lần sau so mã hash từng file, chỉ tải file mới / đã thay đổi.
package main

import (
	"archive/zip"
	"bufio"
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

const version = "1.0 (27/10/2026)"

var (
	repo     = flag.String("repo", "Eng77-gif/nro_dev_al", "repo GitHub dạng chủ/tên")
	branch   = flag.String("branch", "claude/new-session-vmt4qz", "nhánh cần tải")
	outDir   = flag.String("out", "", "thư mục lưu (mặc định: thư mục nro_server cạnh file exe)")
	exclude  = flag.String("exclude", "tools,client", "các thư mục / file bỏ qua, cách nhau dấu phẩy")
	keep     = flag.String("keep", "Config.properties,maintenanceConfig.txt", "file đã có trên máy thì giữ nguyên, không ghi đè (cấu hình riêng)")
	token    = flag.String("token", "", "GitHub token (chỉ cần khi repo riêng tư; có thể để trong file token.txt cạnh exe)")
	workers  = flag.Int("workers", 16, "số file tải cùng lúc ở chế độ cập nhật")
	zipLimit = flag.Int("zip", 3000, "thiếu nhiều hơn số file này thì tải 1 file zip cho nhanh")
	noPause  = flag.Bool("nopause", false, "không chờ bấm Enter khi xong")
)

var (
	client = &http.Client{Timeout: 0}
	logF   *os.File
)

func logf(format string, a ...any) {
	s := fmt.Sprintf(format, a...)
	fmt.Println(s)
	if logF != nil {
		fmt.Fprintf(logF, "%s  %s\n", time.Now().Format("15:04:05"), s)
	}
}

type entry struct {
	Path string `json:"path"`
	Type string `json:"type"`
	Sha  string `json:"sha"`
	Size int64  `json:"size"`
}

type tree struct {
	Tree      []entry `json:"tree"`
	Truncated bool    `json:"truncated"`
}

func request(u string) (*http.Response, error) {
	req, _ := http.NewRequest("GET", u, nil)
	req.Header.Set("User-Agent", "NroRepoDownloader")
	if *token != "" {
		req.Header.Set("Authorization", "Bearer "+*token)
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != 200 {
		b, _ := io.ReadAll(io.LimitReader(resp.Body, 500))
		resp.Body.Close()
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, strings.TrimSpace(string(b)))
	}
	return resp, nil
}

func getTree(sha string, recursive bool) (*tree, error) {
	u := fmt.Sprintf("https://api.github.com/repos/%s/git/trees/%s", *repo, url.PathEscape(sha))
	if recursive {
		u += "?recursive=1"
	}
	resp, err := request(u)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	var t tree
	if err := json.NewDecoder(resp.Body).Decode(&t); err != nil {
		return nil, err
	}
	return &t, nil
}

// listFiles đọc danh sách file (bỏ qua thư mục loại trừ). GitHub cắt bớt danh sách quá lớn,
// khi đó đọc từng thư mục con.
func listFiles(sha, prefix string, excl []string) ([]entry, error) {
	t, err := getTree(sha, true)
	if err != nil {
		return nil, err
	}
	if !t.Truncated {
		var out []entry
		for _, e := range t.Tree {
			e.Path = prefix + e.Path
			if e.Type == "blob" && !matches(e.Path, excl) {
				out = append(out, e)
			}
		}
		return out, nil
	}
	t, err = getTree(sha, false)
	if err != nil {
		return nil, err
	}
	var out []entry
	for _, e := range t.Tree {
		p := prefix + e.Path
		if matches(p, excl) {
			continue
		}
		switch e.Type {
		case "blob":
			e.Path = p
			out = append(out, e)
		case "tree":
			sub, err := listFiles(e.Sha, p+"/", excl)
			if err != nil {
				return nil, err
			}
			out = append(out, sub...)
		}
	}
	return out, nil
}

// gitSha tính mã hash git của file trên máy (giống sha trong cây GitHub) để biết file đã đúng chưa.
func gitSha(p string, size int64) string {
	f, err := os.Open(p)
	if err != nil {
		return ""
	}
	defer f.Close()
	h := sha1.New()
	fmt.Fprintf(h, "blob %d\x00", size)
	if _, err := io.Copy(h, f); err != nil {
		return ""
	}
	return hex.EncodeToString(h.Sum(nil))
}

func splitList(s string) []string {
	var out []string
	for _, x := range strings.Split(s, ",") {
		x = strings.Trim(strings.TrimSpace(strings.ReplaceAll(x, "\\", "/")), "/")
		if x != "" {
			out = append(out, x)
		}
	}
	return out
}

func matches(p string, list []string) bool {
	for _, x := range list {
		if strings.EqualFold(p, x) || strings.HasPrefix(strings.ToLower(p), strings.ToLower(x)+"/") {
			return true
		}
	}
	return false
}

// localPath chặn đường dẫn lạ (../) và đổi sang dạng của Windows.
func localPath(base, p string) (string, error) {
	c := path.Clean("/" + p)
	if c == "/" || strings.Contains(p, "..") {
		return "", errors.New("đường dẫn không hợp lệ: " + p)
	}
	return filepath.Join(base, filepath.FromSlash(strings.TrimPrefix(c, "/"))), nil
}

func writeFile(dst string, r io.Reader) (int64, error) {
	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		return 0, err
	}
	tmp := dst + ".tmp"
	f, err := os.Create(tmp)
	if err != nil {
		return 0, err
	}
	n, err := io.Copy(f, r)
	f.Close()
	if err != nil {
		os.Remove(tmp)
		return n, err
	}
	os.Remove(dst)
	return n, os.Rename(tmp, dst)
}

func human(n int64) string {
	switch {
	case n >= 1<<30:
		return fmt.Sprintf("%.2f GB", float64(n)/(1<<30))
	case n >= 1<<20:
		return fmt.Sprintf("%.1f MB", float64(n)/(1<<20))
	case n >= 1<<10:
		return fmt.Sprintf("%.0f KB", float64(n)/(1<<10))
	}
	return fmt.Sprintf("%d B", n)
}

// progressReader in tiến độ tải file zip.
type progressReader struct {
	r     io.Reader
	n     int64
	last  time.Time
	start time.Time
}

func (p *progressReader) Read(b []byte) (int, error) {
	k, err := p.r.Read(b)
	p.n += int64(k)
	if time.Since(p.last) > 2*time.Second {
		p.last = time.Now()
		sp := float64(p.n) / time.Since(p.start).Seconds()
		fmt.Printf("\r  đã tải %s  (%s/s)      ", human(p.n), human(int64(sp)))
	}
	return k, err
}

func downloadZip(base string, want map[string]entry, all bool, excl, keepL []string) (int, error) {
	zipPath := filepath.Join(base, ".nro_download.zip")
	u := fmt.Sprintf("https://codeload.github.com/%s/zip/refs/heads/%s", *repo, *branch)
	var err error
	for try := 1; try <= 3; try++ {
		logf("Tải file zip của nhánh (lần %d)... file này gồm cả thư mục bị bỏ qua nên hơi lớn, chỉ tải 1 lần", try)
		var resp *http.Response
		resp, err = request(u)
		if err == nil {
			pr := &progressReader{r: resp.Body, start: time.Now(), last: time.Now()}
			var n int64
			n, err = writeFile(zipPath, pr)
			resp.Body.Close()
			fmt.Println()
			if err == nil {
				logf("Tải xong zip %s", human(n))
				break
			}
		}
		logf("  lỗi: %v", err)
		time.Sleep(5 * time.Second)
	}
	if err != nil {
		return 0, err
	}
	defer os.Remove(zipPath)
	zr, err := zip.OpenReader(zipPath)
	if err != nil {
		return 0, err
	}
	defer zr.Close()
	count := 0
	for _, f := range zr.File {
		if f.FileInfo().IsDir() {
			continue
		}
		// bỏ thư mục gốc "nro_dev_al-<nhánh>/" của file zip
		i := strings.Index(f.Name, "/")
		if i < 0 {
			continue
		}
		p := f.Name[i+1:]
		if all {
			if matches(p, excl) {
				continue
			}
		} else if _, ok := want[p]; !ok {
			continue
		}
		dst, err := localPath(base, p)
		if err != nil {
			logf("  bỏ qua %v", err)
			continue
		}
		if matches(p, keepL) {
			if _, e := os.Stat(dst); e == nil {
				continue
			}
		}
		rc, err := f.Open()
		if err != nil {
			return count, err
		}
		_, err = writeFile(dst, rc)
		rc.Close()
		if err != nil {
			return count, fmt.Errorf("%s: %v", p, err)
		}
		count++
		if count%2000 == 0 {
			fmt.Printf("\r  đã giải nén %d file      ", count)
		}
	}
	fmt.Println()
	return count, nil
}

func downloadEach(base string, list []entry) (int, []string) {
	var done int64
	var mu sync.Mutex
	var failed []string
	jobs := make(chan entry)
	var wg sync.WaitGroup
	total := len(list)
	for w := 0; w < *workers; w++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for e := range jobs {
				segs := strings.Split(e.Path, "/")
				for i := range segs {
					segs[i] = url.PathEscape(segs[i])
				}
				u := fmt.Sprintf("https://raw.githubusercontent.com/%s/%s/%s", *repo, *branch, strings.Join(segs, "/"))
				dst, err := localPath(base, e.Path)
				for try := 1; err == nil && try <= 3; try++ {
					var resp *http.Response
					resp, err = request(u)
					if err == nil {
						_, err = writeFile(dst, resp.Body)
						resp.Body.Close()
					}
					if err == nil {
						break
					}
					if try < 3 {
						err = nil
						time.Sleep(time.Duration(try*2) * time.Second)
					}
				}
				if err != nil {
					mu.Lock()
					failed = append(failed, e.Path+": "+err.Error())
					mu.Unlock()
				}
				n := atomic.AddInt64(&done, 1)
				if n%50 == 0 || int(n) == total {
					fmt.Printf("\r  %d/%d file      ", n, total)
				}
			}
		}()
	}
	for _, e := range list {
		jobs <- e
	}
	close(jobs)
	wg.Wait()
	fmt.Println()
	return total - len(failed), failed
}

func ask(r *bufio.Reader, q, def string) string {
	fmt.Printf("%s [%s]: ", q, def)
	s, _ := r.ReadString('\n')
	s = strings.TrimSpace(s)
	if s == "" {
		return def
	}
	return s
}

func main() {
	flag.Parse()
	exe, _ := os.Executable()
	exeDir := filepath.Dir(exe)
	if *token == "" {
		if b, err := os.ReadFile(filepath.Join(exeDir, "token.txt")); err == nil {
			*token = strings.TrimSpace(string(b))
		}
	}
	in := bufio.NewReader(os.Stdin)
	fmt.Printf("NroRepoDownloader %s\nTải file server từ github.com/%s\n\n", version, *repo)
	if *outDir == "" {
		*outDir = ask(in, "Thư mục lưu", filepath.Join(exeDir, "nro_server"))
		*branch = ask(in, "Nhánh", *branch)
		*exclude = ask(in, "Bỏ qua (cách nhau dấu phẩy)", *exclude)
	}
	base, _ := filepath.Abs(*outDir)
	if err := os.MkdirAll(base, 0o755); err != nil {
		fmt.Println("Không tạo được thư mục:", err)
		pause()
		return
	}
	logF, _ = os.OpenFile(filepath.Join(base, "tai_repo_nhat_ky.txt"), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	logf("=== %s  nhánh %s  -> %s  bỏ qua: %s", time.Now().Format("2006-01-02 15:04:05"), *branch, base, *exclude)
	excl := splitList(*exclude)
	keepL := splitList(*keep)

	start := time.Now()
	files, err := listFiles(*branch, "", excl)
	if err != nil {
		logf("Không đọc được danh sách file: %v", err)
		logf("(sai tên nhánh, mất mạng, hoặc repo riêng tư thì cần token: tạo file token.txt cạnh exe)")
		pause()
		return
	}
	var need []entry
	var total, skipped int
	var needBytes int64
	want := map[string]entry{}
	logf("Kiểm tra file trên máy...")
	for _, e := range files {
		total++
		dst, err := localPath(base, e.Path)
		if err != nil {
			continue
		}
		if st, err := os.Stat(dst); err == nil {
			if matches(e.Path, keepL) {
				skipped++
				continue
			}
			if st.Size() == e.Size && gitSha(dst, e.Size) == e.Sha {
				skipped++
				continue
			}
		}
		need = append(need, e)
		want[e.Path] = e
		needBytes += e.Size
	}
	logf("%d file cần có, đã đúng %d, cần tải %d (%s)", total, skipped, len(need), human(needBytes))
	if len(need) == 0 {
		finish(0, nil, nil, start)
		return
	}
	sort.Slice(need, func(i, j int) bool { return need[i].Path < need[j].Path })
	if len(need) > *zipLimit {
		n, err := downloadZip(base, want, false, excl, keepL)
		finish(n, nil, err, start)
		return
	}
	n, failed := downloadEach(base, need)
	finish(n, failed, nil, start)
}

func finish(n int, failed []string, err error, start time.Time) {
	if err != nil {
		logf("LỖI: %v", err)
	}
	for _, f := range failed {
		logf("  lỗi %s", f)
	}
	if len(failed) > 0 {
		logf("%d file lỗi, chạy lại tool để tải tiếp phần còn thiếu", len(failed))
	}
	logf("Xong: tải %d file trong %s", n, time.Since(start).Round(time.Second))
	if logF != nil {
		logF.Close()
	}
	pause()
}

func pause() {
	if *noPause {
		return
	}
	fmt.Print("\nBấm Enter để thoát...")
	bufio.NewReader(os.Stdin).ReadString('\n')
}
