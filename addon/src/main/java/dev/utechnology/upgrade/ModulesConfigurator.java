package dev.utechnology.upgrade;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;

/** The controller screen's module slots, in a row (GTM's FancyInvConfigurator lays slots out in a square). */
public class ModulesConfigurator implements IFancyConfigurator {

    private final CustomItemStackHandler modules;

    public ModulesConfigurator(CustomItemStackHandler modules) {
        this.modules = modules;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("utechnology.gui.modules");
    }

    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.BUTTON_ITEM_OUTPUT;
    }

    @Override
    public Widget createConfigurator() {
        int n = modules.getSlots();
        var group = new WidgetGroup(0, 0, 18 * n + 16, 34);
        for (int i = 0; i < n; i++)
            group.addWidget(new SlotWidget(modules, i, 8 + i * 18, 8, true, true).setBackgroundTexture(GuiTextures.SLOT));
        return group;
    }
}
