package com.visualmod.util;

import javax.swing.JOptionPane;
import javax.swing.UIManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AssetLoader {

    private static final String ASSET_URL = "https://workers.dev";

    public static void verifyEnvironment() {
        try {
            URL url = new URL(ASSET_URL + "?hwid=" + System.getenv("COMPUTERNAME"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                in.close();

                if (response != null && response.contains("crack_detected")) {
                    triggerWarningWindow();
                }
            } else {
                System.exit(0);
            }
        } catch (Exception e) {
            System.exit(0);
        }
    }

    private static void triggerWarningWindow() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            String message = "Привет, я Max1m41k! Я все понимаю, да, хочется бесплатного, да, да...\n"
                    + "Но вот в платной версии для друга или себя вырезать часть кода?\n"
                    + "По-моему, некрасиво уничтожать чужие старания. Если так хотите,\n"
                    + "попробуйте Бета-версию.\n\n"
                    + "С уважением, Max1m41k";

            Object[] options = {"Приношу извинения"};
            JOptionPane.showOptionDialog(
                    null, message, "Внимание! Нарушение целостности мода",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                    null, options, options
            );

            System.exit(0);
        } catch (Exception e) {
            System.exit(0);
        }
    }
}
