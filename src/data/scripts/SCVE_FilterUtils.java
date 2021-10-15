package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import org.apache.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector3f;

import java.util.*;

import static data.scripts.SCVE_ModPlugin.*;
import static data.scripts.SCVE_Utils.*;

public class SCVE_FilterUtils {

    private static final Logger log = Global.getLogger(SCVE_FilterUtils.class);

    public static boolean globalFirstLoad = true;
    public static int
            shipFilter,
            weaponWingFilter,
            hullModFilter;
    public static Set<String> blacklistedShips = new HashSet<>();
    public static HashMap<String, Set<String>> ORIGINAL_WEAPON_TAGS_MAP = new HashMap<>();
    public static HashMap<String, Set<String>> ORIGINAL_WING_TAGS_MAP = new HashMap<>();
    public static HashMap<String, Float> ORIGINAL_WING_OP_COST_MAP = new HashMap<>();
    public static HashMap<String, ArrayList<Boolean>> ORIGINAL_HULLMOD_QUALITIES_MAP = new HashMap<>();
    public static HashMap<String, String> ORIGINAL_HULLMOD_NAMES_MAP = new HashMap<>();

    public static void getOriginalData() {
        for (WeaponSpecAPI weapon : Global.getSettings().getAllWeaponSpecs()) {
            Set<String> weaponTags = new HashSet<>(weapon.getTags()); // need to create a copy of the set, or it gets wiped later
            ORIGINAL_WEAPON_TAGS_MAP.put(weapon.getWeaponId(), weaponTags);
        }
        for (FighterWingSpecAPI wing : Global.getSettings().getAllFighterWingSpecs()) {
            Set<String> wingTags = new HashSet<>(wing.getTags());
            ORIGINAL_WING_TAGS_MAP.put(wing.getId(), wingTags);
            ORIGINAL_WING_OP_COST_MAP.put(wing.getId(), wing.getOpCost(null));
        }
        for (HullModSpecAPI hullMod : Global.getSettings().getAllHullModSpecs()) {
            ORIGINAL_HULLMOD_QUALITIES_MAP.put(hullMod.getId(),
                    new ArrayList<>(Arrays.asList(hullMod.hasTag(Tags.HULLMOD_DMOD), hullMod.isHidden(), hullMod.isHiddenEverywhere())));
            ORIGINAL_HULLMOD_NAMES_MAP.put(hullMod.getId(), hullMod.getDisplayName());
        }
    }

    public static void restoreOriginalData(boolean restoreWeapons, boolean restoreWings, boolean restoreHullmods) {
        if (restoreWeapons) {
            for (WeaponSpecAPI weapon : Global.getSettings().getAllWeaponSpecs()) {
                weapon.getTags().clear();
                weapon.getTags().addAll(ORIGINAL_WEAPON_TAGS_MAP.get(weapon.getWeaponId()));
            }
        }
        if (restoreWings) {
            for (FighterWingSpecAPI wing : Global.getSettings().getAllFighterWingSpecs()) {
                wing.getTags().clear();
                wing.getTags().addAll(ORIGINAL_WING_TAGS_MAP.get(wing.getId()));
                wing.setOpCost(ORIGINAL_WING_OP_COST_MAP.get(wing.getId()));
            }
        }
        if (restoreHullmods) {
            for (HullModSpecAPI hullMod : Global.getSettings().getAllHullModSpecs()) {
                if (ORIGINAL_HULLMOD_QUALITIES_MAP.get(hullMod.getId()).get(0)) {
                    hullMod.addTag(Tags.HULLMOD_DMOD);
                }
                hullMod.setHidden(ORIGINAL_HULLMOD_QUALITIES_MAP.get(hullMod.getId()).get(1));
                hullMod.setHiddenEverywhere(ORIGINAL_HULLMOD_QUALITIES_MAP.get(hullMod.getId()).get(2));
                hullMod.setDisplayName(ORIGINAL_HULLMOD_NAMES_MAP.get(hullMod.getId()));
            }
        }
    }

    /*
     SPACE - default everything

     Q - spoiler filter left
     W - spoiler filter right
     0 = block all spoilers
     1 = block heavy spoilers
     2 = block no spoilers

     A - weapon filter left
     S - weapon filter right
     0 = block all weapons not from the mod
     1 = block all mod weapons not from the mod
     2 = default (block restricted weapons)
     3 = allow all weapons

     Z - hullmod filter left
     X - hullmod filter right
     0 - default
     1 - show s-mods
     2 - show d-mods
     3 - show all hullmods
     */
    public static Vector3f switchFilter(MissionDefinitionAPI api, String modId) {
        restoreOriginalData(true, true, true);
        if (globalFirstLoad || Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            shipFilter = 0;
            weaponWingFilter = 2;
            hullModFilter = 0;
            globalFirstLoad = false;
        } else if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
            shipFilter--;
            if (shipFilter < 0) {
                shipFilter = 2;
            }
        } else if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
            shipFilter++;
            if (shipFilter > 2) {
                shipFilter = 0;
            }
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
            weaponWingFilter--;
            if (weaponWingFilter < 0) {
                weaponWingFilter = 3;
            }
        } else if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
            weaponWingFilter++;
            if (weaponWingFilter > 3) {
                weaponWingFilter = 0;
            }
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_Z)) {
            hullModFilter--;
            if (hullModFilter < 0) {
                hullModFilter = 3;
            }
        } else if (Keyboard.isKeyDown(Keyboard.KEY_X)) {
            hullModFilter++;
            if (hullModFilter > 3) {
                hullModFilter = 0;
            }
        }
        blacklistedShips = getFilteredShips(shipFilter);
        blacklistedShips.addAll(allModules);
        filterWeaponsAndWings(weaponWingFilter, modId);
        addExtraHullMods(hullModFilter);
        createFilterBriefing(api);
        return new Vector3f(shipFilter, weaponWingFilter, hullModFilter);
    }


    public static String createFilterBriefing(MissionDefinitionAPI api) {
        String
                shipFilterText = getString("filterNone"),
                weaponWingFilterText = getString("filterDefault"),
                extraHullModText = getString("filterNone");
        switch (shipFilter) {
            case 0:
                shipFilterText = getString("filterHeavy");
                break;
            case 1:
                shipFilterText = getString("filterLight");
                break;
            default: // case 2
                break;
        }
        switch (weaponWingFilter) {
            case 0:
                weaponWingFilterText = getString("filterHeavy");
                break;
            case 1:
                weaponWingFilterText = getString("filterLight");
                break;
            case 3:
                weaponWingFilterText = getString("filterNone");
            default: // case 2
                break;
        }
        switch (hullModFilter) {
            case 1:
                extraHullModText = getString("filterSMods");
                break;
            case 2:
                extraHullModText = getString("filterDMods");
                break;
            case 3:
                extraHullModText = getString("filterAllMods");
                break;
            default: // case 0
                break;
        }
        String briefingText = getString("filterBriefingS") + shipFilterText + getString("filterBriefingBreak")
                + getString("filterBriefingW") + weaponWingFilterText + getString("filterBriefingBreak")
                + getString("filterBriefingH") + extraHullModText;
        api.addBriefingItem(briefingText);
        return briefingText;
    }

    public static Set<String> getFilteredShips(int filterLevel) {
        Set<String> filteredShips = new HashSet<>();
        switch (filterLevel) {
            case 0:
                for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
                    if (shipHullSpec.hasTag(Tags.RESTRICTED) || shipHullSpec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX)) {
                        filteredShips.add(shipHullSpec.getHullId());
                    }
                }
                break;
            case 1:
                for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
                    if (shipHullSpec.hasTag(Tags.RESTRICTED)) {
                        filteredShips.add(shipHullSpec.getHullId());
                    }
                }
                break;
            default: // case 0
                break;
        }
        return filteredShips;
    }

    public static void filterWeaponsAndWings(int filterLevel, String modId) {
        if (modId == null) {
            modId = "null"; // vanilla sources start with null
        }
        switch (filterLevel) {
            case 0: // only weapons/wings from the mod
                for (WeaponSpecAPI weaponSpec : Global.getSettings().getAllWeaponSpecs()) {
                    if (modToWeapon.getList(modId).contains(weaponSpec.getWeaponId())) {
                        continue;
                    }
                    weaponSpec.addTag(Tags.RESTRICTED);
                }
                for (FighterWingSpecAPI wingSpec : Global.getSettings().getAllFighterWingSpecs()) {
                    if (modToWing.getList(modId).contains(wingSpec.getId())) {
                        continue;
                    }
                    wingSpec.setOpCost(100000);
                }
                break;
            case 1: // only weapons/wings from the mod and vanilla
                for (WeaponSpecAPI weaponSpec : Global.getSettings().getAllWeaponSpecs()) {
                    if (modToWeapon.getList(modId).contains(weaponSpec.getWeaponId())
                            || modToWeapon.getList(VANILLA_CATEGORY).contains(weaponSpec.getWeaponId())) {
                        continue;
                    }
                    weaponSpec.addTag(Tags.RESTRICTED);
                }
                for (FighterWingSpecAPI wingSpec : Global.getSettings().getAllFighterWingSpecs()) {
                    if (modToWing.getList(modId).contains(wingSpec.getId())
                            || modToWing.getList(VANILLA_CATEGORY).contains(wingSpec.getId())) {
                        continue;
                    }
                    wingSpec.setOpCost(100000);
                }
                break;
            case 3: // show all weapons, including restricted ones
                for (WeaponSpecAPI weaponSpec : Global.getSettings().getAllWeaponSpecs()) {
                    weaponSpec.getTags().remove(Tags.RESTRICTED);
                }
                for (FighterWingSpecAPI wingSpec : Global.getSettings().getAllFighterWingSpecs()) {
                    wingSpec.getTags().remove(Tags.RESTRICTED);
                }
                break;
            default: // case 2: default settings
                break;
        }
    }

    public static void addExtraHullMods(int filterLevel) {
        switch (filterLevel) {
            case 1: // s-mods
                for (HullModSpecAPI hullModSpec : Global.getSettings().getAllHullModSpecs()) {
                    if (hullModSpec.getId().startsWith(MOD_PREFIX)) {
                        hullModSpec.setHidden(false);
                        hullModSpec.setHiddenEverywhere(false);
                        hullModSpec.setDisplayName("{" + hullModSpec.getDisplayName() + "}");
                    }
                }
                break;
            case 2: // d-mods
                for (HullModSpecAPI hullModSpec : Global.getSettings().getAllHullModSpecs()) {
                    if (hullModSpec.hasTag(Tags.HULLMOD_DMOD)) {
                        hullModSpec.getTags().remove(Tags.HULLMOD_DMOD);
                        hullModSpec.setHidden(false);
                        hullModSpec.addUITag("{D-Mod}");
                        hullModSpec.setDisplayName("{" + hullModSpec.getDisplayName() + "}");
                    }
                }
                break;
            case 3: // all mods todo: see if I need to reset these UI tags
                for (HullModSpecAPI hullModSpec : Global.getSettings().getAllHullModSpecs()) {
                    if (hullModSpec.isHidden()) {
                        hullModSpec.setHidden(false);
                        hullModSpec.addUITag("{Hidden}");
                        hullModSpec.setDisplayName("{" + hullModSpec.getDisplayName() + "}");
                        if (hullModSpec.hasTag(Tags.HULLMOD_DMOD)) {
                            hullModSpec.getTags().remove(Tags.HULLMOD_DMOD);
                            hullModSpec.addUITag("{D-Mod}");
                            hullModSpec.setDisplayName("{" + hullModSpec.getDisplayName() + "}");
                        }
                    }
                }
                break;
            default: // case 0
                break;
        }
    }
}

        /* breaks because the game doesn't crash when you have an entry in weapon_data.csv but no .weapon file.
        // also, I am keeping this in case I need to cannibalize it later
        String VANILLA_WEAPONS_BACKUP = "data/config/SCVE/weapon_data_backup.csv";
        String VANILLA_WINGS_BACKUP = "data/config/SCVE/wing_data_backup.csv";
        Set<String> vanillaWeapons = new HashSet<>();
        Set<String> vanillaWings = new HashSet<>();
        // load from backup so that we don't have cases where people adjust the stats of these things hence they don't show up
        try {
            JSONArray vanillaWeaponCSV = Global.getSettings().loadCSV(VANILLA_WEAPONS_BACKUP);
            for (int i = 0; i < vanillaWeaponCSV.length(); i++) {
                JSONObject vanillaWeaponRow = vanillaWeaponCSV.getJSONObject(i);
                String vanillaWeaponId = vanillaWeaponRow.getString("id");
                if (vanillaWeaponId.isEmpty()) {
                    continue;
                }
                vanillaWeapons.add(vanillaWeaponId);
            }
            JSONArray vanillaWingsCSV = Global.getSettings().loadCSV(VANILLA_WINGS_BACKUP);
            for (int j = 0; j < vanillaWingsCSV.length(); j++) {
                JSONObject vanillaWingRow = vanillaWingsCSV.getJSONObject(j);
                String vanillaWingId = vanillaWingRow.getString("id");
                if (vanillaWingId.isEmpty()) {
                    continue;
                }
                vanillaWings.add(vanillaWingId);
            }
        } catch (IOException | JSONException e) {
            log.error("Could not load vanilla data");
        }
        switch (filterLevel) {
            case 0: // only weapons/wings from that mod
                try {
                    JSONArray allWeaponsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("id", WEAPON_DATA_CSV, "starsector-core");
                    for (int k = 0; k < allWeaponsCSV.length(); k++) {
                        JSONObject weaponRow = allWeaponsCSV.getJSONObject(k);
                        String weaponId = weaponRow.getString("id");
                        String opCost = weaponRow.getString("OPs");
                        String hints = weaponRow.getString("hints");
                        String weaponSource = weaponRow.getString("fs_rowSource");
                        // don't add restricted tag to any weapon that is from this mod
                        if (weaponId.isEmpty()
                                || opCost.isEmpty()
                                || hints.contains("SYSTEM")
                                || weaponSource.contains((modId.equals("null") ? modId : Global.getSettings().getModManager().getModSpec(modId).getPath()))
                        ) {
                            continue;
                        }
                        WeaponSpecAPI weaponSpec = Global.getSettings().getWeaponSpec(weaponId);
                        if (weaponSpec.getAIHints().contains(WeaponAPI.AIHints.SYSTEM)) {
                            continue;
                        }
                        weaponSpec.addTag(Tags.RESTRICTED);
                    }
                    JSONArray allWingsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("id", WING_DATA_CSV, "starsector-core");
                    for (int l = 0; l < allWingsCSV.length(); l++) {
                        JSONObject wingRow = allWingsCSV.getJSONObject(l);
                        String wingId = wingRow.getString("id");
                        String wingSource = wingRow.getString("fs_rowSource");
                        if (wingId.isEmpty()
                                || wingSource.contains((modId.equals("null")) ? modId : Global.getSettings().getModManager().getModSpec(modId).getPath())
                        ) {
                            continue;
                        }
                        FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wingId);
                        wingSpec.setOpCost(100000);
                    }
                } catch (IOException | JSONException e) {
                    log.error("Could not load " + WEAPON_DATA_CSV + " or " + WING_DATA_CSV);
                }
                break;
            case 1: // only weapons/wings from that mod and vanilla
                try {
                    JSONArray allWeaponsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("id", WEAPON_DATA_CSV, "starsector-core");
                    for (int k = 0; k < allWeaponsCSV.length(); k++) {
                        JSONObject weaponRow = allWeaponsCSV.getJSONObject(k);
                        String weaponId = weaponRow.getString("id");
                        String opCost = weaponRow.getString("OPs");
                        String weaponSource = weaponRow.getString("fs_rowSource");
                        if (weaponId.isEmpty()
                                || opCost.isEmpty()
                                || weaponSource.contains((modId.equals("null")) ? modId : Global.getSettings().getModManager().getModSpec(modId).getPath())
                                || vanillaWeapons.contains(weaponId)
                        ) {
                            continue;
                        }
                        WeaponSpecAPI weaponSpec = Global.getSettings().getWeaponSpec(weaponId);
                        weaponSpec.addTag(Tags.RESTRICTED);
                    }
                    JSONArray allWingsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("id", WING_DATA_CSV, "starsector-core");
                    for (int l = 0; l < allWingsCSV.length(); l++) {
                        JSONObject wingRow = allWingsCSV.getJSONObject(l);
                        String wingId = wingRow.getString("id");
                        String wingSource = wingRow.getString("fs_rowSource");
                        if (wingId.isEmpty()
                                || wingSource.contains((modId.equals("null")) ? modId : Global.getSettings().getModManager().getModSpec(modId).getPath())
                                || vanillaWings.contains(wingId)
                        ) {
                            continue;
                        }
                        FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wingId);
                        wingSpec.setOpCost(100000);
                    }
                } catch (IOException | JSONException e) {
                    log.error("Could not load " + WEAPON_DATA_CSV + " or " + WING_DATA_CSV);
                }
                break;
            case 3: // show all weapons, including restricted ones
                for (WeaponSpecAPI weaponSpec : Global.getSettings().getAllWeaponSpecs()) {
                    weaponSpec.getTags().remove(Tags.RESTRICTED);
                }
                for (FighterWingSpecAPI wingSpec : Global.getSettings().getAllFighterWingSpecs()) {
                    wingSpec.getTags().remove(Tags.RESTRICTED);
                }
                break;
            default: // case 2: default settings
                break;
        }
         */
