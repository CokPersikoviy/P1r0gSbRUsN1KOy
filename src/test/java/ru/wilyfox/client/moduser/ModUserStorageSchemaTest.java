package ru.wilyfox.client.moduser;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModUserStorageSchemaTest {
    @Test
    void savedNamesKeepTheirJsonSchemaAcrossReleases() throws Exception {
        Class<?> fileType = Class.forName("ru.wilyfox.client.moduser.ModUserStorage$ModUsersFile",
                false, getClass().getClassLoader());
        Gson gson = new Gson();
        String existingSave = "{\"names\":[\"Fox\",\"Player_2\"]}";

        Object loaded = gson.fromJson(existingSave, fileType);

        assertEquals(JsonParser.parseString(existingSave), JsonParser.parseString(gson.toJson(loaded)));
        assertEquals("names", fileType.getDeclaredField("names").getAnnotation(SerializedName.class).value());
    }
}
