package com.vegaflor.admin.swing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexion {

    private static final String URL =
     "jdbc:sqlserver://localhost;databaseName=VegaSoftDB;encrypt=false;trustServerCertificate=true";



    private static final String USER = "sa";
    private static final String PASS = "sacha2019*";

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
