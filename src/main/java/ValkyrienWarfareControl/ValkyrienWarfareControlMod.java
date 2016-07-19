package ValkyrienWarfareControl;

import ValkyrienWarfareBase.ValkyrienWarfareMod;
import ValkyrienWarfareControl.Block.BlockAirShipEngine;
import ValkyrienWarfareControl.Block.BlockAntiGravEngine;
import ValkyrienWarfareControl.Block.BlockHovercraftController;
import ValkyrienWarfareControl.Item.ItemSystemLinker;
import ValkyrienWarfareControl.TileEntity.AntiGravEngineTileEntity;
import ValkyrienWarfareControl.TileEntity.TileEntityHoverController;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLStateEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

@Mod(modid=ValkyrienWarfareControlMod.MODID, name=ValkyrienWarfareControlMod.MODNAME, version=ValkyrienWarfareControlMod.MODVER)
public class ValkyrienWarfareControlMod {
	
	public static final String MODID = "valkyrienwarfarecontrol";
    public static final String MODNAME = "Valkyrien Warfare Control";
    public static final String MODVER = "0.2";
    
    public static ValkyrienWarfareControlMod instance;
    
    public Block basicEngine;
    public Block basicHoverController;
    public Block antigravityEngine;
    
    public Item systemLinker;
    
    @SidedProxy(clientSide="ValkyrienWarfareControl.ClientProxyControl", serverSide="ValkyrienWarfareControl.CommonProxyControl")
	public static CommonProxyControl proxy;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event){
    	instance = this;
    	registerBlocks(event);
    	registerTileEntities(event);
    	registerItems(event);
    	proxy.preInit(event);
    }

    @EventHandler
    public void init(FMLInitializationEvent event){
    	proxy.init(event);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event){
    	proxy.postInit(event);
    }
    
    private void registerBlocks(FMLStateEvent event){
    	basicEngine = new BlockAirShipEngine(Material.ROCK).setUnlocalizedName("basicengine").setRegistryName(ValkyrienWarfareMod.MODID, "basicengine").setCreativeTab(CreativeTabs.REDSTONE);
    	basicHoverController = new BlockHovercraftController(Material.ROCK).setUnlocalizedName("basichovercraftcontroller").setRegistryName(ValkyrienWarfareMod.MODID, "basichovercraftcontroller").setCreativeTab(CreativeTabs.REDSTONE);
    	antigravityEngine = new BlockAntiGravEngine(Material.ROCK).setUnlocalizedName("antigravengine").setUnlocalizedName("antigravengine").setRegistryName(ValkyrienWarfareMod.MODID, "antigravengine").setCreativeTab(CreativeTabs.REDSTONE);
    	
    	GameRegistry.registerBlock(basicEngine);
    	GameRegistry.registerBlock(basicHoverController);
    	GameRegistry.registerBlock(antigravityEngine);
    }
    
    private void registerTileEntities(FMLStateEvent event){
    	TileEntity.addMapping(TileEntityHoverController.class, "tilehovercontroller");
    	TileEntity.addMapping(AntiGravEngineTileEntity.class, "tileantigravengine");
    }

    private void registerItems(FMLStateEvent event){
    	systemLinker = new ItemSystemLinker().setUnlocalizedName("systemlinker").setCreativeTab(CreativeTabs.REDSTONE).setMaxStackSize(1);
    	GameRegistry.registerItem(systemLinker, "systemLinker");
    }
    
}
