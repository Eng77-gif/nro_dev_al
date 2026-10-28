package nro.models.panel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import nro.models.utils.Logger;

/**
 * Panel web quản lý server (chạy chung tiến trình với server game, dùng HttpServer có sẵn của JDK).
 * Cấu hình trong Config.properties: panel.enable, panel.host, panel.port, panel.user, panel.pass.
 * Giao diện: data/panel/index.html. API: /api/... (JSON), ảnh icon: /icon/{iconId}.
 */
public class PanelServer {

    private static PanelServer instance;
    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private HttpServer http;
    private String user = "admin";
    private String passHash;
    private int port = 8088;
    private String host = "0.0.0.0";
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final Map<String, int[]> failed = new ConcurrentHashMap<>(); // ip -> {số lần sai, phút}
    private final SecureRandom random = new SecureRandom();
    private final PanelApi api = new PanelApi(this);

    /** Nhật ký console gần nhất cho trang "Nhật ký" */
    static final LogBuffer LOG = new LogBuffer(3000);

    public static PanelServer gI() {
        if (instance == null) {
            instance = new PanelServer();
        }
        return instance;
    }

    public void start() {
        try {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream("Config.properties")) {
                p.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            if ("false".equalsIgnoreCase(p.getProperty("panel.enable", "true").trim())) {
                Logger.log(Logger.YELLOW, "Panel web đang tắt (panel.enable=false)\n");
                return;
            }
            port = Integer.parseInt(p.getProperty("panel.port", "8088").trim());
            host = p.getProperty("panel.host", "0.0.0.0").trim();
            user = p.getProperty("panel.user", "admin").trim();
            String pass = p.getProperty("panel.pass", "").trim();
            if (pass.isEmpty()) {
                pass = Long.toString(Math.abs(random.nextLong()), 36);
                Logger.log(Logger.YELLOW, "Panel web: chưa đặt panel.pass trong Config.properties, mật khẩu tạm lần này: " + pass + "\n");
            }
            passHash = sha256(pass);
            captureConsole();
            http = HttpServer.create(new InetSocketAddress(host, port), 0);
            http.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "Panel web");
                t.setDaemon(true);
                return t;
            }));
            http.createContext("/", this::handle);
            http.start();
            Logger.success("Panel web quản lý: http://" + (host.equals("0.0.0.0") ? "<ip máy chủ>" : host) + ":" + port + "/ (tài khoản " + user + ")\n");
        } catch (Exception e) {
            Logger.error("Không mở được panel web: " + e.getMessage() + "\n");
        }
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
        }
    }

    // ------------------------------------------------------------------ định tuyến

    private void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(ex, path.substring(5));
            } else if (path.startsWith("/icon/")) {
                serveIcon(ex, path.substring(6));
            } else {
                serveStatic(ex);
            }
        } catch (Exception e) {
            try {
                sendJson(ex, 500, Map.of("error", String.valueOf(e.getMessage())));
            } catch (Exception ignored) {
            }
        } finally {
            ex.close();
        }
    }

    private void handleApi(HttpExchange ex, String route) throws Exception {
        String method = ex.getRequestMethod();
        Map<String, String> q = query(ex);
        Map<String, Object> body = new HashMap<>();
        if ("POST".equals(method)) {
            byte[] raw = ex.getRequestBody().readNBytes(2_000_000);
            if (raw.length > 0) {
                Map<String, Object> m = GSON.fromJson(new String(raw, StandardCharsets.UTF_8), new TypeToken<Map<String, Object>>() {
                }.getType());
                if (m != null) {
                    body = m;
                }
            }
            // chống CSRF: chỉ nhận yêu cầu từ trang panel (fetch gửi kèm header này)
            if (!"panel".equals(ex.getRequestHeaders().getFirst("X-Panel"))) {
                sendJson(ex, 403, Map.of("error", "Thiếu header X-Panel"));
                return;
            }
        }
        if (route.equals("login")) {
            login(ex, body);
            return;
        }
        String token = cookie(ex, "panel_token");
        Long exp = token == null ? null : sessions.get(token);
        if (exp == null || exp < System.currentTimeMillis()) {
            sendJson(ex, 401, Map.of("error", "Chưa đăng nhập"));
            return;
        }
        sessions.put(token, System.currentTimeMillis() + 12 * 3600_000L);
        if (route.equals("logout")) {
            sessions.remove(token);
            ex.getResponseHeaders().add("Set-Cookie", "panel_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
            sendJson(ex, 200, Map.of("ok", true));
            return;
        }
        if ("POST".equals(method)) {
            audit(ex, route, body);
        }
        Object res = api.route(route, method, q, body);
        sendJson(ex, 200, res);
    }

    private void login(HttpExchange ex, Map<String, Object> body) throws Exception {
        String ip = ex.getRemoteAddress().getAddress().getHostAddress();
        int minute = (int) (System.currentTimeMillis() / 60_000);
        int[] f = failed.get(ip);
        if (f != null && minute - f[1] < 5 && f[0] >= 5) {
            sendJson(ex, 429, Map.of("error", "Sai mật khẩu quá nhiều lần, thử lại sau 5 phút"));
            return;
        }
        String u = String.valueOf(body.getOrDefault("user", ""));
        String pw = String.valueOf(body.getOrDefault("pass", ""));
        if (!u.equals(user) || !MessageDigest.isEqual(sha256(pw).getBytes(), passHash.getBytes())) {
            failed.put(ip, f == null || minute - f[1] >= 5 ? new int[]{1, minute} : new int[]{f[0] + 1, f[1]});
            appendAudit(ip + " đăng nhập sai (" + u + ")");
            sendJson(ex, 401, Map.of("error", "Sai tài khoản hoặc mật khẩu"));
            return;
        }
        failed.remove(ip);
        byte[] b = new byte[24];
        random.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        String token = sb.toString();
        sessions.put(token, System.currentTimeMillis() + 12 * 3600_000L);
        ex.getResponseHeaders().add("Set-Cookie", "panel_token=" + token + "; Path=/; HttpOnly; SameSite=Strict");
        appendAudit(ip + " đăng nhập");
        sendJson(ex, 200, Map.of("ok", true, "user", user));
    }

    // ------------------------------------------------------------------ tệp tĩnh

    private void serveStatic(HttpExchange ex) throws IOException {
        File f = new File("data/panel/index.html");
        if (!f.exists()) {
            sendText(ex, 404, "text/plain; charset=utf-8", "Thiếu data/panel/index.html".getBytes(StandardCharsets.UTF_8));
            return;
        }
        ex.getResponseHeaders().add("Cache-Control", "no-cache");
        sendText(ex, 200, "text/html; charset=utf-8", Files.readAllBytes(f.toPath()));
    }

    private void serveIcon(HttpExchange ex, String name) throws IOException {
        String id = name.replaceAll("[^0-9]", "");
        for (String z : new String[]{"x2", "x3", "x1", "x4"}) {
            File f = new File("data/icon/" + z + "/" + id + ".png");
            if (!id.isEmpty() && f.exists()) {
                ex.getResponseHeaders().add("Cache-Control", "max-age=86400");
                sendText(ex, 200, "image/png", Files.readAllBytes(f.toPath()));
                return;
            }
        }
        ex.sendResponseHeaders(404, -1);
    }

    // ------------------------------------------------------------------ tiện ích

    static void sendJson(HttpExchange ex, int code, Object o) throws IOException {
        sendText(ex, code, "application/json; charset=utf-8", GSON.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    static void sendText(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) {
            return m;
        }
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? kv : kv.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(kv.substring(i + 1), StandardCharsets.UTF_8);
            m.put(k, v);
        }
        return m;
    }

    private static String cookie(HttpExchange ex, String name) {
        List<String> hs = ex.getRequestHeaders().get("Cookie");
        if (hs == null) {
            return null;
        }
        for (String h : hs) {
            for (String c : h.split(";")) {
                String[] kv = c.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
        }
        return null;
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void audit(HttpExchange ex, String route, Map<String, Object> body) {
        Map<String, Object> safe = new LinkedHashMap<>(body);
        safe.remove("password");
        appendAudit(ex.getRemoteAddress().getAddress().getHostAddress() + " " + route + " " + GSON.toJson(safe));
    }

    static synchronized void appendAudit(String line) {
        new File("logs").mkdirs();
        try (FileWriter w = new FileWriter("logs/panel_actions.log", StandardCharsets.UTF_8, true)) {
            w.write(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " " + line + "\n");
        } catch (IOException ignored) {
        }
    }

    /** Ghi lại console (System.out / err) vào bộ nhớ đệm để xem trên panel. */
    private void captureConsole() {
        System.setOut(new PrintStream(new TeeStream(System.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new TeeStream(System.err), true, StandardCharsets.UTF_8));
    }

    private static class TeeStream extends OutputStream {

        private final OutputStream orig;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        TeeStream(OutputStream orig) {
            this.orig = orig;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            orig.write(b);
            if (b == '\n') {
                LOG.add(line.toString(StandardCharsets.UTF_8));
                line.reset();
            } else if (line.size() < 4000) {
                line.write(b);
            }
        }

        @Override
        public void flush() throws IOException {
            orig.flush();
        }
    }

    static class LogBuffer {

        private final String[] lines;
        private long count;

        LogBuffer(int size) {
            lines = new String[size];
        }

        synchronized void add(String s) {
            lines[(int) (count % lines.length)] = s.replaceAll("\u001B\\[[;\\d]*m", "");
            count++;
        }

        /** Các dòng có số thứ tự > since; trả về {next, lines}. */
        synchronized Map<String, Object> since(long since) {
            long from = Math.max(since, count - lines.length);
            List<String> out = new ArrayList<>();
            for (long i = from; i < count; i++) {
                out.add(lines[(int) (i % lines.length)]);
            }
            return Map.of("next", count, "lines", out);
        }
    }
}
