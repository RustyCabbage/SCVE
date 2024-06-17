package data.scripts;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class SCVE_ComparatorUtils {
    public static final List<String> vanillaManufacturers = new ArrayList<>(Arrays.asList(
            "Low Tech", "Midline", "High Tech", "Pirate", "Luddic Path",
            "Hegemony", "XIV Battlegroup", "Lion's Guard", "Luddic Church", "Tri-Tachyon",
            "Explorarium", "Remnant", "Unknown"
                                                                                         ));

    public static boolean isDamagedVersion(ShipHullSpecAPI hullSpec) {
        // isDHull() only checks if it has d-mods, thus including LP ships
        // but some mod ships can end in _d without being d-hulls, so include both
        return (hullSpec.isDHull() && hullSpec.getHullId().endsWith("_d"));
    }

    //non-vanilla tech types go last using this method
    public static int manufacturerToInt(String manufacturer) {
        return (vanillaManufacturers.contains(manufacturer)) ? vanillaManufacturers.indexOf(manufacturer) : 999;
    }

    public static int hullSizeToInt(ShipAPI.HullSize hullSize) {
        switch (hullSize) {
            case FRIGATE:
                return 1;
            case DESTROYER:
                return 2;
            case CRUISER:
                return 3;
            case CAPITAL_SHIP:
                return 4;
            default:
                return -1;
        }
    }

    public static final Comparator<FleetMemberAPI> memberComparator = new Comparator<FleetMemberAPI>() {
        // sort by:
        // 1. non-d-hulls > d-hulls
        // 2. manufacturer/tech type
        // 3. hull size (ascending)
        // 4. DP (ascending)
        // 5. variant name
        // 6. variant id
        @Override
        public int compare(FleetMemberAPI m1, FleetMemberAPI m2) {
            ShipVariantAPI var1 = m1.getVariant();
            ShipVariantAPI var2 = m2.getVariant();
            boolean isRestricted1 = m1.getHullSpec().hasTag(Tags.RESTRICTED);
            boolean isRestricted2 = m2.getHullSpec().hasTag(Tags.RESTRICTED);
            boolean isHideFromCodex1 = m1.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX);
            boolean isHideFromCodex2 = m2.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX);
            boolean isGoalVariant1 = var1.isGoalVariant();
            boolean isGoalVariant2 = var2.isGoalVariant();
            boolean isDHull1 = isDamagedVersion(var1.getHullSpec());
            boolean isDHull2 = isDamagedVersion(var2.getHullSpec());
            String manufacturer1 = var1.getHullSpec().getManufacturer();
            String manufacturer2 = var2.getHullSpec().getManufacturer();
            int manufacturerScore1 = manufacturerToInt(manufacturer1);
            int manufacturerScore2 = manufacturerToInt(manufacturer2);
            int sizeScore1 = hullSizeToInt(var1.getHullSize());
            int sizeScore2 = hullSizeToInt(var2.getHullSize());
            float DP1 = m1.getStats().getSuppliesToRecover().getBaseValue();
            float DP2 = m2.getStats().getSuppliesToRecover().getBaseValue();
            String name1 = var1.getDisplayName();
            String name2 = var2.getDisplayName();
            String id1 = m1.getHullId();
            String id2 = m2.getHullId();
            // put goalVariants at the top. shouldn't matter for stripped hulls, but using this for other stuff
            if (isGoalVariant1 && !isGoalVariant2) return -1;
            if (!isGoalVariant1 && isGoalVariant2) return 1;
            if (isRestricted1 && !isRestricted2) return 1;
            if (!isRestricted1 && isRestricted2) return -1;
            if (isHideFromCodex1 && !isHideFromCodex2) return 1;
            if (!isHideFromCodex1 && isHideFromCodex2) return -1;
            if (isDHull1 && !isDHull2) return 1;
            if (!isDHull1 && isDHull2) return -1;
            if (manufacturerScore1 != manufacturerScore2) return Integer.compare(manufacturerScore1, manufacturerScore2);
            if (!manufacturer1.equalsIgnoreCase(manufacturer2)) return manufacturer1.compareToIgnoreCase(manufacturer2);
            if (sizeScore1 != sizeScore2) return Integer.compare(sizeScore1, sizeScore2);
            if (DP1 != DP2) return Float.compare(DP1, DP2);
            if (!name1.equalsIgnoreCase(name2)) return name1.compareToIgnoreCase(name2);
            return id1.compareToIgnoreCase(id2);
        }
    };
}