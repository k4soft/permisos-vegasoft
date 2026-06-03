package com.vegaflor.admin.swing;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public class Cypher {

    private static final String SECRET_KEY = "SecurityGrupoVegaFlor";

    public static String encript(String texto) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digestOfPassword = md.digest(SECRET_KEY.getBytes("UTF-8"));
            byte[] keyBytes = Arrays.copyOf(digestOfPassword, 24);

            SecretKey key = new SecretKeySpec(keyBytes, "DESede");
            Cipher cipher = Cipher.getInstance("DESede");
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] buf = cipher.doFinal(texto.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(buf);
        } catch (Exception ex) {
            throw new RuntimeException("Error cifrando contraseña", ex);
        }
    }
}
