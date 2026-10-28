# Client J2ME (Dragonboy247 v7.0) cho server riêng

`NRO_J2ME.jar` là bản Dragonboy247 v7.0 (mod của Hùng Hero, gốc client 2.4.6 – 2.4.7) đã vá để vào server **NRO 36.50.135.16:14445**. Đây là `server.ip` và `server.port` trong `Config.properties`.

## Đã sửa gì
- Client mã hóa mọi chuỗi bằng khóa `hunghero`. Công cụ giải mã, thay rồi mã hóa lại đúng 8 chuỗi:
  - Danh sách server mặc định (Việt / Indo / Global, cả bản tên miền lẫn bản IP).
  - IP mặc định `112.213.94.23`.
- Tên ứng dụng (`MIDlet-Name`) đổi thành `NRO`.
- Không biên dịch lại class nào: chỉ 3 class và MANIFEST thay đổi, 755 file còn lại giữ nguyên byte. Mọi tính năng của mod (auto đánh, tàn sát, xmap, auto nhặt…) còn nguyên.

## Đã kiểm tra khớp với server
- **Gói khai báo client** (`setClientType`): client gửi đúng thứ tự server đọc. Phiên bản `|2.4.6`, server tính ra version 246 (> 220, đủ điều kiện các phần mới).
- **Gói đăng nhập:** server đọc tài khoản và mật khẩu như client gửi.
- **Chưa test được:** môi trường làm việc không cho chạy file jar của bên thứ ba, nên chưa test client thật trên giả lập. Hãy thử trên điện thoại. Nếu không vào được, gửi ảnh màn hình để mình xem tiếp.

## Đổi IP / port khi chuyển VPS
Cần Python 3:
```
python patch_client.py Dragonboy247_v7.0.jar NRO_J2ME.jar <ip> <port> "<tên server>"
```
Ví dụ: `python patch_client.py Dragonboy247_v7.0.jar NRO_J2ME.jar 36.50.135.16 14445 NRO`.

Trong game, bản mod cũng có ô "IP / PORT" để thêm server. Client lưu vào bộ nhớ (`List_Server`) và dùng sau khi thoát game vào lại.

Lưu ý: server còn gửi danh sách server cho client từ `Config.properties` (các dòng `server.sv1=...`). Dòng `server.sv1=NroLight:fw.patus.tech:14449` đang thêm một server lạ vào danh sách, nên xóa nếu không dùng.

## Chạy mượt trên Android (J2ME Loader)
1. Cài **J2ME Loader** (CH Play hoặc F-Droid), mở và chọn file `NRO_J2ME.jar`.
2. Nhấn giữ biểu tượng game → **Cài đặt** (cấu hình riêng cho game):
   - **Kích thước màn hình:** đặt theo tỉ lệ máy, ví dụ 640×360 hoặc 720×1280 / 2. Độ phân giải càng thấp càng mượt; 320×240 cho máy yếu.
   - **Chế độ đồ họa:** chọn bản tăng tốc phần cứng (OpenGL ES / Hardware) nếu máy hỗ trợ. Nếu bị lỗi hình thì về Software.
   - **Giới hạn FPS:** 30 – 40 (máy yếu) hoặc 60 (máy mạnh). Giới hạn FPS giúp đỡ nóng máy và giật khi đông người.
   - **Bộ nhớ (heap):** 64 – 128 MB. Đông người / nhiều hiệu ứng thì để 128 MB.
   - **Bàn phím ảo:** dùng kiểu có phím mũi tên + phím mềm trái / phải. Bật "rung khi nhấn" nếu thích.
   - **Tỉ lệ / căn hình:** chọn vừa màn hình, giữ tỉ lệ.
3. Trong game: trong cài đặt của mod, tắt bớt hiệu ứng và hiển thị không cần (ẩn người / ẩn quái / ẩn item khi treo máy) để nhẹ hơn.

## Tính năng "Điều khiển từ xa" (Firebase)
Bản mod có mục **Thiết lập → Điều khiển từ xa**, mặc định **Tắt**. Nếu bật và nhập UserID, client gửi thông tin nhân vật và nhận lệnh từ Firebase `nro-controller-default-rtdb.firebaseio.com` của tác giả mod. Không cần thì cứ để Tắt.
