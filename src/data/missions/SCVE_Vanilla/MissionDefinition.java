package data.missions.SCVE_Vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import com.fs.starfarer.api.mission.MissionDefinitionPlugin;
import data.scripts.SCVE_ComparatorUtils;
import org.apache.log4j.Logger;

import java.util.Set;
import java.util.TreeSet;

import static data.scripts.SCVE_ModPlugin.allModules;
import static data.scripts.SCVE_Utils.*;

public class MissionDefinition implements MissionDefinitionPlugin {

    private final Logger log = Global.getLogger(MissionDefinition.class);

    @Override
    public void defineMission(MissionDefinitionAPI api) {
        // initialize
        initializeMission(api, getString("vanillaTagline"));

        boolean flagship = true;
        for (FleetMemberAPI member : getVanillaFleetMembers(allModules)) {
            // don't use api.addFleetMember() because then the ships start at 0 CR
            String variantId = member.getVariant().getHullVariantId();
            FleetMemberAPI ship = api.addToFleet(FleetSide.PLAYER, variantId, FleetMemberType.SHIP, flagship);
            if (flagship) {
                flagship = false;
            }
        }
    }

    // this method is simpler for grabbing vanilla ships than relying on the listMap
    public static Set<FleetMemberAPI> getVanillaFleetMembers(Set<String> blacklist) {
        Set<FleetMemberAPI> fleetMemberSet = new TreeSet<>(SCVE_ComparatorUtils.memberComparator);
        for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
            if (!shipHullSpec.getShipFilePath().startsWith("data") && validateHullSpec(shipHullSpec, blacklist)) {
                String hullVariantId = shipHullSpec.getHullId() + HULL_SUFFIX;
                FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, hullVariantId); // need to repair...
                fleetMemberSet.add(member);
            }
        }
        return fleetMemberSet;
    }

    // used Set<FleetMemberAPI> getVanillaFleetMembers(Set<String> blacklist) instead because it's hard to grab DP with just the variant
    @Deprecated
    public static Set<String> getVanillaVariantIds(Set<String> blacklist) {
        Set<String> variantIdSet = new TreeSet<>(SCVE_ComparatorUtils.variantComparator);
        for (ShipHullSpecAPI shipHullSpec : Global.getSettings().getAllShipHullSpecs()) {
            if (!shipHullSpec.getShipFilePath().startsWith("data") && validateHullSpec(shipHullSpec, blacklist)) {
                String hullVariantId = shipHullSpec.getHullId() + HULL_SUFFIX;
                variantIdSet.add(hullVariantId);
            }
        }
        return variantIdSet;
    }
}