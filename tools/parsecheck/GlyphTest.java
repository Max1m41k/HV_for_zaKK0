public class GlyphTest {
    static String printable(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                    || c >= '\u00c0' && c <= '\u024f'
                    || c >= '\u0400' && c <= '\u04ff'
                    || ("\u2013\u2014\u00b7\u2026\u2018\u2019\u00ab\u00bb "
                            + ".,:;!?()[]{}'\"&+-/%#_*~@").indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        String out = sb.toString().replaceAll("\\s{2,}", " ").trim();
        return out.replaceAll("^[.,:;\\-]+|[.,:;\\-]+$", "").trim();
    }

    public static void main(String[] a) {
        String[] cases = {
                "1 HOUR OF VIRAL PHONK 2026 \uD83C\uDFB5 - \u263B DARK AURA \u263B - YouTube",
                "\uD83D\uDD25 Jolyne Cujoh X Jotaro Kuji #shorts \u2013 YouTube",
                "Wonderless \u00b7 Pierce The Veil",
                "Красная осень \u2014 Ёлка",
                "\u25EB\u266A",
                "Dark Aura - 2026 \u2014 Spotify Music",
        };
        for (String c : cases) System.out.println("[" + c + "] -> [" + printable(c) + "]");
    }
}
