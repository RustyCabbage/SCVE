package data.missions.SCVE_Custom;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import com.fs.starfarer.api.util.Pair;
import data.scripts.SCVE_ComparatorUtils;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import static data.scripts.SCVE_FilterUtils.blacklistedShips;
import static data.scripts.SCVE_Utils.*;

public class MissionDefinition implements MissionDefinitionPlugin {

    private final Logger log = Global.getLogger(MissionDefinition.class);

    // usually you can only access the system id and mass of a ship from its ShipAPI
    // pair.one = system id
    // pair.two = mass
    public static HashMap<String, Pair<String, Float>> hullIdToSpecialStatsMap = new HashMap<>();
    public static String CUSTOM_DATA_PATH = "custom_mission.csv";

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

        Set<FleetMemberAPI> validShipsSet = new TreeSet<>(SCVE_ComparatorUtils.memberComparator);
        try {
            JSONArray shipCSV = Global.getSettings().getMergedSpreadsheetDataForMod("id", SHIP_DATA_CSV, "starsector-core");
            JSONArray customCSV = Global.getSettings().loadCSV(CUSTOM_DATA_PATH);
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
                    hullIdToSpecialStatsMap.put(id, new Pair<>(shipRow.getString("system id"), (float) shipRow.getDouble("mass")));
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
            // skins
            for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
                if (shipHullSpec.isBaseHull() || !(validateHullSpec(shipHullSpec, blacklistedShips))) { // skip base hulls and modules/stations
                    continue;
                }
                String id = shipHullSpec.getHullId();
                String[] shipFilePathArray = shipHullSpec.getShipFilePath().split("data\\\\hulls\\\\");
                String shipFilePath = "data/hulls/" + shipFilePathArray[shipFilePathArray.length - 1].replace("\\", "/");
                String systemId;
                float mass;
                // get special stats
                mass = hullIdToSpecialStatsMap.get(shipHullSpec.getBaseHullId()).two;
                try {
                    systemId = Global.getSettings().loadJSON(shipFilePath).getString("systemId");
                    hullIdToSpecialStatsMap.put(id, new Pair<>(systemId, mass));
                } catch (JSONException e) {
                    systemId = hullIdToSpecialStatsMap.get(shipHullSpec.getBaseHullId()).one;
                }
                hullIdToSpecialStatsMap.put(id, new Pair<>(systemId, mass));
                // check if valid member
                boolean valid = true;
                for (int j = 0; j < customCSV.length(); j++) {
                    JSONObject customRow = customCSV.getJSONObject(j);
                    String parameter = customRow.getString("parameter");
                    String operator = customRow.getString("operator");
                    String value = customRow.getString("value");
                    if (parameter.isEmpty() || operator.isEmpty() || value.isEmpty()) {
                        continue;
                    }
                    if (!validateShipStat(id, parameter, operator, value)) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    String hullVariantId = shipHullSpec.getHullId() + HULL_SUFFIX;
                    FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, hullVariantId); // need to repair...
                    validShipsSet.add(member);
                }
            }
        } catch (IOException | JSONException e) {
            log.error("Could not load " + SHIP_DATA_CSV + " or " + CUSTOM_DATA_PATH);
        }

        boolean flagship = true;
        for (FleetMemberAPI member : validShipsSet) {
            // don't use api.addFleetMember() because then the ships start at 0 CR
            String variantId = member.getVariant().getHullVariantId();
            FleetMemberAPI ship = api.addToFleet(FleetSide.PLAYER, variantId, FleetMemberType.SHIP, flagship);
            if (flagship) {
                flagship = false;
            }
        }
    }

    public ArrayList<String> createFilterListBriefing(MissionDefinitionAPI api) {
        ArrayList<String> filterList = new ArrayList<>();
        try {
            JSONArray customCSV = Global.getSettings().loadCSV(CUSTOM_DATA_PATH);
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
            log.error("Could not load " + CUSTOM_DATA_PATH);
        }
        api.addBriefingItem("");
        api.addBriefingItem("");
        api.addBriefingItem(getString("customBriefing") + filterList);
        return filterList;
    }

    // todo weapons
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
                stringToCheck = hullIdToSpecialStatsMap.get(hullId).one;
                break;
            case "system name":
                stringToCheck = Global.getSettings().getShipSystemSpec(hullIdToSpecialStatsMap.get(hullId).one).getName();
                break;
            case "defense type": // todo: color, radius?
                stringToCheck = shipHullSpec.getDefenseType().name();
                break;
            case "hullSize":
                stringToCheck = shipHullSpec.getHullSize().toString();
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
                floatToCheck = hullIdToSpecialStatsMap.get(hullId).two;
                break;
            case "shield arc":
                base = shipHullSpec.getShieldSpec().getArc();
                floatToCheck = stats.getShieldArcBonus().computeEffective(base);
                break;
            case "shield upkeep":
                base = shipHullSpec.getShieldSpec().getUpkeepCost() / shipHullSpec.getFluxDissipation();
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
            case "sensor profile":
                floatToCheck = stats.getSensorProfile().getModifiedValue();
                break;
            case "sensor strength":
                floatToCheck = stats.getSensorStrength().getModifiedValue();
                break;
            case "numModules":
                floatToCheck = member.getVariant().getStationModules().size();
                break;
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
            default:
                log.error("Unexpected default parameter");
        }
        switch (operator) {
            case "startsWith":
                valid = stringToCheck.startsWith(value);
                break;
            case "endsWith":
                valid = stringToCheck.endsWith(value);
                break;
            case "contains":
                if (!stringToCheck.isEmpty()) {
                    valid = stringToCheck.contains(value);
                }
                if (!arrayToCheck.isEmpty()) {
                    valid = !Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                }
                break;
            case "equals":
                valid = stringToCheck.equalsIgnoreCase(value);
                break;
            case "matches":
                valid = stringToCheck.matches(value);
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
            case "containsAny":
                valid = !Collections.disjoint(arrayToCheck, Arrays.asList(value.split("\\s*,\\s*")));
                break;
            default:
                log.error("Unexpected default operator");
        }
        return valid;
    }
}