package nro.models.server;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Calendar;
import java.util.Date;

/**
 *
 * @author By Mr Blue
 *
 */
public class ServerLog {

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("dd_MM_yyyy");
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public static synchronized void logCombine(String name, String itemname, int param) {
        try {
            Calendar calender = Calendar.getInstance();
            Date date = calender.getTime();
            String str = toTimeString(Date.from(Instant.now()));
            new java.io.File("log").mkdirs();
            String filename = "log/Combine_" + dateFormat.format(date) + ".txt";
            FileWriter fw = new FileWriter(filename, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("Player: " + name + "- Item: " + itemname + " " + param + " Sao - Time : " + str + "\n");
            bw.close();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void logItemDrop(String name, String item) {
        try {
            Calendar calender = Calendar.getInstance();
            Date date = calender.getTime();
            String str = toTimeString(Date.from(Instant.now()));
            new java.io.File("log").mkdirs();
            String filename = "log/ItemDrop_" + dateFormat.format(date) + ".txt";
            FileWriter fw = new FileWriter(filename, true);
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write("Player: " + name + "-" + item + " - Time : " + str + "\n");
            bw.close();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized String toTimeString(Date date) {
        try {
            String a = timeFormat.format(date);
            return a;
        } catch (Exception e) {
            return "01:01:00";
        }
    }

    public static synchronized void logAdmin(String name, int quantity) {
        try {
            Calendar cl = Calendar.getInstance();
            Date dt = cl.getTime();
            String str = toTimeString(Date.from(Instant.now()));
            String filename = "admin/AD_" + dateFormat.format(dt) + ".txt";
            new java.io.File("admin").mkdirs();
            // trước đây chỉ tạo tên file mà không ghi gì nên log admin luôn trống
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename, true))) {
                bw.write("Admin buff: " + name + " - Số lượng: " + quantity + " - Time : " + str + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
