package com.admincontrol;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminControl implements ModInitializer {
    public static final String MOD_ID = "admincontrol";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static volatile Config CFG = new Config();

    @Override
    public void onInitialize() {
        CFG = Config.load();
        Net.register();
        Events.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Commands.register(dispatcher));
        LOGGER.info("Admin Control cargado. Usa /ac help");
    }
}
