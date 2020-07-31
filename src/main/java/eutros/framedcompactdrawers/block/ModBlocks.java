package eutros.framedcompactdrawers.block;

import com.jaquadro.minecraft.storagedrawers.item.ItemDrawers;
import eutros.framedcompactdrawers.FramedCompactDrawers;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.IForgeRegistry;

import java.util.Objects;

public class ModBlocks {

    public static BlockCompDrawersCustom framedCompactDrawer;
    public static BlockControllerCustom framedDrawerController;
    public static BlockSlaveCustom framedSlave;

    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        IForgeRegistry<Block> r = event.getRegistry();

        AbstractBlock.Properties properties = AbstractBlock.Properties.create(Material.WOOD);
        framedCompactDrawer = new BlockCompDrawersCustom(properties);
        r.register(framedCompactDrawer
                .setRegistryName(FramedCompactDrawers.MOD_ID, "framed_compact_drawer"));

        framedDrawerController = new BlockControllerCustom(properties);
        r.register(framedDrawerController
                .setRegistryName(FramedCompactDrawers.MOD_ID, "framed_drawer_controller"));

        framedSlave = new BlockSlaveCustom(properties);
        r.register(framedSlave
                .setRegistryName(FramedCompactDrawers.MOD_ID, "framed_slave"));
    }

    public static void registerItems(RegistryEvent.Register<Item> event) {
        IForgeRegistry<Item> r = event.getRegistry();

        Item.Properties properties = new Item.Properties()
                .group(FramedCompactDrawers.CREATIVE_TAB);

        r.register(new ItemDrawers(framedCompactDrawer, properties)
                .setRegistryName(Objects.requireNonNull(framedCompactDrawer.getRegistryName())));

        r.register(new BlockItem(framedDrawerController, properties)
                .setRegistryName(Objects.requireNonNull(framedDrawerController.getRegistryName())));

        r.register(new BlockItem(framedSlave, properties)
                .setRegistryName(Objects.requireNonNull(framedSlave.getRegistryName())));
    }

    public static void setRenderLayers() {
        RenderTypeLookup.setRenderLayer(framedCompactDrawer, RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(framedDrawerController, RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(framedSlave, RenderType.getTranslucent());
    }

}
