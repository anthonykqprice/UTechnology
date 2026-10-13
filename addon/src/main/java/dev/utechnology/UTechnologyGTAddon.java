package dev.utechnology;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

@GTAddon
public class UTechnologyGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return UTechnology.REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return UTechnology.MOD_ID;
    }
}
