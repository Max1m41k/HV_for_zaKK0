package com.visualmod;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class WelcomeDialog implements PreLaunchEntrypoint {

    private static final String TG_URL = "https://t.me/Holy_visual";
    private static final String FILE_NAME = "visualmod_welcome.properties";

    @Override
    public void onPreLaunch() {
        try {
            if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;

            Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            if (isHidden(file)) return;

            System.setProperty("java.awt.headless", "false");
            if (GraphicsEnvironment.isHeadless()) return;

            boolean[] hide = {false};
            SwingUtilities.invokeAndWait(() -> hide[0] = show());
            if (hide[0]) save(file);
        } catch (Throwable ignored) {
        }
    }

    static boolean show() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JDialog dialog = new JDialog((Frame) null, "Holy-Visual", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setAlwaysOnTop(true);
        dialog.setResizable(false);

        JLabel text = new JLabel("<html><body style='width: 330px'>"
                + "<b>Здравствуйте!</b><br><br>"
                + "Вы запускаете игру с Holy-Visual.<br>"
                + "Просим учесть, что ответственности за блокировки на серверах мы не несём.<br><br>"
                + "Автор: Max1m41k"
                + "</body></html>");

        JLabel link = new JLabel("<html>ТГК: <a href=''>" + TG_URL + "</a></html>");
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                        Desktop.getDesktop().browse(URI.create(TG_URL));
                    }
                } catch (IOException | RuntimeException ignored) {}
            }
        });

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 8, 18));
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(text);
        content.add(Box.createVerticalStrut(2));
        content.add(link);

        boolean[] hide = {false};
        JButton ok = new JButton("OK");
        ok.setPreferredSize(new Dimension(80, 26));
        JButton dontShow = new JButton("Больше не показывать");
        JLabel tick = new JLabel("\u2713");
        tick.setFont(tick.getFont().deriveFont(Font.BOLD, 18f));
        tick.setForeground(new Color(0, 150, 0));
        tick.setVisible(false);
        tick.setPreferredSize(new Dimension(22, 26));

        dontShow.addActionListener(e -> {
            hide[0] = !hide[0];
            tick.setVisible(hide[0]);
        });
        ok.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 10, 4, 10));
        buttons.add(ok);
        buttons.add(dontShow);
        buttons.add(tick);

        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(content, BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                dialog.toFront();
                ok.requestFocusInWindow();
            }
        });

        dialog.setVisible(true);
        return hide[0];
    }

    private static boolean isHidden(Path file) {
        try {
            if (!Files.exists(file)) return false;
            return Files.readString(file, StandardCharsets.UTF_8).contains("hide=true");
        } catch (IOException e) {
            return false;
        }
    }

    private static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "hide=true\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }
}
