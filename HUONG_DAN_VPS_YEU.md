# Chạy server trên VPS yếu

## 1. Chọn JDK và NetBeans

| Thứ | Nên dùng | Lý do |
|---|---|---|
| JDK | **JDK 21 LTS** (Eclipse Temurin 21 hoặc Oracle JDK 21) | Server và các thư viện đã chạy thử trên JDK 21. Lombok trong `lib` là bản 1.18.30, chỉ hỗ trợ tới JDK 21. **Đừng dùng JDK 23, 24, 25**: lombok sẽ báo lỗi khi build. JDK 17 vẫn chạy được nhưng chậm hơn JDK 21 một chút. |
| NetBeans | **Apache NetBeans 21 trở lên** (22, 23… đều được) | Mở thẳng thư mục project (có sẵn `nbproject`). NetBeans 21+ chạy tốt trên JDK 21. |

Cài đặt trong NetBeans:
1. Tools → Java Platforms → Add Platform → chọn thư mục JDK 21.
2. Chuột phải project → Properties → Libraries → Java Platform: JDK 21.
3. Source/Binary Format: 17 (giữ nguyên, file jar chạy được cả trên JDK 17 lẫn 21).
4. Build: Clean and Build → file `dist/…jar`, hoặc dùng `20.jar` có sẵn.

## 2. Chạy server cho VPS yếu
- **Windows:** chạy `run_vps_yeu.bat`.
- **Linux:** chạy `./run_vps_yeu.sh`.

Mở file và sửa `RAM_MAX` cho hợp với máy:

| RAM của VPS | RAM_MAX |
|---|---|
| 1 GB | 512m |
| 2 GB | 768m |
| 4 GB trở lên | 1g – 2g, hoặc dùng `run.bat` bình thường |

Ý nghĩa các cờ:
- `-XX:+UseSerialGC`: bộ dọn rác ít tốn RAM và CPU nhất, hợp với máy 1–2 nhân.
- `-Xss512k`: mỗi luồng chiếm ít RAM hơn (server có khoảng 100 luồng cộng 1 luồng mỗi người chơi).
- `-XX:MaxMetaspaceSize=128m`, `-XX:ReservedCodeCacheSize=64m`: giới hạn vùng nhớ phụ của Java.
- `-XX:+ExitOnOutOfMemoryError`: hết RAM thì tắt hẳn, để `restart_server.bat` tự bật lại thay vì treo.

Đo trên máy test lúc không có người chơi:
- Mặc định (`run.bat`): khoảng 420 MB RAM.
- `run_vps_yeu.bat`: khoảng 160 MB RAM.

## 3. Lỗi đã sửa trong code (đỡ đầy RAM khi chạy lâu)
- **Rò rỉ luồng:** trước đây mỗi lần người chơi đăng nhập, hiện hiệu ứng, ghi log, vào phó bản… server tạo 1 luồng mới không bao giờ đóng. Chạy vài ngày là có hàng nghìn luồng chết, VPS yếu bị đầy RAM rồi treo. Đo thử: 15 lần đăng nhập để lại 13 luồng. Nay dùng chung 1 pool, luồng rảnh 60 giây tự đóng; 15 lần đăng nhập không còn để lại luồng nào.
- **Khu Ngọc Rồng Sao Đen:** các khu không có người trước đây cập nhật 7 lần/giây, nay 1 lần/giây.

## 4. MySQL cho VPS yếu
Thêm vào `my.ini` (Windows, XAMPP: `xampp/mysql/bin/my.ini`) hoặc `/etc/mysql/my.cnf` (Linux), trong mục `[mysqld]`:

```ini
innodb_buffer_pool_size = 128M   # VPS 1 GB; VPS 2 GB dùng 256M
performance_schema = OFF         # tiết kiệm khoảng 100-200 MB RAM
max_connections = 50             # server chỉ dùng tối đa 10 kết nối (database.max)
innodb_log_buffer_size = 8M
key_buffer_size = 8M
table_open_cache = 400
skip-name-resolve
```

Sửa xong khởi động lại MySQL.

## 5. Mẹo khác
- Không mở NetBeans hay trình duyệt trên VPS, mỗi thứ tốn 500 MB – 1 GB RAM. Build jar ở máy nhà rồi chép `20.jar` lên.
- Bật file swap (Linux) hoặc tăng virtual memory (Windows) khoảng 1–2 GB để tránh tắt server khi RAM vọt lên.
- `server.maxplayer` trong `Config.properties`: VPS 1 GB nên để khoảng 200–300.
