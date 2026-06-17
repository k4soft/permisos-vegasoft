package com.vegaflor.admin.swing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Autenticación contra AWS Cognito replicando el flujo SRP (USER_SRP_AUTH) que
 * usa la app móvil VegaApp vía Amplify. Devuelve el {@code IdToken} (JWT) que el
 * web service de producción espera en el header {@code Authorization: Bearer ...}.
 *
 * Se usa el pool/credenciales del ambiente de PRUEBAS (igual que el móvil con
 * {@code Parametro.CredencialCognito} y {@code amplifyconfiguration.json}).
 *
 * El protocolo SRP fue verificado contra Cognito real antes de portarlo.
 */
public final class CognitoAuth {

    // ── Configuración Cognito (ambiente de PRUEBAS) ────────────────────────────
    private static final String REGION        = "us-east-1";
    private static final String POOL_ID        = "us-east-1_zYcblaLP9";
    private static final String CLIENT_ID      = "3uplh7kivsv4965k6apsoo9jk9";
    private static final String CLIENT_SECRET  = "atlr9f3racde27f3tmiqjl36cqet2jqltuplgqu0lar62m7eho1";
    private static final String USERNAME       = "admin";
    private static final String PASSWORD       = "Veg@dmin2019";

    private static final String ENDPOINT = "https://cognito-idp." + REGION + ".amazonaws.com/";
    private static final String AMZ_JSON = "application/x-amz-json-1.1";
    private static final byte[] INFO_BITS = "Caldera Derived Key".getBytes(StandardCharsets.UTF_8);

    /** Primo N de 3072 bits (RFC 5054) usado por Cognito; g = 2. */
    private static final String N_HEX =
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
            "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6BF12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
            "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB3143DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF";

    private static final BigInteger N = new BigInteger(N_HEX, 16);
    private static final BigInteger G = BigInteger.valueOf(2);
    private static final BigInteger K = new BigInteger(hexHash(padHex(N_HEX) + padHex("2")), 16);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private CognitoAuth() {}

    /** Realiza el flujo SRP completo y devuelve el IdToken (JWT). */
    public static String getIdToken() throws Exception {
        String poolName = POOL_ID.substring(POOL_ID.indexOf('_') + 1);

        // a aleatorio y A = g^a mod N
        BigInteger a = new BigInteger(1024, RANDOM).mod(N);
        BigInteger bigA = G.modPow(a, N);

        // 1) InitiateAuth (USER_SRP_AUTH)
        JsonObject initParams = new JsonObject();
        initParams.addProperty("USERNAME", USERNAME);
        initParams.addProperty("SRP_A", bigA.toString(16));
        initParams.addProperty("SECRET_HASH", secretHash(USERNAME));
        JsonObject initBody = new JsonObject();
        initBody.addProperty("AuthFlow", "USER_SRP_AUTH");
        initBody.addProperty("ClientId", CLIENT_ID);
        initBody.add("AuthParameters", initParams);

        JsonObject initResp = post("AWSCognitoIdentityProviderService.InitiateAuth", initBody);
        JsonObject cp = initResp.getAsJsonObject("ChallengeParameters");
        String salt      = cp.get("SALT").getAsString();
        String srpB      = cp.get("SRP_B").getAsString();
        String secBlock  = cp.get("SECRET_BLOCK").getAsString();
        String uid       = cp.get("USER_ID_FOR_SRP").getAsString();

        // 2) Cálculo de la clave de prueba de contraseña (SRP)
        BigInteger bigB = new BigInteger(srpB, 16);
        BigInteger u = new BigInteger(hexHash(padHex(bigA.toString(16)) + padHex(srpB)), 16);
        String xHashHex = sha256Hex((poolName + uid + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
        BigInteger x = new BigInteger(hexHash(padHex(salt) + xHashHex), 16);
        BigInteger s = bigB.subtract(K.multiply(G.modPow(x, N))).modPow(a.add(u.multiply(x)), N);

        byte[] hkdf = hkdf(hexToBytes(padHex(s.toString(16))), hexToBytes(padHex(u.toString(16))));

        String timestamp = timestamp();
        byte[] secBlockBytes = java.util.Base64.getDecoder().decode(secBlock);
        byte[] msg = concat(
                (poolName + uid).getBytes(StandardCharsets.UTF_8),
                secBlockBytes,
                timestamp.getBytes(StandardCharsets.UTF_8));
        String signature = java.util.Base64.getEncoder().encodeToString(hmacSha256(hkdf, msg));

        // 3) RespondToAuthChallenge (PASSWORD_VERIFIER)
        JsonObject responses = new JsonObject();
        responses.addProperty("USERNAME", uid);
        responses.addProperty("PASSWORD_CLAIM_SECRET_BLOCK", secBlock);
        responses.addProperty("PASSWORD_CLAIM_SIGNATURE", signature);
        responses.addProperty("TIMESTAMP", timestamp);
        responses.addProperty("SECRET_HASH", secretHash(uid));
        JsonObject respBody = new JsonObject();
        respBody.addProperty("ChallengeName", "PASSWORD_VERIFIER");
        respBody.addProperty("ClientId", CLIENT_ID);
        respBody.add("ChallengeResponses", responses);

        JsonObject authResp = post("AWSCognitoIdentityProviderService.RespondToAuthChallenge", respBody);
        if (!authResp.has("AuthenticationResult"))
            throw new IllegalStateException("Cognito no devolvió AuthenticationResult: " + authResp);
        return authResp.getAsJsonObject("AuthenticationResult").get("IdToken").getAsString();
    }

    // ── HTTP a Cognito ─────────────────────────────────────────────────────────

    private static JsonObject post(String target, JsonObject body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(ENDPOINT))
                .header("Content-Type", AMZ_JSON)
                .header("X-Amz-Target", target)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200)
            throw new IllegalStateException("Cognito " + target + " -> HTTP " + resp.statusCode() + ": " + resp.body());
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    // ── Primitivas SRP / cripto ─────────────────────────────────────────────────

    private static String secretHash(String username) throws Exception {
        byte[] mac = hmacSha256(CLIENT_SECRET.getBytes(StandardCharsets.UTF_8),
                (username + CLIENT_ID).getBytes(StandardCharsets.UTF_8));
        return java.util.Base64.getEncoder().encodeToString(mac);
    }

    /** HKDF reducido (igual al de Cognito): clave de 16 bytes. */
    private static byte[] hkdf(byte[] ikm, byte[] salt) throws Exception {
        byte[] prk = hmacSha256(salt, ikm);
        byte[] info = concat(INFO_BITS, new byte[]{1});
        byte[] full = hmacSha256(prk, info);
        byte[] out = new byte[16];
        System.arraycopy(full, 0, out, 0, 16);
        return out;
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String sha256Hex(byte[] data) {
        MessageDigest md;
        try { md = MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new RuntimeException(e); }
        return toHex(md.digest(data));
    }

    /** SHA-256 de la representación binaria de un hex, devuelto como hex. */
    private static String hexHash(String hex) {
        return sha256Hex(hexToBytes(hex));
    }

    /** Padding de Cognito: longitud par y prefijo 00 si el primer nibble es alto. */
    private static String padHex(String hex) {
        if (hex.length() % 2 == 1) {
            hex = "0" + hex;
        } else if ("89abcdefABCDEF".indexOf(hex.charAt(0)) != -1) {
            hex = "00" + hex;
        }
        return hex;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) { System.arraycopy(a, 0, out, pos, a.length); pos += a.length; }
        return out;
    }

    /** Formato exacto exigido por Cognito: "EEE MMM d HH:mm:ss 'UTC' yyyy" en UTC/inglés. */
    private static String timestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d HH:mm:ss 'UTC' yyyy", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
