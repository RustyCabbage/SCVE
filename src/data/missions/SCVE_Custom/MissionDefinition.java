package data.missions.SCVE_Custom;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.loading.RoleEntryAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import static data.scripts.SCVE_ComparatorUtils.memberComparator;
import static data.scripts.SCVE_FilterUtils.blacklistedShips;
import static data.scripts.SCVE_Utils.*;

public class MissionDefinition implements MissionDefinitionPlugin {

    private final Logger log = Global.getLogger(MissionDefinition.class);

    // usually you can only access the mass of a ship from its ShipAPI
    public static HashMap<String, Float> hullIdToMassMap = new HashMap<>();
    public static String CUSTOM_MISSION_PATH = "custom_mission.csv";

    @Override
    public void defineMission(MissionDefinitionAPI api) {

        // initialize
        ArrayList<String> filterList = createFilterListBriefing(api);
        if (filterList.isEmpty()) {
            initializeMission(api, getString("customNoFilters"), null);
            api.addToFleet(FleetSide.PLAYER, Global.getSettings().getString("errorShipVariant"), FleetMemberType.SHIP,
                    getString("customNoFilters"), false);
            return;
        } else {
            initializeMission(api, getString("customTagline"), null);
        }

        Set<FleetMemberAPI> validShipsSet = new TreeSet<>(memberComparator);
        try {
            JSONArray shipCSV = Global.getSettings().getMergedSpreadsheetDataForMod("id", SHIP_DATA_CSV, "starsector-core");
            JSONArray customCSV = Global.getSettings().loadCSV(CUSTOM_MISSION_PATH);
            // base hulls
            for (int i = 0; i < shipCSV.length(); i++) {
                JSONObject shipRow = shipCSV.getJSONObject(i);
                String id = shipRow.getString("id");
                if (id.isEmpty()) {
                    continue;
                }
                ShipHullSpecAPI shipHullSpec = Global.getSettings().getHullSpec(id);
                if (validateHullSpec(shipHullSpec, blacklistedShips)) {
                    // get special stats
                    hullIdToMassMap.put(id, (float) shipRow.getDouble("mass"));
                    // check if valid member
                    boolean addToMission = true;
                    for (int j = 0; j < customCSV.length(); j++) {
                        JSONObject customRow = customCSV.getJSONObject(j);
                        String parameter = customRow.getString("parameter");
                        String operator = customRow.getString("operator");
                        String value = customRow.getString("value");
                        if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) {
                            continue;
                        }
                        if (!validateShipStat(id, parameter, operator, value)) {
                            addToMission = false;
                            break;
                        }
                    }
                    if (addToMission) {
                        String hullVariantId = shipHullSpec.getHullId() + HULL_SUFFIX;
                        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, hullVariantId); // need to repair...
                        validShipsSet.add(member);
                    }
                }

            }
            // skins - needs to be done separately to get mass if possible
            for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
                if (shipHullSpec.isBaseHull() || !(validateHullSpec(shipHullSpec, blacklistedShips))) { // skip base hulls and modules/stations
                    continue;
                }
                String id = shipHullSpec.getHullId();
                hullIdToMassMap.put(id, hullIdToMassMap.get(shipHullSpec.getBaseHullId())); //take bass hull id mass
                // check if valid member
                boolean addToMission = true;
                for (int j = 0; j < customCSV.length(); j++) {
                    JSONObject customRow = customCSV.getJSONObject(j);
                    String parameter = customRow.getString("parameter");
                    String operator = customRow.getString("operator");
                    String value = customRow.getString("value");
                    if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) {
                        continue;
                    }
                    if (!validateShipStat(id, parameter, operator, value)) {
                        addToMission = false;
                        break;
                    }
                }
                if (addToMission) {
                    String hullVariantId = shipHullSpec.getHullId() + HULL_SUFFIX;
                    FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, hullVariantId); // need to repair...
                    validShipsSet.add(member);
                }
            }
        } catch (IOException | JSONException e) {
            log.error("Could not load " + SHIP_DATA_CSV + " or " + CUSTOM_MISSION_PATH, e);
        }

        boolean flagship = true;
        for (FleetMemberAPI member : validShipsSet) {
            // don't use api.addFleetMember() because then the ships start at 0 CR
            String variantId = member.getVariant().getHullVariantId();
            FleetMemberAPI ship = api.addToFleet(FleetSide.PLAYER, variantId, FleetMemberType.SHIP, MOD_PREFIX + " " + member.getHullId(), flagship);
            if (flagship) {
                flagship = false;
            }
        }
        if (flagship) {
            api.addToFleet(FleetSide.PLAYER, Global.getSettings().getString("errorShipVariant"), FleetMemberType.SHIP,
                    getString("customNoShips"), true);
        }
    }

    public ArrayList<String> createFilterListBriefing(MissionDefinitionAPI api) {
        ArrayList<String> filterList = new ArrayList<>();
        try {
            JSONArray customCSV = Global.getSettings().loadCSV(CUSTOM_MISSION_PATH);
            for (int j = 0; j < customCSV.length(); j++) {
                JSONObject customRow = customCSV.getJSONObject(j);
                String parameter = customRow.getString("parameter");
                String operator = customRow.getString("operator");
                String value = customRow.getString("value");
                if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) {
                    continue;
                }
                filterList.add(parameter);
            }
        } catch (IOException | JSONException e) {
            log.error("Could not load " + CUSTOM_MISSION_PATH, e);
        }
        api.addBriefingItem("");
        api.addBriefingItem("");
        api.addBriefingItem(getString("customBriefing") + filterList);
        return filterList;
    }

    public boolean validateShipStat(String hullId, String stat, String operator, String value) {
        ShipHullSpecAPI shipHullSpec = Global.getSettings().getHullSpec(hullId);
        float base;
        String stringToCheck = "";
        float floatToCheck = Float.NaN, lower, upper;
        List<String> arrayToCheck = new ArrayList<>();
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(Factions.PLAYER, "fleet", true); // aiMode=true means no crew required
        FleetMemberAPI member = fleet.getFleetData().addFleetMember(hullId + HULL_SUFFIX);
        member.updateStats(); // fixes it being set to 0 CR and having -10% on a bunch of stats
        //Global.getFactory().createFleetMember(FleetMemberType.SHIP,hullId+HULL_SUFFIX); // can't use this because they have 0 CR and mess up the stats
        MutableShipStatsAPI stats = member.getStats();
        boolean valid = false;
        boolean checkWeapons = false;
        switch (stat) { // todo: can do basically anything in MutableShipStatsAPI
            // STRINGS
            case "id":
                stringToCheck = hullId;
                break;
            case "name":
                stringToCheck = shipHullSpec.getHullName();
                break;
            case "designation":
                stringToCheck = shipHullSpec.getDesignation();
                break;
            case "tech/manufacturer":
                stringToCheck = shipHullSpec.getManufacturer();
                break;
            case "system id": // todo: maybe more details like cd/charges?
                stringToCheck = shipHullSpec.getShipSystemId();
                break;
            case "system name":
                stringToCheck = Global.getSettings().getShipSystemSpec(shipHullSpec.getShipSystemId()).getName();
                break;
            case "defense type": // todo: color, radius?
                stringToCheck = shipHullSpec.getDefenseType().name();
                break;
            case "hullSize":
                stringToCheck = shipHullSpec.getHullSize().toString();
                break;
            // OTHER
            // ARRAYS
            case "hints":
                arrayToCheck = Arrays.asList(shipHullSpec.getHints().toString().replaceAll("[\\[\\]]", "").split(", "));
                break;
            case "tags":
                arrayToCheck = new ArrayList<>(shipHullSpec.getTags());
                break;
            case "builtInMods":
                arrayToCheck = shipHullSpec.getBuiltInMods();
                break;
            case "builtInWings":
                arrayToCheck = shipHullSpec.getBuiltInWings();
                break;
            // FLOATS
            case "DP":
                floatToCheck = stats.getSuppliesToRecover().getBaseValue();
                break;
            case "fleet pts":
                floatToCheck = shipHullSpec.getFleetPoints();
                break;
            case "hitpoints":
                base = shipHullSpec.getHitpoints();
                floatToCheck = stats.getHullBonus().computeEffective(base);
                break;
            case "armor rating":
                base = shipHullSpec.getArmorRating();
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
            case "ordnance points":
                floatToCheck = shipHullSpec.getOrdnancePoints(null);
                break;
            case "fighter bays":
                floatToCheck = stats.getNumFighterBays().getModifiedValue();
                //floatToCheck = shipHullSpec.getFighterBays();
                break;
            case "numBuiltInWings":
                floatToCheck = shipHullSpec.getBuiltInWings().size();
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
            case "mass":
                floatToCheck = hullIdToMassMap.get(hullId);
                break;
            case "shield arc":
                base = shipHullSpec.getShieldSpec().getArc();
                floatToCheck = stats.getShieldArcBonus().computeEffective(base);
                break;
            case "shield upkeep":
                base = shipHullSpec.getShieldSpec().getUpkeepCost() / Math.max(0.0001f, stats.getFluxDissipation().getModifiedValue());
                floatToCheck = base * stats.getShieldUpkeepMult().getModifiedValue();
                break;
            case "shield efficiency":
                base = shipHullSpec.getShieldSpec().getFluxPerDamageAbsorbed();
                floatToCheck = base * stats.getShieldDamageTakenMult().getModifiedValue();
                break;
            case "phase cost":
                base = shipHullSpec.getShieldSpec().getPhaseCost();
                floatToCheck = stats.getPhaseCloakActivationCostBonus().computeEffective(base);
                break;
            case "phase upkeep":
                base = shipHullSpec.getShieldSpec().getPhaseUpkeep();
                floatToCheck = stats.getPhaseCloakUpkeepCostBonus().computeEffective(base);
                break;
            case "min crew":
                base = shipHullSpec.getMinCrew();
                floatToCheck = stats.getMinCrewMod().computeEffective(base);
                break;
            case "max crew":
                base = shipHullSpec.getMaxCrew();
                floatToCheck = stats.getMaxCrewMod().computeEffective(base);
                break;
            case "cargo":
                base = shipHullSpec.getCargo();
                floatToCheck = stats.getCargoMod().computeEffective(base);
                break;
            case "fuel":
                base = shipHullSpec.getFuel();
                floatToCheck = stats.getFuelMod().computeEffective(base);
                break;
            case "fuel/ly":
                base = shipHullSpec.getFuelPerLY();
                floatToCheck = stats.getFuelUseMod().computeEffective(base);
                break;
            case "max burn":
                floatToCheck = stats.getMaxBurnLevel().getModifiedValue();
                break;
            case "base value":
                floatToCheck = shipHullSpec.getBaseValue();
                break;
            case "cr %/day":
                floatToCheck = stats.getBaseCRRecoveryRatePercentPerDay().getModifiedValue();
                break;
            case "repair %/day":
                floatToCheck = stats.getRepairRatePercentPerDay().getModifiedValue();
                break;
            case "CR to deploy":
                base = shipHullSpec.getCRToDeploy();
                floatToCheck = stats.getCRPerDeploymentPercent().computeEffective(base);
                break;
            case "peak CR sec":
                base = shipHullSpec.getNoCRLossTime();
                floatToCheck = stats.getPeakCRDuration().computeEffective(base);
                break;
            case "CR loss/sec":
                base = shipHullSpec.getCRLossPerSecond();
                floatToCheck = stats.getCRLossPerSecondPercent().computeEffective(base);
                break;
            case "supplies/rec":
                floatToCheck = stats.getSuppliesToRecover().getModifiedValue();
                break;
            case "supplies/mo":
                floatToCheck = stats.getSuppliesPerMonth().getModifiedValue();
                break;
            case "rarity":
                floatToCheck = shipHullSpec.getRarity();
                break;
            case "breakProb":
                floatToCheck = stats.getBreakProb().getModifiedValue();
                break;
            case "minPieces":
                floatToCheck = shipHullSpec.getMinPieces();
                break;
            case "maxPieces":
                floatToCheck = shipHullSpec.getMaxPieces();
                break;
            case "sensorProfile":
                floatToCheck = stats.getSensorProfile().getModifiedValue();
                break;
            case "sensorStrength":
                floatToCheck = stats.getSensorStrength().getModifiedValue();
                break;
            case "numModules":
                floatToCheck = member.getVariant().getStationModules().size();
                break;
            case "numSmallSlots":
            case "numMediumSlots":
            case "numLargeSlots":
            case "numBallisticSlots":
            case "numEnergySlots":
            case "numMissileSlots":
            case "numSBSlots":
            case "numSESlots":
            case "numSMSlots":
            case "numMBSlots":
            case "numMESlots":
            case "numMMSlots":
            case "numLBSlots":
            case "numLESlots":
            case "numLMSlots":
                checkWeapons = true;
                break;
            default:
                log.error("Unexpected default parameter");
        }
        if (checkWeapons) {
            List<WeaponSlotAPI> weaponSlots = shipHullSpec.getAllWeaponSlotsCopy();
            int
                    numSB = 0, numSE = 0, numSM = 0,
                    numMB = 0, numME = 0, numMM = 0,
                    numLB = 0, numLE = 0, numLM = 0;
            List<WeaponType> ballistics = Arrays.asList(WeaponType.BALLISTIC, WeaponType.HYBRID, WeaponType.COMPOSITE, WeaponType.UNIVERSAL);
            List<WeaponType> energies = Arrays.asList(WeaponType.ENERGY, WeaponType.HYBRID, WeaponType.SYNERGY, WeaponType.UNIVERSAL);
            List<WeaponType> missiles = Arrays.asList(WeaponType.MISSILE, WeaponType.COMPOSITE, WeaponType.SYNERGY, WeaponType.UNIVERSAL);
            for (WeaponSlotAPI weaponSlot : weaponSlots) {
                //Pair<WeaponSize, WeaponType> weaponSizeTypePair = new Pair<>(weaponSlot.getSlotSize(), weaponSlot.getWeaponType());
                if (weaponSlot.getSlotSize() == WeaponSize.SMALL) {
                    if (ballistics.contains(weaponSlot.getWeaponType())) {
                        numSB++;
                    }
                    if (energies.contains(weaponSlot.getWeaponType())) {
                        numSE++;
                    }
                    if (missiles.contains(weaponSlot.getWeaponType())) {
                        numSM++;
                    }
                } else if (weaponSlot.getSlotSize() == WeaponSize.MEDIUM) {
                    if (ballistics.contains(weaponSlot.getWeaponType())) {
                        numMB++;
                    }
                    if (energies.contains(weaponSlot.getWeaponType())) {
                        numME++;
                    }
                    if (missiles.contains(weaponSlot.getWeaponType())) {
                        numMM++;
                    }
                } else if (weaponSlot.getSlotSize() == WeaponSize.LARGE) {
                    if (ballistics.contains(weaponSlot.getWeaponType())) {
                        numLB++;
                    }
                    if (energies.contains(weaponSlot.getWeaponType())) {
                        numLE++;
                    }
                    if (missiles.contains(weaponSlot.getWeaponType())) {
                        numLM++;
                    }
                }
            }
            int
                    numS = numSB + numSE + numSM,
                    numMed = numMB + numME + numMM,
                    numL = numLB + numLE + numLM,
                    numB = numSB + numMB + numLB,
                    numE = numSE + numME + numLE,
                    numMissile = numSM + numMM + numLM;
            switch (stat) {
                case "numSmallSlots":
                    floatToCheck = numS;
                    break;
                case "numMediumSlots":
                    floatToCheck = numMed;
                    break;
                case "numLargeSlots":
                    floatToCheck = numL;
                    break;
                case "numBallisticSlots":
                    floatToCheck = numB;
                    break;
                case "numEnergySlots":
                    floatToCheck = numE;
                    break;
                case "numMissileSlots":
                    floatToCheck = numMissile;
                    break;
                case "numSBSlots":
                    floatToCheck = numSB;
                    break;
                case "numSESlots":
                    floatToCheck = numSE;
                    break;
                case "numSMSlots":
                    floatToCheck = numSM;
                    break;
                case "numMBSlots":
                    floatToCheck = numMB;
                    break;
                case "numMESlots":
                    floatToCheck = numME;
                    break;
                case "numMMSlots":
                    floatToCheck = numMM;
                    break;
                case "numLBSlots":
                    floatToCheck = numLB;
                    break;
                case "numLESlots":
                    floatToCheck = numLE;
                    break;
                case "numLMSlots":
                    floatToCheck = numLM;
                    break;
                default:
                    break;
            }
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
                    case "knownShips":
                        valid = Global.getSettings().getFactionSpec(value).getKnownShips().contains(shipHullSpec.getHullId());
                        break;
                    case "priorityShips":
                        valid = Global.getSettings().getFactionSpec(value).getPriorityShips().contains(shipHullSpec.getHullId());
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