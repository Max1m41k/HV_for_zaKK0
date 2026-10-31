import java.lang.reflect.*;

/**
 * Проверка «кнопки» вне игры: реальный путь BrowserMedia.togglePlayPause()
 * (распаковка media_ctl.ps1 -> скрытый powershell -> команда SMTC) плюс снимок статуса
 * до и после, чтобы убедиться, что воспроизведение правда переключилось, и вернуть как было.
 */
public class ToggleLive {
    static Class<?> bm;
    static Method track, diag, start, stop, toggle;

    public static void main(String[] args) throws Exception {
        bm = Class.forName("com.visualmod.util.BrowserMedia");
        Class<?> src = Class.forName("com.visualmod.util.BrowserMedia$Source");
        track = bm.getMethod("track");
        diag = bm.getMethod("diag");
        start = bm.getMethod("start", src);
        stop = bm.getMethod("stop");
        toggle = bm.getMethod("togglePlayPause");

        start.invoke(null, Enum.valueOf((Class) src, "SMTC"));
        Thread.sleep(2000);
        String before = status();
        System.out.println("before: " + before);

        System.out.println("toggle#1 sent=" + toggle.invoke(null));
        Thread.sleep(2500);
        String mid = status();
        System.out.println("after#1:  " + mid + "  diag=" + diag.invoke(null));

        if (!mid.equals(before)) {
            System.out.println("status changed -> restoring");
            toggle.invoke(null);
            Thread.sleep(2500);
            System.out.println("restored: " + status());
        } else {
            System.out.println("status unchanged (video may have been paused by the player itself)");
        }
        stop.invoke(null);
    }

    private static String status() throws Exception {
        Object t = track.invoke(null);
        if (t == null) return "null (" + diag.invoke(null) + ")";
        return "playing=" + t.getClass().getField("playing").getBoolean(t)
                + " pos=" + t.getClass().getField("posMs").getLong(t)
                + " site=" + t.getClass().getField("site").get(t)
                + " title=" + String.valueOf(t.getClass().getField("title").get(t)).substring(0,
                        Math.min(40, String.valueOf(t.getClass().getField("title").get(t)).length()));
    }
}
