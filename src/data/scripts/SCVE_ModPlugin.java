package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.ListMap;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static data.scripts.SCVE_Utils.*;

public class SCVE_ModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(SCVE_ModPlugin.class);

    public static Set<String> allModules = new HashSet<>();
    public static ListMap<String> modToHull = new ListMap<>();
    public static ListMap<String> modToWeapon = new ListMap<>();
    public static ListMap<String> modToWing = new ListMap<>();

    public static final String
            MOD_PREFIX = "SCVE",
            CAMPAIGN_MOD_PREFIX = "SCVariantEditor",
            DEFAULT_SHIP_FILTER_SETTING = MOD_PREFIX + "_" + "defaultShipFilter",
            DEFAULT_WEAPON_WING_FILTER_SETTING = MOD_PREFIX + "_" + "defaultWeaponWingFilter",
            DEFAULT_HULLMOD_FILTER_SETTING = MOD_PREFIX + "_" + "defaultHullModFilter";
    public static int
            DEFAULT_SHIP_FILTER = 0,
            DEFAULT_WEAPON_WING_FILTER = 2,
            DEFAULT_HULLMOD_FILTER = 0;

    @Override
    public void onApplicationLoad() {
        allModules = getAllModuleIds();
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

    // for save compatibility
    @Override
    public void beforeGameSave() {
        FactionAPI faction = Global.getSector().getPlayerFaction();
        Iterator<String> knownHullMods = faction.getKnownHullMods().iterator();
        while (knownHullMods.hasNext()) {
            String hullModId = knownHullMods.next();
            if (hullModId.startsWith(CAMPAIGN_MOD_PREFIX)) {
                knownHullMods.remove();
            }
        }
        long tick = System.currentTimeMillis();
        // check for ships in storage / sold to markets
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                if (submarket.getCargo().getMothballedShips() == null) continue;
                for (FleetMemberAPI m : submarket.getCargo().getMothballedShips().getMembersListCopy()) {
                    Iterator<String> hullMods = m.getVariant().getHullMods().iterator();
                    while (hullMods.hasNext()) {
                        String hullModId = hullMods.next();
                        if (hullModId.startsWith(CAMPAIGN_MOD_PREFIX)) {
                            hullMods.remove();
                        }
                    }
                }
            }
        }
        // frick what if the Cabal steals one or something
        for (LocationAPI loc : Global.getSector().getAllLocations()) {
            for (CampaignFleetAPI f : loc.getFleets()) {
                for (FleetMemberAPI m : f.getFleetData().getMembersListCopy()) {
                    Iterator<String> hullMods = m.getVariant().getHullMods().iterator();
                    while (hullMods.hasNext()) {
                        String hullModId = hullMods.next();
                        if (hullModId.startsWith(CAMPAIGN_MOD_PREFIX)) {
                            hullMods.remove();
                        }
                    }
                }
            }
        }
        long tock = System.currentTimeMillis();
        log.info("Time to clean save of hullmods (ms): " + (tock - tick)); //in my test this took 1 ms
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SCVE_FilterUtils.restoreOriginalData(true, true, true);
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            if (faction.getId().equals(Factions.PLAYER)) continue;
            Iterator<String> knownHullMods = faction.getKnownHullMods().iterator();
            while (knownHullMods.hasNext()) {
                String hullModId = knownHullMods.next();
                if (hullModId.startsWith(CAMPAIGN_MOD_PREFIX)) {
                    knownHullMods.remove();
                }
            }
        }
    }
}