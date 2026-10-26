# Danh sách lỗi đã sửa — Teamobi2026

Commit `26569b3` là mã nguồn gốc. Mỗi commit sau đó gom một nhóm lỗi; xem `git log -p` để biết chi tiết từng thay đổi.

## 1. Nhân bản đồ / in tiền

| Khu vực | Lỗi | File |
|---|---|---|
| Ký gửi | Người mua trả **vàng thường**, người bán lại nhận **thỏi vàng** → in tiền bằng 2 tài khoản | `shop_ky_gui/ConsignShopService.java` |
| Ký gửi | Điều kiện `x > 99 && x < 0` luôn sai nên không có giới hạn số lượng/giá; không kiểm tra ô hành trang (lỗi id); hành trang đầy thì mất đồ/tiền | `ConsignShopService.java` |
| Ký gửi | Lưu bằng `TRUNCATE` + insert không transaction, lỗi giữa chừng là mất toàn bộ shop | `ConsignShopManager.java` |
| Ký gửi | Đưa lên trang đầu: kiểm tra 50 ngọc nhưng trừ 5, `isUpTop` kiểu byte bị cộng dồn đến tràn âm, không lưu DB | `npc/NpcFactory.java` |
| Giao dịch | Một người bấm "Đồng ý" 2 lần là giao dịch tự chốt dù bên kia chưa đồng ý | `services_func/Trade.java` |
| Giao dịch | Không kiểm tra số vàng thật đang có (vàng âm); hành trang bị ghi đè bằng bản sao lúc mở giao dịch; vẫn thêm được đồ sau khi khóa | `Trade.java` |
| Giao dịch với bot | Người chơi không bao giờ nhận được đồ của bot; số lượng bị làm tròn lên | `Trade.java`, `Bot/BotGiaoDich.java` |
| Nhặt đồ | `isPickedUp` không bao giờ được bật → gửi 2 gói nhặt cùng lúc thì nhận đồ 2 lần | `map/Zone.java` |
| Ghép đồ | Gửi cùng một ô hành trang 2 lần thì 1 vật phẩm được tính là 2 nguyên liệu; vẫn ghép được với đồ đã chuyển khỏi hành trang | `combine/CombineService.java` |
| Shop đặc biệt | Mua bằng ngọc **không bị trừ ngọc** | `shop/ShopService.java` |
| Shop | Giá hồng ngọc lại so với ngọc xanh; shop điểm sự kiện có giá mặc định 0; shop Chipi không thu tiền; SANTA_HEAD đổi kiểu tóc tùy ý miễn phí; trừ tiền trước rồi mới kiểm tra điều kiện | `ShopService.java` |
| Cộng tiềm năng | `point * (...)` bị tràn int → giá âm hoặc rất rẻ | `player/NPoint.java` |
| Thách đấu | Lớp cha gọi `start()` trước khi tiền cược được gán → không trừ cược nhưng người thắng vẫn nhận thưởng | `matches/ThachDau.java` |
| Form nhập | Form của admin (bot, tặng đồ, đổi tên…) không kiểm tra quyền; tặng ngọc bị tràn int (âm); tặng hồng ngọc lại trừ ngọc xanh; bán thỏi vàng nhận số âm; đổi VND không kiểm tra kết quả trừ tiền trong DB | `services_func/Input.java`, `database/PlayerDAO.java` |
| Vòng quay | Gửi số lượt âm → giá âm → được cộng ngọc/vàng | `services_func/LuckyRound.java` |
| Hàm dùng chung | `subQuantityItem` với số âm biến phép trừ thành phép cộng | `services/InventoryService.java` |
| Võ đài sinh tử | Hiển thị phí bằng "thỏi vàng" nhưng trừ vàng thường | `DeathOrAliveArenaService.java` |
| Chuyển hóa trang bị | Không kiểm tra đủ 2 tỷ vàng (vàng âm); bản "Ngọc" hiển thị 5000 ngọc nhưng lại trừ 2 tỷ vàng | `combine/ChuyenHoaTrangBi_*.java` |

## 2. Bảo mật / phân quyền

- **Bang hội**: chỉ cần gửi `clanId` là vào được bất kỳ bang nào (không cần lời mời) → giờ phải có lời mời, hiệu lực 5 phút.
- Giải tán bang ở NPC không kiểm tra quyền bang chủ; các thành viên khác vẫn giữ tham chiếu tới bang đã bị xóa.
- Bang chủ tự đuổi được chính mình (bang mất chủ).
- Mã bảo vệ 6 số dò được không giới hạn → khóa 10 phút sau 5 lần sai; trước đây còn chấp nhận cả mã âm.
- Path traversal ở lệnh xin ảnh theo tên (`../`).
- Tạo nhân vật: chấp nhận giới tính ngoài 0–2 và cho tạo nhiều nhân vật trên 1 tài khoản.
- Giftcode không bao giờ hết hạn (code so ngày tạo với ngày hết hạn); số lượt giftcode bị trừ trong race condition.

## 3. Crash / NullPointerException / logic

- Đổi đệ tử Uub/Kid Beerus/Jiren: gán `pet = null` rồi đọc `pet.gender` → crash và người chơi mất đệ tử; đổi Jiren lại tạo ra Kid Beerus.
- `MapService.getAllMaps()` gọi đệ quy vô hạn.
- `itemBodyToBox` có điều kiện kiểm tra bị đảo ngược nên chức năng cất đồ đang mặc vào rương không chạy.
- Vật phẩm loại 25 bị mặc 2 lần do switch fall-through (đồ cũ bị mặc lại).
- Danh hiệu bị thêm 2 lần; NPE khi nâng skill đệ tử; NPE ở các boss Majin Buu khi nhận sát thương không phải từ người chơi; NPE khi rồng thần rời đi; NPE ở màn hình nâng cấp vật phẩm.
- Kiểm tra trùng kẻ thù so sánh một phần tử với chính nó.
- VuaHung: `List<Integer>.contains(long)` luôn trả về false → trồng dưa nhiều lần trong một phase.
- `removeClanPlayer` gọi đệ quy lại khi lỗi DB → StackOverflowError.
- Học kỹ năng có thể làm tiềm năng âm; menu "Hủy học kỹ năng" không có code xử lý.
- Máu/sát thương boss phó bản bị tràn int ở cấp cao; x3/x5/x7 HP/KI ở Ngọc Rồng Sao Đen bị tràn khiến người chơi chết luôn.
- Minigame "Chọn ai đây": quỹ thưởng bị tràn int (người thắng nhận số âm); thông báo thắng đi qua đường chat của người chơi nên bị trừ ngọc và giới hạn thời gian.
- Giftcode giới hạn tiền ở 2 tỷ vàng / 200 triệu ngọc nên **làm giảm** tiền của người đang có nhiều hơn mức đó.

## 4. Đa luồng & cơ sở dữ liệu

- Pool kết nối DB `database.max=1` → chỉ cần một truy vấn lồng nhau là cả server treo; đã nâng lên 2/10.
- Reset quà hằng ngày kết nối cứng tới DB `ngocrong` (root, mật khẩu rỗng) thay vì DB đang dùng.
- Danh sách giao dịch, bang hội, rồng thần, giải đấu và chat thế giới được dùng chung giữa nhiều luồng mà không đồng bộ (ConcurrentModificationException); id bang bị trùng khi 2 bang được tạo cùng lúc; danh sách bang tải lên không theo thứ tự nhưng lại được tìm bằng binary search.
- `SimpleDateFormat` tĩnh dùng chung nhiều luồng; log admin không bao giờ được ghi.
- Rò rỉ statement và SQL ghép chuỗi trong phần dọn lịch sử giao dịch và cập nhật bảng xếp hạng.

## 5. Chạy được trên Linux / MySQL mới

- Code đọc `tile_set_info` nhưng file thật tên `tile_set_Info` → chỉ chạy được trên Windows.
- Vòng lệnh console ném `NoSuchElementException` khi chạy nền (không có stdin).
- `mysql-connector-java` 5.1.23 → 5.1.49 (thay thế trực tiếp, checksum khớp Maven Central). Bản 5.1.23 bị crash với MariaDB ≥ 10.10 / MySQL 8. Không nâng lên 8.x vì code dùng `rs.first()` trên ResultSet forward-only.

## Việc cần làm thêm (không sửa trong code)

- **`sql/team2026.sql` có sẵn 3 tài khoản admin `1`, `11`, `111` với mật khẩu `1`.** Hãy đổi mật khẩu hoặc xóa trước khi mở server.
- Mật khẩu tài khoản đang lưu dạng plain text (do web đăng ký quyết định).
- Lệnh khởi động lại dùng `cmd /c start restart_server.bat`, chỉ chạy được trên Windows.
- File `client/LÂU CỒ MOD/XUNGLORDLOCAL_Data/resources.assets.resS` (126 MB, vượt giới hạn 100 MB của GitHub) đã được cắt thành `.part00` + `.part01`. **Trước khi chạy client, hãy chạy `client/ghep_file_client.bat`** (Windows) hoặc `ghep_file_client.sh` để ghép lại.

## Cấu trúc sau khi gộp

- `sql/team2026.sql` là file SQL duy nhất. `nro1.sql` có cùng 41 bảng và cùng cấu trúc nhưng là bản cũ hơn; khác biệt duy nhất là 123 dòng bảng `part` mà `team2026.sql` đã cập nhật. Đã sửa 1 dòng `part` bị trùng id (vị trí 1919 bị ghi nhầm thành id 1949).
- `20.jar` được build lại từ code đã sửa (bản cũ chứa code còn lỗi). `run.bat` vẫn chạy `java -jar 20.jar` như trước.
- `data/` là tài nguyên game của server; `client/` là bản client mod; `Lệnh admin.docx` là tài liệu lệnh admin.
- `build/` và `dist/` là output cũ do NetBeans tự sinh từ code chưa sửa nên không đưa lên.

## Đã kiểm tra

- Toàn bộ mã nguồn biên dịch không lỗi (Java 17).
- SpotBugs chạy lại: tất cả lỗi mức High/Correctness liên quan đã biến mất, không có cảnh báo mới.
- Đã khởi động thật với MariaDB và `team2026.sql`: tải đủ dữ liệu, server lắng nghe cổng 14445, không có exception.
- Client test theo giao thức game: đăng nhập, tạo nhân vật (giới tính sai bị từ chối), một bên bấm đồng ý giao dịch 2 lần thì bị chặn, giao dịch hợp lệ chuyển đúng 5.000.000 vàng, đưa ra số vàng nhiều hơn số đang có thì giao dịch bị hủy.

## Vật phẩm mới (gói res team 18th7 / 22th8 / 18th9)

Đã thêm 542 icon cho cả 4 mức zoom (x1–x3 tự thu nhỏ từ x4), 59 part mới (id 2099–2157) và 19 vật phẩm vào các slot trống id 1961–1979 trong `sql/team2026.sql`:

| Id | Tên | Loại |
|---|---|---|
| 1961 | Cải trang Broly Xanh | cải trang |
| 1962 | Cải trang Naruto | cải trang |
| 1963 | Cải trang Sasuke | cải trang |
| 1964 | Cải trang Hề Xanh | cải trang |
| 1965 | Cải trang Akatsuki | cải trang |
| 1966 | Cải trang Pan Trung Thu (+100% KI) | cải trang |
| 1967 | Cải trang Kính Vàng | cải trang |
| 1968 | Cải trang Hồn Ma Tóc Xanh | cải trang |
| 1969 | Cải trang Tóc Bạc | cải trang |
| 1970 | Cải trang Tóc Trắng | cải trang |
| 1971 | Cải trang Zamasu Hợp Thể | cải trang |
| 1972 | Cải trang Akaza | cải trang |
| 1973 | Cải trang Beerus (+70% sức đánh, đầu động 3 frame) | cải trang |
| 1974 | Cải trang Nappa Aura (+100% HP) | cải trang |
| 1975 | Pet Cửu Vĩ | pet đi theo |
| 1976 | Pet Robot Đỏ | pet đi theo |
| 1977 | Pet Cừu Bông | pet đi theo |
| 1978 | Pet Hề Súng Nước | pet đi theo |
| 1979 | Pet Ngựa Gỗ | pet đi theo |

- Tên do mình đặt theo hình, hãy đổi lại trong bảng `item_template` nếu cần. Vật phẩm chưa có chỉ số (option) và chưa được bán ở shop nào. Hãy thêm qua admin, giftcode hoặc `item_shop`.
- Pet loại 27 giờ tự hoạt động theo part trong `item_template`, không cần thêm `case` trong code. Thay đổi này cũng sửa pet Ghost Rider (1900) trước đây không bao giờ hiện ra.
- `vsData` và `vsItem` được tăng lên 10 để client tải lại dữ liệu vật phẩm và part.
- Gói gốc thiếu 4 frame động tác (17275, 17281, 17369, 17386, thuộc thân bộ Tóc Bạc và thân/chân bộ Akaza). Mỗi frame thiếu được thay bằng frame liền trước.
- Các icon 17232–17235 trên server cũ là ảnh trống, không dùng ở đâu; đã được thay bằng hình của bộ Hồn Ma Tóc Xanh.
- Còn khoảng 80 icon trong gói không gắn với part nào (frame Sharingan 17054–17088, cúp World Cup, khóa, đèn lồng Doraemon, thỏ mặt trăng…). Mình đã chép các icon này vào `data/icon`, nhưng chưa tạo vật phẩm vì gói không có thông tin để biết chúng là gì.

- Beerus, Nappa Aura và Pan Trung Thu được bán ở SHOP_VIP › tab Cải trang với giá 300 ngọc, giống các cải trang VIP khác. Chỉ số nằm trong `item_shop_option`, nên vật phẩm phát qua admin hoặc form tặng đồ cũng nhận đúng chỉ số này.
