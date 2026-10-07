package ru.wilyfox.client.highlight;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static ru.wilyfox.client.highlight.UsefulWorldHighlightRenderHook.HighlightBlockType.*;

class ShardTextureSignatureTest {
    @Test void changingProfileMetadataAndUrlSchemePreservesShardIdentity() {
        String hash = "b1767faa366c80577952f5e0078159d5977f37202f38d4817d4a9245ad48a90d";
        assertEquals(DIAMOND_SHARD, fromTextureValue(encoded("{\"timestamp\":123,\"profileName\":\"another\","
                + "\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + hash + "\"}}}")));
        assertEquals(GOLDEN_SHARD, fromTextureValue(encoded("""
                { "textures": { "SKIN": { "url": "http://textures.minecraft.net/texture/54bf893fc6defad218f7836efefbe636f1c2cc1bb650c82fccd99f2c1ee6" } } }
                """)));
    }

    @Test void malformedOrUnrelatedTexturesDoNotMatchOrThrow() {
        assertNull(fromTextureValue(null));
        assertNull(fromTextureValue("bad base64"));
        assertNull(fromTextureValue(encoded("{}")));
        assertNull(fromTextureValue(encoded("{\"textures\":{\"SKIN\":{\"url\":\"https://example.com/texture/54bf893fc6defad218f7836efefbe636f1c2cc1bb650c82fccd99f2c1ee6\"}}}")));
    }

    private static String encoded(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
