package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.util.ListMap;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SCVE_Utils {

    private static final Logger log = Global.getLogger(SCVE_Utils.class);

    public static final String
            //MOD_ID = "ShipCatalogueVariantEditor",
            MOD_PREFIX = "SCVE",
            VANILLA_CATEGORY = "SCVE_Vanilla",
            HULL_SUFFIX = "_Hull", //todo replace with getString("hullSuffix")
            SHIP_DATA_CSV = "data/hulls/ship_data.csv",
            WEAPON_DATA_CSV = "data/weapons/weapon_data.csv",
            WING_DATA_CSV = "data/hulls/wing_data.csv";

    public static String getString(String id) {
        return Global.getSettings().getString(MOD_PREFIX, id);
    }

    public static Set<String> getAllModules() {
        Set<String> modulesSet = new HashSet<>();
        for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
            if (shipHullSpec.isDefaultDHull()) {
                continue;
            }
            String hullVariantId = shipHullSpec.getHullId() + HULL_SUFFIX;
            ShipVariantAPI variant = Global.getSettings().getVariant(hullVariantId);
            for (String moduleId : variant.getStationModules().values()) {
                modulesSet.add(Global.getSettings().getVariant(moduleId).getHullSpec().getHullId());
            }
            //modulesSet.addAll(variant.getStationModules().values()); // values() are hull variant ids
        }
        // remove skins of modules because idk this is a thing with Diable
        for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
            if (!shipHullSpec.isBaseHull()
                    && modulesSet.contains(shipHullSpec.getBaseHullId())) {
                modulesSet.add(shipHullSpec.getHullId());
            }
        }
        //log.info(modulesSet);
        return modulesSet;
    }

    public static void initializeMission(MissionDefinitionAPI api, String playerTagline, String modId) {
        initializeMission(api, playerTagline, modId, true);
    }

    public static void initializeMission(MissionDefinitionAPI api, String playerTagline, String modId, boolean switchFilter) {
        String fleetPrefix = getString("fleetPrefix");
        //String playerTagline = getString("vanillaTagline");
        String enemyTagLine = getString("enemyTagline");
        float mapSize = 8000f; // multiples of 2000 only
        api.initFleet(FleetSide.PLAYER, fleetPrefix, FleetGoal.ATTACK, true);
        api.initFleet(FleetSide.ENEMY, fleetPrefix, FleetGoal.ATTACK, true);
        api.setFleetTagline(FleetSide.PLAYER, playerTagline);
        api.setFleetTagline(FleetSide.ENEMY, enemyTagLine);
        api.addToFleet(FleetSide.ENEMY, "atlas_Standard", FleetMemberType.SHIP, true);
        api.initMap(-mapSize / 2f, mapSize / 2f, -mapSize / 2f, mapSize / 2f);
        if (switchFilter) {
            SCVE_FilterUtils.setFilter(api, modId);
        }
    }

    public static boolean validateHullSpec(ShipHullSpecAPI shipHullSpec, Set<String> blacklist) {
        if (shipHullSpec.isDefaultDHull()) {
            return false;
        } else return !(shipHullSpec.getHullSize() == ShipAPI.HullSize.FIGHTER
                || shipHullSpec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)
                //|| shipHullSpec.getHullId().matches(STATION_OR_MODULE_REGEX)
                //|| shipHullSpec.getSpriteName().matches(STATION_OR_MODULE_REGEX)
                //|| shipHullSpec.getTags().toString().matches(STATION_OR_MODULE_REGEX)
                //|| shipHullSpec.getBuiltInMods().contains(HullMods.VASTBULK)
                || blacklist.contains(shipHullSpec.getHullId())
                || Global.getSettings().getVariant(shipHullSpec.getHullId() + "_Hull").isStation()
                || (shipHullSpec.getManufacturer().equals(getString("commonTech")) && (!shipHullSpec.hasHullName() || shipHullSpec.getDesignation().isEmpty()))
                || shipHullSpec.getHullId().equals("shuttlepod") // frick it has the same format as SWP arcade ships
                || shipHullSpec.getHullId().startsWith("TAR_") // literally can't find anything to block Practice Target hulls from the custom mission
                || shipHullSpec.getHullId().startsWith("loa_arscapitol") // breaks stuff in simulator
        );

    }

    public static boolean validateHullSpecExcludingFighters(ShipHullSpecAPI shipHullSpec, Set<String> blacklist) {
        if (shipHullSpec.isDefaultDHull()) {
            return false;
        } else return !(shipHullSpec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)
                //|| shipHullSpec.getHullId().matches(STATION_OR_MODULE_REGEX)
                //|| shipHullSpec.getSpriteName().matches(STATION_OR_MODULE_REGEX)
                //|| shipHullSpec.getTags().toString().matches(STATION_OR_MODULE_REGEX)
                //|| shipHullSpec.getBuiltInMods().contains(HullMods.VASTBULK)
                || blacklist.contains(shipHullSpec.getHullId())
                || Global.getSettings().getVariant(shipHullSpec.getHullId() + "_Hull").isStation()
                || (shipHullSpec.getManufacturer().equals(getString("commonTech")) && (!shipHullSpec.hasHullName() || shipHullSpec.getDesignation().isEmpty()))
                || shipHullSpec.getHullId().equals("shuttlepod") // frick it has the same format as SWP arcade ships
                || shipHullSpec.getHullId().startsWith("TAR_")); // literally can't find anything to block Practice Target hulls from the custom mission
    }

    public static ListMap<String> getModToHullListMap(Set<String> blacklist) {
        try {
            // create ListMap of sources (file paths) to base hulls, mods only
            ListMap<String> sourceToHullListMap = new ListMap<>();
            JSONArray array = Global.getSettings().getMergedSpreadsheetDataForMod("id", SHIP_DATA_CSV, "starsector-core");
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                String id = row.getString("id");
                String source = row.getString("fs_rowSource");
                if (id.isEmpty()
                        || source.startsWith("null")) { // block vanilla hulls
                    continue;
                }
                ShipHullSpecAPI shipHullSpec = Global.getSettings().getHullSpec(id);
                // double check that the csv entry isn't just an edited vanilla hull
                if (shipHullSpec.getShipFilePath().startsWith("data") && validateHullSpec(shipHullSpec, blacklist)) {
                    sourceToHullListMap.add(source, id);
                }
            }
            //log.info("sourceToHullListMap: " + sourceToHullListMap);
            // convert ListMap keys from sources to mod IDs
            ListMap<String> modToHullListMap = new ListMap<>();
            for (String source : sourceToHullListMap.keySet()) {
                for (ModSpecAPI modSpec : Global.getSettings().getModManager().getEnabledModsCopy()) {
                    if (modSpec.isUtility()) { // skip utility mods
                        continue;
                    }
                    if (source.contains(modSpec.getPath())) {
                        modToHullListMap.put(modSpec.getId(), sourceToHullListMap.getList(source));
                    }
                }
            }
            // add skins to ListMap
            for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
                if (!shipHullSpec.getShipFilePath().startsWith("data") // skip vanilla hulls
                        || shipHullSpec.isBaseHull() // skip non-skins
                        || !(validateHullSpec(shipHullSpec, blacklist))) { // skip modules/stations
                    continue;
                }
                String hullId = shipHullSpec.getHullId();
                // non-vanilla skins
                boolean foundBaseHull = false;
                for (String key : modToHullListMap.keySet()) {
                    // if we see the base hull in the mod, add the skin to the mod's list
                    if (modToHullListMap.getList(key).contains(shipHullSpec.getBaseHullId())) {
                        modToHullListMap.add(key, hullId);
                        foundBaseHull = true;
                        break;
                    }
                }
                // vanilla skins
                if (!foundBaseHull) {
                    // we can't only include the mods that are already in the list map because some mods don't have any ship_data but do have skins
                    for (ModSpecAPI modSpec : Global.getSettings().getModManager().getEnabledModsCopy()) {
                        if (modSpec.isUtility()) {
                            continue;
                        }
                        // check if the .skin file is within that mod
                        try {
                            String shipFilePath = shipHullSpec.getShipFilePath();
                            shipFilePath = shipFilePath.replace("\\", "/"); // fix path so that we can load it
                            Global.getSettings().loadJSON(shipFilePath, modSpec.getId());
                            modToHullListMap.add(modSpec.getId(), hullId);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            //log.info("modToHullListMap: " + modToHullListMap);
            return modToHullListMap;
        } catch (IOException | JSONException e) {
            log.error("Could not load " + SHIP_DATA_CSV, e);
        }
        return null;
    }

    public static ListMap<String> getModToWeaponListMap() {
        try {
            // create ListMap of sources (file paths) to weapons, mods only
            ListMap<String> sourceToWeaponListMap = new ListMap<>();
            JSONArray array = Global.getSettings().getMergedSpreadsheetDataForMod("id", WEAPON_DATA_CSV, "starsector-core");
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                String id = row.getString("id");
                String opCost = row.getString("OPs");
                String hints = row.getString("hints");
                String source = row.getString("fs_rowSource");
                if (id.isEmpty()
                        || opCost.isEmpty() // skip weapons with N/A OP cost
                        || hints.contains("SYSTEM") // system weapons don't show up in mission anyways
                ) {
                    continue;
                }
                // vanilla weapons get their own category

                sourceToWeaponListMap.add(source, id);
            }
            //log.info("sourceToWeaponListMap: " + sourceToWeaponListMap);
            // convert ListMap keys from sources to mod IDs
            ListMap<String> modToWeaponListMap = new ListMap<>();
            for (String source : sourceToWeaponListMap.keySet()) {
                if (source.startsWith("null")) {
                    modToWeaponListMap.put(VANILLA_CATEGORY, sourceToWeaponListMap.getList(source));
                } else {
                    for (ModSpecAPI modSpec : Global.getSettings().getModManager().getEnabledModsCopy()) {
                        if (modSpec.isUtility()) {
                            continue;
                        }
                        if (source.contains(modSpec.getPath())) {
                            modToWeaponListMap.put(modSpec.getId(), sourceToWeaponListMap.getList(source));
                        }
                    }
                }
            }
            return modToWeaponListMap;
        } catch (IOException | JSONException e) {
            log.error("Could not load " + WEAPON_DATA_CSV, e);
        }
        return null;
    }

    public static ListMap<String> getModToWingListMap() {
        try {
            // create ListMap of sources (file paths) to wings, mods only
            ListMap<String> sourceToWingListMap = new ListMap<>();
            JSONArray array = Global.getSettings().getMergedSpreadsheetDataForMod("id", WING_DATA_CSV, "starsector-core");
            for (int i = 0; i < array.length(); i++) {
                JSONObject row = array.getJSONObject(i);
                String id = row.getString("id");
                String source = row.getString("fs_rowSource");
                if (id.isEmpty()) {
                    continue;
                }
                sourceToWingListMap.add(source, id);
            }
            //log.info("sourceToWingListMap: " + sourceToWingListMap);
            // convert ListMap keys from sources to mod IDs
            ListMap<String> modToWingListMap = new ListMap<>();
            for (String source : sourceToWingListMap.keySet()) {
                if (source.startsWith("null")) {
                    modToWingListMap.put(VANILLA_CATEGORY, sourceToWingListMap.getList(source));
                } else {
                    for (ModSpecAPI modSpec : Global.getSettings().getModManager().getEnabledModsCopy()) {
                        if (modSpec.isUtility()) {
                            continue;
                        }
                        if (source.contains(modSpec.getPath())) {
                            modToWingListMap.put(modSpec.getId(), sourceToWingListMap.getList(source));
                        }
                    }
                }
            }
            return modToWingListMap;
        } catch (IOException | JSONException e) {
            log.error("Could not load " + WING_DATA_CSV, e);
        }
        return null;
    }
}
