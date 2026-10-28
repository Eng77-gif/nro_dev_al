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
- Đồ Hủy Diệt rơi từ Super Broly (1%) mang dòng set 260 và 3 dòng mô tả 261–263. (Đợt 9 đổi sang đồ cấp 12.)
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
- Icon 17662 (1 khung chân của Cải Trang Póc đường phố, part 2166) đã bổ sung từ gói `resteam`.
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

## Đợt 9: Hộp đen bí ẩn và SKH Beerus theo bản chính thức
**Hộp đen bí ẩn** (id 2095):
- NPC Chi Chi bán giá 20 ngọc, có nút mua 1 hộp hoặc 10 hộp.
- Mở ra nhận ngẫu nhiên một trong các món:
  - Bóng WC 2026: có bảo hiểm, 20% vĩnh viễn
  - Cải trang Hit
  - Mảnh thẻ VIP (Tờ mảnh VIP)
  - Năng lượng tà ác
  - Pet rắn cute
  - Phượng hoàng băng
  - Pet Pikachu
  - Cải trang Goku cận vô cực
- Đồ mặc được có HSD 7/15/30 ngày hoặc vĩnh viễn. Tỉ lệ do mình đặt vì bài đăng không ghi.

**Đua top mua hộp:**
- Mỗi hộp mua được tính 1 điểm. Xem top ở Chi Chi; admin chat `chottophdba` để chốt top.
- Quà:
  - Top 1 (theo bài đăng): Danh hiệu Đón Xuân 180 ngày, Đá bảo vệ x100, Capsule 1 món kích hoạt x3, Capsule vỡ x3, Bông tai Porata cấp 3, Giáp tập luyện cấp 5.
  - Top 2–3: Đá bảo vệ x70/x50, Capsule 1 món kích hoạt x2/x1, Capsule vỡ x2/x1.
  - Top 4–10: Đá bảo vệ x20, Capsule vỡ x1.
  - Quà top 2 trở đi do mình đặt vì ảnh bài đăng bị cắt.
- Danh hiệu Đón Xuân (1978) đã có dòng trong `data_badges` (idEffect 303) để kích hoạt được, nhưng chưa có chỉ số.

**SKH Thần hủy diệt Beerus:**
- Dòng mô tả đổi theo bản chính thức: "[2] Tăng vừa phải…", "[4] Tăng đáng kể…", "[5] Tăng mạnh sức đánh và sát thương chí mạng". Đồ mang set này không giao dịch được.
- Super Broly rơi set này trên đồ cấp 12 (jean Calic / vàng Zealot / lưỡng long, Rada cấp 12) thay cho đồ Hủy Diệt.
- "Set kích hoạt Beerus 1 món tự chọn" (2102): dùng để chọn Áo/Quần/Găng/Giày/Rada đúng hành tinh.

**Chưa có chức năng:**
- Pet rắn cute và Pet Pikachu chưa có hình đi theo, vì chưa có part.
- Năng lượng tà ác chưa có hiệu ứng khi dùng.

## Đợt 10: tool tải và nhập res, sửa part 1999
**Sửa lỗi part 1999:** part đầu 1999 chỉ có 2 khung, trong khi client luôn đọc đầu đủ 3 khung. Vì vậy client đọc lệch mọi part từ 2000 trở đi, gồm cả các cải trang mới thêm. Đã thêm khung trống cho đủ 3 và tăng `vsData` lên 15 để client tải lại part.

### `tools/res_download.py`: tải res từ một server NRO
Tool đăng nhập server như một client thật, rồi tải về:
- icon (`--icons 0-20000`)
- toàn bộ part, ghi ra `Nr_Part.sql` (`--parts`)
- mount, aura và ảnh theo tên (`--mounts 0-120 --auras 0-130 --names ...`)
- effect (`--effects 0-1200`, cần đăng nhập)
- file trong `data/res` (`--res`)

Ví dụ:
```
python tools/res_download.py --host <ip> --port 14445 --user <tk> --password <mk> --icons 0-20000 --parts --mounts 0-120 --auras 0-130 --effects 0-1200 --out res_out
```
- Chỉ dùng với server bạn có quyền tải dữ liệu, bằng tài khoản của bạn.
- Server chính thức của TeaMobi có thể đòi chuỗi phiên bản hoặc thông tin client khác; đổi bằng `--platform` và `--version`.
- Tải nhanh quá có thể bị server chặn; chỉnh nhịp tải bằng `--batch`, `--wait`, `--delay`.

### `tools/res_import.py`: nhập gói res vào server
Tool đọc một thư mục gói res (gói team hoặc thư mục do `res_download.py` tải về):
- Icon, mount/aura và effect: chép vào `data/`. Ảnh chỉ có cỡ x4 được tự thu nhỏ ra x1–x3.
- Part trong `Nr_Part.sql`:
  - Mặc định chỉ thêm part dùng icon có trong gói và chưa có y hệt trong database.
  - Part mới được đánh id nối tiếp id lớn nhất hiện có; bảng đổi id thư viện → id mới ghi ở đầu file SQL.
- Mount/aura: tự thêm dòng `img_by_name` với số khung (lấy từ `img_by_name.json` hoặc tự đoán).

Ví dụ:
```
python tools/res_import.py "D:/res team new 16th10" --db team2026 --apply
```
- Thêm `--apply` để chạy SQL bằng lệnh `mysql`; không có thì chỉ ghi file `res_import.sql`.
- `--parts 2064-2072` để chỉ nhập các part chỉ định; `--overwrite` để ghi đè file đã có.
- Nhập xong nhớ tăng `vsData` để client tải lại part.

## Đợt 11: giao diện web cho tool res, sửa 2 lỗi làm client đọc sai dữ liệu
### `tools/res_web.py`: giao diện web
Chạy `python tools/res_web.py` rồi mở `http://127.0.0.1:8765` (thêm `--bind 0.0.0.0` để máy khác trong mạng LAN mở được). Trang có 3 tab:
- **Tải res:** điền host/port/tài khoản, chọn icon, part, mount, aura, effect, file res. Có ô "Server TeaMobi chính thức".
- **Nhập res:** nhập thư mục gói, hoặc kéo thả file .zip để tải lên; chọn database rồi bấm Nhập.
- **Xem:** duyệt icon, mount/aura, effect của một thư mục, có lọc theo tên/id.

Mỗi lần chạy hiện nhật ký trực tiếp và có nút Dừng. Tool chỉ cần Python 3 và Pillow.

### Lỗi 1: danh sách vật phẩm vượt 65.535 byte
- Gói `-28` chỉ có 2 byte độ dài. Từ khi có 2112 vật phẩm, gói thứ hai (id 750–2111) bị cắt, client đọc lệch mọi gói sau đó.
- Đã chia thành nhiều gói "thêm vật phẩm", mỗi gói dưới 60 KB.
- Server giờ in cảnh báo nếu còn gói nào vượt 65.535 byte.

### Lỗi 2: quá 255 dòng chỉ số
- Server gửi số lượng dòng chỉ số bằng 1 byte, nên client LÂU CỒ chỉ đọc được tối đa 255 dòng. Có 266 dòng (id 0–265), client chỉ đọc được 10.
- 11 dòng mới đã dời về các id trống hoặc chưa nơi nào dùng:

| Dòng chỉ số | Id cũ → mới |
|---|---|
| Pet Cửu Vĩ Hồ | 255 → 172 |
| Cờ Vạn Hoa Đồng | 256 → 39 |
| Ảo Thuật | 257 → 90 |
| Cờ World Cup | 258 → 91 |
| Đi cùng SKH Beerus | 259 → 92 |
| Set Beerus | 260 → 115 |
| Mô tả [2] / [4] / [5] | 261 / 262 / 263 → 163 / 167 / 168 |
| Pet Xên hoàn thiện | 264 → 169 |
| Pet Xên Max | 265 → 42 |

- Các dòng 251–254 giữ nguyên. Id ghi ở các đợt trên là id cũ.
- **Database đang chạy thật:** chạy `python tools/migrate_option_ids.py --db <tên_db>`. Script đổi luôn chỉ số trong đồ người chơi, ký gửi, danh hiệu và thẻ radar.
- `vsItem` tăng lên 15.

## Đợt 12: `tools/NroResTool.exe`, tool tải res chạy thẳng trên Windows
- Một file exe duy nhất, không cần cài Python hay Java. Bấm đúp là tool tự mở giao diện trên trình duyệt; tắt bằng cách đóng cửa sổ đen.
- Tab **Tải res**: phần **Server** điền host/port/tài khoản một lần; phần **Tải từng mục** có nút tải riêng cho:
  - res (`data/res`)
  - part (ghi ra `Nr_Part.sql`)
  - icon (id)
  - effect (id, cần tài khoản để vào game)
  - mount (id)
  - aura (id)
  - ảnh theo tên
- Mỗi lần chỉ chạy một mục để không dồn yêu cầu làm server quá tải. Ô "Bỏ qua file đã có" (bật sẵn) chỉ tải những id còn thiếu, nên server bảo trì giữa chừng thì tải lại cùng khoảng id là chạy tiếp. Nhật ký các lần tải được ghi nối vào `nhat_ky.txt`.
- Server chính thức ngừng trả lời sau khoảng 100 yêu cầu mỗi phiên: tool tự mở phiên mới (đăng nhập lại) khi 2 đợt liền không nhận được gì, và khi tích "Server TeaMobi chính thức" thì cứ 90 yêu cầu lại mở phiên mới.
- Ô "Server TeaMobi chính thức" bật thêm định dạng gói của server gốc. Nếu bị server chặn vì tải nhanh, chỉnh mục "Nâng cao" (số yêu cầu mỗi đợt, thời gian chờ).
- Tab **Xem**: duyệt icon, mount/aura, effect vừa tải.
- Kết quả lưu vào thư mục `res_out` cạnh file exe, cùng cấu trúc với `data/` của server: `icon/x4`, `img_by_name/x4`, `effect/x4`, `effdata`, `res/x4`, `Nr_Part.sql`, `img_by_name.json`.
- Server chính thức giới hạn lưu lượng (ảnh aura, mount có thể gần 1 MB mỗi ảnh). Tool xử lý như sau:
  - Tải mount/aura từng ảnh một.
  - Mỗi phiên server chỉ trả vài ảnh rồi im, nên khi server ngừng trả lời thì tool mở phiên mới ngay và gửi lại đúng chỗ đó. Phiên mới vẫn im thì chờ 1, 2, 5 rồi 10 phút trước khi mở phiên tiếp.
  - "Mở phiên mới sau N yêu cầu" chỉ áp dụng cho icon. Effect, mount, aura không tự đổi phiên nữa, vì mỗi lần đổi phiên server bắt chờ 28 giây.
  - Nhật ký và giao diện ghi số phiên bản tool để biết đang chạy bản nào.
  - Một ảnh không có trả lời: tool gửi lại một ảnh chắc chắn có (ảnh nhỏ nhất đã tải) để kiểm tra. Server trả ảnh kiểm tra thì ảnh kia không có trên server (ví dụ aura_8): bỏ qua ngay và ghi vào `khong_co_tren_server_<mục>.txt`. Không trả cả ảnh kiểm tra mới là bị giới hạn: đăng nhập lại, chờ dần lâu hơn.
  - Một ảnh không có trả lời thì tạm bỏ qua và thử cái kế tiếp (có thể server không có ảnh đó). Cái kế tiếp cũng im mới coi là bị giới hạn. Cuối cùng thử lại các ảnh đã bỏ qua với phiên mới; còn thiếu thì ghi vào `chua_tai_duoc_<mục>.txt`. Nhật ký ghi dung lượng ảnh nhận được mỗi phiên.
  - Hiểu lệnh 122 của server ("chờ N giây mới cho đăng nhập" khi đăng nhập lại quá nhanh): chờ đủ rồi tự đăng nhập lại. Nhật ký cảnh báo nếu đăng nhập xong mà chưa vào được game.
  - Chỉ bỏ qua khi chờ hết các lần trên mà vẫn không có trả lời.
- Chỉ dùng với server bạn có quyền tải dữ liệu.
- Mã nguồn ở `tools/nro-res-exe` (Go). Build lại bằng: `cd tools/nro-res-exe && GOOS=windows GOARCH=amd64 go build -ldflags "-s -w" -o ../NroResTool.exe .`


## Đợt 13: Sự kiện theo mùa (theo các bài thông báo sự kiện chính thức)
Sự kiện tự bật và tắt theo lịch mỗi năm: Tết, Giỗ tổ, Trung Thu tính theo âm lịch (thuật toán Hồ Ngọc Đức), các sự kiện khác theo dương lịch. Tất cả tham gia ở **NPC Santa** (Đảo Kamê, Đảo Guru, Vách núi đen), nút đầu tiên "Sự kiện".

| id | Sự kiện | Thời gian | Nội dung chính |
|---|---|---|---|
| 1 | Tết Nguyên Đán | mùng 1 Tết đến +17 ngày | Nấu bánh chưng, bánh tét (nhận thêm Capsule Tết 2026); top Lì xì 2026, Pháo hoa, Hộp Nón Tết; boss Lân con |
| 2 | Valentine | 10/2 - 17/2 | Hoa hồng giấy, Sôcôla đổi hộp quà |
| 3 | Phái đẹp 8/3 | 6/3 - 31/3 | Giấy màu, Hộp đựng quà, Hộp quà Nhẹ Nhàng / Chỉn Chu, tặng quà, trồng hoa hồng; top tặng hộp Chỉn Chu (danh hiệu Em xinh em đẹp) |
| 10 | Cá tháng 4 | 1/4 - 20/4 | Hộp Cá Tháng Tư (ngọc), Hộp Tháng Tư (điểm sự kiện), 2 top |
| 4 | Giỗ tổ Hùng Vương | 8/3 âm - 14/4 âm | Dâng sính lễ (Ngà voi, Cựa gà, Hồng mao), nấu và dâng Bánh chưng Lang Liêu, Bánh dầy, Hộp Hào Khí; 3 top; boss Sơn Tinh, Thủy Tinh |
| 11 | Quốc tế Thiếu nhi | 1/6 - 15/6 | Phiếu bé ngoan đổi Hộp quà thiếu nhi |
| 5 | Sự kiện Hè | 16/6 - 18/8 | Săn cá (Cá nóc, Cá bảy màu, Cá diêu hồng, Xô cá), tìm vỏ ốc, nước mía, vé World Cup; 2 top. Thức ăn cho Thần (Tayaki...) chỉ rơi trong Hè |
| 6 | Trung Thu | 21/8 - 18/10 (theo rằm tháng 8) | Làm bánh Gà Quay / hảo hạng (30% thêm Thập Cẩm) / Hạt Sen, đổi Pet Thỏ xám, Thỏ trắng, Mèo đen; Đuôi khỉ, Cà rốt; top Hộp bánh 2026 và Chay; boss Khỉ đột, Nguyệt thần |
| 7 | Halloween | 29/10 - 19/11 | Kẹo bàn tay chế Hộp Kẹo Ma Quỷ, đổi Kẹo mù, Ngọc bí ngô gọi Rồng Xương, ước nguyện 50 ngọc; 3 top; boss Bí ma, Ma trơi, Dơi |
| 8 | Nhà giáo 20/11 | 15/11 - 22/11 | Bông hoa, Thiệp chúc, Sách giáo khoa |
| 9 | Giáng sinh | 18/12 - 1/1 | Chuông, Kẹo giáng sinh, Tất; boss Ông già Noel |

- **Dữ liệu** nằm trong các bảng, sửa xong gõ `sukien reload` là áp dụng, không cần khởi động lại:
  - `season_event`: lịch (dương/âm, số ngày trước/sau ngày chính), boss gọi ra.
  - `season_event_drop`: vật phẩm rơi từ quái hoặc boss, tỉ lệ /10000, giới hạn mỗi ngày, lọc theo mẫu quái / boss / bản đồ.
  - `season_event_exchange`: công thức (nhóm nút, nguyên liệu, vàng/ngọc/hồng ngọc/điểm sự kiện); quà có HSD hoặc vĩnh viễn, quà thêm theo tỉ lệ, "mở ngay" (tặng quà NPC), điểm sự kiện, điểm top.
  - `season_event_top` + `season_event_top_reward`: đua top và quà theo hạng.
  - `item_box_reward`: quà trong hộp.
- **Đua top**:
  - Tự chốt khi hết sự kiện, quà vào hành trang khi người chơi vào game.
  - Admin chốt sớm bằng `sukien chottop <id>`, xem danh sách id bằng `sukien top`.
- **Lệnh admin**:
  - `sukien`: xem trạng thái các sự kiện.
  - `sukien bat|tat|tudong <id>`: bật tay, tắt tay, hoặc trả về theo lịch.
  - `sukien ngay yyyy-mm-dd`: giả lập ngày để thử; `sukien ngay` trả về ngày thật.
  - `sukien reload`: nạp lại dữ liệu.
- **Sửa lỗi kèm theo**:
  - Các luồng boss sự kiện (Tết, Hùng Vương, Trung Thu, Halloween, Noel) trước đây không chạy nên boss đứng im; giờ đã chạy.
  - Hộp quà giáng sinh (648) và Hộp quà giỗ tổ (1823, 1824) dùng id vật phẩm cũ trước khi xếp lại id; giờ lấy quà từ `item_box_reward`.
  - Menu Santa xếp lại theo danh sách chức năng, không còn lệch nút khi có Phiếu giảm giá.
- **Phần đơn giản hóa so với bản chính thức**:
  - Nguyên liệu lấy từ võ đài, nhiệm vụ Bò Mộng, phó bản được chuyển thành rơi khi đánh quái, có giới hạn mỗi ngày.
  - Không làm các phần sau: Ra Đa Ngọc Rồng VIP (không có vật phẩm), KOL, Hái lộc, Ngũ Hành Sơn, Thành Cổ, rước đèn, thời gian chờ trồng hoa.


## Đợt 14: Ngựa ăn cỏ (Tết), Đại chiến Zombie (Halloween), hình pet ngựa
- **Ngựa ăn cỏ** (Tết):
  - Ba ngựa con (Ngựa Đỏ, Ngựa Be, Ngựa Trắng) xuất hiện ngẫu nhiên ở các bản đồ ba hành tinh, có thông báo toàn server.
  - Ngựa không đánh ai và không bị đánh chết.
  - Đứng gần ngựa (trong khoảng 150) và dùng **Bó rơm** để cho ngựa ăn, nhận 1 Capsule Tết 2026.
  - Ngựa Đỏ có 1% cho **Pet Ngựa Đỏ** vĩnh viễn.
  - Mỗi con ăn 30 bó rơm (hoặc sau 10-15 phút) thì chạy sang bản đồ khác.
  - Bó rơm: đánh quái (99/ngày) hoặc mua 1 ngọc ở Santa.
- **Đại chiến Zombie** (Halloween):
  - Từ 21h00 đến 22h00 mỗi ngày (giờ Việt Nam), 30 Zombie lang thang ở khu 2 trở lên, không vào làng tân thủ.
  - Zombie cắn ai thì người đó hóa bộ xương 5 phút.
  - Mỗi đòn đánh chỉ trừ tối đa 1% máu Zombie nên phải hợp lực.
  - Người hạ Zombie nhận 1 phần quà ngẫu nhiên (Cá Koi zombie, Cải trang bộ xương có hạn hoặc vĩnh viễn, Ngọc bí ngô, hồng ngọc...), kèm Kẹo bàn tay rơi ra đất.
  - **Rađa dò Zombie** (1 ngọc ở Santa) hiện bản đồ và khu của các Zombie đang lang thang.
  - Admin gọi Zombie ngay để thử: `sukien zombie [phút]`.
- **Hình pet**: thêm part cho Pet Ngựa Đỏ, Ngựa Be, Ngựa Trắng, Ngựa Thần Tài, Kirby, Rồng xương. Trước đây các pet này mặc vào không hiện hình. `vsData` lên 16 để client tải lại part. Quà top Hộp bánh Trung Thu Chay đổi từ Pet ngựa chiến (chưa có hình) sang Pet Ngựa Be.
- **Sửa lỗi**: hiệu ứng hóa ma Halloween (Bí ma, Ma trơi, Dơi, Zombie) trước đây không bao giờ hết; giờ hết đúng thời gian.
- **SQL**: tất cả nằm trong một file `sql/team2026.sql`.


## Đợt 15: Halloween theo video hướng dẫn chính thức
- **Rồng Xương**:
  - Đủ 7 viên Bí ngô 1–7 sao thì dùng một viên để gọi rồng (chỉ trong sự kiện Halloween, không gọi ở làng hay nhà).
  - Rồng chờ 5 phút. Ước xong hoặc hết giờ thì rồng đi; phải chờ 1 phút mới gọi lại được.
  - 9 điều ước, chia hai trang như trong game:
    1. Đổi 3 kỹ năng đầu của đệ tử (cấp 1, có thể trùng).
    2. X3 tiềm năng sức mạnh đệ tử 30 phút.
    3. +15% HP khi hợp thể bông tai Porata 30 phút.
    4. +15% KI khi hợp thể bông tai Porata 30 phút.
    5. +15% sức đánh khi hợp thể bông tai Porata 30 phút.
    6. X3 tiềm năng sức mạnh bản thân 30 phút.
    7. Giảm 100 triệu sức mạnh (cần trên 40 tỷ).
    8. Pet Cerberus (chó địa ngục) hạn 15–90 ngày.
    9. 2% Quả trứng Mabư, hụt thì ra Rương sao pha lê VIP.
  - Thời gian các điều ước 2–6 lưu vào `player_extra.hlw_buffs`, hiện lại khi vào game.
  - Loại rồng gửi cho client mặc định là 1. Nếu client hiện sai hình, admin đổi bằng `sukien rongtype <số>`.
- **Bí ngô 1–7 sao** (tối đa 50/ngày):
  - Hạ Dơi, Ma trơi: phải mang **Giỏ đựng ngọc bí**, mất 1 giỏ mỗi lần nhặt.
  - Hạ Xên, Black Goku, Cumber: 30% ra 1 viên ngẫu nhiên, không cần giỏ.
- **Bà Hạt Mít / Santa**: Ước bằng 50 ngọc, Ước miễn phí (Vé ước miễn phí, có trong Kẹo mù).
- **Cửa hàng** theo giá trong video:
  - Giấy trang trí: 3 ngọc.
  - Giỏ đựng ngọc bí: 10 ngọc.
  - Máy dò ngọc rồng sự kiện: 30 ngọc.
  - Hòm Halloween: 99 ngọc.
  - Túi mù: 99 ngọc.
  - Capsule Halloween: 349 ngọc (Ván bay cung trăng, CT Android 21 Evil, Cánh thiên thần – Ác quỷ, Gohan Beast, Pet Ma tốc độ).
- **Túi mù Halloween** ra quà như video: Cờ hồn Xên bọ hung, Đá may mắn cấp 1, Cờ Ma đèn nhảy múa, Pet Cerberus, CT Cađíc SSJ4, Cánh thiên thần sa ngã, Cuốn sách cũ.
- **Zombie**: 10 triệu HP như trong game.
- **Hình pet**: thêm part cho Pet Cerberus.
- `season_event_drop` thêm cột `item_to` (rơi ngẫu nhiên trong khoảng id) và `need_item` (vật phẩm phải mang theo, mất 1 cái mỗi lần rơi).


## Đợt 16: Mục sự kiện ở đúng NPC như bản chính thức
- NPC nào có mục sự kiện đang diễn ra thì menu chính của NPC đó có thêm một nút ở cuối (tên sự kiện, ví dụ "Halloween"). Các nút cũ giữ nguyên vị trí. Bấm vào ra "<Sự kiện> vui vẻ / Ta có thể giúp gì cho con?" với các mục của NPC đó.
- Santa vẫn có đủ mọi mục như trước.
- Gán theo các bài thông báo và video:

| Sự kiện | NPC | Mục |
|---|---|---|
| Halloween | Bà Hạt Mít | Làm Hộp Kẹo Ma Quỷ (x1 / x10 / hết), Đổi Hộp Kẹo lấy Kẹo mù, Ước bằng 50 ngọc, Ước miễn phí |
| Halloween | Chi Chi | Shop sự kiện (Giỏ đựng kẹo, Giấy trang trí, Giỏ đựng ngọc bí, Máy dò, Capsule Halloween, Túi mù, Thiệp, Hòm, Rađa dò Zombie) |
| Tết | Nồi Bánh / Chi Chi | Nấu bánh / Cửa hàng |
| Valentine | Lý Tiểu Nương | Đổi quà |
| 8/3 | Lý Tiểu Nương, Bà Hạt Mít, Bunma / Chi Chi | Tặng quà / Cửa hàng |
| Cá tháng 4 | Chi Chi, Quy Lão | Hộp Cá Tháng Tư, Hộp Hề / Hộp Tháng Tư |
| Giỗ tổ | Hùng Vương / Nồi Bánh / Chi Chi, Quy Lão | Dâng sính lễ, Dâng bánh / Nấu bánh / Cửa hàng, Hộp Hào Khí |
| Hè | Quy Lão / Chi Chi | Xô cá, Vỏ ốc / Cửa hàng |
| Trung Thu | NPC Trung Thu / Chi Chi | Làm bánh, Đổi bánh, Đổi quà / Hộp bánh |
| Thiếu nhi, 20/11 | Chi Chi | Đổi quà |

- Gán NPC bằng cột `npc` (danh sách id mẫu NPC) trong `season_event_exchange`. Sửa rồi gõ `sukien reload` là áp dụng.

## Đợt 17: NPC sự kiện tự xuất hiện ở nhiều bản đồ
- Bảng mới `season_event_npc` (event_id, npc_id, map_id, x, y). Khi sự kiện bật, NPC trong bảng được đặt thêm vào bản đồ; khi sự kiện tắt thì tự gỡ. `x = 0` thì tự tìm chỗ trống gần giữa map (cách NPC khác ≥ 70px), `y = 0` thì lấy mặt đất.
- Dữ liệu mặc định:
  - Santa ở 3 làng (0, 7, 14).
  - Các NPC có mục sự kiện của mùa đó (Chi Chi, Bà Hạt Mít, Lý Tiểu Nương, Nồi Bánh, Hùng Vương, Trung Thu) ở 3 làng và Đảo Kamê / Đảo Guru / Vách núi đen.
  - Bản đồ đã có sẵn NPC đó thì bỏ qua. Quy Lão, Bunma giữ nguyên chỗ cũ.
- NPC đặt thêm (trừ Santa) chỉ mở các mục sự kiện của nó, nên không dính các chức năng gắn với bản đồ gốc. Santa mở được ở mọi map.
- `Map.npcs` đổi sang CopyOnWriteArrayList để thêm/gỡ NPC lúc server đang chạy. NPC mặc định (không có menu riêng) mở menu qua `createOtherMenu`, nên cũng có nút sự kiện.
- Người đang đứng trong map sẽ thấy NPC mới sau khi chuyển map hoặc đăng nhập lại.

## Đợt 18: Bổ sung icon/res tải từ server chính thức (NroResTool)
- Thêm 436 icon có trên server chính thức mà server chưa có, và 150 file res mới. Bản gốc x4, tự thu nhỏ ra x1/x2/x3.
- `DataGame.vsRes` lên 2 để client tải lại res.
- Không ghi đè 232 icon trùng số nhưng khác hình (ví dụ 11120, 11576–11655). Bộ part 1656–2081 của server này khác bản chính thức, nên thay ảnh sẽ làm lệch hình các đồ đang dùng. Cũng không nhập Nr_Part.sql chính thức, vì 434 part khác số với dữ liệu hiện tại.

## Đợt 19: Xoá dữ liệu không có trên bản chính thức
- 81 part (trong khoảng 1779–1942) dùng icon không tồn tại cả trên server lẫn bản chính thức (đa số số 20097 trở lên) được làm rỗng: mọi khung về `[0,0,0]`. Không xoá dòng, vì part được đánh số theo thứ tự dòng; xoá sẽ làm lệch mọi part phía sau. Các part này không vật phẩm, boss hay NPC nào dùng.
- Xoá 11 dòng `head_avatar` của các part đầu đó.
- 3 vật phẩm 2093 (Cải Trang Goku Ninja), 2094 (Pet ma dơi), 2099 (Cải Trang công chúa quỷ) không có icon và không có cách nhận: đổi tên thành "Vật phẩm đã xoá", icon 0. Giữ dòng để id các vật phẩm sau không lệch.
- Sau khi dọn, không còn part, vật phẩm hay avatar nào trỏ tới icon bị thiếu. `vsData` lên 17 để client tải lại part.

## Đợt 20: Giỗ tổ Hùng Vương theo video hướng dẫn 2026
- **Hùng Vương**: xuất hiện ở 3 làng khi có sự kiện, menu giống bản chính thức: Đến cổ thành, (Trồng dưa), Đổi dưa hấu, Dâng sính lễ, Dâng sính lễ xịn, Dâng bánh dầy, Dâng bánh chưng Lang Liêu. Các mục dâng tính điểm sự kiện và đua top.
- **Trồng dưa**:
  - Trồng vào phút 25–29 và 55–59 mỗi giờ (mỗi khung một lần), dưa chín đúng phút 00 / 30.
  - Thu hoạch ở nhà xong thì trồng được lượt mới.
  - Đổi dưa + Tem chứng nhận Mai An Tiêm nhận **hồng ngọc** 5 / 40 / 120 / 185 / 250 (trước là ngọc xanh).
- **Tìm sính lễ**:
  - Voi Chín Ngà (20 triệu máu, rơi Ngà voi) và Gà Chín Cựa (12 triệu máu, rơi Cựa gà) có ở mọi khu Rừng nấm khi bật sự kiện, chết thì 5 phút sau hồi sinh.
  - Hồng mao rơi từ boss.
  - Bảng mới `season_event_mob` (quái to theo sự kiện).
- **Sơn Tinh – Thủy Tinh**: hạ Thủy Tinh rơi Cải trang Sơn Tinh 7 ngày, hạ Sơn Tinh rơi Cải trang Thủy Tinh 7 ngày; 10% thêm Yêu quái núi lãng lãng 45 ngày.
- **Thành cổ**:
  - Mua Lệnh bài thành cổ (50 ngọc) ở Chi Chi, nhờ Hùng Vương đưa đến Thành cổ 1.
  - Hùng Vương ở mỗi map: Đi tiếp → Thành cổ 2 → Đấu trường thành cổ.
  - Lên sàn đấu với Gohan chiến binh (máu bằng máu người chơi, chỉ người lên sàn đánh được); thắng nhận Hộp quà thường Giỗ tổ.
- **Shop Chi Chi**:

  | Món | Giá |
  |---|---|
  | Cải trang Sơn Tinh, Cải trang Thủy Tinh (30 ngày) | 99 ngọc |
  | Tem chứng nhận | 2 ngọc |
  | Chả lụa (35 ngày) | 10 ngọc |
  | Bản đồ truyền thuyết (2 ngày) | 2 ngọc |
  | Pháo bông (30 ngày) | 10 triệu vàng |
  | Lệnh bài thành cổ (30 ngày) | 50 ngọc |

- **Hộp hào khí** (Quy Lão): thêm Cải trang Goku / Chi Chi giỗ tổ, CT Hắc Mị Nương, Mảnh hồn bông tai, Rương sao pha lê VIP, Đá Linh Hồn / Hiện Thực / Sức Mạnh / Thời Gian.
- **Top Hộp quà Cao Cấp, Dâng Bánh Dầy**: thêm Rađa ngọc rồng VIP (100 / 50 / 30 / 20 / 10 / 5).
- **Chỉ số đúng bản chính thức**:

  | Món | Chỉ số |
  |---|---|
  | Cải trang Sơn Tinh | SĐ 22%, giảm 15% sát thương, HP 27%, KI 22%, +20% TN SM, 12% né |
  | Cải trang Thủy Tinh | SĐ 27% (các chỉ số còn lại đặt giống Sơn Tinh) |
  | Yêu quái núi lãng lãng | SĐ 19%, HP 25%, KI 21%, +72% TN SM |
  | Cải trang Goku giỗ tổ | KI 35%, KI +17.000, 21% né |
  | Cải trang Chi Chi giỗ tổ | biến 15% tấn công thành HP, +30% vàng, giảm 50% SĐ / HP / KI và +150% SM TN vàng |
  | Cánh hào khí | KI 40%, KI +40.000, +10% vàng |

- **Sửa lỗi**: option "Giảm 50% sức đánh, HP, KI và +#% SM, TN, vàng" trong code dùng nhầm id 156 (trong DB là 155).

## Đợt 21: Bản đồ truyền thuyết
- Map mới **186 Rừng nguyên sinh**: bản sao map 17, 30 khu, không có cổng.
  - Thêm bằng SQL vào `map_template`, chép `data/map/tile_map_data/186` và `data/map/item_bg_map_data/186` từ map 17.
  - `vsMap` lên 3 để client tải lại danh sách map.
- Dùng **Bản đồ truyền thuyết** (1565, mua 2 ngọc ở Chi Chi):
  - Được đưa vào một khu trống riêng của Rừng nguyên sinh, có 1 trong 3 quái to (ngẫu nhiên): Voi Chín Ngà (20 triệu máu), Gà Chín Cựa (12 triệu), Ngựa Chín Hồng Mao (15 triệu).
  - Thời gian 10 phút. Hạ xong quái thì 30 giây sau, hoặc hết giờ, tự đưa về nhà.
- **Rơi đồ trong map này**: Ngà voi / Cựa gà / Hồng mao 3–5 cái (100%). Ngựa Chín Hồng Mao ở mọi nơi đều rơi Hồng mao.
- Quái 84 đổi tên "Ngựa Chín Lmao" thành "Ngựa Chín Hồng Mao".

## Đợt 22: Vòng quay Thượng Đế mùa Giỗ tổ
- Khi sự kiện Giỗ tổ Hùng Vương đang diễn ra, "Vòng quay đặc biệt" ở Thượng Đế (và quay bằng ngọc / vé) có 5% mỗi ô ra 1 trong 9 món:
  - Cải trang Mị Nương, Búa Stormbreaker, Kiếm ánh sáng, Pet Minion, Cờ mèo Hoàng Thượng
  - Cây nắp ấm, Cải trang Drabura hóa băng, Hào quang rực rỡ, CT Hắc Mị Nương
- **Quay càng nhiều tỉ lệ vĩnh viễn càng cao (cộng dồn đến hết sự kiện)**: 1% + 1% mỗi 20 lần quay, tối đa 10%; còn lại có hạn 3–15 ngày. Sau mỗi lượt quay có thông báo số lần đã quay và tỉ lệ hiện tại.
  - Số lần quay lưu trong `season_event_point` (top_id âm = bộ đếm theo mùa, ví dụ -401), khởi động lại server vẫn giữ.
  - Sự kiện kết thúc thật (hết lịch, hoặc admin `sukien tudong` khi đã quá ngày) thì tự xoá cùng lúc chốt top.
- Chỉ số:
  - Cải trang Mị Nương theo ảnh bản chính thức: SĐ 24%, Đẹp +20% SĐ cho mình và người xung quanh, +20% tốc độ chạy, HP 24%.
  - 7 món còn lại chưa có chỉ số được đặt mặc định:
    - Drabura: 20–25%
    - Các món đeo: 8–12%
    - Pet Minion: 6–10%
- **Sửa lỗi**: nút "Vòng quay đặc biệt" trước đây đi vào nhánh quay thường, nên vật phẩm đặc biệt (cả danh sách Trung Thu / World Cup) không bao giờ ra.

## Đợt 23: Hình đi theo cho 54 pet (lấy từ dữ liệu chính thức)
- Đối chiếu ảnh icon pet của server với 133 pet trong `Nr_Part.sql` tải từ server chính thức (NroResTool).
- Mỗi pet có 3 part mới (id 2193–2348): đầu trống, thân và chân chép từ part chính thức. Gắn vào `head/body/leg` của vật phẩm. Pet trùng hình dùng chung part.
- **Icon**: 36 icon trùng số nhưng khác hình được chép sang số mới (sau 32237) để không đè icon đang dùng, đủ x1–x4.
- **Đã có hình**:
  - Pokemon: Pikachu, Squirtle, Bulbasaur, MewTwo, Meowth
  - Thú: Rắn cute, Đại bàng, Hổ, Hươu, Mèo thần tài, Cậu vàng
  - Godzilla, Kong, Po, Shimo, Shiba (2 bản), Ông già Noel, Albart (2 bản), Minion, Minion Otto, Baby Shark (3 bản)
  - Capybara (3 bản), Zịt vàng, Mini Desutoron Gas, Hải Ly (2 bản), Giru, Rồng Xanh, Tuần Lộc Cute, Cá mập Koi
  - Secret, Rabbit, Dino, Panda, Mắt Long Lanh, Mắt Rưng, Thỏ Vui Vẻ, Cá Dory, Cá Nemo, Gấu Dâu
  - Ngựa KPI, Bư Noel, Tuần lộc ngố, Ngựa chiến, Ma vàng phù thủy, Bọ cánh cứng, Ngài đêm, Heo bướm
- **Chưa tìm thấy hình chính thức**:
  - Khỉ bong bóng, các Pet mèo đuôi vàng, Pet Voi / Gà / Ngựa chín
  - Mông Quỷ, Mèo Phù Thủy, Chim cánh cụt, Rồng Pikachu thanh long, Rồng con thần tài
  - Thỏ Ú, Ma cầu mưa, Khủng Long ngok
- `vsData` lên 18 để client tải lại part.
- Script: `pets_import.py` (bảng ghép vật phẩm → part chính thức nằm trong script).

## Đợt 24: Hình đeo lưng còn thiếu
- **Hình đeo lưng mới** (khung động lấy từ icon chính thức, thêm vào `flag_bag`):

  | Món | flag_bag | Hình | Icon khung |
  |---|---|---|---|
  | Bóng WC 2026 | 159 | quả bóng nhỏ xoay | 32274–32277 (chép sang số mới vì số chính thức 16931–16934 trùng icon khác) |
  | Cờ World Cup 2026 | 160 | cúp vàng nhỏ | 17089–17092 |
  | Đeo lưng Mãi Dell thành công | 161 | ngựa "mã đáo thành công" | 16573–16576 |

- **Icon vật phẩm** của Bóng WC 2026 (16935) và Pet đại bàng (16930) trước là ảnh trống 1×1, nay dùng ảnh chính thức.
- **Chưa làm**:
  - Bóng mờ Trung Thu chưa tìm thấy khung đeo lưng.
  - Ván bay WC / Trung Thu là thú cưỡi, cần ảnh `mount_...` (mục Mount của NroResTool), vẫn đang mượn hình cũ.

## Đợt 25: Chỉ số theo ảnh vật phẩm bản chính thức
| id | Món | Chỉ số |
|---|---|---|
| 2086 | Cải Trang Póc đường phố | HP 27%, giảm 20% sát thương, 20% né, giảm 20% tác dụng khống chế khi dùng khiên |
| 1503 / 1504 / 1512 | CT Lý Tiểu Nương hầu gái xanh / hồng / Bikini | SĐ 22%, HP 21%, KI 21%, chí mạng 10%, né chí mạng 10%, +5% SĐ (tối đa 12%) khi ở gần Lý Tiểu Nương hầu gái / bikini khác loại |
| 1922 | Chổi bay phù thủy | SĐ 13%, HP 10%, KI 10%, +5% tấn công lên Boss, +10% sát thương chí mạng, bay không tốn KI |
| 2006 | Giáp tập luyện cấp 5 | HP 15%, KI 15%, giảm 5% sát thương, Giáp +50.000 |
| 1731 | CT Black Goku Rose | SĐ 33%, KI 29%, +18% sát thương chí mạng, chí mạng 15%, chống lạnh, đi cùng Lưỡi hái hồng +5% sát thương chí mạng |
| 1832 | Cải trang Gohan Beast | SĐ 29%, KI 29%, sát thương chuẩn 7%, chí mạng 10%, giảm 5% thời gian hồi Khiên |
| 2085 | Cải Trang Bunma bụi | SĐ 25%, tấn công +25.000, +11% sát thương chí mạng, chí mạng 7%, sát thương chuẩn 5% |

**Option mới / mới có tác dụng:**
- **255** "Đi cùng Lưỡi hái hồng tăng #% sức đánh chí mạng": kiểm tra trên server test, Black Goku Rose + Lưỡi hái hồng cho chí mạng 16%, sát thương chí mạng 23%.
- **256** "+#% sức đánh, tối đa 12% khi ở gần Lý Tiểu Nương: Hầu Gái xanh, hồng, bikini": tính lúc ra đòn, mỗi loại CT khác loại đứng trong 200px cộng thêm.
- **191** Né chí mạng: có tỉ lệ biến đòn chí mạng của người đánh thành đòn thường.
- **183** Giảm % thời gian hồi Khiên năng lượng.
- **227** Giảm % thời gian bị choáng / thôi miên / socola / mù khi đang bật khiên.

`vsData` lên 19 để client tải lại tên option.

**Chưa làm:** "Triệu hồi quái 120% sức đánh, CD 360s" của Chổi bay phù thủy cần cơ chế triệu hồi mới, chưa có.

## Đợt 26: Kiểm tra toàn bộ option và nội tại (test thật trên server)
Cách test:
- **Từng option:** mặc cải trang chỉ có đúng option đó, đọc biến chỉ số của server (lệnh admin `chisoraw`) và so với lúc không có option. Đã chạy 92 option đang có trên đồ.
- **Option chiến đấu:** 2 nhân vật đánh nhau thật (lệnh admin `pkall`), đọc sát thương từng đòn trong gói tin.
- **Set đứng gần:** 2 nhân vật mặc 2 cải trang khác loại đứng cạnh nhau.

**Lỗi đã sửa:**
| Lỗi | Trước | Sau |
|---|---|---|
| Nội tại tải trùng 8 lần | 7/87 lần mở nội tại ra "Chưa kích hoạt" | Mỗi nội tại 1 lần |
| Nội tại 23 (+% vàng từ quái) | Không có tác dụng | Cộng vàng rơi từ quái |
| Nội tại 3 (Thái Dương Hạ San −% KI) | Không giảm KI | Giảm KI tiêu hao |
| Nội tại 9 Masenko | Cộng sát thương 2 lần, còn giảm nhầm hồi chiêu | Chỉ cộng sát thương 1 lần |
| Nội tại 24 / 25 | Dùng nội tại cũ: đòn đầu tiên hoặc vừa đổi nội tại thì sai | Lấy nội tại hiện tại |
| Set Picolo 5 món (Makankosappo ×1,5) | Viết `*= 3 / 2` = nhân 1, không tăng | Tăng 50% |
| Bùa trí tuệ x4 | Bị cộng 2 lần (thành x8) | x4 |
| Option 117 / 226 Sức đánh đẹp | Bị cộng 2 lần (+10% thành +21%) | +10% |
| Option 155 "Giảm 50% SĐ, HP, KI…" | Chỉ giảm SĐ | Giảm cả HP, KI |
| Option 100 / 155 +% vàng | Không có tác dụng | Cộng vàng rơi |
| Option 114 / 148 +% tốc độ | Không có tác dụng | Tăng tốc chạy |
| Option 191 Né chí mạng | Khi né vẫn hiện chữ chí mạng | Hiện đòn thường |

**Kết quả test chiến đấu** (B tộc Namếc đánh A, mỗi kịch bản 8 đòn):
| Option trên A | Sát thương / đòn | Kết quả |
|---|---|---|
| không có | ~96.000 | gốc |
| 199 giảm 50% ST từ Namếc | ~47.000 | đúng |
| 198 giảm 50% ST từ Trái Đất | ~96.000 | đúng (không giảm đòn Namếc) |
| 157 giảm 50% ST khi KI < 20% | ~48.000 khi KI thấp, ~96.000 khi KI đầy | đúng |
| 4 hồi 10% KI khi bị đánh | KI +88 triệu sau 8 đòn | đúng |
| 15 phản 100.000 ST cận chiến | B mất đúng 800.000 HP | đúng |
| B chí mạng 100%, A không có 191 | ~188.000 | gốc chí mạng |
| B chí mạng 100%, A có 191 100% | ~96.000 | đúng |

**Kết quả set đứng gần:** đúng hết, gồm:
- 145/146 Dr Slum: +20% SĐ, +66% tốc độ.
- 150 Póc–Píc–King Kong: +10% SĐ/HP/KI cho cả 2 người.
- 177 Gohan Bư gần Mabư: +2%.
- 179 Demons Frost: +2%.
- 193 Cầu thủ, 196 Thỏ.
- 256 Hầu gái: +5%; cùng loại thì không cộng.

**Option chỉ để hiện chữ** (hạn dùng, không giao dịch, sách kỹ năng…), không cần code: 1, 9, 30, 93, 210, 231…

## Đợt 27: Tối ưu cho VPS yếu, effect chính thức, tool tải repo
- **Rò rỉ luồng (lỗi nặng):** mỗi lần đăng nhập, hiện hiệu ứng (LogicEffect), ghi log, hẹn giờ (setTimeout), Mabư ăn người, luyện tập, trả thù, phó bản… server tạo 1 luồng không bao giờ đóng.
  - Test cũ: 15 lần đăng nhập để lại 13 luồng.
  - Nay dùng chung `Util.POOL` (luồng rảnh 60 giây tự đóng) và `Util.TIMER`. Test lại: không còn luồng thừa.
- Khu Ngọc Rồng Sao Đen không có người: cập nhật 1 lần/giây thay vì 7 lần/giây.
- `run_vps_yeu.bat` / `run_vps_yeu.sh`: dùng SerialGC, giới hạn RAM. Lúc không có người chơi tốn khoảng 160 MB RAM, so với khoảng 420 MB khi chạy mặc định.
- Hướng dẫn chọn JDK / NetBeans và chỉnh MySQL: xem `HUONG_DAN_VPS_YEU.md`.
- **Effect chính thức:** thêm effect 500–519; cập nhật 19 effect khác bản chính thức (ảnh x4 gốc, x1–x3 thu nhỏ từ x4).
  - Danh hiệu 300–303 không có trên server chính thức nên vẫn thiếu ảnh.
- **`tools/NroRepoDownloader.exe`:** tải toàn bộ file server từ GitHub, mặc định bỏ `tools/` và `client/` (LÂU CỒ MOD).
  - Lần đầu tải 1 file zip (khoảng 690 MB) rồi giải nén: test được 79.325 file trong 55 giây.
  - Lần sau so mã hash từng file, chỉ tải file mới / đã sửa.
  - Không ghi đè `Config.properties` và `maintenanceConfig.txt` nếu trên máy đã có.
  - Mã nguồn: `tools/repo-downloader`.

## Đợt 28: NroResTool v8 dò danh hiệu
- **Vì sao tool cũ không tìm được danh hiệu:**
  - Danh hiệu dùng số effect của server chính thức. Số này khác bản server lậu (218–303), nên đoán theo dải 0–1200 không trúng.
  - Server gửi danh hiệu bằng lệnh 24 loại 2 (id người chơi, giây, id effect), cứ 10 giây gửi lại. Tool cũ không đọc lệnh này.
- **Mục mới "Dò danh hiệu":**
  - Tool đăng nhập rồi đi qua các khu (vd `0-15`) của map nhân vật đang đứng, mỗi khu chờ 12 giây.
  - Ghi lại số effect người khác đang đeo: danh hiệu (lệnh 24) và hiệu ứng set / cải trang (lệnh -128). Sau đó tự tải đúng các effect đó.
  - Danh sách tìm được lưu ở `danh_hieu_do_duoc.txt`.
  - Test trên server thử: nhân vật khác đeo danh hiệu 222 ở khu 3, tool tìm thấy và tải đúng ảnh và effdata.

## Đợt 29: NroResTool v9 thử các kiểu hỏi danh hiệu
- Bản v8 dò được danh hiệu server chính thức đang dùng (224, 228), nhưng server không trả ảnh khi hỏi effect kiểu thường.
- **Tải không được thì tool tự thử thêm các kiểu hỏi:**
  - effect + 1 byte loại (1, 2, 3, 0);
  - id dạng int;
  - ảnh theo tên (`danhhieu_`, `danh_hieu_`, `title_`, `Title_`, `dh_`, `ImgEffect_` + id).
- Tool ghi mọi gói server trả về trong lúc thử vào cuối `danh_hieu_do_duoc.txt` (bỏ gói di chuyển / chiến đấu) để phân tích.
- **Gói effect định dạng lạ:** tool lấy ảnh PNG nằm cuối gói. Không đọc được thì lưu nguyên gói vào `effect_loi/goi_<id>.bin`.

## Đợt 30: NroResTool v10 giả client 2.5.2
- **Đã kiểm tra client chính thức Dragonboy252** (đóng gói Enigma Virtual Box, đã bung ra):
  - Trong client không có sẵn ảnh danh hiệu, chỉ có effect cũ e_0–28.
  - Client không tải ảnh qua web.
  - Danh sách lệnh -111 lúc đăng nhập chỉ là các file res (ô gạch bản đồ), repo đã có đủ.
- **Server đọc phiên bản client từ chuỗi platform** lúc đăng nhập (`pc|2.3.3` → 233). Tool trước báo là 2.3.3, nên server chính thức có thể không gửi effect / danh hiệu mới.
  - Nay mặc định `pc|2.5.2` / `2.5.2` cho khớp client chính thức (sửa được ở mục Nâng cao).
- **Dự phòng:** client chính thức lưu dữ liệu đã tải ở `%USERPROFILE%\AppData\LocalLow\Team\Dragonboy252`. Nén thư mục này gửi lên để lấy ảnh danh hiệu client đã tải.

## Đợt 31: NroResTool v11 thử zoom x2 cho danh hiệu
- **Dữ liệu AppData của client chính thức:**
  - Client không lưu effect / danh hiệu xuống ổ đĩa, chỉ giữ trong RAM.
  - File `lastZoomlevel` = 2: client chính thức chạy zoom x2, còn tool hỏi x4.
- Hỏi x4 không được thì tool tự kết nối lại với zoom x2, x1, x3 rồi hỏi lại. Ảnh lưu ở `effect/x<zoom>`.
- Test trên server thử: danh hiệu chỉ có ảnh x2 → tool không tải được ở x4, tự chuyển sang x2 và tải được.

## Đợt 32: NroResTool v12 sửa lỗi không vào game
- **Lỗi của v10–v11:** khi tool báo phiên bản `pc|2.5.2`, server chính thức không cho vào game (bản 2.5.2 thật đăng nhập bằng gói khác). Vì vậy dò 50 khu đều không thấy gì.
- **Sửa:**
  - Trả mặc định về `pc|2.3.3`.
  - Nếu với phiên bản đã chọn mà không vào được game, tool tự kết nối lại bằng 2.3.3.
  - Phần thử zoom x2 / x1 / x3 cho danh hiệu (v11) vẫn giữ.

## Đợt 33: Kiểm tra icon, NPC, map
**Icon item** (đối chiếu 2.112 item với file ảnh 4 mức zoom):
- 125 item thiếu icon x1, khoảng 15 item có icon x1/x3 là ảnh trống 1x1. Đã tạo lại từ bản lớn nhất. Làm cho mọi icon trên ổ: thêm 1.566 file. Part cải trang / pet không còn thiếu file nào.
- Icon thật cho Trực thăng thỏ đế, Pet hươu, Pet hổ (trước là ảnh trống).
- Còn 13 item trống icon ở mọi zoom (abcd, TT, Đai lưng, CT Sida/Heles/Rumsshi/Mosco/Champa/Belmod/Fide chai/Test). Bản chính thức cũng trống; không có trong shop, hộp quà hay rơi đồ nên người chơi không gặp.

**NPC:** test tự động 176 lượt NPC trên 91 map: mở menu, bấm từng lựa chọn cấp 1, theo dõi lỗi server.
- **Boss luyện tập tự động:** `leaveMap` gây NullPointer lặp vô hạn (khoảng 1.200 lần trong một lần test). Server chậm hẳn, nhiều NPC bấm không phản hồi. Đã sửa.
- **Quả trứng:** bấm khi chưa có trứng Mabư thì lỗi NullPointer. Nay báo "Bạn chưa có trứng Ma Bư!".
- **Đại Thiên Sứ** (NPC 64, Võ Đài Siêu Cấp / Lãnh địa Fize): lớp rỗng, bấm không có gì, mà map không có cổng ra. Thêm menu "Về nhà".
- **Whis:** ở map 164, nút "Từ chối" mở nhầm menu học tuyệt kỹ. Map khác chưa có menu nay có "Về nhà".
- **Bà Hạt Mít, nút "Top 100" ở Võ đài:** trước không làm gì. Nay hiện top 100 lượt thách đấu võ đài trong ngày.
- **NPC chưa có chức năng** (chỉ "Từ chối"): Rương Sưu Tầm (Nhà Bunma), Berry (Khu hang động); Ngộ Không, Đường Tăng ở map không vào được.

**Map:** kiểm tra cổng, điểm đến, ô gạch; test thoát game ở từng map (170 map) rồi đăng nhập lại.
- Phòng thí nghiệm Myuu (166) và Rừng nguyên sinh (186): đăng nhập lại vẫn ở đó, mà map không có lối ra → kẹt. Nay đưa về nhà.
- 7 map thiếu dữ liệu ô gạch (78, 95, 101, 121, 125, 130, 134): vào là kẹt. Nay không cho dịch chuyển vào (báo "Bản đồ này chưa mở"); đăng nhập ở đó thì đưa về nhà.
- Các map còn lại (phó bản, doanh trại, Ngọc Rồng Sao Đen, Bụng Mabư, kho báu…) đăng nhập lại đều được đưa ra đúng chỗ. Mọi cổng dẫn tới map có thật và có đất để đứng.

## Đợt 34: Rương sưu tập (NPC Rương Sưu Tầm, Nhà Bunma)
Theo ảnh bản chính thức:
- **Menu:** "Vàng bạc châu báu gì cứ yên tâm giao hết cho tôi" — **Mở rương** / **Nâng cấp rương** / **Từ chối**.
- **Mở rương:** mở giao diện rương đồ của client, nhưng là một rương riêng, mặc định 20 ô.
  - Chỉ cất được: cải trang, đeo lưng, thú cưỡi (ván bay, cân đẩu…), pet (đồ loại 27 có hình đi theo), có hạn sử dụng hay không đều được.
  - Đồ khác bị từ chối: "Rương sưu tập chỉ cất được cải trang, đeo lưng, pet và thú cưỡi".
  - Cất từ hành trang hoặc cởi thẳng từ trên người; lấy về hành trang.
- **Nâng cấp rương:** hỏi "Bạn có chắc muốn mở thêm ô 21 với giá 1,5 Tỷ vàng?", mỗi lần +1 ô.
  - Ô sau đắt thêm 10% giá gốc (ô 22: 1,65 tỷ…), tối đa 100 ô. Giá ô 22 trở đi tự đặt; sửa ở `CollectionService.BASE_PRICE`.
- **Lưu dữ liệu:** cột mới `player_extra.collection`, không đụng rương đồ thường. Mở lại rương đồ ở nhà hoặc chuyển map là quay về rương đồ thường.
- **Test trên server:** mở 20 ô → cất cải trang, pet được, đồ ăn bị từ chối → nâng cấp lên 21 ô → đăng nhập lại vẫn còn đồ → lấy về hành trang được. Rương đồ thường vẫn đúng 30 ô.

## Đợt 35: Danh hiệu Perfect (effect 302) làm lại từ video
- **Nguồn:** video quay danh hiệu "PERRFECT" (server ngocrong.net). Gồm chữ PERRFECT trên bệ tối, Xên hoàn thiện, 3 đầu Xên đỏ / xanh lá / xanh dương, kén tím lăn và nhịp chớp trắng.
- **Cách làm:**
  - Dựng ảnh nền sạch từ những lúc danh hiệu ẩn, rồi tách danh hiệu ra bằng cách so với nền.
  - 1 vòng chuyển động khoảng 0,74 giây.
  - Chia 3 vùng (trên / chữ + bệ / Xên phải), mỗi vùng chỉ lưu các trạng thái khác nhau.
  - Canh lưới 1 px game để các mảnh ghép khít.
- **Giới hạn:** tấm effect x1 tối đa 255x255 (toạ độ mảnh là 1 byte), nên dùng 11 khung, mỗi khung giữ 2 nhịp (vòng lặp vẫn dài như video). Tỉ lệ ước tính 2,5 px màn hình = 1 px game; ảnh x3/x4 phóng từ video nên hơi mềm.
- **File:** `data/effdata/DataEffect_302`, `data/effect/x1..x4/ImgEffect_302.png` (26 mảnh, ảnh 256 màu). Đã test server gửi effect 302 cho client.

## Đợt 36: Danh hiệu tạm, đập sao 10, nâng cấp +13
**Danh hiệu tạm** (đến khi có effect thật):
- Vẽ bảng tên đúng tên cho 300 Phá Cổ Trung Thu (cam), 301 Phá Cổ Trung Thu VIP (tím), 303 Đón Xuân (đỏ), cùng kiểu bảng chữ danh hiệu sẵn có.
- Có đủ x1–x4; muốn thay chỉ cần chép đè file `ImgEffect_<số>` và `DataEffect_<số>`.
- Perfect (302) dùng bản làm từ video.

**Pha lê hóa tối đa 10 sao** (trước là 9):
- Sao thứ 10: tỉ lệ 0,05% (hiện 1%), 500 triệu vàng, 280 ngọc (giá ngọc có sẵn trong bảng cũ).
- **Cường hóa lỗ (Hematite + Dùi đục):** viết lại theo bậc 8 → 9 → 10, không vượt quá số sao đang có.
  - Tỉ lệ lỗ 8: 100%, lỗ 9: 25%, lỗ 10: 15%.
  - Thông tin hiện đúng lỗ sắp cường hóa và tỉ lệ thật (trước ghi chung "50%").
- **Ép sao:** cho ép vào lỗ 10 (đá ở lỗ 8–10 thành dòng riêng như lỗ 8, 9), 10 ngọc.

**Nâng cấp vật phẩm tối đa +13** (trước +8): bảng cho +8 → +13.
| Cấp | Đá | Vàng | Tỉ lệ |
|---|---|---|---|
| +8 → +9 | 90 | 300 Tr | 0,3% |
| +9 → +10 | 110 | 400 Tr | 0,25% |
| +10 → +11 | 130 | 500 Tr | 0,2% |
| +11 → +12 | 160 | 700 Tr | 0,15% |
| +12 → +13 | 200 | 900 Tr | 0,1% |

- Bảng cũ có sẵn tỉ lệ vô lý cho +8 trở lên (5%, 1%), đã đặt lại cho giảm dần.
- Thất bại ở cấp chẵn (+2, +4, … +12) rớt 1 cấp như +2/+4/+6 cũ, trừ khi có đá bảo vệ.
- Mỗi cấp vẫn +10% chỉ số.
- Hào quang trọn bộ vẫn tính tới +8 (client chỉ có hiệu ứng tới +8).

**Test qua NPC Bà Hạt Mít:**
- Áo +12 hiện "Sau khi nâng cấp (+13)", thất bại rớt +11.
- Pha lê hóa từ 9 sao hiện đúng giá và tỉ lệ.
- Cường hóa lỗ 10 thành công sau 2 lần.
- Ép sao lỗ 10 → 10/10 sao (+2% né đòn).
- Không có lỗi server.

## Đợt 37: Sao pha lê 10 theo bài cập nhật chính thức (Đá cấp 9, Ngọc rồng xanh)
Code mới: `services/SaoPhaLe10Service.java`.

**Chi phí**
- Pha lê hóa 9 → 10 sao: 500 Tr vàng + 50 ngọc bất kỳ. Trừ ngọc xanh trước, thiếu thì trừ hồng ngọc. Trước đây là 280 ngọc xanh.
- Cường hóa lỗ 10: 1 Hematite + 1 Dùi đục + 100 ngọc bất kỳ, không tốn vàng. Lỗ 8 và 9 vẫn tốn 500 Tr vàng.

**Luật khảm theo lỗ** (Ép sao, Bà Hạt Mít)

| Lỗ | Chỉ nhận |
|---|---|
| 1 – 8 | Đá pha lê thường. Không nhận Đá cấp 9 hay Ngọc rồng xanh |
| 9 | Đá cấp 9 (2000 – 2004) |
| 10 | Ngọc rồng xanh 1 – 7 sao (2064 – 2070) |

Chỉ số Đá cấp 9 (cố định):

| Đá | Chỉ số |
|---|---|
| Linh Hồn | Chí mạng +2% |
| Hiện Thực | Giảm 5% sát thương |
| Sức Mạnh | Sức đánh +5% |
| Không Gian | HP +10% |
| Thời Gian | KI +10% |

Chỉ số Ngọc rồng xanh (ngẫu nhiên mỗi lần khảm):

| Viên | Chỉ số |
|---|---|
| 1 sao | Sát thương cuối 1 – 3% |
| 2 sao | Sức đánh 3 – 10% |
| 3 sao | HP 5 – 20% |
| 4 sao | KI 5 – 20% |
| 5 sao | Chí mạng 1 – 5% |
| 6 sao | Giảm sát thương 3 – 10% |
| 7 sao | Biến tấn công thành HP và KI 5 – 10% (2 dòng cùng giá trị) |

**Hộp**
- Hộp đá cấp 9 (2034, đổi tên từ "Hộp đá nâng cấp +9"): mở ra chọn 1 trong 5 đá cấp 9.
- Hộp ngọc rồng xanh (2071): chọn 1 trong 7 viên.

**Nguồn rơi từ boss**
- Boss từ 50 Tr HP: 5% rơi Hộp đá cấp 9.
- Boss từ 500 Tr HP: 5% rơi 1 Ngọc rồng xanh ngẫu nhiên.

**Nhiệm vụ Sao pha lê 10** (Bà Hạt Mít đảo Kame, nút mới)
- Đếm số món đủ 10 sao ở người, hành trang và rương.
- Phần thưởng theo mốc:

| Số món | Phần thưởng |
|---|---|
| 1 | 1 Hộp ngọc rồng xanh |
| 2 | 2 Hộp ngọc rồng xanh |
| 3 | 1 Capsule vỡ |
| 5 | 2 Capsule vỡ |
| 7 | Capsule kích hoạt tự chọn + Bóng mờ hoa hồng (bước chân) |

- Mỗi mốc nhận 1 lần, lưu ở `player_extra.spl10_reward`. Cột này đã thêm vào cả 2 DB.

**Đã thử trên server**
- Khảm sai đá bị chặn đúng thông báo.
- Lỗ 9 + Đá Sức Mạnh → Sức đánh +5%.
- Lỗ 10 + NRX 7 sao → 2 dòng Biến 5% thành HP và KI.
- Còn 0 ngọc xanh, 1000 hồng ngọc: sau pha lê hóa và cường hóa lỗ 10 còn 850 hồng ngọc.
- Mở 2 hộp chọn đúng viên.
- Nhận mốc 1 món, lưu lại sau khi thoát.

## Đợt 38: Nâng cấp vật phẩm chỉ tới +9
- `MAX_LEVEL_ITEM` từ 13 về 9 theo yêu cầu.
- Đồ +8 vẫn nâng được lên +9. Đồ +9 báo "đã đạt cấp tối đa".
- Kiểm tra DB: không có người chơi nào giữ đồ trên +9.
- Bảng giá và tỉ lệ cho +10 trở lên vẫn để trong code nhưng không còn dùng tới.
