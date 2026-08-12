package yofred.dev.justcompatible;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yofred.dev.justcore.api.JustCoreApi;
import yofred.dev.justcore.api.JustModule;
import yofred.dev.justcompatible.compat.bountiful.BountifulBaublesReconciler;
import net.neoforged.fml.ModList;

@Mod(JustCompatible.MODID)
public final class JustCompatible {
    public static final String MODID = "justcompatible";
    public static final String VERSION = "0.2.1";
    public static final Logger LOGGER = LoggerFactory.getLogger("Just Compatible");

    public JustCompatible(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, JustCompatibleConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(JustCompatibleCommands::register);
        if (CompatibilityProbe.bountifulSupported()) {
            NeoForge.EVENT_BUS.addListener(BountifulBaublesReconciler::onLogin);
            NeoForge.EVENT_BUS.addListener(BountifulBaublesReconciler::onChangedDimension);
        }
        JustCoreApi.registerModule(new JustModule(MODID, "Just Compatible", VERSION));
    }
}
