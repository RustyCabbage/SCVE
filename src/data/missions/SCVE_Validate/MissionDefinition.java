package data.missions.SCVE_Validate;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.scripts.SCVE_FilterUtils;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static data.scripts.SCVE_ComparatorUtils.memberComparator;
import static data.scripts.SCVE_ModPlugin.allModules;
import static data.scripts.SCVE_Utils.*;

public class MissionDefinition implements MissionDefinitionPlugin {

    private final Logger log = Global.getLogger(MissionDefinition.class);

    @Override
    public void defineMission(MissionDefinitionAPI api) {
        // initialize
        initializeMission(api, getString("validateTagline"), null, false);
        SCVE_FilterUtils.setFilter(api, null, false);
        if (SCVE_FilterUtils.weaponWingFilter < 2) {
            SCVE_FilterUtils.weaponWingFilter = 2;
        } else if (SCVE_FilterUtils.weaponWingFilter > 3) {
            SCVE_FilterUtils.weaponWingFilter = 3;
        }
        SCVE_FilterUtils.applyFilter(api, null);

        HashMap<String, Integer> maxCapsAndVents = getMaxCapsAndVents();
        TreeMap<FleetMemberAPI, String> badFleetMemberMap = new TreeMap<>(memberComparator);
        for (String variantId : Global.getSettings().getAllVariantIds()) {
            String error = "";
            ShipVariantAPI variant = Global.getSettings().getVariant(variantId);
            // skip for certain reasons
            // // skip variants that Alex hardcoded into the game
            if (variant.getVariantFilePath() == null) {
                continue;
            }
            // // skip variants that will crash the mission upon addition
            ArrayList<String> hullSpecWeaponSlotIds = new ArrayList<>();
            for (WeaponSlotAPI weaponSlot : variant.getHullSpec().getAllWeaponSlotsCopy()) {
                hullSpecWeaponSlotIds.add(weaponSlot.getId());
            }
            if (!hullSpecWeaponSlotIds.containsAll(variant.getFittedWeaponSlots())) {
                log.error("ERROR: VARIANT " + variantId + " IS FITTING SLOTS THAT AREN'T IN THE HULL SPEC");
                continue;
            }
            if (!validateHullSpecExcludingFighters(variant.getHullSpec(), allModules)) {
                continue;
            }
            // check for over max OP
            if (variant.getUnusedOP(null) < 0 && !variant.isFighter()) {
                error += Math.abs(variant.getUnusedOP(null)) + getString("validateOverMaxOP") + ", ";
            }
            // check for under max OP
            if (variant.getUnusedOP(null) > 0 && !variant.isFighter()) {
                error += Math.abs(variant.getUnusedOP(null)) + getString("validateUnderMaxOP") + ", ";
            }
            // check for over max flux capacitors/vents
            if (variant.getNumFluxCapacitors() > maxCapsAndVents.get(variant.getHullSize().toString())) {
                error += (variant.getNumFluxCapacitors() - maxCapsAndVents.get(variant.getHullSize().toString())) + getString("validateOverMaxCaps") + ", ";
            }
            if (variant.getNumFluxVents() > maxCapsAndVents.get(variant.getHullSize().toString())) {
                error += (variant.getNumFluxVents() - maxCapsAndVents.get(variant.getHullSize().toString())) + getString("validateOverMaxVents") + ", ";
            }
            /* many issues, todo too many false positives atm. you live today chase
            // check for hiddenEverywhere hullmods
            ArrayList<String> hullModIds = new ArrayList<>();
            for (String hullModId : variant.getHullMods()) {
                HullModSpecAPI hullModSpec = Global.getSettings().getHullModSpec(hullModId);
                /*
                if (hullModSpec.isHiddenEverywhere() && !variant.isFighter()) {
                    hullModIds.add(hullModId);
                }
                // don't have a ShipAPI to check, sadge
                //if (!hullModSpec.getEffect().isApplicableToShip())
            }
            if (!hullModIds.isEmpty()) {
                error += getString("validateHiddenEverywhere") + hullModIds + ", ";
            }
            */
            /* todo too many false positives atm
            // check for permaMods not in hullSpec, ignoring sMods
            ArrayList<String> permaModIds = new ArrayList<>();
            for (String permaModId : variant.getPermaMods()) {
                if (variant.getSMods().contains(permaModId)) {
                    continue;
                }
                if (variant.getHullSpec().getBuiltInMods().contains(permaModId)) {
                    permaModIds.add(permaModId);
                }
            }
            if (!permaModIds.isEmpty()) {
                error += getString("validatePermaMods") + permaModIds + ", ";
            }
            // check for suppressedMods
            if (!variant.getSuppressedMods().isEmpty()) {
                error += getString("validateSuppressedMods") + variant.getSuppressedMods() + ", ";
            }
            */
            // check for weapons in wrong slot type/size
            if (!variant.isFighter()) {
                ArrayList<String> invalidWeaponSlotIds = new ArrayList<>();
                ArrayList<String> hiddenSlotIds = new ArrayList<>();
                ArrayList<String> badHardpointSlotIds = new ArrayList<>();
                ArrayList<String> badTurretSlotIds = new ArrayList<>();
                int ARC_FOR_TURRET = 20;
                for (String slotId : variant.getFittedWeaponSlots()) {
                    WeaponSlotAPI slot = variant.getSlot(slotId);
                    WeaponSpecAPI weaponSpec = variant.getWeaponSpec(slotId);
                    if (slot.isBuiltIn()) {
                        continue;
                    }
                    if (!slot.weaponFits(weaponSpec)) {
                        invalidWeaponSlotIds.add(slotId);
                    }
                    if (slot.isHidden()) {
                        hiddenSlotIds.add(slotId);
                    } else if (slot.isHardpoint() && slot.getArc() > ARC_FOR_TURRET) {
                        badHardpointSlotIds.add(slotId);
                    } else if (slot.isTurret() && slot.getArc() <= ARC_FOR_TURRET) {
                        badTurretSlotIds.add(slotId);
                    }
                }
                if (!invalidWeaponSlotIds.isEmpty()) {
                    error += getString("validateWeapons") + invalidWeaponSlotIds + ", ";
                }
                if (!hiddenSlotIds.isEmpty()) {
                    if (hiddenSlotIds.size() < 5) { // sometimes hidden slots are intentional, but if so it probably won't be many
                        error += getString("validateHiddenMounts") + hiddenSlotIds + ", ";
                    } else {
                        error += hiddenSlotIds.size() + " " + getString("validateHiddenMounts") + ", ";
                    }
                }
                if (!badHardpointSlotIds.isEmpty()) {
                    error += getString("validateHardpoints") + badHardpointSlotIds + ", ";
                }
                if (!badTurretSlotIds.isEmpty()) {
                    error += getString("validateTurrets") + badTurretSlotIds + ", ";
                }
            } else {
                // check for <1 efficiency shields on fighters
                if (variant.getHullSpec().getShieldType().equals(ShieldAPI.ShieldType.NONE)
                        && variant.getHullSpec().getShieldType().equals(ShieldAPI.ShieldType.PHASE)
                        && variant.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed() < 1f) {
                    error += getString("validateFighterShields") + variant.getHullSpec().getShieldSpec().getFluxPerDamageAbsorbed() + ", ";
                }
                // check for SO on fighters, which will cause crashes I think
                if (variant.getHullMods().contains(HullMods.SAFETYOVERRIDES)) {
                    if (variant.getHullSpec().getBuiltInMods().contains(HullMods.SAFETYOVERRIDES)) {
                        log.info("--------------------------------------------");
                        log.info("ERROR: Fighter " + variant.getHullSpec().getHullId() + " has SO and will cause a crash");
                        log.info("--------------------------------------------");
                        api.addBriefingItem("ERROR: Fighter " + variant.getHullSpec().getHullId() + " has SO and will cause a crash");
                    }
                    error += getString("validateFighterSO") + ": " + variant.getHullVariantId() + ", ";
                }
            }
            if (!error.isEmpty()) {
                if (error.contains(getString("validateFighterSO"))) {
                    if (variant.getHullSpec().getBuiltInMods().contains(HullMods.SAFETYOVERRIDES)) {
                        continue;
                    }
                    FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant.getHullSpec().getHullId() + HULL_SUFFIX);
                    badFleetMemberMap.put(member, error);
                } else {
                    FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
                    badFleetMemberMap.put(member, error);
                }
            }
        }
        if (badFleetMemberMap.entrySet().isEmpty()) {
            api.addToFleet(FleetSide.PLAYER, Global.getSettings().getString("errorShipVariant"), FleetMemberType.SHIP,
                    getString("validateNoShips"), true);
        } else {
            log.info("-----------------");
            log.info("INVALID VARIANTS:");
            log.info("-----------------");
            for (Map.Entry<FleetMemberAPI, String> badMember : badFleetMemberMap.entrySet()) {
                String variantId = badMember.getKey().getVariant().getHullVariantId();
                log.info(variantId + ": " + badMember.getValue());
                FleetMemberAPI ship = api.addToFleet(FleetSide.PLAYER, variantId, FleetMemberType.SHIP, badMember.getValue(), false);
            }
            log.info("-----------------");
        }
    }

    public static HashMap<String, Integer> getMaxCapsAndVents() {
        HashMap<String, Integer> maxCapsAndVents = new HashMap<>();
        maxCapsAndVents.put(ShipAPI.HullSize.FRIGATE.toString(), 10);
        maxCapsAndVents.put(ShipAPI.HullSize.DESTROYER.toString(), 20);
        maxCapsAndVents.put(ShipAPI.HullSize.CRUISER.toString(), 30);
        maxCapsAndVents.put(ShipAPI.HullSize.CAPITAL_SHIP.toString(), 50);
        maxCapsAndVents.put(ShipAPI.HullSize.FIGHTER.toString(), 0);
        maxCapsAndVents.put(ShipAPI.HullSize.DEFAULT.toString(), 0);
        return maxCapsAndVents;
    }
}
