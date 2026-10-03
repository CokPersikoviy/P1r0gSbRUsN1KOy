package ru.wilyfox;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.wilyfox.client.Client;

public class FrogHelper implements ClientModInitializer {
	public static final String MOD_ID = "froghelper";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		new Client().init();
	}

}
