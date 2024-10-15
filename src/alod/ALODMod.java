package alod;

import arc.*;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;

@SuppressWarnings("InstantiationOfUtilityClass")
public class ALODMod extends Mod{

    public ALODMod(){
        if (Vars.steamPlayerName.equals("Slotterleet"))
            Log.level = Log.LogLevel.debug;

        new DamageDisplay();

        Events.on(ClientLoadEvent.class, e -> {
            new AutoUpdater();
        });

        Vars.ui.settings.addCategory("A Lot of Damage", t -> {
            t.checkPref("alod-toggle", true, b -> {
                Core.settings.put("alod-toggle", true);
                Vars.ui.showOkText("Did You Know?", "You can disable damage numbers by going to Mods section of the main menu, then disabling [accent]A Lot of Damage[] there! You should absolutely try it out!", () -> {});
            });
        });
    }
}
