package com.vegaflor.admin.swing;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

class JasyptUtil {

    private static final PooledPBEStringEncryptor ENCRYPTOR;

    static {
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword("AlfaVegaFlor@");
        config.setAlgorithm("PBEWithMD5AndDES");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setStringOutputType("base64");

        ENCRYPTOR = new PooledPBEStringEncryptor();
        ENCRYPTOR.setConfig(config);
    }

    static String decrypt(String value) {
        if (value == null) return null;
        if (value.startsWith("ENC(") && value.endsWith(")"))
            return ENCRYPTOR.decrypt(value.substring(4, value.length() - 1));
        return value;
    }
}
