package dev.grzybalson.license;

import dev.grzybalson.HollyCore;
import dev.grzybalson.config.configs.LicenseConfig;
import dev.grzybalson.internal.HollyIntegrityGuard;
import dev.grzybalson.internal.HollyRuntime;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public final class LicenseManager {

    private static final String TEAM_ID = "31b42d00-26cf-42dd-b10d-446d384b0748";
    private static final String PRODUCT_ID = "9df2ef20-ef49-4dd0-8ad1-940514d59ec8";
    private static final String VERIFY_URL = "https://app.lukittu.com/api/v1/client/teams/" + TEAM_ID + "/verification/verify";
    private static final String FIXED_SLOT_LICENSE_KEY = "OU2LY-ACXWS-WCJ6U-4QJ98-K7Z09";

    private static final String RSA_PUBLIC_KEY =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnnSYQp32Pu0rZXPOzjPW" +
            "DbZusLjRZor75oGHvh06rleNaQor9rkZsw03hyvZSo7PAcOjTd/8sqK96gfs6fqr" +
            "Kvxu8R3zGzwY1ZguSpwGN8EYo/gBBWTvlaP3DrxbwpARCvjctN2XyhUzJ26tOJzX" +
            "LnCU83yP2GM/sqLIMeEE29byZsLqJ8jpVwK5bE94IA+HHoibD5MHT5Y9xsayMURA" +
            "ZZzR2cWbwkFS+tk2p0QUC5Qb7ZD5iN2/HHoGpzfKvhz/Zrz1cMf2D2rVdMiMFb/H" +
            "zBH9U794F/sZp16tsHQM3lowhXsy3zBrRJ0FTClzPiQPJ6xFSB9/GXkCbhhHHOpe" +
            "bQIDAQAB";

    private final HollyCore plugin;

    public LicenseManager(HollyCore plugin) {
        this.plugin = plugin;
    }

    public static boolean isFixedSlotLicense(String licenseKey) {
        return normalizeLicenseKey(licenseKey).equals(FIXED_SLOT_LICENSE_KEY);
    }

    public boolean validate(LicenseConfig licenseConfig) {
        HollyIntegrityGuard.preflight(plugin, "service:validate");

        String licenseKey = licenseConfig.getLicenseKey();

        if (licenseKey == null || licenseKey.isEmpty() || licenseKey.equals("XXXXX-XXXXX-XXXXX-XXXXX-XXXXX")) {
            plugin.getLogger().severe("");
            plugin.getLogger().severe("========================================");
            plugin.getLogger().severe("  BRAK KLUCZA USLUGI!");
            plugin.getLogger().severe("  Wpisz klucz w: plugins/HollyCore/license.yml");
            plugin.getLogger().severe("========================================");
            plugin.getLogger().severe("");
            return false;
        }

        String challenge = UUID.randomUUID().toString().replace("-", "");

        try {
            URL url = new URL(VERIFY_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setDoOutput(true);

            JsonObject body = new JsonObject();
            body.addProperty("licenseKey", licenseKey);
            body.addProperty("productId", PRODUCT_ID);
            body.addProperty("challenge", challenge);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();

            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(
                            responseCode >= 200 && responseCode < 300
                                    ? conn.getInputStream()
                                    : conn.getErrorStream(),
                            StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
            }

            conn.disconnect();

            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

            if (!json.has("result")) {
                plugin.getLogger().severe("Nieprawidlowa odpowiedz serwera uslug!");
                return false;
            }

            JsonObject result = json.getAsJsonObject("result");
            boolean valid = result.has("valid") && result.get("valid").getAsBoolean();

            if (!valid) {
                String code = result.has("code") ? result.get("code").getAsString() : "UNKNOWN";
                plugin.getLogger().severe("");
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("  KLUCZ USLUGI NIEPRAWIDLOWY!");
                plugin.getLogger().severe("  Kod: " + code);
                plugin.getLogger().severe("  Sprawdz klucz w: plugins/HollyCore/license.yml");
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("");
                return false;
            }

            if (!result.has("challengeResponse") || result.get("challengeResponse").isJsonNull()) {
                plugin.getLogger().severe("");
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("  BRAK PODPISU USLUGI!");
                plugin.getLogger().severe("  Serwer uslug nie zwrocil challengeResponse.");
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("");
                return false;
            }

            String challengeResponse = result.get("challengeResponse").getAsString();
            if (!verifyChallengeResponse(challenge, challengeResponse)) {
                plugin.getLogger().severe("");
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("  WERYFIKACJA RSA NIEUDANA!");
                plugin.getLogger().severe("  Odpowiedz serwera uslug mogla zostac sfalszona.");
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("");
                return false;
            }

            HollyRuntime.bind(plugin, licenseKey, challenge, challengeResponse, response.toString());
            HollyIntegrityGuard.requireBound(plugin, "service:validated");
            plugin.getLogger().info("Usluga zweryfikowana pomyslnie!");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("Blad podczas weryfikacji uslug: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean verifyChallengeResponse(String challenge, String challengeResponse) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(RSA_PUBLIC_KEY);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(challenge.getBytes(StandardCharsets.UTF_8));

            byte[] signatureBytes = hexStringToByteArray(challengeResponse);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            plugin.getLogger().warning("Blad weryfikacji RSA challenge: " + e.getMessage());
            return false;
        }
    }

    private static byte[] hexStringToByteArray(String hex) {
        if (hex == null || (hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Invalid hex length");
        }

        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex character");
            }
            data[i / 2] = (byte) ((high << 4) + low);
        }
        return data;
    }

    private static String normalizeLicenseKey(String licenseKey) {
        return licenseKey == null ? "" : licenseKey.trim().toUpperCase(Locale.ROOT);
    }
}
