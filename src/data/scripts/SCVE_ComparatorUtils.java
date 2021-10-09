package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import java.util.ArrayList;
import java.util.Comparator;

public class SCVE_ComparatorUtils {
    public static final ArrayList<String> vanillaManufacturers = new ArrayList<>();

    static {
        vanillaManufacturers.add("Low Tech");
        vanillaManufacturers.add("Midline");
        vanillaManufacturers.add("High Tech");
        vanillaManufacturers.add("Pirate");
        vanillaManufacturers.add("Luddic Path");
        vanillaManufacturers.add("Hegemony");
        vanillaManufacturers.add("XIV Battlegroup");
        vanillaManufacturers.add("Luddic Church");
        vanillaManufacturers.add("Tri-Tachyon");
        vanillaManufacturers.add("Explorarium");
        vanillaManufacturers.add("Remnant");
        vanillaManufacturers.add("Unknown");
    }

    public static boolean isDamagedVersion(ShipHullSpecAPI hullSpec) {
        // isDHull() only checks if it has d-mods, thus including LP ships
        // but some mod ships can end in _d without being d-hulls, so include both
        return (hullSpec.isDHull() && hullSpec.getHullId().endsWith("_d"));
    }

    public static int manufacturerToInt(String manufacturer) {
        if (vanillaManufacturers.contains(manufacturer)) {
            return vanillaManufacturers.indexOf(manufacturer);
        }
        return 999; //non-vanilla tech types go last using this method
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
            if (isGoalVariant1 && !isGoalVariant2) {
                return -1;
            } else if (!isGoalVariant1 && isGoalVariant2) {
                return 1;
            }
            // testing this
            if (isRestricted1 && !isRestricted2) {
                return 1;
            } else if (!isRestricted1 && isRestricted2) {
                return -1;
            }
            if (isHideFromCodex1 && !isHideFromCodex2) {
                return 1;
            } else if (!isHideFromCodex1 && isHideFromCodex2) {
                return -1;
            }
            // end test
            // d-hull comparison first
            if (isDHull1 && !isDHull2) {
                //log.info("D-Hull " + id1 + " moved below " + id2);
                return 1;
            } else if (!isDHull1 && isDHull2) {
                //log.info("D-Hull " + id2 + " moved below " + id1);
                return -1;
            }
            if (manufacturerScore1 != manufacturerScore2) { // compare numeric manufacturer score
                //log.info("Comparing m-score " + id1 + " vs " + id2 + ": " + Integer.compare(manufacturerScore1, manufacturerScore2));
                return Integer.compare(manufacturerScore1, manufacturerScore2);
            }
            if (!manufacturer1.equalsIgnoreCase(manufacturer2)) { // if same score, compare strings (i.e. non-vanilla manufacturers are sorted alphabetically)
                //log.info("Comparing m-id " + id1 + " vs " + id2 + ": " + manufacturer1.compareToIgnoreCase(manufacturer2));
                return manufacturer1.compareToIgnoreCase(manufacturer2);
            }
            if (sizeScore1 != sizeScore2) {
                //log.info("Comparing s-score " + id1 + " vs " + id2 + ": " + Integer.compare(sizeScore1, sizeScore2));
                return Integer.compare(sizeScore1, sizeScore2);
            }
            if (DP1 != DP2) {
                //log.info("Comparing dp " + id1 + " vs " + id2 + ": " + Float.compare(DP1, DP2));
                return Float.compare(DP1, DP2);
            }
            if (!name1.equalsIgnoreCase(name2)) {
                //log.info("Comparing name " + id1 + " vs " + id2 + ": " + name1.compareToIgnoreCase(name2));
                return name1.compareToIgnoreCase(name2);
            }
            //log.info("Comparing id " + id1 + " vs " + id2 + ": " + id1.compareToIgnoreCase(id2));
            return id1.compareToIgnoreCase(id2);
        }
    };

    @Deprecated
    public static final Comparator<String> variantComparator = new Comparator<String>() {
        // sort by:
        // 1. non-d-hulls > d-hulls
        // 2. manufacturer/tech type
        // 3. hull size (ascending)
        // 4. DP (ascending)
        // 5. variant name
        // 6. variant id
        @Override
        public int compare(String id1, String id2) {
            ShipVariantAPI var1 = Global.getSettings().getVariant(id1);
            ShipVariantAPI var2 = Global.getSettings().getVariant(id2);
            boolean isRestricted1 = var1.getHullSpec().hasTag(Tags.RESTRICTED);
            boolean isRestricted2 = var2.getHullSpec().hasTag(Tags.RESTRICTED);
            boolean isHideFromCodex1 = var1.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX);
            boolean isHideFromCodex2 = var2.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX);
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
            //float DP1 = var1.getStatsForOpCosts().getSuppliesToRecover().getBaseValue(); // don't do this, NPEs
            //float DP2 = var2.getStatsForOpCosts().getSuppliesToRecover().getBaseValue();
            String name1 = var1.getDisplayName();
            String name2 = var2.getDisplayName();
            // put goalVariants at the top. shouldn't matter for stripped hulls, but using this for other stuff
            if (isGoalVariant1 && !isGoalVariant2) {
                return -1;
            } else if (!isGoalVariant1 && isGoalVariant2) {
                return 1;
            }
            // testing this
            if (isRestricted1 && !isRestricted2) {
                return 1;
            } else if (!isRestricted1 && isRestricted2) {
                return -1;
            }
            if (isHideFromCodex1 && !isHideFromCodex2) {
                return 1;
            } else if (!isHideFromCodex1 && isHideFromCodex2) {
                return -1;
            }
            // end test
            // d-hull comparison first
            if (isDHull1 && !isDHull2) {
                return 1;
            } else if (!isDHull1 && isDHull2) {
                return -1;
            }
            if (manufacturerScore1 != manufacturerScore2) { // compare numeric manufacturer score
                return Integer.compare(manufacturerScore1, manufacturerScore2);
            }
            if (!manufacturer1.equalsIgnoreCase(manufacturer2)) { // if same score, compare strings (i.e. non-vanilla manufacturers are sorted alphabetically)
                return manufacturer1.compareToIgnoreCase(manufacturer2);
            }
            if (sizeScore1 != sizeScore2) {
                return Integer.compare(sizeScore1, sizeScore2);
            }
            //if (DP1 != DP2) {
            //    return Float.compare(DP1, DP2);
            //}
            if (!name1.equalsIgnoreCase(name2)) {
                return name1.compareToIgnoreCase(name2);
            }
            return id1.compareToIgnoreCase(id2);
        }
    };
}
