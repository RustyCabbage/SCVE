package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.ListMap;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

import static data.scripts.SCVE_Utils.*;

public class SCVE_ModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(SCVE_ModPlugin.class);

    public static Set<String> allModules = new HashSet<>();
    public static ListMap<String> modToHull = new ListMap<>();
    public static ListMap<String> modToWeapon = new ListMap<>();
    public static ListMap<String> modToWing = new ListMap<>();

    public static final String
            DEFAULT_SHIP_FILTER_SETTING = MOD_PREFIX + "_" + "defaultShipFilter",
            DEFAULT_WEAPON_WING_FILTER_SETTING = MOD_PREFIX + "_" + "defaultWeaponWingFilter",
            DEFAULT_HULLMOD_FILTER_SETTING = MOD_PREFIX + "_" + "defaultHullModFilter";
    public static int
            DEFAULT_SHIP_FILTER = 0,
            DEFAULT_WEAPON_WING_FILTER = 2,
            DEFAULT_HULLMOD_FILTER = 0;

    @Override
    public void onApplicationLoad() {
        allModules = getAllModules();
        modToHull = getModToHullListMap(allModules);
        modToWeapon = getModToWeaponListMap();
        modToWing = getModToWingListMap();
        SCVE_FilterUtils.getOriginalData();

        try {
            DEFAULT_SHIP_FILTER = Global.getSettings().getInt(DEFAULT_SHIP_FILTER_SETTING);
        } catch (Exception ex) {
            DEFAULT_SHIP_FILTER = 0;
        }
        try {
            DEFAULT_WEAPON_WING_FILTER = Global.getSettings().getInt(DEFAULT_WEAPON_WING_FILTER_SETTING);
        } catch (Exception ex) {
            DEFAULT_WEAPON_WING_FILTER = 2;
        }
        try {
            DEFAULT_HULLMOD_FILTER = Global.getSettings().getInt(DEFAULT_HULLMOD_FILTER_SETTING);
        } catch (Exception ex) {
            DEFAULT_HULLMOD_FILTER = 0;
        }

    }

    @Override
    public void onGameLoad(boolean newGame) {
        SCVE_FilterUtils.restoreOriginalData(true, true, true);
    }
}
