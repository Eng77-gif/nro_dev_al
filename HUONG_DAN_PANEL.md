# Panel web quản lý server

Panel chạy chung với server game, không cần cài thêm gì: dùng `HttpServer` có sẵn trong JDK. Mở bằng trình duyệt tại `http://<ip máy chủ>:8088/`.

## Cấu hình (Config.properties)

```
panel.enable=true      # false để tắt panel
panel.host=0.0.0.0     # 127.0.0.1 nếu chỉ dùng trên chính VPS (qua SSH tunnel)
panel.port=8088
panel.user=admin
panel.pass=            # ĐẶT MẬT KHẨU. Để trống thì mỗi lần chạy server sẽ in một mật khẩu tạm ra console
```

**Bảo mật**
- Đặt mật khẩu mạnh.
- Nên chỉ mở cổng 8088 cho IP của bạn (tường lửa VPS / Windows Firewall).
- Sai mật khẩu 5 lần thì khóa IP đó 5 phút.
- Mọi thao tác ghi vào `logs/panel_actions.log`.

## Các mục

| Mục | Làm được gì |
|---|---|
| Tổng quan | Người online, RAM / CPU, số luồng, boss đang có mặt, sự kiện đang chạy, danh sách online |
| Người chơi | Tìm theo tên / tài khoản / ID. Xem đồ đang mặc, hành trang, rương. Cộng / trừ vàng, ngọc, hồng ngọc. Gửi vật phẩm bất kỳ kèm chỉ số. Kick, dịch chuyển, khóa tài khoản, cấp admin. Online thì có hiệu lực ngay, offline thì sửa thẳng database |
| Tài khoản | Đổi mật khẩu, số dư, tổng nạp, khóa, admin |
| Bảng xếp hạng | Sức mạnh, các top sự kiện, Hộp đen bí ẩn, tổng nạp |
| Bang hội, Ký gửi, Nạp thẻ | Xem danh sách |
| Vật phẩm | Tìm item kèm icon, xem chỉ số mặc định, tra cứu option |
| Hộp quà | Chọn hộp có sẵn hoặc biến vật phẩm bất kỳ thành hộp. Thêm / sửa / xóa quà (vật phẩm bất kỳ, vàng / ngọc / hồng ngọc), số lượng, trọng số (tỉ lệ % tự tính), hạn dùng, % vĩnh viễn. Lưu là server nạp lại ngay |
| Giftcode | Tạo / sửa / xóa, chọn quà bằng ô tìm vật phẩm kèm chỉ số, số lượt, ngày hết hạn. Có hiệu lực ngay |
| Cửa hàng | Xem từng tab, thêm / sửa / xóa món, giá, loại tiền, ẩn / hiện. Nạp lại shop ngay |
| Bản đồ & NPC | Danh sách bản đồ (số người, số NPC). Đặt NPC bất kỳ vào bản đồ (X / Y hoặc tự chọn giữa map, mặt đất). Xóa NPC. Chọn lưu vào database hay chỉ tạm thời đến lần khởi động lại |
| Sự kiện | Bật tay / tắt tay / tự động theo lịch cho từng sự kiện, chốt top, nạp lại dữ liệu, giả lập ngày |
| Boss | Gọi boss theo ID, xem boss đang ở đâu và còn bao nhiêu HP, cho boss rời bản đồ |
| Quái | Danh sách quái |
| Thông báo & quà | Gửi dòng chữ chạy / hộp thông báo / chat thế giới. Tặng vật phẩm hoặc tiền cho mọi người đang online. Đổi tỉ lệ EXP ngay khi đang chạy |
| Bảo trì & lưu | Lưu toàn bộ người chơi. Bảo trì có đếm ngược |
| Nhật ký console | Xem log server trực tiếp, có lọc |
| Nhật ký thao tác | Ai đã làm gì trên panel |
| Cấu hình | Sửa các mục an toàn của Config.properties |
| Truy vấn SQL | Chỉ cho chạy câu đọc (SELECT / SHOW / DESCRIBE), tối đa 500 dòng |

Giao diện nằm trong `data/panel/index.html`, sửa xong chỉ cần tải lại trang. Có chế độ sáng / tối, dùng được trên điện thoại.
