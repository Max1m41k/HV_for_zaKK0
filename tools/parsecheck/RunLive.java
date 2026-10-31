import java.lang.reflect.*;

/**
 * Прогон реального пути BrowserMedia (распаковка скриптов -> запуск powershell ->
 * чтение файла -> снимок Track) без Майнкрафта. Запускать из корня проекта:
 *   tools/parsecheck/runlive.sh
 */
public class RunLive {
    public static void main(String[] args) throws Exception {
        Class<?> bm = Class.forName("com.visualmod.util.BrowserMedia");
        Class<?> src = Class.forName("com.visualmod.util.BrowserMedia$Source");
        Method start = bm.getMethod("start", src);
        Method stop = bm.getMethod("stop");
        Method track = bm.getMethod("track");
        Method diag = bm.getMethod("diag");
        Method supported = bm.getMethod("supported");
        Method setLoose = bm.getMethod("setLoose", boolean.class);

        System.out.println("supported=" + supported.invoke(null) + ", cwd=" + System.getProperty("user.dir"));
        setLoose.invoke(null, true);

        for (String name : new String[]{"SMTC", "TITLES"}) {
            Object s = Enum.valueOf((Class) src, name);
            start.invoke(null, s);
            for (int i = 0; i < 8; i++) {
                Thread.sleep(1000);
                Object t = track.invoke(null);
                System.out.println("[" + name + " " + i + "s] diag=" + diag.invoke(null) + " track=" + describe(t));
            }
            stop.invoke(null);
        }
    }

    static String describe(Object t) throws Exception {
        if (t == null) return "<null>";
        Class<?> c = t.getClass();
        StringBuilder sb = new StringBuilder();
        for (String n : new String[]{"title", "artist", "site", "status", "playing", "posMs", "endMs"}) {
            Field f = c.getDeclaredField(n);
            f.setAccessible(true);
            sb.append(n).append('=').append(f.get(t)).append(' ');
        }
        return sb.toString();
    }
}
