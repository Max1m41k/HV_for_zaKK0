package com.visualmod.hv.kit;

import com.visualmod.VisualsMod;
import com.visualmod.internal.crypto.HvKeyStore;
import com.visualmod.internal.kv.SessionVault;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class HvIntegrity {
    private static final long TTL_MS = 60_000L;
    private static volatile boolean checked;

    public static void verify() {
        if (checked || SessionVault.fresh(TTL_MS)) return;
        checked = true;

        if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) return;

        String ref = HvMarker.REF, stamp = HvMarker.STAMP;
        if (ref == null || ref.isEmpty() || "DEV".equals(ref)) {
            fail("сборка сделана без ключа администратора");
        }

        String[] marker = null;
        try (InputStream in = HvIntegrity.class.getResourceAsStream("/hv-auth.bin")) {
            if (in != null) {
                marker = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().split("\n");
            }
        } catch (Throwable t) {
            fail("маркер подлинности повреждён");
        }
        if (marker == null) fail("маркер подлинности отсутствует");

        if (marker.length < 3 || !HvKeyStore.equal(marker[1].trim(), stamp)) {
            fail("маркер не совпадает с этой сборкой");
        }
        if (!HvKeyStore.equal(HvKeyStore.anchor(ref, marker[1].trim()), marker[0].trim())) {
            fail("маркер подлинности недействителен");
        }

        try {
            Class.forName("com.visualmod.LockScreen");
        } catch (Throwable t) {
            fail("система лицензий вырезана из сборки");
        }

        SessionVault.seal(ref, stamp);
    }

    private static void fail(String why) {
        String msg = "HV: сборка не подлинная (" + why + ")";
        com.visualmod.internal.net.HvRelayClient.enqueue("lic/integrity");
        if (VisualsMod.LOGGER != null) VisualsMod.LOGGER.error(msg);
        throw new RuntimeException(msg + ". Мод нужно собрать с HOLY_ADMIN_TOKEN.");
    }

    private HvIntegrity() {}
}
