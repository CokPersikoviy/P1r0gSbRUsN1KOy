package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialConfigMigrationTest {
    @Test void oldOptOutSurvivesWithoutSerializingLegacyTransportSetting() {
        Gson gson = new Gson();
        RenderConfig config = gson.fromJson("{\"modUserMesh\":false}", RenderConfig.class);
        assertFalse(config.socialsEnabled);
        String saved = gson.toJson(config);
        assertFalse(saved.contains("modUserMesh"));
        assertTrue(saved.contains("\"socialsEnabled\":false"));
    }
}
