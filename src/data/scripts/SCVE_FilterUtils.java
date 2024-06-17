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
            if (hullModSpec.getId().startsWith(GachaSMods_MOD_ID)) continue;
            ORIGINAL_HULLMOD_QUALITIES_MAP.put(hullModSpec.getId(),
                                               new ArrayList<>(Arrays.asList(hullModSpec.hasTag(Tags.HULLMOD_DMOD), hullModSpec.isHidden(), hullModSpec.isHiddenEverywhere())));
            ORIGINAL_HULLMOD_NAMES_MAP.put(hullModSpec.getId(), hullModSpec.getDisplayName());
            ORIGINAL_HULLMOD_OP_COST_MAP.put(hullModSpec.getId(),
                                             new ArrayList<>(Arrays.asList(
                                                     hullModSpec.getFrigateCost(), hullModSpec.getDestroyerCost()
                                                     , hullModSpec.getCruiserCost(), hullModSpec.getCapitalCost())));
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
                if (hullModSpec.getId().startsWith(GachaSMods_MOD_ID)) continue;

                ArrayList<Boolean> hullModQualities = ORIGINAL_HULLMOD_QUALITIES_MAP.get(hullModSpec.getId());
                if (hullModQualities.get(0)) hullModSpec.addTag(Tags.HULLMOD_DMOD);
                hullModSpec.setHidden(hullModQualities.get(1));
                hullModSpec.setHiddenEverywhere(hullModQualities.get(2));

                hullModSpec.setDisplayName(ORIGINAL_HULLMOD_NAMES_MAP.get(hullModSpec.getId()));

                ArrayList<Integer> hullModCosts = ORIGINAL_HULLMOD_OP_COST_MAP.get(hullModSpec.getId());
                hullModSpec.setFrigateCost(hullModCosts.get(0));
                hullModSpec.setDestroyerCost(hullModCosts.get(1));
                hullModSpec.setCruiserCost(hullModCosts.get(2));
                hullModSpec.setCapitalCost(hullModCosts.get(3));
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
                if (shipFilter < 0) shipFilter = 2;
            } else if (Keyboard.isKeyDown(Keyboard.KEY_W)) {
                shipFilter++;
                if (shipFilter > 2) shipFilter = 0;
            }
            if (Keyboard.isKeyDown(Keyboard.KEY_A)) {
                weaponWingFilter--;
                if (weaponWingFilter < 0) weaponWingFilter = 4;
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
                    if (parameter.equals("showRestricted") && Boolean.parseBoolean(value)) {
                        weaponSpec.getTags().remove(Tags.RESTRICTED);
                    }
                    if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) continue;
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
                    if (parameter.equals("showRestricted") && Boolean.parseBoolean(value)) {
                        wingSpec.getTags().remove(Tags.RESTRICTED);
                    }
                    if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) continue;
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
                    if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) continue;
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

    public static boolean validateWeaponStat(String weaponId, String stat, String operator, String value) {
        WeaponSpecAPI weaponSpec = Global.getSettings().getWeaponSpec(weaponId);
        switch (stat) {
            // STRINGS
            case "id":
                return validateString(weaponId, operator, value);
            case "name":
                return validateString(weaponSpec.getWeaponName(), operator, value);
            case "tech/manufacturer":
                return validateString(weaponSpec.getManufacturer(), operator, value);
            case "size":
                return validateString(weaponSpec.getSize().toString(), operator, value);
            case "mount type":
                return validateString(weaponSpec.getMountType().toString(), operator, value);
            case "damage type":
                return validateString(weaponSpec.getDamageType().toString(), operator, value);
            case "primaryRoleStr":
                return validateString(weaponSpec.getPrimaryRoleStr(), operator, value);
            case "speedStr":
                return validateString(weaponSpec.getSpeedStr(), operator, value);
            case "trackingStr":
                return validateString(weaponSpec.getTrackingStr(), operator, value);
            case "turnRateStr":
                return validateString(weaponSpec.getTurnRateStr(), operator, value);
            case "accuracyStr":
                return validateString(weaponSpec.getAccuracyStr(), operator, value);
            // ARRAYS
            case "hints":
                String hints = weaponSpec.getAIHints().toString().replaceAll("[\\[\\]]", "");
                return validateArray(Arrays.asList(hints.split(",\\s*")), operator, value);
            case "tags":
                return validateArray(new ArrayList<>(weaponSpec.getTags()), operator, value);
            // FLOATS
            case "tier":
                return validateFloat(weaponSpec.getTier(), operator, value);
            case "rarity":
                return validateFloat(weaponSpec.getRarity(), operator, value);
            case "base value":
                return validateFloat(weaponSpec.getBaseValue(), operator, value);
            case "range":
                return validateFloat(weaponSpec.getMaxRange(), operator, value);
            case "damage/second":
                return validateFloat(weaponSpec.getDerivedStats().getDps(), operator, value);
            case "damage/shot":
                return validateFloat(weaponSpec.getDerivedStats().getDamagePerShot(), operator, value);
            case "emp/second":
                return validateFloat(weaponSpec.getDerivedStats().getEmpPerSecond(), operator, value);
            case "emp/shot":
                return validateFloat(weaponSpec.getDerivedStats().getEmpPerShot(), operator, value);
            case "OPs":
                return validateFloat(weaponSpec.getOrdnancePointCost(null), operator, value);
            case "ammo":
                return validateFloat(weaponSpec.getMaxAmmo(), operator, value);
            case "ammo/sec":
                return validateFloat(weaponSpec.getAmmoPerSecond(), operator, value);
            case "energy/shot":
                return validateFloat(weaponSpec.getDerivedStats().getFluxPerDam() * weaponSpec.getDerivedStats().getDamagePerShot(), operator, value);
            case "energy/second":
                return validateFloat(weaponSpec.getDerivedStats().getFluxPerSecond(), operator, value);
            case "chargeup":
                return (weaponSpec.isBeam()) ?
                       validateFloat(weaponSpec.getBeamChargeupTime(), operator, value) : validateFloat(weaponSpec.getChargeTime(), operator, value);
            case "burst duration":
                return validateFloat(weaponSpec.getDerivedStats().getBurstFireDuration(), operator, value);
            case "min spread":
                return validateFloat(weaponSpec.getMinSpread(), operator, value);
            case "max spread":
                return validateFloat(weaponSpec.getMaxSpread(), operator, value);
            case "spread/shot":
                return validateFloat(weaponSpec.getSpreadBuildup(), operator, value);
            case "spread decay/sec":
                return validateFloat(weaponSpec.getSpreadDecayRate(), operator, value);
            case "proj speed":
                return weaponSpec.getProjectileSpec() instanceof ProjectileSpecAPI
                        && validateFloat(((ProjectileSpecAPI) weaponSpec.getProjectileSpec()).getMoveSpeed(null, null), operator, value);
            case "proj hitpoints":
                return weaponSpec.getProjectileSpec() instanceof ProjectileSpecAPI
                        && validateFloat(((MissileSpecAPI) weaponSpec.getProjectileSpec()).getHullSpec().getHitpoints(), operator, value);
            // OTHER
            case "knownWeapons":
                return Global.getSettings().getFactionSpec(value).getKnownWeapons().contains(weaponId);
            case "priorityWeapons":
                return Global.getSettings().getFactionSpec(value).getPriorityWeapons().contains(weaponId);
            default:
                log.error("Unexpected parameter: " + stat);
                return false;
        }
    }

    public static boolean validateWingStat(String wingId, String stat, String operator, String value) {
        FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wingId);
        float base = 0;
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "fleet", true); // aiMode=true means no crew required
        FleetMemberAPI member = fleet.getFleetData().addFleetMember(wingSpec.getVariantId());
        member.updateStats(); // fixes it being set to 0 CR and having -10% on a bunch of stats
        MutableShipStatsAPI stats = member.getStats();
        switch (stat) { // todo: can technically grab anything accessible through hullSpec?
            // STRINGS
            case "id":
                return validateString(wingId, operator, value);
            case "name":
                return validateString(wingSpec.getWingName(), operator, value);
            case "tech/manufacturer":
                return validateString(wingSpec.getVariant().getHullSpec().getManufacturer(), operator, value);
            case "formation":
                return validateString(wingSpec.getFormation().toString(), operator, value);
            case "role":
                return validateString(wingSpec.getRole().toString(), operator, value);
            case "role desc":
                return validateString(wingSpec.getRoleDesc(), operator, value);
            case "defense type":
                return validateString(wingSpec.getVariant().getHullSpec().getDefenseType().name(), operator, value);
            // ARRAYS
            case "tags":
                return validateArray(new ArrayList<>(wingSpec.getTags()), operator, value);
            case "hints":
                String hints = wingSpec.getVariant().getHullSpec().getHints().toString().replaceAll("[\\[\\]]", "");
                return validateArray(Arrays.asList(hints.split(",\\s*")), operator, value);
            case "hullmods":
                return validateArray(new ArrayList<>(wingSpec.getVariant().getHullMods()), operator, value);
            // FLOATS
            case "tier":
                return validateFloat(wingSpec.getTier(), operator, value);
            case "rarity":
                return validateFloat(wingSpec.getRarity(), operator, value);
            case "fleet pts":
                return validateFloat(wingSpec.getFleetPoints(), operator, value);
            case "op cost":
                return validateFloat(wingSpec.getOpCost(null), operator, value);
            case "num":
                return validateFloat(wingSpec.getNumFighters(), operator, value);
            case "refit":
                return validateFloat(wingSpec.getRefitTime(), operator, value);
            case "base value":
                return validateFloat(wingSpec.getBaseValue(), operator, value);
            case "hitpoints":
                base = wingSpec.getVariant().getHullSpec().getHitpoints();
                return validateFloat(stats.getHullBonus().computeEffective(base), operator, value);
            case "armor rating":
                base = wingSpec.getVariant().getHullSpec().getArmorRating();
                return validateFloat(stats.getArmorBonus().computeEffective(base), operator, value);
            case "max flux":
                return validateFloat(stats.getFluxCapacity().getModifiedValue(), operator, value);
            case "flux dissipation":
                return validateFloat(stats.getFluxDissipation().getModifiedValue(), operator, value);
            case "max speed":
                return validateFloat(stats.getMaxSpeed().getModifiedValue(), operator, value);
            case "acceleration":
                return validateFloat(stats.getAcceleration().getModifiedValue(), operator, value);
            case "deceleration":
                return validateFloat(stats.getDeceleration().getModifiedValue(), operator, value);
            case "max turn rate":
                return validateFloat(stats.getMaxTurnRate().getModifiedValue(), operator, value);
            case "turn acceleration":
                return validateFloat(stats.getTurnAcceleration().getModifiedValue(), operator, value);
            case "shield arc":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getArc();
                return validateFloat(stats.getShieldArcBonus().computeEffective(base), operator, value);
            case "shield upkeep":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getUpkeepCost() / Math.max(0.0001f, stats.getFluxDissipation().getModifiedValue());
                return validateFloat(base * stats.getShieldUpkeepMult().getModifiedValue(), operator, value);
            case "shield efficiency":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed();
                return validateFloat(base * stats.getShieldDamageTakenMult().getModifiedValue(), operator, value);
            case "phase cost":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getPhaseCost();
                return validateFloat(stats.getPhaseCloakActivationCostBonus().computeEffective(base), operator, value);
            case "phase upkeep":
                base = wingSpec.getVariant().getHullSpec().getShieldSpec().getPhaseUpkeep();
                return validateFloat(stats.getPhaseCloakUpkeepCostBonus().computeEffective(base), operator, value);
            case "crew":
                base = wingSpec.getVariant().getHullSpec().getMinCrew();
                return validateFloat(stats.getMinCrewMod().computeEffective(base), operator, value); // todo same as max crew in vanilla, should check with mods...
            // OTHER
            case "knownWings":
                return Global.getSettings().getFactionSpec(value).getKnownFighters().contains(wingId);
            case "priorityWings":
                return Global.getSettings().getFactionSpec(value).getPriorityFighters().contains(wingId);
            default:
                log.error("Unexpected parameter: " + stat);
                return false;
        }
    }

    // todo add fs_rowSource
    public static boolean validateHullModStat(String hullModId, String stat, String operator, String value) {
        HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
        switch (stat) {
            // STRINGS
            case "id":
                return validateString(hullModId, operator, value);
            case "name":
                return validateString(hullModSpec.getDisplayName(), operator, value);
            case "tech/manufacturer":
                return validateString(hullModSpec.getManufacturer(), operator, value);
            case "script":
                return validateString(hullModSpec.getEffectClass(), operator, value);
            case "description":
                return validateString(hullModSpec.getDescription(ShipAPI.HullSize.DEFAULT), operator, value);
            case "sMod description":
                return validateString(hullModSpec.getSModDescription(ShipAPI.HullSize.DEFAULT), operator, value);
            // ARRAYS
            case "tags":
                return validateArray(new ArrayList<>(hullModSpec.getTags()), operator, value);
            case "uiTags":
                return validateArray(new ArrayList<>(hullModSpec.getUITags()), operator, value);
            // FLOATS
            case "tier":
                return validateFloat(hullModSpec.getTier(), operator, value);
            case "rarity":
                return validateFloat(hullModSpec.getRarity(), operator, value);
            case "base value":
                return validateFloat(hullModSpec.getBaseValue(), operator, value);
            case "frigateCost":
                return validateFloat(hullModSpec.getFrigateCost(), operator, value);
            case "destroyerCost":
                return validateFloat(hullModSpec.getDestroyerCost(), operator, value);
            case "cruiserCost":
                return validateFloat(hullModSpec.getCruiserCost(), operator, value);
            case "capitalCost":
                return validateFloat(hullModSpec.getCapitalCost(), operator, value);
            // OTHER
            case "knownHullMods":
                return Global.getSettings().getFactionSpec(value).getKnownHullMods().contains(hullModId);
            default:
                log.error("Unexpected parameter: " + stat);
                return false;
        }
    }

    public static boolean validateString(String stringToCheck, String operator, String value) {
        if (stringToCheck == null) return false;
        switch (operator) {
            case "startsWith":
                return stringToCheck.startsWith(value);
            case "!startsWith":
                return !stringToCheck.startsWith(value);
            case "endsWith":
                return stringToCheck.endsWith(value);
            case "!endsWith":
                return !stringToCheck.endsWith(value);
            case "contains":
                if (!stringToCheck.isEmpty()) {
                    return stringToCheck.contains(value);
                }
            case "!contains":
                if (!stringToCheck.isEmpty()) {
                    return !stringToCheck.contains(value);
                }
            case "in":
                return Arrays.asList(value.split("\\s*,\\s*")).contains(stringToCheck);
            case "!in":
                return !Arrays.asList(value.split("\\s*,\\s*")).contains(stringToCheck);
            case "equals":
                return stringToCheck.equalsIgnoreCase(value);
            case "!equals":
                return !stringToCheck.equalsIgnoreCase(value);
            case "matches":
                return stringToCheck.matches(value);
            case "!matches":
                return !stringToCheck.matches(value);
            default:
                log.error("Unexpected operator " + operator);
                return false;
        }
    }

    public static boolean validateArray(List<String> arrayToCheck, String operator, String value) {
        if (arrayToCheck.isEmpty()) return false;
        HashSet<String> arraySet = new HashSet<>(arrayToCheck);
        ArrayList<String> valueArray = (ArrayList<String>) Arrays.asList(value.split("\\s*,\\s*"));
        switch (operator) {
            case "contains":
            case "containsAny":
                return !Collections.disjoint(arraySet, valueArray);
            case "!contains":
            case "!containsAny":
                return Collections.disjoint(arraySet, valueArray);
            case "containsAll":
                return arraySet.containsAll(valueArray);
            case "!containsAll":
                return !arraySet.containsAll(valueArray);
            case "allIn":
                return valueArray.containsAll(arraySet);
            case "!allIn":
                return !valueArray.containsAll(arraySet);
            default:
                log.error("Unexpected operator " + operator);
                return false;
        }
    }

    public static boolean validateFloat(float floatToCheck, String operator, String value) {
        if (Float.isNaN(floatToCheck)) return false;
        float lower, upper;
        switch (operator) {
            case "<":
                return floatToCheck < Float.parseFloat(value);
            case ">":
                return floatToCheck > Float.parseFloat(value);
            case "=":
                return floatToCheck == Float.parseFloat(value);
            case "<=":
                return floatToCheck <= Float.parseFloat(value);
            case ">=":
                return floatToCheck >= Float.parseFloat(value);
            case "!=":
                return floatToCheck != Float.parseFloat(value);
            case "()":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                return floatToCheck > lower && floatToCheck < upper;
            case "[]":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                return floatToCheck >= lower && floatToCheck <= upper;
            case "[)":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                return floatToCheck >= lower && floatToCheck < upper;
            case "(]":
                lower = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[0]);
                upper = Float.parseFloat(value.replaceAll("[()\\[\\]]", "").split("\\s*,\\s*")[1]);
                return floatToCheck > lower && floatToCheck <= upper;
            default:
                log.error("Unexpected operator " + operator);
                return false;
        }
    }
}