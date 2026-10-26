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

> **Lưu ý:** id vật phẩm trong các mục đợt 1–5 ở trên là id cũ. Từ đợt 6, id đã xếp lại theo DataNRO; bảng đổi id cũ → mới nằm ở `sql/doi_id_vat_pham.txt`.

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
- 4 frame động tác bị thiếu trong gói đầu (17275, 17281, 17369, 17386) đã được bổ sung từ gói `Icon19th7`, part dùng lại frame gốc. Gói này có thêm avatar 17122 cho bộ Hề Xanh và các icon 17390–17393.
- Các icon 17232–17235 trên server cũ là ảnh trống, không dùng ở đâu; đã được thay bằng hình của bộ Hồn Ma Tóc Xanh.
- Còn khoảng 80 icon trong gói không gắn với part nào (frame Sharingan 17054–17088, cúp World Cup, khóa, đèn lồng Doraemon, thỏ mặt trăng…). Mình đã chép các icon này vào `data/icon`, nhưng chưa tạo vật phẩm vì gói không có thông tin để biết chúng là gì.

- Beerus, Nappa Aura và Pan Trung Thu được bán ở SHOP_VIP › tab Cải trang với giá 300 ngọc, giống các cải trang VIP khác. Chỉ số nằm trong `item_shop_option`, nên vật phẩm phát qua admin hoặc form tặng đồ cũng nhận đúng chỉ số này.

## Đợt Trung Thu / World Cup 2026 (chỉ số, vật phẩm mới, vòng quay, boss)

### Chỉ số mặc định theo vật phẩm
Bảng mới `item_template_option` (template_id, option_id, param, param_max, chance, group_id) lưu chỉ số của từng vật phẩm, hỗ trợ:
- khoảng ngẫu nhiên, ví dụ Pet Xên "17–23%";
- tỉ lệ có dòng, ví dụ Pet Kuma "10% có chống lạnh";
- nhóm chọn 1 dòng, ví dụ Lồng Đèn "1 dòng ẩn trong các dòng sau".

Hàm `getListOptionItemShop` ưu tiên đọc bảng này, nên vòng quay, boss rơi đồ và lệnh admin/tặng đồ đều ra đúng chỉ số.

### Chỉ số mới có tác dụng thật
| Option | Tên | Cách tính |
|---|---|---|
| 10 | Sát thương chuẩn #% | Khi đánh người chơi, phần % này bỏ qua né, giáp % và giáp trừ. Khi đánh boss, cộng thẳng vào sát thương. |
| 204 | Tấn công +#% lên Boss | Nhân thêm khi mục tiêu là boss |
| 251 | Sát thương cuối +#% | Nhân vào sát thương cuối cùng |
| 252 | Chống năng lượng xấu | Miễn hiệu ứng "hôi, giảm HP" của người xung quanh (ví dụ E10) |
| 253 | +#% TN, SM cho đệ tử khi sư phụ mặc | Cộng vào TN, SM đệ tử nhận |
| 254 | Akaza: HP dưới 50% tăng #% sức đánh 3 giây | Hồi chiêu 180 giây |
| 255 / 256 / 258 | Hiệu ứng đi cùng | Naruto + Pet Kuma +7% HP; Sasuke + Cờ Vạn Hoa Đồng +1% sát thương cuối; Ván bay WC 2026 + Cờ World Cup 2026 +7% chí mạng |
| 257 | Ảo Thuật (Cờ Vạn Hoa Đồng) | +50% sức đánh và giảm 50% sát thương nhận vào trong 3 giây, hồi chiêu 120 giây, tự kích hoạt khi bị đánh |

Các option của danh hiệu (chí mạng, sát thương chuẩn…) trước đây bị bỏ qua, chỉ có sức đánh/HP/KI/né có tác dụng. Giờ mọi option của danh hiệu đều được tính. Đã sửa dữ liệu danh hiệu KOL bị ghi nhầm.

### Vật phẩm
- Chỉ số theo ảnh cho Akaza, Naruto, Sasuke, Akatsuki, Beerus, Nappa Aura, Pan Trung Thu 2026, Pet Kuma (đổi tên từ Pet Cửu Vĩ), Pet Ngựa Thành Troy. Hề Xanh đổi tên thành "Cải trang chú hề Picolo" theo DataNRO.
- Vật phẩm mới, id 1980–1995:

| Id | Tên | Ghi chú |
|---|---|---|
| 1980 | Bóng Mờ Trung Thu | |
| 1981 | Lồng Đèn Dôremon | hình động đèn Doraemon trên lưng |
| 1982 | Cờ Vạn Hoa Đồng | hình động Sharingan |
| 1983–1984 | Đại Bàng Đất Sét Nổ, bản VIP | |
| 1985 | Ván bay WC 2026 | |
| 1986 | Cờ World Cup 2026 | |
| 1987 | Ván bay Trung Thu 2026 | |
| 1988 | Bóng WC 2026 | |
| 1989 | Pet Minion 2026 | |
| 1990–1992 | Pet Xên bọ hung con / hoàn thiện / Max | |
| 1993–1995 | Danh hiệu Phá Cổ Trung Thu / VIP / Perfect | |

- Vật phẩm danh hiệu (type 36) giờ dùng được để kích hoạt danh hiệu: hạn theo vật phẩm, không có hạn thì vĩnh viễn.
- Có đủ 3 Pet Xên vĩnh viễn (trong hành trang, rương hoặc trên người) thì tự nhận danh hiệu Perfect khi vào game hoặc khi mặc Pet Xên.

### Nguồn nhận
- **Vòng quay ngọc/vé:** mỗi ô có 3% ra vật phẩm đặc biệt (danh sách trong `RewardService.SPECIAL_WHEEL_ITEMS`). Mỗi món có 3% vĩnh viễn (×3 so với 1% của vật phẩm thường), còn lại có hạn 3–15 ngày.
- **Boss Super Broly:** giáp 65%. Rơi đá nâng cấp, item chí mạng, đồ thần linh (cả găng), ngọc rồng 1–4 sao, sao pha lê cấp 2 ×4–8, sao pha lê, đồ cấp 8–11 (3% ra đồ 8 sao), set Hủy Diệt 1% (từ đợt 3 là SKH Thần hủy diệt Beerus), 1 triệu vàng 5%.

### Phần tạm hoặc chưa làm được
- Gói tài nguyên không có hình nên mình tạm mượn:
  - Đại Bàng dùng hình bay của sao chổi trắng (mount 25).
  - Ván bay WC 2026 dùng tàu vàng (33); Ván bay Trung Thu 2026 dùng mây vàng (34).
  - 3 Pet Xên cùng dùng hình CT Xên nhí (chưa có bản xanh/xanh lá/đỏ).
  - Pet Minion 2026 dùng hình Pet Minion.
  - Bóng WC 2026 và Cờ World Cup 2026 dùng icon cúp.
  - Bóng Mờ Trung Thu chưa có hình trên lưng.
  - Danh hiệu mới chưa có hiệu ứng hình (idEffect 300–302).
- Tỉ lệ rơi của boss và vòng quay do mình đặt (ảnh không ghi), chỉnh trong `SuperBroly.reward` và `RewardService`.
- "Hộp đá cấp 9" và "Hộp NRO Xanh" chưa có trong database. Boss rơi đá nâng cấp thay cho hộp đá; Hộp NRO Xanh chưa rơi.
- Tanjiro, Inosuke (2 bản), Zenitsu, Nezuko, Pet cậu vàng, "Cờ Mãi Dell Thành Công" chưa có hình nên chưa tạo. Thượng Tam = Akaza đã có.
- "Hề Hước: +5% né cho người xung quanh" của chú hề Picolo mới chỉ là mô tả, chưa có hiệu ứng.

## Đợt 3: Đường Phố, SKH Beerus, bóng bước chân, hộp quà, đua top Quy Lão

### Vật phẩm
| id | Tên | Chỉ số |
|---|---|---|
| 1996 | Cải trang Bulma Đường Phố | SĐ 25%, SĐ +25.000, SĐ chí mạng 11%, chí mạng 7% |
| 1997 | Cải trang Lý Tiểu Nương Đường Phố | HP 25%, HP +30.000, giảm ST 10%, né 10% |
| 1998 | Cải trang Android 18 Đường Phố | (ảnh chưa ghi chỉ số) |
| 1973 | Cải trang Beerus (cập nhật) | SĐ 70%, SĐ lên Boss 20%, đi cùng SKH Beerus +10% chí mạng |
| 1999 | Bóng bước chân Hoa Hồng Xanh | SĐ 3–7%, SĐ +3.000–10.000, đi cùng Pet Xên hoàn thiện +6% ST chí mạng |
| 1940 | Bóng bước chân Hoa Hồng Đỏ | HP 3–7%, HP +5.000–15.000, đi cùng Pet Xên Max +5% giảm ST |
| 1941 | Ván Rồng 7 Sao | SĐ 12%, SĐ lên quái 7%, lên Boss 7%, SĐ chí mạng 7%, chí mạng 7%, hút máu 7%, vàng rơi 7% |
| 1942–1945 | Capsule vỡ, Mảnh capsule vỡ, Khóa chỉ số bông tai cấp 3, Trứng Xên | nguyên liệu |
| 1946–1951 | Thiệp chúc VIP, Hộp quà TT 2026 VIP, Hộp bánh TT 2026, Hộp bánh TT chay 2026, Quà Trăng Tròn, Quà Trăng Khuyết | hộp mở ngẫu nhiên |

Phần hình 3 cải trang Đường Phố là part 2158–2166, lấy từ `Nr_Part.sql` của gói team new 16th10. `vsData` và `vsItem` tăng lên 11 để client tải lại dữ liệu.

### SKH Thần hủy diệt Beerus
- Đồ Hủy Diệt rơi từ Super Broly (1%) mang dòng set 260 và 3 dòng mô tả 261–263.
- Mặc 2 món: +10% SĐ, +10% ST chí mạng.
- Mặc 4 món: +15% SĐ, +20% ST chí mạng.
- Mặc 5 món: +30% SĐ, +30% ST chí mạng.
- Set không có aura. Aura lấy theo thứ tự: thẻ radar, rồi aura thưởng top.

### Bóng bước chân (ô riêng)
- Dùng vật phẩm để mang vào ô riêng, không chiếm ô cờ hay ô pet. Mang món khác thì món cũ trả về hành trang.
- Chat `thao buoc chan` để tháo ra.
- Ô này lưu ở bảng `player_extra`.
- Client cũ không có ô này nên chỉ có chỉ số, chưa hiện hình dưới chân.

### Hộp quà (bảng `item_box_reward`)
- Mỗi dòng gồm vật phẩm (-1 vàng, -2 ngọc, -3 hồng ngọc), số lượng, tỉ trọng, các mức HSD và % ra vĩnh viễn. Sửa bảng rồi khởi động lại server là có tác dụng.
- Hộp 20/10 (1720) cũng dùng bảng này.
- Tỉ lệ do mình đặt, ảnh không ghi.
- Vật phẩm chưa có trong database nên chưa đưa vào hộp: Chi Chi Áo Cưới, Pan VIP, Năng lượng tà ác, Bunma Rider mới, Janemba, Pet ngựa KPI, Ván bay Magikarp, Hộp nâng cấp đồ thiên sứ, Tờ mảnh VIP, Cánh hào khí, Chi Chi giỗ tổ, Thiên sư phụ, Cờ ngọc rồng 4 sao / Super, Bóng sinh nhật NRO, Picolo Kid.
- Hiện chưa có nguồn rơi Thiệp chúc VIP và các hộp Trung Thu. Admin phát bằng lệnh `item`, hoặc thêm vào shop / boss.

### Quy Lão
- **Đua top ngọc rồng:** tặng 1 bộ ngọc rồng 1–7 sao được 1 điểm, có nút tặng tất cả và nút xem top 10.
- **Chốt top:** admin chat `chottopql` để chốt top 100 và đưa điểm về 0.
  - Người đang online nhận quà ngay. Người offline nhận khi vào game, nếu hành trang đủ chỗ.
  - Top 1–3 nhận thêm aura 122/121/120 trong 30 ngày (`Event2026Service.TOP_AURA_DAYS`).
- **Quà Trung Thu:** đổi Quà Trăng Tròn bằng 99 điểm sự kiện, Quà Trăng Khuyết bằng 20 triệu vàng.

## Đợt 4: gói Icon11th7
- Bù 6 icon đang thiếu: Đai lưng 1752–1754 (14523–14525), item 673 (15313) và 2 khung chân của part 2017 (15199, 15200).
- Thêm Pet Charmander (id 1952, part 2167–2169) vào Hộp bánh Trung Thu 2026 và Hộp bánh chay (HSD 7/15/30 ngày, 5% vĩnh viễn). Ảnh chưa ghi chỉ số nên pet chưa có chỉ số.
- `vsData` và `vsItem` tăng lên 13.
- Gói thiếu icon 17662, là 1 khung chân của Cải trang Android 18 Đường Phố (part 2166). Khung đó sẽ trống cho tới khi có icon.
- Hợp thể Bông tai cấp 3 của Trái Đất đổi sang Goku Blue cầm kiếm: 3 khung đầu động, part 2170–2174, array_head_2_frames 53. Trước đây Trái Đất dùng chung thân và chân với Xayda.
- Thêm Cải trang Poc Thỏ Đen (id 1953, part 2175–2177) vào Hộp quà Trung Thu 2026 VIP (HSD 7/15/30 ngày, 20% vĩnh viễn vì có bảo hiểm). Ảnh chưa ghi chỉ số nên chưa có chỉ số.

## Đợt 6: xếp lại id vật phẩm theo DataNRO
Nguồn: `ItemTemplates.json` của DataNRO (TeaMobi), gồm 2103 vật phẩm, id 0–2102.

**Cách ghép:**
- Ghép từng món của server với danh sách chính thức theo tên và icon. Những món từng mượn icon thì ghép tay.
- Tên lấy theo DataNRO. Riêng 5 món DataNRO chỉ đặt tên tạm "Cải trang N" (2048–2052) thì giữ tên của server.
- Icon cũng lấy theo DataNRO nếu server đã có file icon đó.
- Cột `description` chỉ dài 75 ký tự nên mô tả dài hơn bị cắt.

**Kết quả** (chi tiết ở `sql/doi_id_vat_pham.txt`):
- Giữ nguyên id: 1759 món.
- Đổi sang id chính thức: 133 món. Phần lớn nằm ở dải 1758–1999, nơi server cũ tự đánh số riêng; vd. Bông tai Porata cấp 3 1819 → 1884, Cải trang Beerus 1973 → 2082, bóng bước chân → Bóng mờ hoa hồng 2096/2097.
- Thêm 223 món của DataNRO mà server chưa có. Các món này mới chỉ có tên, icon và loại, chưa có hình mặc hay chức năng.
  - Loại 18 (pet), 19 và 38 (bóng mờ) của client mới được đổi thành loại 27.
  - Cờ không giữ `part` vì id flag_bag của server khác bản chính thức.
- 9 món riêng của server không có trong DataNRO nhưng code hoặc DB vẫn dùng: dời ra sau, id 2103–2111. Gồm Hộp thần linh, 2 thẻ Oozaru phụ, Phiếu thức ăn, Super 2/3/4 sao, Vé KOL VIP và Capsule vỡ.
- 19 món không có trong DataNRO và không còn chỗ nào dùng: bỏ, trong đó có Thiệp chúc VIP và các "Cải trang Gốc" thiếu icon.
- Các món trùng gộp lại: Pet Minion 2026 vào Pet Minion 2059, Pet Xên Max cũ vào Pet Xên bọ hung MAX 2038.

**Những chỗ đã đổi theo id mới:**
- **Bảng DB:** `item_template_option`, `item_box_reward`, `item_shop`, `data_badges`, `radar`, `shop_ky_gui`, cùng đồ trong dữ liệu người chơi và ô bước chân.
- **Code Java:** 114 dòng dùng id cứng; các số không phải id vật phẩm (thời gian 1800, id part, toạ độ…) giữ nguyên.
- **Kiểm tra Rồng nhí:** trước là khoảng 1765–1771, nay là 1814–1819 và 1929.

File `sql/team2026.sql` được xuất lại từ DB đã đổi. `vsData` và `vsItem` tăng lên 14.

**Lưu ý khi cập nhật server đang chạy:** nếu database đang có người chơi thật thì đừng import đè. Sao lưu database, rồi chạy trên chính database đó (đúng thứ tự):

1. `python3 tools/remap.py tools/DataNRO_ItemTemplates.json <tên_db> out tools/remap_code_ids.txt`
2. `mysql <tên_db> < out/remap.sql`
3. `python3 tools/remap_players.py <tên_db> out/map.json`

Bước 3 đổi luôn đồ trong hành trang, rương và trên người.

## Đợt 7: bù icon từ gói Hunr2025 và Icon11th7
- Gói Hunr2025 chỉ có 12.550 icon x4 (`Small0`–`Small32249`). Phần lớn giống icon server đã có; những icon id lớn còn lại thuộc server khác, không có vật phẩm nào dùng nên không lấy.
- Đã bù 79 icon mà vật phẩm, part, avatar, cờ hoặc thẻ radar đang dùng nhưng còn thiếu: 60 icon từ Icon11th7, 19 icon từ Hunr2025. Đã tạo đủ x1–x4.
- Phần lớn là icon của các món mới thêm từ DataNRO, vd. Ván bay Magikarp, Bóng sinh nhật NRO, Pet Pikachu/Squirtle/MewTwo, Kochiro, Musashi, Thú cưỡi rồng.
- Còn 574 icon thiếu (17410–17735 và 20097–32052) mà cả hai gói đều không có. Phần lớn thuộc các part riêng của source cũ.

## Đợt 8: Thẻ khỉ con và bảng chỉ số (theo video)
**Thẻ khỉ con** (radar id 2040, thẻ no.20):
- Sưu tầm bằng Mảnh hồn khỉ con (id 2040). Cần 10 mảnh mỗi cấp, tối đa Lv.3. Hình thẻ là Goku GT Kid.
- Chỉ số:
  - Mở khóa: Sát thương cuối +1%.
  - Sử dụng Lv.1: Sát thương cuối +1%, HP +10%, KI +10%.
  - Sử dụng Lv.2: Sát thương cuối +2%, Sức đánh +10%.
  - Sử dụng Lv.3: Sát thương cuối +2%, Chí mạng +10%, Giảm 5% sát thương.
- Shop Đổi thưởng của Quy Lão bán Mảnh hồn khỉ con giá 8250 điểm sự kiện, không giao dịch được.

**Đổi cách tính thẻ radar cho giống bản chính thức:**
- Dòng "Mở khóa" (activeCard 0) nay có tác dụng với mọi thẻ đã sưu tầm, kể cả khi không dùng. Trước đây chỉ có tác dụng với thẻ đang dùng mà còn ở cấp đầu.
- Dòng "Sử dụng Lv.x" có tác dụng khi đang dùng thẻ ở đúng cấp x.
- Chỉ số của thẻ không có xử lý riêng (vd. Sát thương cuối) nay được cộng như chỉ số đồ.
- Cấp tối đa của thẻ lấy theo cấp cao nhất ghi trong thẻ, tối thiểu là 2.

**Bảng chỉ số chi tiết:** client là Unity đã biên dịch nên không thêm được bảng nổi như video. Thay vào đó:
- Chat `chiso` để xem chỉ số của mình, `chiso de` để xem chỉ số đệ tử.
- Bảng gồm: HP, MP, Sức đánh, Chí mạng, STCM, Giáp, Giảm sát thương, Sát thương chuẩn, Sát thương cuối, Sát thương Boss, Né đòn, Hút máu, Hút KI, TNSM, Vàng rơi từ quái, và các set đang kích hoạt.
