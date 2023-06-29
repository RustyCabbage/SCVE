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

        String variantFileName = String.format("%s_%s", ship.getHullSpec().getHullId(), shipVariant.getDisplayName());
        writeVariantFile(shipVariant, variantFileName);
        if (!shipVariant.getStationModules().isEmpty()) {
            // modules are given the parent ship's name
            for (String slotId : shipVariant.getModuleSlots()) {
                ShipVariantAPI moduleVariant = shipVariant.getModuleVariant(slotId);
                String moduleVariantFileName = variantFileName.replace(ship.getHullSpec().getHullId(), moduleVariant.getHullSpec().getHullId());
                writeVariantFile(moduleVariant, shipVariant.getDisplayName(), moduleVariantFileName);
            }
        }
        Global.getSoundPlayer().playUISound(Sounds.STORY_POINT_SPEND, 1.0f, 1.0f);
    }

    public void writeVariantFile(ShipVariantAPI variant, String variantFileName) {
        try {
            String validVariantFileName = variantFileName.replace(" ", "_").replaceAll("[\\\\/:*?\"<>|]", "");
            log.info(validVariantFileName);
            ArrayList<String> nonBuiltInHullMods = new ArrayList<>(variant.getNonBuiltInHullmods());
            ArrayList<String> permaMods = new ArrayList<>(variant.getPermaMods());
            ArrayList<String> sMods = new ArrayList<>(variant.getSMods());
            ArrayList<String> suppressedMods = new ArrayList<>(variant.getSuppressedMods());
            ArrayList<String> nonBuiltInWings = new ArrayList<>(variant.getNonBuiltInWings()); // unsorted
            Collections.sort(nonBuiltInHullMods);
            Collections.sort(permaMods);
            Collections.sort(sMods);
            Collections.sort(suppressedMods);
            //log.info(variant.getDisplayName());
            String data = "{" + newLine
                    + String.format("%s\"displayName\": \"%s\",", tab, variant.getDisplayName()) + newLine
                    + String.format("%s\"fluxCapacitors\": %s,", tab, variant.getNumFluxCapacitors()) + newLine
                    + String.format("%s\"fluxVents\": %s,", tab, variant.getNumFluxVents()) + newLine
                    + String.format("%s\"goalVariant\": %s,", tab, variant.isGoalVariant()) + newLine
                    + String.format("%s\"hullId\": \"%s\",", tab, variant.getHullSpec().getHullId()) + newLine
                    + createArrayString(nonBuiltInHullMods, ArrayType.hullMods) + newLine
                    + createArrayString(permaMods, ArrayType.permaMods) + newLine
                    + createArrayString(sMods, ArrayType.sMods) + newLine
                    + createArrayString(suppressedMods, ArrayType.suppressedMods) + newLine
                    + String.format("%s\"variantId\": \"%s\",", tab, validVariantFileName) + newLine;
            data += createWeaponGroupString(variant) + newLine
                    + createArrayString(nonBuiltInWings, ArrayType.wings) + newLine
                    + createModulesString(variant, variantFileName, variant.getModuleSlots()) + newLine
                    + "}";
            log.info(data);
            Global.getSettings().writeTextFileToCommon(String.format("SCVE/%s.variant",
                    validVariantFileName), data);
            log.info("Saved to " + String.format("SCVE/%s.variant",
                    validVariantFileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String createArrayString(ArrayList<String> array, ArrayType type) {
        String firstLine = String.format("%s\"%s\": [", tab, type.toString());
        String lastLine = tab + "],";
        if (array.isEmpty()) {
            lastLine = "],";
            return firstLine + lastLine;
        }
        firstLine += newLine;
        String itemsString = "";
        for (String item : array) {
            itemsString += String.format("%s%s\"%s\",", tab, tab, item) + newLine;
        }

        return firstLine + itemsString + lastLine;
    }

    public String createWeaponGroupString(ShipVariantAPI variant) {
        String firstLine = tab + "\"weaponGroups\": [";
        String lastLine = tab + "],";
        List<WeaponGroupSpec> weaponGroupSpecList = variant.getWeaponGroups();
        if (weaponGroupSpecList.isEmpty()) {
            lastLine = "],";
            return firstLine + lastLine;
        }
        firstLine += newLine;
        String weaponGroupsString = "";
        for (WeaponGroupSpec weaponGroup : weaponGroupSpecList) {
            if (weaponGroup.getSlots().isEmpty()) {
                continue;
            }
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

    public String createModulesString(ShipVariantAPI parentVariant, String parentVariantFileName, List<String> moduleSlots) {
        String firstLine = tab + "\"modules\": [";
        String lastLine = tab + "],";
        if (moduleSlots.isEmpty()) {
            lastLine = "],";
            return firstLine + lastLine;
        }
        firstLine += newLine;
        String modulesString = "";
        for (String slotId : parentVariant.getModuleSlots()) {
            ShipVariantAPI moduleVariant = parentVariant.getModuleVariant(slotId);
            String moduleVariantFileName = parentVariantFileName.replace(parentVariant.getHullSpec().getHullId(), moduleVariant.getHullSpec().getHullId());
            modulesString += String.format("%s%s{\"%s\": \"%s\"},", tab, tab, slotId, moduleVariantFileName) + newLine;
        }
        return firstLine + modulesString + lastLine;
    }

    public void writeVariantFile(ShipVariantAPI variant, String variantDisplayName, String variantFileName) {
        try {
            String validVariantFileName = variantFileName.replace(" ", "_").replaceAll("[\\\\/:*?\"<>|]", "");
            log.info(validVariantFileName);
            ArrayList<String> nonBuiltInHullMods = new ArrayList<>(variant.getNonBuiltInHullmods());
            ArrayList<String> permaMods = new ArrayList<>(variant.getPermaMods());
            ArrayList<String> sMods = new ArrayList<>(variant.getSMods());
            ArrayList<String> suppressedMods = new ArrayList<>(variant.getSuppressedMods());
            ArrayList<String> nonBuiltInWings = new ArrayList<>(variant.getNonBuiltInWings()); // unsorted
            Collections.sort(nonBuiltInHullMods);
            Collections.sort(permaMods);
            Collections.sort(sMods);
            Collections.sort(suppressedMods);
            //log.info(variant.getDisplayName());
            String data = "{" + newLine
                    + String.format("%s\"displayName\": \"%s\",", tab, variantDisplayName) + newLine
                    + String.format("%s\"fluxCapacitors\": %s,", tab, variant.getNumFluxCapacitors()) + newLine
                    + String.format("%s\"fluxVents\": %s,", tab, variant.getNumFluxVents()) + newLine
                    + String.format("%s\"goalVariant\": %s,", tab, variant.isGoalVariant()) + newLine
                    + String.format("%s\"hullId\": \"%s\",", tab, variant.getHullSpec().getHullId()) + newLine
                    + createArrayString(nonBuiltInHullMods, ArrayType.hullMods) + newLine
                    + createArrayString(permaMods, ArrayType.permaMods) + newLine
                    + createArrayString(sMods, ArrayType.sMods) + newLine
                    + createArrayString(suppressedMods, ArrayType.suppressedMods) + newLine
                    + String.format("%s\"variantId\": \"%s\",", tab, validVariantFileName) + newLine;
            data += createWeaponGroupString(variant) + newLine
                    + createArrayString(nonBuiltInWings, ArrayType.wings) + newLine
                    + createModulesString(variant, variantFileName, variant.getModuleSlots()) + newLine
                    + "}";
            log.info(data);
            Global.getSettings().writeTextFileToCommon(String.format("SCVE/%s.variant",
                    validVariantFileName), data);
            log.info("Saved to " + String.format("SCVE/%s.variant",
                    validVariantFileName));
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        String variantFileName = String.format("%s_%s", ship.getHullSpec().getHullId(), shipVariant.getDisplayName());
        float pad = 10f;

        tooltip.setBulletedListMode("");
        tooltip.addSectionHeading("Variants Created", Alignment.MID, pad);
        tooltip.addPara("Will add the following variants files to %s:", pad, Misc.getHighlightColor(), "../Starsector/saves/common/SCVE/");
        tooltip.addPara(variantFileName, Misc.getDesignTypeColor(ship.getHullSpec().getManufacturer()), pad);
        if (!shipVariant.getStationModules().isEmpty()) {
            // modules are given the parent ship's name
            for (String slotId : shipVariant.getModuleSlots()) {
                ShipVariantAPI moduleVariant = shipVariant.getModuleVariant(slotId);
                String moduleVariantFileName = variantFileName.replace(ship.getHullSpec().getHullId(), moduleVariant.getHullSpec().getHullId());
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
                , new Color[]{Misc.getHighlightColor(), Misc.getNegativeHighlightColor()
                        , Misc.getHighlightColor()
                        , Misc.getNegativeHighlightColor()}
                , String.format("%s%s", variantFileName, ".variant"), "data"
                , String.format("%s%s", variantFileName, ".variant")
                , ".data");
        tooltip.addPara("Use a text editor to edit the file and set", pad / 2f);
        tooltip.addPara("%s %s", 0f
                , new Color[]{Misc.getTextColor(), Misc.getPositiveHighlightColor()}
                , tab, "\"goalVariant\": true,");
        tooltip.addPara("Move %s to a %s folder", pad / 2f, Misc.getHighlightColor()
                , String.format("%s%s",variantFileName, ".variant"), "../data/variants/");
        tooltip.addPara("Reload the game!",pad/2f);
    }

}