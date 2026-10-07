package ru.wilyfox.client.hud.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SocialConfigMigrationTest {
    @Test void obsoleteSocialOptOutIsIgnoredAndRemovedOnSave() {
        Gson gson = new Gson();
        for (String setting : new String[]{"modUserMesh", "socialsEnabled"}) {
            RenderConfig config = gson.fromJson("{\"" + setting + "\":false,\"modUserBadge\":false}", RenderConfig.class);
            assertFalse(config.modUserBadge, "Visual badge preference must survive");
            String saved = gson.toJson(config);
            assertFalse(saved.contains("modUserMesh"));
            assertFalse(saved.contains("socialsEnabled"));
        }
    }
}
