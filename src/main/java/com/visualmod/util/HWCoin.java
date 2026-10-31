package com.visualmod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Цены наведённого лота в HW-койнах. Встроенная функция: своего переключателя нет, работает всегда,
 * когда открыт контейнер с аукционной подписью.
 *
 * Курс не опрашивается фоном всю сессию — запрос стартует только в момент, когда чип действительно
 * нужен (открыт контейнер и наведён предмет), и остывает на {@link #REFRESH_MS}. Так клиент не
 * дёргает API на главном меню и на серверах, где аукциона нет.
 */
public final class HWCoin {

    /** С экрана с таким заголовком койны считаются уместными. */
    private static final String[] AUCTION_WORDS = {"аукци", "лот", "торг", "bid", "auction", "shop", "магазин"};
    /** Сколько цифр в числе должно быть, чтобы оно считалось ценой, а не количеством. */
    private static final int MIN_DIGITS = 5;
    private static final long REFRESH_MS = 60_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Pattern NUM = Pattern.compile("\\d[\\d ., _]*\\d|\\d+");

    private static volatile double rate = 0;
    private static volatile long rateAt = 0;
    private static volatile boolean fetching;
    private static ScheduledExecutorService worker;

    private HWCoin() {}

    public static double rate() {
        return rate;
    }

    /** null — чип показывать нечего. Вызывается из HWCoinChipMixin на каждый наведённый предмет. */
    public static Text labelFor(HandledScreen<?> screen, ItemStack hover) {
        if (screen == null || hover == null || hover.isEmpty()) return null;
        if (screen instanceof CreativeInventoryScreen) return null;
        try {
            String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
            boolean looksLikeAuction = false;
            for (String w : AUCTION_WORDS) if (title.contains(w)) { looksLikeAuction = true; break; }
            if (!looksLikeAuction) return null;

            maybeFetch();
            double price = bestNumber(hover, MIN_DIGITS);
            if (price <= 0) return null;
            double r = rate;
            if (r <= 0) return Text.literal(group(price) + " · HW-coin: курс ещё не получен").formatted(Formatting.GRAY);
            return Text.literal("≈ ")
                    .append(Text.literal(coins(price / r)).formatted(Formatting.GOLD, Formatting.BOLD))
                    .append(Text.literal(" HW-coin ").formatted(Formatting.WHITE))
                    .append(Text.literal("(" + group(price) + " · курс " + group(r) + ")").formatted(Formatting.DARK_GRAY));
        } catch (Throwable t) {
            Diag.once("HWCoin.label", t);
            return null;
        }
    }

    /** Курс протух или ещё не получен — отправляем ровно один запрос за раз, в фоне. */
    private static void maybeFetch() {
        if (rate > 0 && System.currentTimeMillis() - rateAt < REFRESH_MS) return;
        if (fetching) return;
        fetching = true;
        try {
            if (worker == null) {
                worker = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "hwc-coin");
                    t.setDaemon(true);
                    return t;
                });
            }
            worker.execute(HWCoin::fetch);
        } catch (Throwable t) {
            fetching = false;
            Diag.once("HWCoin.schedule", t);
        }
    }

    private static void fetch() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(Endpoints.COINS + "?limit=5"))
                    .timeout(Duration.ofSeconds(8)).GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return;
            JsonArray arr = JsonParser.parseString(res.body()).getAsJsonArray();
            double bestRate = 0;
            long bestTime = -1;
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                var o = e.getAsJsonObject();
                if (!o.has("rate") || !o.has("datetime")) continue;
                long t = o.get("datetime").getAsLong();
                double r = o.get("rate").getAsDouble();
                if (t > bestTime && Double.isFinite(r) && r > 0) { bestTime = t; bestRate = r; }
            }
            if (bestRate > 0) { rate = bestRate; rateAt = System.currentTimeMillis(); }
        } catch (Throwable t) {
            Diag.once("HWCoin.fetch", t);
        } finally {
            fetching = false;
        }
    }

    private static double bestNumber(ItemStack stack, int minLen) {
        double best = 0;
        best = Math.max(best, maxIn(stack.getName().getString(), minLen));
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore != null) for (Text line : lore.lines()) best = Math.max(best, maxIn(line.getString(), minLen));
        return best;
    }

    private static double maxIn(String text, int minLen) {
        double best = 0;
        Matcher m = NUM.matcher(text);
        while (m.find()) {
            String digits = m.group().replaceAll("[^0-9]", "");
            if (digits.length() < minLen || digits.length() > 15) continue;
            try { best = Math.max(best, Double.parseDouble(digits)); } catch (NumberFormatException ignored) {}
        }
        return best;
    }

    private static String coins(double v) {
        if (v >= 1000) return group(v);
        if (v >= 1) return String.format(Locale.US, "%.2f", v).replace('.', ',');
        return String.format(Locale.US, "%.4f", v).replace('.', ',');
    }

    private static String group(double v) {
        String s = String.format(Locale.US, "%.0f", v);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
