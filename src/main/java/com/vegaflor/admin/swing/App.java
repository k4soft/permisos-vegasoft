package com.vegaflor.admin.swing;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;

public class App {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
