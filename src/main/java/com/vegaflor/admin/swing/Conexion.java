package com.vegaflor.admin.swing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class Conexion {

    private static String entorno = "local";
    private static final Properties props = new Properties();

    static {
        Path external = Path.of("config.properties");
        if (Files.exists(external)) {
            try (InputStream is = Files.newInputStream(external)) {
                props.load(is);
            } catch (IOException ignored) {}
        } else {
            try (InputStream is = Conexion.class.getResourceAsStream("/config.properties")) {
                if (is != null) props.load(is);
            } catch (IOException ignored) {}
        }
    }

    public static void setEntorno(String e) { entorno = e; }
    public static String getEntorno()       { return entorno; }
    public static String getUrl()           { return props.getProperty(entorno + ".url", ""); }

    public static Connection get() throws SQLException {
        String url  = JasyptUtil.decrypt(props.getProperty(entorno + ".url"));
        String user = JasyptUtil.decrypt(props.getProperty(entorno + ".user"));
        String pass = JasyptUtil.decrypt(props.getProperty(entorno + ".password"));
        if (url == null || url.isBlank())
            throw new SQLException("No hay configuración para el entorno: " + entorno);
        return DriverManager.getConnection(url, user, pass);
    }
}
