package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Sounds;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.apache.log4j.Logger;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SCVE_SaveVariant extends BaseHullMod {

    public static Logger log = Global.getLogger(SCVE_SaveVariant.class);
    String newLine = System.getProperty("line.separator");
    String tab = "    ";

    public enum ArrayType {
        hullMods,
        permaMods,
        sMods,
        suppressedMods,
        wings,
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ShipVariantAPI shipVariant = ship.getVariant();
        shipVariant.removeMod(spec.getId());
        shipVariant.removePermaMod(spec.getId());

        String variantFileName = createVariantFileName(ship, false);
        writeVariantFile(shipVariant, variantFileName);

        if (!shipVariant.getStationModules().isEmpty()) {
            // modules are given the parent ship's name
            for (String slotId : shipVariant.getModuleSlots()) {
                ShipVariantAPI moduleVariant = shipVariant.getModuleVariant(slotId);
                String moduleVariantFileName = createVariantFileName(moduleVariant.getHullSpec().getHullId(), ship.getVariant().getDisplayName(), false);
                writeVariantFile(moduleVariant, moduleVariantFileName, shipVariant.getDisplayName());
            }
        }
        Global.getSoundPlayer().playUISound(Sounds.STORY_POINT_SPEND, 1.0f, 1.0f);
    }

    public String createVariantFileName(ShipAPI ship, boolean withExtension) {
        return createVariantFileName(ship.getHullSpec().getHullId(), ship.getVariant().getDisplayName(), withExtension);
    }

    public String createVariantFileName(String hullId, String variantDisplayName, boolean withExtension) {
        String fileName = String.format("%s_%s", hullId, variantDisplayName.replace(" ", "_").replaceAll("[\\\\/:*?\"<>|]", ""));
        if (withExtension) fileName += ".variant";
        return fileName;
    }

    public void writeVariantFile(ShipVariantAPI variant, String variantFileName) {
        writeVariantFile(variant, variantFileName, variant.getDisplayName());
    }

    public void writeVariantFile(ShipVariantAPI variant, String variantFileName, String variantDisplayName) {
        try {
            log.info(variantFileName);
            String data = constructVariantData(variant, variantFileName, variantDisplayName);
            log.info(data);
            Global.getSettings().writeTextFileToCommon(String.format("SCVE/%s.variant",
                                                                     variantFileName), data);
            log.info("Saved to " + String.format("SCVE/%s.variant", variantFileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String constructVariantData(ShipVariantAPI variant, String variantFileName, String variantDisplayName) {
        ArrayList<String>[] sortedModLists = sortModLists(variant);
        ArrayList<String> nonBuiltInWings = new ArrayList<>(variant.getNonBuiltInWings()); // unsorted
        String data = "{" + newLine
                + String.format("%s\"displayName\": \"%s\",", tab, variantDisplayName) + newLine
                + String.format("%s\"fluxCapacitors\": %s,", tab, variant.getNumFluxCapacitors()) + newLine
                + String.format("%s\"fluxVents\": %s,", tab, variant.getNumFluxVents()) + newLine
                + String.format("%s\"goalVariant\": %s,", tab, variant.isGoalVariant()) + newLine
                + String.format("%s\"hullId\": \"%s\",", tab, variant.getHullSpec().getHullId()) + newLine
                + createArrayString(sortedModLists[0], ArrayType.hullMods) + newLine
                + createArrayString(sortedModLists[1], ArrayType.permaMods) + newLine
                + createArrayString(sortedModLists[2], ArrayType.sMods) + newLine
                + createArrayString(sortedModLists[3], ArrayType.suppressedMods) + newLine
                + String.format("%s\"variantId\": \"%s\",", tab, variantFileName) + newLine
                + createWeaponGroupString(variant) + newLine
                + createArrayString(nonBuiltInWings, ArrayType.wings) + newLine
                + createModulesString(variant, variant.getModuleSlots()) + newLine
                + "}";
        return data;
    }

    public ArrayList<String>[] sortModLists(ShipVariantAPI variant) {
        ArrayList<String>[] sortedLists = new ArrayList[4];
        sortedLists[0] = new ArrayList<>(variant.getNonBuiltInHullmods());
        sortedLists[1] = new ArrayList<>(variant.getPermaMods());
        sortedLists[2] = new ArrayList<>(variant.getSMods());
        sortedLists[3] = new ArrayList<>(variant.getSuppressedMods());

        for (ArrayList<String> list : sortedLists) {
            Collections.sort(list);
        }
        return sortedLists;
    }

    public String createArrayString(ArrayList<String> array, ArrayType type) {
        String firstLine = String.format("%s\"%s\": [", tab, type.toString());
        String lastLine = (array.isEmpty()) ? "]," : tab + "],";
        if (array.isEmpty()) {
            return firstLine + lastLine;
        }
        String itemsString = newLine;
        for (String item : array) {
            itemsString += String.format("%s%s\"%s\",", tab, tab, item) + newLine;
        }
        return firstLine + itemsString + lastLine;
    }

    public String createWeaponGroupString(ShipVariantAPI variant) {
        List<WeaponGroupSpec> weaponGroupSpecList = variant.getWeaponGroups();
        String firstLine = tab + "\"weaponGroups\": [";
        String lastLine = (weaponGroupSpecList.isEmpty()) ? "]," : tab + "],";
        if (weaponGroupSpecList.isEmpty()) {
            return firstLine + lastLine;
        }
        String weaponGroupsString = newLine;
        for (WeaponGroupSpec weaponGroup : weaponGroupSpecList) {
            if (weaponGroup.getSlots().isEmpty()) continue;
            String weaponGroupFirstLine = tab + tab + "{" + newLine;
            String weaponGroupAutofire = String.format("%s%s%s\"autofire\": %s,", tab, tab, tab, weaponGroup.isAutofireOnByDefault()) + newLine;
            String weaponGroupMode = String.format("%s%s%s\"mode\": \"%s\",", tab, tab, tab, weaponGroup.getType().toString()) + newLine;
            String weaponsFirstLine = tab + tab + tab + "\"weapons\": {" + newLine;
            String weaponsString = "";
            for (String slotId : weaponGroup.getSlots()) {
                weaponsString += String.format("%s%s%s%s\"%s\": \"%s\",", tab, tab, tab, tab, slotId, variant.getWeaponId(slotId)) + newLine;
            }
            String weaponsLastLine = tab + tab + tab + "}" + newLine;
            String weaponGroupLastLine = tab + tab + "}," + newLine;
            weaponGroupsString += weaponGroupFirstLine + weaponGroupAutofire + weaponGroupMode + weaponsFirstLine + weaponsString + weaponsLastLine + weaponGroupLastLine;
        }
        return firstLine + weaponGroupsString + lastLine;
    }

    public String createModulesString(ShipVariantAPI parentVariant, List<String> moduleSlots) {
        String firstLine = tab + "\"modules\": [";
        String lastLine = (moduleSlots.isEmpty()) ? "]," : tab + "],";
        if (moduleSlots.isEmpty()) {
            return firstLine + lastLine;
        }
        String modulesString = newLine;
        for (String slotId : parentVariant.getModuleSlots()) {
            ShipVariantAPI moduleVariant = parentVariant.getModuleVariant(slotId);
            String moduleVariantFileName = createVariantFileName(moduleVariant.getHullSpec().getHullId(), parentVariant.getDisplayName(), false);
            modulesString += String.format("%s%s{\"%s\": \"%s\"},", tab, tab, slotId, moduleVariantFileName) + newLine;
        }
        return firstLine + modulesString + lastLine;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return true;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        ShipVariantAPI shipVariant = ship.getVariant();
        String variantFileName = createVariantFileName(ship, true);
        float pad = 10f;

        tooltip.setBulletedListMode("");
        tooltip.addSectionHeading("Variants Created", Alignment.MID, pad);
        tooltip.addPara("Will add the following variants files to %s:", pad, Misc.getHighlightColor(), "../Starsector/saves/common/SCVE/");
        tooltip.addPara(variantFileName, Misc.getDesignTypeColor(ship.getHullSpec().getManufacturer()), pad);
        if (!shipVariant.getStationModules().isEmpty()) {
            // modules are given the parent ship's name
            for (String slotId : shipVariant.getModuleSlots()) {
                ShipVariantAPI moduleVariant = shipVariant.getModuleVariant(slotId);
                String moduleVariantFileName = createVariantFileName(moduleVariant.getHullSpec().getHullId(), ship.getVariant().getDisplayName(), true);
                tooltip.addPara(moduleVariantFileName,
                        Misc.interpolateColor(Misc.getDesignTypeColor(ship.getHullSpec().getManufacturer())
                                , Misc.getGrayColor(), 0.5f)
                        , pad / 2f);
            }
        }

        tooltip.addSectionHeading("How To Add To Autofit Menu", Alignment.MID, pad);
        tooltip.setBulletedListMode("- ");
        tooltip.addPara("Go to %s", pad, Misc.getHighlightColor(), "../Starsector/saves/common/SCVE/");
        tooltip.addPara("Rename %s.%s to %s (remove %s)", pad / 2f
                , new Color[]{Misc.getHighlightColor()
                        , Misc.getNegativeHighlightColor()
                        , Misc.getHighlightColor()
                        , Misc.getNegativeHighlightColor()}
                , variantFileName, "data", variantFileName, ".data");
        tooltip.addPara("Use a text editor to edit the file and set", pad / 2f);
        tooltip.addPara("%s %s", 0f
                , new Color[]{Misc.getTextColor(), Misc.getPositiveHighlightColor()}
                , tab, "\"goalVariant\": true,");
        tooltip.addPara("Move %s to a %s folder", pad / 2f, Misc.getHighlightColor()
                , variantFileName, "../data/variants/");
        tooltip.addPara("Reload the game!",pad/2f);
    }

}