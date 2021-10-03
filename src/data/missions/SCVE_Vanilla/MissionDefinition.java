package data.missions.SCVE_Vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import org.apache.log4j.Logger;

import java.util.Set;
import java.util.TreeSet;

import static data.scripts.SCVE_Utils.*;

public class MissionDefinition implements MissionDefinitionPlugin {

    private static final Logger log = Global.getLogger(MissionDefinition.class);

    @Override
    public void defineMission(MissionDefinitionAPI api) {
        // initialize
        initializeMission(api, getString("vanillaTagline"));

        boolean flagship = true;
        for (FleetMemberAPI member : getVanillaFleetMembers(allModules)) {
            String variantId = member.getVariant().getHullVariantId();
            FleetMemberAPI ship = api.addToFleet(FleetSide.PLAYER, variantId, FleetMemberType.SHIP, flagship);
            if (flagship) {
                flagship = false;
            }
        }
    }

    public void initializeMission(MissionDefinitionAPI api, String playerTagline) {
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
    }

    public Set<String> getVanillaVariantIds(Set<String> blacklist) {
        Set<String> variantIdSet = new TreeSet<>(variantComparator);
        for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
            if (!shipHullSpec.getShipFilePath().contains("starsector-core")
                    || shipHullSpec.isDefaultDHull()
                    || shipHullSpec.getHullSize() == ShipAPI.HullSize.FIGHTER
                    || shipHullSpec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)
                    || shipHullSpec.getHullId().matches(STATION_OR_MODULE_REGEX)
                    || shipHullSpec.getSpriteName().matches(STATION_OR_MODULE_REGEX)
                    || shipHullSpec.getTags().toString().matches(STATION_OR_MODULE_REGEX)
                    || shipHullSpec.getBuiltInMods().contains(HullMods.VASTBULK)
                    || shipHullSpec.getManufacturer().equals(getString("commonTech"))
            ) {
                log.info("Invalid: Removing " + shipHullSpec.getHullId());
                continue;
            }
            String hullVariantId = shipHullSpec.getHullId() + HULL_SUFFIX;
            if (blacklist.contains(hullVariantId)) {
                log.info("Blacklisted: Removing " + shipHullSpec.getHullId());
                continue;
            }
            log.info("Adding " + shipHullSpec.getHullId());
            variantIdSet.add(hullVariantId);
        }
        return variantIdSet;
    }

    public Set<FleetMemberAPI> getVanillaFleetMembers(Set<String> blacklist) {
        Set<FleetMemberAPI> fleetMemberSet = new TreeSet<>(memberComparator);
        for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
            if (!shipHullSpec.getShipFilePath().contains("starsector-core")
                    || shipHullSpec.isDefaultDHull()
                    || shipHullSpec.getHullSize() == ShipAPI.HullSize.FIGHTER
                    || shipHullSpec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)
                    || shipHullSpec.getHullId().matches(STATION_OR_MODULE_REGEX)
                    || shipHullSpec.getSpriteName().matches(STATION_OR_MODULE_REGEX)
                    || shipHullSpec.getTags().toString().matches(STATION_OR_MODULE_REGEX)
                    || shipHullSpec.getBuiltInMods().contains(HullMods.VASTBULK)
                    || shipHullSpec.getManufacturer().equals(getString("commonTech"))
            ) {
                //log.info("Invalid: Removing " + shipHullSpec.getHullId());
                continue;
            }
            String hullVariantId = shipHullSpec.getHullId() + HULL_SUFFIX;
            if (blacklist.contains(hullVariantId)) {
                //log.info("Blacklisted: Removing " + shipHullSpec.getHullId());
                continue;
            }
            //log.info("Adding " + shipHullSpec.getHullId());
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, hullVariantId); // need to repair...
            fleetMemberSet.add(member);
        }
        return fleetMemberSet;
    }
}