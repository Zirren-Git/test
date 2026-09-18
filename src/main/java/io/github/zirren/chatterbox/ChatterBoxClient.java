package io.github.zirren.chatterbox;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatterBoxClient implements ClientModInitializer {
	public static final String MOD_ID = "chatterbox";
	public static final Logger LOGGER = LoggerFactory.getLogger("ChatterBox");

	@Override
	public void onInitializeClient() {
		LOGGER.info("ChatterBox initializing");
	}
}
