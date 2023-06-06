package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.*;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector3f;

import java.io.IOException;
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
    public static HashMap<String, Float> ORIGINAL_WEAPON_OP_COST_MAP = new HashMap<>();
    public static HashMap<String, ArrayList<Boolean>> ORIGINAL_HULLMOD_QUALITIES_MAP = new HashMap<>();
    public static HashMap<String, String> ORIGINAL_HULLMOD_NAMES_MAP = new HashMap<>();
    public static HashMap<String, ArrayList<Integer>> ORIGINAL_HULLMOD_OP_COST_MAP = new HashMap<>();
    public static String CUSTOM_WEAPONS_PATH = "custom_wep_filter.csv";
    public static String CUSTOM_WINGS_PATH = "custom_wing_filter.csv";
    public static String CUSTOM_HULLMODS_PATH = "custom_hullmod_filter.csv";

    private static final String GachaSMods_MOD_ID = "GachaSMods";

    public static void getOriginalData() {
        for (WeaponSpecAPI weapon : Global.getSettings().getAllWeaponSpecs()) {
            Set<String> weaponTags = new HashSet<>(weapon.getTags()); // need to create a copy of the set, or it gets wiped later
            ORIGINAL_WEAPON_TAGS_MAP.put(weapon.getWeaponId(), weaponTags);
            ORIGINAL_WEAPON_OP_COST_MAP.put(weapon.getWeaponId(), weapon.getOrdnancePointCost(null));
        }
        for (FighterWingSpecAPI wing : Global.getSettings().getAllFighterWingSpecs()) {
            Set<String> wingTags = new HashSet<>(wing.getTags());
            ORIGINAL_WING_TAGS_MAP.put(wing.getId(), wingTags);
            ORIGINAL_WING_OP_COST_MAP.put(wing.getId(), wing.getOpCost(null));
        }
        for (HullModSpecAPI hullModSpec : Global.getSettings().getAllHullModSpecs()) {
            if (hullModSpec.getId().startsWith(GachaSMods_MOD_ID)) {
                continue;
            }
            ORIGINAL_HULLMOD_QUALITIES_MAP.put(hullModSpec.getId(),
                    new ArrayList<>(Arrays.asList(hullModSpec.hasTag(Tags.HULLMOD_DMOD), hullModSpec.isHidden(), hullModSpec.isHiddenEverywhere())));
            ORIGINAL_HULLMOD_NAMES_MAP.put(hullModSpec.getId(), hullModSpec.getDisplayName());
            ORIGINAL_HULLMOD_OP_COST_MAP.put(hullModSpec.getId(),
                    new ArrayList<>(Arrays.asList(hullModSpec.getFrigateCost()
                            , hullModSpec.getDestroyerCost()
                            , hullModSpec.getCruiserCost()
                            , hullModSpec.getCapitalCost())));
        }
    }

    public static void restoreOriginalData(boolean restoreWeapons, boolean restoreWings, boolean restoreHullmods) {
        if (restoreWeapons) {
            for (WeaponSpecAPI weapon : Global.getSettings().getAllWeaponSpecs()) {
                weapon.getTags().clear();
                weapon.getTags().addAll(ORIGINAL_WEAPON_TAGS_MAP.get(weapon.getWeaponId()));
                weapon.setOrdnancePointCost(ORIGINAL_WEAPON_OP_COST_MAP.get(weapon.getWeaponId()));
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
            for (HullModSpecAPI hullModSpec : Global.getSettings().getAllHullModSpecs()) {
                if (hullModSpec.getId().startsWith(GachaSMods_MOD_ID)) {
                    continue;
                }
                if (ORIGINAL_HULLMOD_QUALITIES_MAP.get(hullModSpec.getId()).get(0)) {
                    hullModSpec.addTag(Tags.HULLMOD_DMOD);
                }
                hullModSpec.setHidden(ORIGINAL_HULLMOD_QUALITIES_MAP.get(hullModSpec.getId()).get(1));
                hullModSpec.setHiddenEverywhere(ORIGINAL_HULLMOD_QUALITIES_MAP.get(hullModSpec.getId()).get(2));
                hullModSpec.setDisplayName(ORIGINAL_HULLMOD_NAMES_MAP.get(hullModSpec.getId()));
                hullModSpec.setFrigateCost(ORIGINAL_HULLMOD_OP_COST_MAP.get(hullModSpec.getId()).get(0));
                hullModSpec.setDestroyerCost(ORIGINAL_HULLMOD_OP_COST_MAP.get(hullModSpec.getId()).get(1));
                hullModSpec.setCruiserCost(ORIGINAL_HULLMOD_OP_COST_MAP.get(hullModSpec.getId()).get(2));
                hullModSpec.setCapitalCost(ORIGINAL_HULLMOD_OP_COST_MAP.get(hullModSpec.getId()).get(3));
            }
        }
    }

    public static Vector3f setFilter(MissionDefinitionAPI api, String modId) {
        return setFilter(api, modId, true);
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
     4 = custom filter

     Z - hullmod filter left
     X - hullmod filter right
     0 - default
     1 - show s-mods
     2 - show d-mods
     3 - show all hullmods
     */
    // todo separate weapons and wings?
    public static Vector3f setFilter(MissionDefinitionAPI api, String modId, boolean applyFilter) {
        restoreOriginalData(true, true, true);
        if (globalFirstLoad || Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            shipFilter = DEFAULT_SHIP_FILTER;
            weaponWingFilter = DEFAULT_WEAPON_WING_FILTER;
            hullModFilter = DEFAULT_HULLMOD_FILTER;
            globalFirstLoad = false;
        } else {
            if (Keyboard.isKeyDown(Keyboard.KEY_Q)) {
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
                    weaponWingFilter = 4;
                }
            } else if (Keyboard.isKeyDown(Keyboard.KEY_S)) {
                weaponWingFilter++;
                if (weaponWingFilter > 4) {
                    weaponWingFilter = 0;
                }
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_Z)) {
                hullModFilter--;
                if (hullModFilter < 0) {
                    hullModFilter = 4;
                }
            } else if (Keyboard.isKeyDown(Keyboard.KEY_X)) {
                hullModFilter++;
                if (hullModFilter > 4) {
                    hullModFilter = 0;
                }
            }
        }
        if (applyFilter) {
            applyFilter(api, modId);
        }
        return new Vector3f(shipFilter, weaponWingFilter, hullModFilter);
    }

    public static void applyFilter(MissionDefinitionAPI api, String modId) {
        blacklistedShips = getFilteredShips(shipFilter);
        blacklistedShips.addAll(allModules);
        filterWeaponsAndWings(weaponWingFilter, modId);
        addExtraHullMods(hullModFilter);
        createFilterBriefing(api);
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
                break;
            case 4:
                weaponWingFilterText = getString("filterCustom");
                break;
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
            case 4:
                extraHullModText = getString("filterCustomMods");
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
                    weaponSpec.setOrdnancePointCost(100000);
                    //weaponSpec.addTag(Tags.RESTRICTED);
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
                    weaponSpec.setOrdnancePointCost(100000);
                    //weaponSpec.addTag(Tags.RESTRICTED);
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
            case 4: // use weapon and wing filter
                loadCustomWeaponsAndWingsFilter();
                break;
            default: // case 2: default settings
                break;
        }
    }

    public static void addExtraHullMods(int filterLevel) {
        switch (filterLevel) {
            case 1: // s-mods
                for (HullModSpecAPI hullModSpec : Global.getSettings().getAllHullModSpecs()) {
                    if (hullModSpec.getId().startsWith(GachaSMods_MOD_ID)) {
                        continue;
                    }
                    if (hullModSpec.getId().startsWith(MOD_PREFIX)) {
                        hullModSpec.setHidden(false);
                        hullModSpec.setHiddenEverywhere(false);
                        hullModSpec.setDisplayName("{" + hullModSpec.getDisplayName() + "}");
                    }
                }
                break;
            case 2: // d-mods
                for (HullModSpecAPI hullModSpec : Global.getSettings().getAllHullModSpecs()) {
                    if (hullModSpec.getId().startsWith(GachaSMods_MOD_ID)) {
                        continue;
                    }
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
                    if (hullModSpec.getId().startsWith(GachaSMods_MOD_ID)) {
                        continue;
                    }
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
            case 4: // custom
                loadCustomHullmodFilter();
                break;
            default: // case 0
                break;
        }
    }

    public static void loadCustomWeaponsAndWingsFilter() {
        try {
            JSONArray customWeaponCSV = Global.getSettings().loadCSV(CUSTOM_WEAPONS_PATH);
            for (WeaponSpecAPI weaponSpec : Global.getSettings().getAllWeaponSpecs()) {
                for (int j = 0; j < customWeaponCSV.length(); j++) {
                    JSONObject customRow = customWeaponCSV.getJSONObject(j);
                    String parameter = customRow.getString("parameter");
                    String operator = customRow.getString("operator");
                    String value = customRow.getString("value");
                    if (parameter.equals("showRestricted")) {
                        boolean showRestricted = Boolean.parseBoolean(value);
                        if (showRestricted) {
                            weaponSpec.getTags().remove(Tags.RESTRICTED);
                        }
                    }
                    if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) {
                        continue;
                    }
                    if (!validateWeaponStat(weaponSpec.getWeaponId(), parameter, operator, value)) {
                        weaponSpec.setOrdnancePointCost(10000);
                        break;
                    }
                }
            }
            JSONArray customWingCSV = Global.getSettings().loadCSV(CUSTOM_WINGS_PATH);
            for (FighterWingSpecAPI wingSpec : Global.getSettings().getAllFighterWingSpecs()) {
                for (int j = 0; j < customWingCSV.length(); j++) {
                    JSONObject customRow = customWingCSV.getJSONObject(j);
                    String parameter = customRow.getString("parameter");
                    String operator = customRow.getString("operator");
                    String value = customRow.getString("value");
                    if (parameter.equals("showRestricted")) {
                        boolean showRestricted = Boolean.parseBoolean(value);
                        if (showRestricted) {
                            wingSpec.getTags().remove(Tags.RESTRICTED);
                        }
                    }
                    if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) {
                        continue;
                    }
                    if (!validateWingStat(wingSpec.getId(), parameter, operator, value)) {
                        wingSpec.setOpCost(10000);
                        break;
                    }
                }
            }
        } catch (IOException | JSONException e) {
            log.error("Could not load " + CUSTOM_WEAPONS_PATH + " or " + CUSTOM_WINGS_PATH, e);
        }
    }

    public static boolean validateWeaponStat(String weaponId, String stat, String operator, String value) {
        WeaponSpecAPI weaponSpec = Global.getSettings().getWeaponSpec(weaponId);
        String stringToCheck = "";
        float floatToCheck = Float.NaN, lower, upper;
        List<String> arrayToCheck = new ArrayList<>();
        boolean valid = false;
        switch (stat) {
            // STRINGS
            case "id":
                stringToCheck = weaponId;
                break;
            case "name":
                stringToCheck = weaponSpec.getWeaponName();
                break;
            case "tech/manufacturer":
                stringToCheck = weaponSpec.getManufacturer();
                break;
            case "size":
                stringToCheck = weaponSpec.getSize().toString();
                break;
            case "mount type":
                stringToCheck = weaponSpec.getMountType().toString();
                break;
            case "damage type":
                stringToCheck = weaponSpec.getDamageType().toString();
                break;
            /* weapon grouping
            case "groupTag":
                stringToCheck = weaponSpec.getWeaponGroupTag();
                break;
                */
            case "primaryRoleStr":
                stringToCheck = weaponSpec.getPrimaryRoleStr();
                break;
            case "speedStr":
                if (weaponSpec.getSpeedStr() != null) {
                    stringToCheck = weaponSpec.getSpeedStr();
                }
                break;
            case "trackingStr":
                if (weaponSpec.getTrackingStr() != null) {
                    stringToCheck = weaponSpec.getTrackingStr();
                }
                break;
            case "turnRateStr":
                if (weaponSpec.getTurnRateStr() != null) {
                    stringToCheck = weaponSpec.getTurnRateStr();
                }
                break;
            case "accuracyStr":
                if (weaponSpec.getAccuracyStr() != null) {
                    stringToCheck = weaponSpec.getAccuracyStr();
                }
                break;
            /* don't think anyone wants to use these
            case "customPrimary":
                stringToCheck = weaponSpec.getCustomPrimary();
                break;
            case "customAncillary":
                stringToCheck = weaponSpec.getCustomAncillary();
                break;
            case "autofitCategory":
                stringToCheck = weaponSpec.getAutofitCategory();
                break;
             */
            // OTHER
            case "knownWeapons":
            case "priorityWeapons":
                break;
            // ARRAYS
            case "hints":
                arrayToCheck = Arrays.asList(weaponSpec.getAIHints().toString().replaceAll("[\\[\\]]", "").split(", "));
                break;
            case "tags":
                arrayToCheck = new ArrayList<>(weaponSpec.getTags());
                break;
            case "autofitCategoriesInPriorityOrder":
                arrayToCheck = new ArrayList<>(weaponSpec.getAutofitCategoriesInPriorityOrder());
                break;
            // FLOATS
            case "tier":
                floatToCheck = weaponSpec.getTier();
                break;
            case "rarity":
                floatToCheck = weaponSpec.getRarity();
                break;
            case "base value":
                floatToCheck = weaponSpec.getBaseValue();
                break;
            case "range":
                floatToCheck = weaponSpec.getMaxRange();
                break;
            case "damage/second":
                floatToCheck = weaponSpec.getDerivedStats().getDps();
                break;
            case "damage/shot":
                floatToCheck = weaponSpec.getDerivedStats().getDamagePerShot();
                break;
            case "emp/second":
                floatToCheck = weaponSpec.getDerivedStats().getEmpPerSecond();
                break;
            case "emp/shot":
                floatToCheck = weaponSpec.getDerivedStats().getEmpPerShot();
                break;
            //case "impact": // can't grab for beams
            //case "turn rate": // not in spec API
            case "OPs":
                floatToCheck = weaponSpec.getOrdnancePointCost(null);
                break;
            case "ammo":
                floatToCheck = weaponSpec.getMaxAmmo();
                break;
            case "ammo/sec":
                floatToCheck = weaponSpec.getAmmoPerSecond();
                break;
            //case "reload size": // can't be easily obtained from spec
            case "energy/shot":
                floatToCheck = weaponSpec.getDerivedStats().getFluxPerDam() * weaponSpec.getDerivedStats().getDamagePerShot();
                break;
            case "energy/second":
                floatToCheck = weaponSpec.getDerivedStats().getFluxPerSecond();
                break;
            case "chargeup":
                if (weaponSpec.isBeam()) {
                    floatToCheck = weaponSpec.getBeamChargeupTime();
                } else {
                    floatToCheck = weaponSpec.getChargeTime();
                }
                break;
            //case "chargedown": // can't grab for non-beams
            //case "burst size": // gives wrong results for beams
            //case "burst delay": // gives wrong results for beams
            case "burst duration":
                floatToCheck = weaponSpec.getDerivedStats().getBurstFireDuration();
                break;
            case "min spread":
                floatToCheck = weaponSpec.getMinSpread();
                break;
            case "max spread":
                floatToCheck = weaponSpec.getMaxSpread();
                break;
            case "spread/shot":
                floatToCheck = weaponSpec.getSpreadBuildup();
                break;
            case "spread decay/sec":
                floatToCheck = weaponSpec.getSpreadDecayRate();
                break;
            //case "beam speed": // obfuscated or something
            case "proj speed":
                if (weaponSpec.getProjectileSpec() instanceof ProjectileSpecAPI) {
                    floatToCheck = ((ProjectileSpecAPI) weaponSpec.getProjectileSpec()).getMoveSpeed(null, null);
                }
                break;
            /* kind of useless without projectile speed
            case "launch speed":
                if (weaponSpec.getProjectileSpec() instanceof MissileSpecAPI) {
                    floatToCheck = ((MissileSpecAPI) weaponSpec.getProjectileSpec()).getLaunchSpeed();
                }
                break;
            case "flight time":
                if (weaponSpec.getProjectileSpec() instanceof MissileSpecAPI) {
                    floatToCheck = ((MissileSpecAPI) weaponSpec.getProjectileSpec()).getMaxFlightTime();
                }
                break;
             */
            case "proj hitpoints":
                if (weaponSpec.getProjectileSpec() instanceof MissileSpecAPI) {
                    floatToCheck = ((MissileSpecAPI) weaponSpec.getProjectileSpec()).getHullSpec().getHitpoints();
                }
                break;
            /* just why
            case "autofireAccBonus":
                floatToCheck = weaponSpec.getAutofireAccBonus();
                break;
            case "extraArcForAI":
                floatToCheck = weaponSpec.getExtraArcForAI();
                break;
             */
            default:
                log.error("Unexpected default parameter: " + stat);
        }
        switch (operator) {
            case "startsWith":
                valid = stringToCheck.startsWith(value);
                break;
            case "!startsWith":
                valid = !stringToCheck.startsWith(value);
                break;
            case "endsWith":
                valid = stringToCheck.endsWith(value);
                break;
            case "!endsWith":
                valid = !stringToCheck.endsWith(value);
                break;
            case "contains":
                if (!stringToCheck.isEmpty()) {
                    valid = stringToCheck.contains(value);
                }
                if (!arrayToCheck.isEmpty()) {
                    valid = !Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                }
                break;
            case "!contains":
                if (!stringToCheck.isEmpty()) {
                    valid = !stringToCheck.contains(value);
                }
                if (!arrayToCheck.isEmpty()) {
                    valid = Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                }
                break;
            case "in":
                valid = Arrays.asList(value.split("\\s*,\\s*")).contains(stringToCheck);
                break;
            case "!in":
                valid = !Arrays.asList(value.split("\\s*,\\s*")).contains(stringToCheck);
                break;
            case "equals":
                valid = stringToCheck.equalsIgnoreCase(value);
                break;
            case "!equals":
                valid = !stringToCheck.equalsIgnoreCase(value);
                break;
            case "matches":
                valid = stringToCheck.matches(value);
                break;
            case "!matches":
                valid = !stringToCheck.matches(value);
                break;
            case "<":
                valid = floatToCheck < Float.parseFloat(value);
                break;
            case ">":
                valid = floatToCheck > Float.parseFloat(value);
                break;
            case "=":
                valid = floatToCheck == Float.parseFloat(value);
                break;
            case "<=":
                valid = floatToCheck <= Float.parseFloat(value);
                break;
            case ">=":
                valid = floatToCheck >= Float.parseFloat(value);
                break;
            case "!=":
                valid = floatToCheck != Float.parseFloat(value);
                break;
            case "()":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck > lower && floatToCheck < upper;
                break;
            case "[]":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck >= lower && floatToCheck <= upper;
                break;
            case "[)":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck >= lower && floatToCheck < upper;
                break;
            case "(]":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck > lower && floatToCheck <= upper;
                break;
            case "containsAll":
                valid = arrayToCheck.containsAll(Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "!containsAll":
                valid = !arrayToCheck.containsAll(Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "containsAny":
                valid = !Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "!containsAny":
                valid = Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "allIn":
                valid = Arrays.asList(value.split("\\s*,\\s*")).containsAll(arrayToCheck);
                break;
            case "!allIn":
                valid = !Arrays.asList(value.split("\\s*,\\s*")).containsAll(arrayToCheck);
                break;
            case "*":
                switch (stat) {
                    case "knownWeapons":
                        valid = Global.getSettings().getFactionSpec(value).getKnownWeapons().contains(weaponId);
                        break;
                    case "priorityWeapons":
                        valid = Global.getSettings().getFactionSpec(value).getPriorityWeapons().contains(weaponId);
                        break;
                    default:
                        break;
                }
                break;
            default:
                log.error("Unexpected default operator " + operator);
        }
        return valid;
    }

    public static boolean validateWingStat(String wingId, String stat, String operator, String value) {
        FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wingId);
        float base;
        String stringToCheck = "";
        float floatToCheck = Float.NaN, lower, upper;
        List<String> arrayToCheck = new ArrayList<>();
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "fleet", true); // aiMode=true means no crew required
        FleetMemberAPI member = fleet.getFleetData().addFleetMember(wingSpec.getVariantId());
        member.updateStats(); // fixes it being set to 0 CR and having -10% on a bunch of stats
        MutableShipStatsAPI stats = member.getStats();
        boolean valid = false;
        switch (stat) { // todo: can technically grab anything accessible through hullSpec?
            // STRINGS
            case "id":
                stringToCheck = wingId;
                break;
            case "name":
                stringToCheck = wingSpec.getWingName();
                break;
            case "tech/manufacturer":
                stringToCheck = wingSpec.getVariant().getHullSpec().getManufacturer();
                break;
            case "formation":
                stringToCheck = wingSpec.getFormation().toString();
                break;
            case "role":
                stringToCheck = wingSpec.getRole().toString();
                break;
            case "role desc":
                stringToCheck = wingSpec.getRoleDesc();
                break;
            case "defense type":
                stringToCheck = wingSpec.getVariant().getHullSpec().getDefenseType().name();
                break;
            // OTHER
            case "knownWings":
            case "priorityWings":
                break;
            // ARRAYS
            case "tags":
                arrayToCheck = new ArrayList<>(wingSpec.getTags());
                break;
            case "hints":
                arrayToCheck = Arrays.asList(wingSpec.getVariant().getHullSpec().getHints().toString().replaceAll("[\\[\\]]", "").split(", "));
                break;
            case "hullmods":
                arrayToCheck = new ArrayList<>(wingSpec.getVariant().getHullMods());
                break;
            case "autofitCategoriesInPriorityOrder":
                arrayToCheck = new ArrayList<>(wingSpec.getAutofitCategoriesInPriorityOrder());
                break;
            // FLOATS
            case "tier":
                floatToCheck = wingSpec.getTier();
                break;
            case "rarity":
                floatToCheck = wingSpec.getRarity();
                break;
            case "fleet pts":
                floatToCheck = wingSpec.getFleetPoints();
                break;
            case "op cost":
                floatToCheck = wingSpec.getOpCost(null);
                break;
            //case "range": // not in spec API
            //case "attackRunRange": // gets confusing without engagement range
            //case "attackPositionOffset": // pointless without attack run range
            case "num":
                floatToCheck = wingSpec.getNumFighters();
                break;
            case "refit":
                floatToCheck = wingSpec.getRefitTime();
                break;
            case "base value":
                floatToCheck = wingSpec.getBaseValue();
                break;
            case "hitpoints":
                base = wingSpec.getVariant().getHullSpec().getHitpoints();
                floatToCheck = stats.getHullBonus().computeEffective(base);
                break;
            case "armor rating":
                base = wingSpec.getVariant().getHullSpec().getArmorRating();
                floatToCheck = stats.getArmorBonus().computeEffective(base);
                break;
            case "max flux":
                floatToCheck = stats.getFluxCapacity().getModifiedValue();
                //floatToCheck = shipHullSpec.getFluxCapacity();
                break;
            case "flux dissipation":
                floatToCheck = stats.getFluxDissipation().getModifiedValue();
                //floatToCheck = shipHullSpec.getFluxDissipation();
                break;
            case "max speed":
                floatToCheck = stats.getMaxSpeed().getModifiedValue();
                break;
            case "acceleration":
                floatToCheck = stats.getAcceleration().getModifiedValue();
                break;
            case "deceleration":
                floatToCheck = stats.getDeceleration().getModifiedValue();
                break;
            case "max turn rate":
                floatToCheck = stats.getMaxTurnRate().getModifiedValue();
                break;
            case "turn acceleration":
                floatToCheck = stats.getTurnAcceleration().getModifiedValue();
                break;
            case "shield arc":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getArc();
                floatToCheck = stats.getShieldArcBonus().computeEffective(base);
                break;
            case "shield upkeep":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getUpkeepCost() / Math.max(0.0001f, stats.getFluxDissipation().getModifiedValue());
                floatToCheck = base * stats.getShieldUpkeepMult().getModifiedValue();
                break;
            case "shield efficiency":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed();
                floatToCheck = base * stats.getShieldDamageTakenMult().getModifiedValue();
                break;
            case "phase cost":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getPhaseCost();
                floatToCheck = stats.getPhaseCloakActivationCostBonus().computeEffective(base);
                break;
            case "phase upkeep":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getPhaseUpkeep();
                floatToCheck = stats.getPhaseCloakUpkeepCostBonus().computeEffective(base);
                break;
            case "crew":
                base = wingSpec.getVariant().getHullSpec().getMinCrew();
                floatToCheck = stats.getMinCrewMod().computeEffective(base); // todo same as max crew in vanilla, should check with mods...
                break;
            default:
                log.error("Unexpected default parameter: " + stat);
        }
        switch (operator) {
            case "startsWith":
                valid = stringToCheck.startsWith(value);
                break;
            case "!startsWith":
                valid = !stringToCheck.startsWith(value);
                break;
            case "endsWith":
                valid = stringToCheck.endsWith(value);
                break;
            case "!endsWith":
                valid = !stringToCheck.endsWith(value);
                break;
            case "contains":
                if (!stringToCheck.isEmpty()) {
                    valid = stringToCheck.contains(value);
                }
                if (!arrayToCheck.isEmpty()) {
                    valid = !Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                }
                break;
            case "!contains":
                if (!stringToCheck.isEmpty()) {
                    valid = !stringToCheck.contains(value);
                }
                if (!arrayToCheck.isEmpty()) {
                    valid = Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                }
                break;
            case "in":
                valid = Arrays.asList(value.split("\\s*,\\s*")).contains(stringToCheck);
                break;
            case "!in":
                valid = !Arrays.asList(value.split("\\s*,\\s*")).contains(stringToCheck);
                break;
            case "equals":
                valid = stringToCheck.equalsIgnoreCase(value);
                break;
            case "!equals":
                valid = !stringToCheck.equalsIgnoreCase(value);
                break;
            case "matches":
                valid = stringToCheck.matches(value);
                break;
            case "!matches":
                valid = !stringToCheck.matches(value);
                break;
            case "<":
                valid = floatToCheck < Float.parseFloat(value);
                break;
            case ">":
                valid = floatToCheck > Float.parseFloat(value);
                break;
            case "=":
                valid = floatToCheck == Float.parseFloat(value);
                break;
            case "<=":
                valid = floatToCheck <= Float.parseFloat(value);
                break;
            case ">=":
                valid = floatToCheck >= Float.parseFloat(value);
                break;
            case "!=":
                valid = floatToCheck != Float.parseFloat(value);
                break;
            case "()":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck > lower && floatToCheck < upper;
                break;
            case "[]":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck >= lower && floatToCheck <= upper;
                break;
            case "[)":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck >= lower && floatToCheck < upper;
                break;
            case "(]":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck > lower && floatToCheck <= upper;
                break;
            case "containsAll":
                valid = arrayToCheck.containsAll(Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "!containsAll":
                valid = !arrayToCheck.containsAll(Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "containsAny":
                valid = !Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "!containsAny":
                valid = Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "allIn":
                valid = Arrays.asList(value.split("\\s*,\\s*")).containsAll(arrayToCheck);
                break;
            case "!allIn":
                valid = !Arrays.asList(value.split("\\s*,\\s*")).containsAll(arrayToCheck);
                break;
            case "*":
                switch (stat) {
                    case "knownWings":
                        valid = Global.getSettings().getFactionSpec(value).getKnownFighters().contains(wingId);
                        break;
                    case "priorityWings":
                        valid = Global.getSettings().getFactionSpec(value).getPriorityFighters().contains(wingId);
                        break;
                    default:
                        break;
                }
                break;
            default:
                log.error("Unexpected default operator " + operator);
        }
        return valid;
    }

    public static void loadCustomHullmodFilter() {
        try {
            JSONArray customHullModCSV = Global.getSettings().loadCSV(CUSTOM_HULLMODS_PATH);
            for (HullModSpecAPI hullModSpec : Global.getSettings().getAllHullModSpecs()) {
                if (hullModSpec.getFrigateCost() == 0
                        && hullModSpec.getDestroyerCost() == 0
                        && hullModSpec.getCruiserCost() == 0
                        && hullModSpec.getCapitalCost() == 0) {
                    continue;
                }
                for (int j = 0; j < customHullModCSV.length(); j++) {
                    JSONObject customRow = customHullModCSV.getJSONObject(j);
                    String parameter = customRow.getString("parameter");
                    String operator = customRow.getString("operator");
                    String value = customRow.getString("value");
                    if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) {
                        continue;
                    }
                    if (!validateHullModStat(hullModSpec.getId(), parameter, operator, value)) {
                        hullModSpec.setFrigateCost(10000);
                        hullModSpec.setDestroyerCost(10000);
                        hullModSpec.setCruiserCost(10000);
                        hullModSpec.setCapitalCost(10000);
                        break;
                    }
                }
            }
        } catch (IOException | JSONException e) {
            log.error("Could not load " + CUSTOM_WEAPONS_PATH + " or " + CUSTOM_WINGS_PATH, e);
        }
    }

    // todo add fs_rowSource
    public static boolean validateHullModStat(String hullModId, String stat, String operator, String value) {
        HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
        String stringToCheck = "";
        float floatToCheck = Float.NaN, lower, upper;
        List<String> arrayToCheck = new ArrayList<>();
        boolean valid = false;
        switch (stat) {
            // STRINGS
            case "id":
                stringToCheck = hullModId;
                break;
            case "name":
                stringToCheck = hullModSpec.getDisplayName();
                break;
            case "tech/manufacturer":
                stringToCheck = hullModSpec.getManufacturer();
                break;
            case "script":
                stringToCheck = hullModSpec.getEffectClass();
                break;
            case "description":
                stringToCheck = hullModSpec.getDescription(ShipAPI.HullSize.DEFAULT);
                break;
            case "sMod description":
                stringToCheck = hullModSpec.getSModDescription(ShipAPI.HullSize.DEFAULT);
                break;
            // OTHER
            case "knownHullMods":
            case "priorityHullMods":
                break;
            // ARRAYS
            case "tags":
                arrayToCheck = new ArrayList<>(hullModSpec.getTags());
                break;
            case "uiTags":
                arrayToCheck = new ArrayList<>(hullModSpec.getUITags());
                break;
            // FLOATS
            case "tier":
                floatToCheck = hullModSpec.getTier();
                break;
            case "rarity":
                floatToCheck = hullModSpec.getRarity();
                break;
            case "base value":
                floatToCheck = hullModSpec.getBaseValue();
                break;
            case "frigateCost":
                floatToCheck = hullModSpec.getFrigateCost();
                break;
            case "destroyerCost":
                floatToCheck = hullModSpec.getDestroyerCost();
                break;
            case "cruiserCost":
                floatToCheck = hullModSpec.getCruiserCost();
                break;
            case "capitalCost":
                floatToCheck = hullModSpec.getCapitalCost();
                break;
            default:
                log.error("Unexpected default parameter: " + stat);
        }
        switch (operator) {
            case "startsWith":
                valid = stringToCheck.startsWith(value);
                break;
            case "!startsWith":
                valid = !stringToCheck.startsWith(value);
                break;
            case "endsWith":
                valid = stringToCheck.endsWith(value);
                break;
            case "!endsWith":
                valid = !stringToCheck.endsWith(value);
                break;
            case "contains":
                if (!stringToCheck.isEmpty()) {
                    valid = stringToCheck.contains(value);
                }
                if (!arrayToCheck.isEmpty()) {
                    valid = !Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                }
                break;
            case "!contains":
                if (!stringToCheck.isEmpty()) {
                    valid = !stringToCheck.contains(value);
                }
                if (!arrayToCheck.isEmpty()) {
                    valid = Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                }
                break;
            case "in":
                valid = Arrays.asList(value.split("\\s*,\\s*")).contains(stringToCheck);
                break;
            case "!in":
                valid = !Arrays.asList(value.split("\\s*,\\s*")).contains(stringToCheck);
                break;
            case "equals":
                valid = stringToCheck.equalsIgnoreCase(value);
                break;
            case "!equals":
                valid = !stringToCheck.equalsIgnoreCase(value);
                break;
            case "matches":
                valid = stringToCheck.matches(value);
                break;
            case "!matches":
                valid = !stringToCheck.matches(value);
                break;
            case "<":
                valid = floatToCheck < Float.parseFloat(value);
                break;
            case ">":
                valid = floatToCheck > Float.parseFloat(value);
                break;
            case "=":
                valid = floatToCheck == Float.parseFloat(value);
                break;
            case "<=":
                valid = floatToCheck <= Float.parseFloat(value);
                break;
            case ">=":
                valid = floatToCheck >= Float.parseFloat(value);
                break;
            case "!=":
                valid = floatToCheck != Float.parseFloat(value);
                break;
            case "()":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck > lower && floatToCheck < upper;
                break;
            case "[]":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck >= lower && floatToCheck <= upper;
                break;
            case "[)":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck >= lower && floatToCheck < upper;
                break;
            case "(]":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                valid = floatToCheck > lower && floatToCheck <= upper;
                break;
            case "containsAll":
                valid = arrayToCheck.containsAll(Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "!containsAll":
                valid = !arrayToCheck.containsAll(Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "containsAny":
                valid = !Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "!containsAny":
                valid = Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                break;
            case "allIn":
                valid = Arrays.asList(value.split("\\s*,\\s*")).containsAll(arrayToCheck);
                break;
            case "!allIn":
                valid = !Arrays.asList(value.split("\\s*,\\s*")).containsAll(arrayToCheck);
                break;
            case "*":
                switch (stat) {
                    case "knownHullMods":
                        valid = Global.getSettings().getFactionSpec(value).getKnownHullMods().contains(hullModId);
                        break;
                    default:
                        break;
                }
                break;
            default:
                log.error("Unexpected default operator " + operator);
        }
        return valid;
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
            log.error("Could not load vanilla data", e);
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
                    log.error("Could not load " + WEAPON_DATA_CSV + " or " + WING_DATA_CSV, e);
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
                    log.error("Could not load " + WEAPON_DATA_CSV + " or " + WING_DATA_CSV, e);
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
