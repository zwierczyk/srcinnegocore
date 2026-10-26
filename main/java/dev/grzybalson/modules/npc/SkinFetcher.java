package dev.grzybalson.modules.npc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches Minecraft skin textures from the Mojang API.
 */
public class SkinFetcher {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    /**
     * Fetch skin data for a given player name.
     * Returns a String[2] = { textureValue, textureSignature }, or null on failure.
     */
    public static CompletableFuture<String[]> fetchSkin(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest uuidReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + playerName))
                    .GET()
                    .build();
                HttpResponse<String> uuidResp = HTTP.send(uuidReq, HttpResponse.BodyHandlers.ofString());
                if (uuidResp.statusCode() != 200) return null;

                JsonObject uuidJson = JsonParser.parseString(uuidResp.body()).getAsJsonObject();
                String uuid = uuidJson.get("id").getAsString();

                HttpRequest profileReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false"))
                    .GET()
                    .build();
                HttpResponse<String> profileResp = HTTP.send(profileReq, HttpResponse.BodyHandlers.ofString());
                if (profileResp.statusCode() != 200) return null;

                JsonObject profileJson = JsonParser.parseString(profileResp.body()).getAsJsonObject();
                JsonObject textures = profileJson.getAsJsonArray("properties").get(0).getAsJsonObject();

                String value = textures.get("value").getAsString();
                String signature = textures.get("signature").getAsString();

                return new String[]{value, signature};
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }
}
