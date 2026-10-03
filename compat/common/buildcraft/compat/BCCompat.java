/*
 * Copyright (c) 2020 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */

package buildcraft.compat;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import buildcraft.api.core.BCLog;

import buildcraft.compat.module.crafttweaker.CompatModuleCraftTweaker;
import buildcraft.compat.module.forestry.CompatModuleForestry;
import buildcraft.compat.module.ic2.CompatModuleIndustrialCraft2;
import buildcraft.compat.module.theoneprobe.CompatModuleTheOneProbe;
import buildcraft.compat.network.CompatGui;
import buildcraft.core.BCCoreConfig;
import buildcraft.lib.BCBuildInfo;

//@formatter:off
@Mod(
        modid = BCCompat.MODID,
        name = "BuildCraft Legacy Compat",
        version = BCCompat.VERSION,
        acceptedMinecraftVersions = "[" + BCBuildInfo.MC_VERSION + "]",
        dependencies = BCCompat.DEPENDENCIES
)
//@formatter:on
public class BCCompat {

    static final String DEPENDENCIES = "required-after:forge@[" + BCBuildInfo.FORGE_VERSION + ",)"//
        + ";required-after:buildcraftcore@[" + BCBuildInfo.VERSION + "]"//
        + ";after:buildcrafttransport"//
        + ";after:buildcraftbuilders"//
        + ";after:buildcraftsilicon"//
        + ";after:theoneprobe"//
        + ";after:forestry"//
        + ";after:crafttweaker"//
        + ";after:ic2"//
    ;

    public static final String MODID = "buildcraftcompat";
    public static final String VERSION = BCBuildInfo.VERSION;
    public static final String GIT_BRANCH = BCBuildInfo.GIT_BRANCH;
    public static final String GIT_COMMIT_HASH = BCBuildInfo.GIT_COMMIT_HASH;
    public static final String GIT_COMMIT_MSG = BCBuildInfo.GIT_COMMIT_MSG;
    public static final String GIT_COMMIT_AUTHOR = BCBuildInfo.GIT_COMMIT_AUTHOR;

    @Mod.Instance(MODID)
    public static BCCompat instance;

    private static final Map<String, CompatModuleBase> modules = new LinkedHashMap<>();

    private static void offerAndPreInitModule(final CompatModuleBase module) {
        String cModId = module.compatModId();
        if (module.canLoad()) {
            Property prop = BCCoreConfig.config.get("modules", cModId, true);
            if (prop.getBoolean(true)) {
                modules.put(cModId, module);
                BCLog.logger.info("[compat]   + " + cModId);
                module.preInit();
            } else {
                BCLog.logger.info("[compat]   x " + cModId + " (It has been disabled in the config)");
            }
        } else {
            BCLog.logger.info("[compat]   x " + cModId + " (It cannot load)");
        }
    }

    @Mod.EventHandler
    public static void preInit(final FMLPreInitializationEvent evt) {

        BCLog.logger.info("");
        BCLog.logger.info("Starting BuildCraftCompat " + VERSION);
        BCLog.logger.info("Copyright (c) the BuildCraft team, 2011-2017");
        BCLog.logger.info("https://www.mod-buildcraft.com");
        if (!GIT_COMMIT_HASH.startsWith("${")) {
            BCLog.logger.info("Detailed Build Information:");
            BCLog.logger.info("  Branch " + GIT_BRANCH);
            BCLog.logger.info("  Commit " + GIT_COMMIT_HASH);
            BCLog.logger.info("    " + GIT_COMMIT_MSG);
            BCLog.logger.info("    committed by " + GIT_COMMIT_AUTHOR);
        }
        BCLog.logger.info("");

        BCLog.logger.info("[compat] Module list:");
        // List of all modules
        offerAndPreInitModule(new CompatModuleForestry());
        offerAndPreInitModule(new CompatModuleTheOneProbe());
        offerAndPreInitModule(new CompatModuleCraftTweaker());
        offerAndPreInitModule(new CompatModuleIndustrialCraft2());
        // End of module list
    }

    @Mod.EventHandler
    public static void init(final FMLInitializationEvent evt) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, CompatGui.guiHandlerProxy);

        // compatChannelHandler = new ChannelHandler();
        // MinecraftForge.EVENT_BUS.register(this);

        // compatChannelHandler.registerPacketType(PacketGenomeFilterChange.class);
        // compatChannelHandler.registerPacketType(PacketTypeFilterChange.class);
        // compatChannelHandler.registerPacketType(PacketRequestFilterSet.class);

        for (final CompatModuleBase m : modules.values()) {
            m.init();
        }
    }

    @Mod.EventHandler
    public static void postInit(final FMLPostInitializationEvent evt) {
        for (final CompatModuleBase m : modules.values()) {
            m.postInit();
        }
    }

    // @Mod.EventHandler
    // public void missingMapping(FMLMissingMappingsEvent event) {
    // CompatModuleForestry.missingMapping(event);
    // }

    // @SubscribeEvent
    // @SideOnly(Side.CLIENT)
    // public void handleTextureRemap(TextureStitchEvent.Pre event) {
    // if (event.map.getTextureType() == 1) {
    // TextureManager.getInstance().initIcons(event.map);
    // }
    // }

    // public static boolean isLoaded(String module) {
    // return moduleNames.contains(module);
    // }

    // public static boolean hasModule(final String module) {
    // return BuildCraftCompat.moduleNames.contains(module);
    // }
}
