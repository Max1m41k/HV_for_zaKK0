package com.visualmod.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.visualmod.VisualsMod;
import com.visualmod.module.Module;
import com.visualmod.util.Diag;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Feature Control: сервер HolyWorld сам решает, какие функции мода скрыты.
 *
 * Клиент работает fail-closed: с момента входа на внешний сервер всё, что можно скрыть,
 * скрыто и остаётся скрытым, пока не придёт корректный ответ. Ответ без id, без method,
 * без payload.blocklist или с некорректным списком корректным не считается — он только
 * порождает повторный запрос.
 */
public final class LiteApi {
    private static final Identifier CHANNEL = Identifier.of("liteapi", "feature-control");
    private static final CustomPayload.Id<Data> ID = new CustomPayload.Id<>(CHANNEL);
    private static final CustomPayload.Type<PacketByteBuf, Data> TYPE = new CustomPayload.Type<>(
            ID, CustomPayload.codecOf(Data::write, Data::read));

    private static final String METHOD = "checkFeatures";
    private static final int MAX_PAYLOAD_BYTES = 1 << 20;
    /** Серверный лимит частоты запросов: раньше этого срока не отправляем ничего, даже после reconnect. */
    private static final long MIN_INTERVAL_MS = 11_000;
    private static final long TIMEOUT_MS = 5_000;
    private static final long MAX_BACKOFF_MS = 60_000;
    /** Сколько таймаутов подряд терпим, пока сервер не подтвердил, что говорит по протоколу. */
    private static final int ABSENT_ATTEMPTS = 3;
    private static final Gson GSON = new Gson();

    private static volatile boolean onRemoteServer;
    /** id нашего неотвеченного запроса; null — запроса в полёта нет. */
    private static volatile String pendingId;
    private static volatile long sentAt;
    /** Не раньше этого момента отправляем следующий запрос. Переживает disconnect. */
    private static volatile long nextSendAt;
    private static volatile int timeouts;
    /** Сервер доказал, что владеет протоколом: объявил канал или хотя бы раз ответил. */
    private static volatile boolean verifiedServer;
    /** Сервер молчит и канала не объявляет — контроль функций не применяется. */
    private static volatile boolean noControl;
    /** По этому соединению уже применён настоящий список: переспрашиваем, не скрывая всё заново. */
    private static volatile boolean haveList;

    private LiteApi() {}

    public static void init() {
        PayloadTypeRegistry.playC2S().register(ID, TYPE.codec());
        PayloadTypeRegistry.playS2C().register(ID, TYPE.codec());
        ClientPlayNetworking.registerGlobalReceiver(ID, LiteApi::onReceive);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(LiteApi::onDisconnect));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    /** Скрыто ли сейчас что-то по причине «сервер ещё не подтвердил список». */
    public static boolean isPending() {
        return onRemoteServer && !noControl;
    }

    private static void onJoin() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.isIntegratedServerRunning()) {
            onRemoteServer = false;
            return;
        }
        onRemoteServer = true;
        pendingId = null;
        timeouts = 0;
        noControl = false;
        haveList = false;
        verifiedServer = ClientPlayNetworking.canSend(CHANNEL);
        // Fail-closed: до подтверждения сервера скрыто всё, что подлежит сокрытию.
        hideAllUnverified();
        long now = System.currentTimeMillis();
        if (nextSendAt < now) nextSendAt = now;
    }

    private static void onDisconnect() {
        // nextSendAt не сбрасываем: быстрый reconnect не должен обходить серверный лимит.
        onRemoteServer = false;
        pendingId = null;
        timeouts = 0;
        noControl = false;
        haveList = false;
        verifiedServer = false;
        release();
    }

    private static void tick() {
        if (!onRemoteServer) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) return;
        if (noControl) {
            // Канал мог объявиться позже: проверяем снова, чтобы не остаться навечно в снисходительном режиме.
            if (!ClientPlayNetworking.canSend(CHANNEL)) return;
            noControl = false;
            haveList = false;
            hideAllUnverified();
        }
        long now = System.currentTimeMillis();

        if (pendingId != null) {
            if (now - sentAt < TIMEOUT_MS) return;
            pendingId = null;
            timeouts++;
            VisualsMod.LOGGER.warn("[LiteAPI] Сервер не ответил за {} мс (попытка {}), функции остаются скрытыми",
                    TIMEOUT_MS, timeouts);
            if (!verifiedServer && timeouts >= ABSENT_ATTEMPTS && !ClientPlayNetworking.canSend(CHANNEL)) {
                noControl = true;
                release();
                VisualsMod.LOGGER.warn("[LiteAPI] Сервер не объявил канал {} и не ответил — контроль функций не применяется",
                        CHANNEL);
                return;
            }
            nextSendAt = now + Math.min(MAX_BACKOFF_MS, MIN_INTERVAL_MS * (1L << Math.min(3, timeouts - 1)));
            return;
        }
        if (now >= nextSendAt) send();
    }

    private static void send() {
        try {
            if (VisualsMod.moduleManager == null) return;
            boolean declared = ClientPlayNetworking.canSend(CHANNEL);
            if (declared) verifiedServer = true;

            JsonArray features = new JsonArray();
            for (Module m : VisualsMod.moduleManager.getModules()) {
                features.add(m.getName().toLowerCase(Locale.ROOT));
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("client", VisualsMod.MOD_ID);
            payload.add("features", features);

            JsonObject request = new JsonObject();
            String id = UUID.randomUUID().toString();
            request.addProperty("id", id);
            request.addProperty("method", METHOD);
            request.add("payload", payload);

            pendingId = id;
            sentAt = System.currentTimeMillis();
            nextSendAt = sentAt + MIN_INTERVAL_MS;
            if (!haveList) hideAllUnverified();
            ClientPlayNetworking.send(new Data(GSON.toJson(request).getBytes(StandardCharsets.UTF_8)));
            VisualsMod.LOGGER.info("[LiteAPI] Запрос checkFeatures отправлен ({} функций), канал объявлен сервером: {}",
                    features.size(), declared);
        } catch (Throwable t) {
            pendingId = null;
            nextSendAt = System.currentTimeMillis() + MIN_INTERVAL_MS;
            Diag.once("LiteApi.send", t);
        }
    }

    private static void onReceive(Data data, ClientPlayNetworking.Context ctx) {
        ctx.client().execute(() -> {
            try {
                handle(new String(data.bytes(), StandardCharsets.UTF_8));
            } catch (Throwable t) {
                Diag.once("LiteApi.receive", t);
            }
        });
    }

    private static void handle(String json) {
        if (!onRemoteServer) return;
        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (Exception e) {
            VisualsMod.LOGGER.warn("[LiteAPI] Сообщение не читается как JSON-объект: {}", brief(json));
            return;
        }
        if (root == null) {
            VisualsMod.LOGGER.warn("[LiteAPI] Пустое или не-объект сообщение: {}", brief(json));
            return;
        }

        String id = string(root, "id");
        String method = string(root, "method");
        if (id == null || method == null) {
            VisualsMod.LOGGER.warn("[LiteAPI] Сообщение без id или method — игнорируем: {}", brief(json));
            return;
        }
        verifiedServer = true;
        noControl = false;

        if (METHOD.equals(method)) {
            if (!id.equals(pendingId)) {
                VisualsMod.LOGGER.info("[LiteAPI] Ответ не на наш запрос (ждали «{}», пришёл «{}») — игнорируем",
                        pendingId, id);
                return;
            }
            pendingId = null;
            timeouts = 0;
            List<String> blocked = readBlocklist(root);
            if (blocked == null) {
                VisualsMod.LOGGER.warn("[LiteAPI] Ответ без корректного payload.blocklist — ничего не применяем, "
                        + "функции остаются скрытыми");
                nextSendAt = System.currentTimeMillis() + MIN_INTERVAL_MS;
                return;
            }
            JsonElement ok = root.get("ok");
            if (ok == null || !ok.isJsonPrimitive() || !ok.getAsJsonPrimitive().isBoolean() || !ok.getAsBoolean()) {
                VisualsMod.LOGGER.warn("[LiteAPI] Сервер отклонил запрос: {} — функции остаются скрытыми",
                        string(root, "error"));
                nextSendAt = System.currentTimeMillis() + MIN_INTERVAL_MS;
                return;
            }
            haveList = true;
            int hidden = VisualsMod.moduleManager == null
                    ? 0 : VisualsMod.moduleManager.applyLiteBlocklist(blocked);
            VisualsMod.LOGGER.info("[LiteAPI] Ответ принят, скрыто модулей: {} (в списке {})", hidden, blocked.size());
            return;
        }

        List<String> blocked = readBlocklist(root);
        if (blocked == null) {
            VisualsMod.LOGGER.info("[LiteAPI] Push-сообщение без корректного payload.blocklist — игнорируем: {}",
                    brief(json));
            return;
        }
        pendingId = null;
        timeouts = 0;
        haveList = true;
        int hidden = VisualsMod.moduleManager == null
                ? 0 : VisualsMod.moduleManager.applyLiteBlocklist(blocked);
        VisualsMod.LOGGER.info("[LiteAPI] Push-обновление «{}» принято, скрыто модулей: {}", method, hidden);
    }

    /** null — список непригоден (отсутствует, не массив, пустая или не-строка), применять нельзя. */
    private static List<String> readBlocklist(JsonObject root) {
        JsonElement payload = root.get("payload");
        if (payload == null || !payload.isJsonObject()) return null;
        JsonElement list = payload.getAsJsonObject().get("blocklist");
        if (list == null || !list.isJsonArray()) return null;
        List<String> names = new ArrayList<>();
        for (JsonElement el : list.getAsJsonArray()) {
            if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                VisualsMod.LOGGER.warn("[LiteAPI] payload.blocklist содержит не-строку — ответ не применяем");
                return null;
            }
            String name = el.getAsString().trim();
            if (name.isEmpty()) {
                VisualsMod.LOGGER.warn("[LiteAPI] payload.blocklist содержит пустую строку — ответ не применяем");
                return null;
            }
            names.add(name);
        }
        return names;
    }

    private static void hideAllUnverified() {
        if (VisualsMod.moduleManager != null) VisualsMod.moduleManager.applyLiteBlocklist(null);
    }

    private static void release() {
        if (VisualsMod.moduleManager != null) VisualsMod.moduleManager.applyLiteBlocklist(List.of());
    }

    private static String brief(String json) {
        String s = json == null ? "null" : json.replace('\n', ' ').replace('\r', ' ');
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private static String string(JsonObject root, String key) {
        JsonElement el = root.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) return null;
        String s = el.getAsString().trim();
        return s.isEmpty() ? null : s;
    }

    private record Data(byte[] bytes) implements CustomPayload {
        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }

        static Data read(PacketByteBuf buf) {
            // Во входящем custom_payload тело идёт сырым остатком пакета, без VarInt-длины.
            // readByteArray(max) прочитал бы первый байт JSON как длину и уронил бы коннект.
            byte[] out = new byte[Math.min(buf.readableBytes(), MAX_PAYLOAD_BYTES)];
            buf.readBytes(out);
            return new Data(out);
        }

        void write(PacketByteBuf buf) {
            // Сырые байты JSON: writeByteArray добавил бы VarInt длину перед телом,
            // и сервер начал бы разбор с первого байта длины.
            buf.writeBytes(bytes);
        }
    }
}
