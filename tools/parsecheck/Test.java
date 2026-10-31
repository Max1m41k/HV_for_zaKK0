import java.lang.reflect.*;

public class Test {
    static Method readTitles;
    static Field trackF;
    static Field looseF;

    public static void main(String[] args) throws Exception {
        Class<?> bm = Class.forName("com.visualmod.util.BrowserMedia");
        readTitles = bm.getDeclaredMethod("readTitles", String.class);
        readTitles.setAccessible(true);
        trackF = bm.getDeclaredField("track");
        trackF.setAccessible(true);
        Field diag = bm.getDeclaredField("diag");
        diag.setAccessible(true);
        looseF = bm.getDeclaredField("loose");
        looseF.setAccessible(true);

        String[] samples = {
            "chrome\tJolyne Cujoh X Jotaro Kuji #shorts #trending #cosplay - YouTube - Google Chrome",
            "chrome\tSkillet - Monster - YouTube Music - Google Chrome",
            "browser\tТоксичный свет — Артур Бест | Яндекс.Музыка - Яндекс.Браузер",
            "msedge\tImagine Dragons - Believer | Spotify - Microsoft Edge",
            "chrome\tGoogle Translate",
            "browser\tНеизвестный исполнитель – Неизвестный трек - Some Radio Site",
        };
        for (String line : samples) {
            looseF.setBoolean(null, false);
            readTitles.invoke(null, line);
            show(line, diag);
            looseF.setBoolean(null, true);
            readTitles.invoke(null, line);
            System.out.println("   loose -> " + dump(trackF.get(null)) + "  [" + diag.get(null) + "]");
        }
    }

    static void show(String line, Field diag) throws Exception {
        System.out.println("IN : " + line.split("\t")[1]);
        System.out.println("OFF-> " + dump(trackF.get(null)) + "  [" + diag.get(null) + "]");
    }

    static String dump(Object track) throws Exception {
        if (track == null) return "<null>";
        Class<?> c = track.getClass();
        StringBuilder sb = new StringBuilder(c.getSimpleName() + " ");
        for (String n : new String[]{"title", "artist", "site", "playing", "posMs", "endMs"}) {
            Field f = c.getDeclaredField(n);
            f.setAccessible(true);
            sb.append(n).append("=[").append(f.get(track)).append("] ");
        }
        return sb.toString();
    }
}
