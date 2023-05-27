package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.WeaponGroupSpec;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
        modules,
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getVariant().removeMod(spec.getId());
        ship.getVariant().removePermaMod(spec.getId());
        /*
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return;
        }
         */

        writeVariantFile(ship.getVariant());
        if (!ship.getVariant().getStationModules().isEmpty()) {
            for (String variantId : ship.getVariant().getStationModules().values()) {
                writeVariantFile(Global.getSettings().getVariant(variantId));
            }
        }
    }

    public void writeVariantFile(ShipVariantAPI variant) {
        try {
            String data = "{" + newLine
                    + String.format("%s\"displayName\": \"%s\",", tab, variant.getDisplayName()) + newLine
                    + String.format("%s\"fluxCapacitors\": %s,", tab, variant.getNumFluxCapacitors()) + newLine
                    + String.format("%s\"fluxVents\": %s,", tab, variant.getNumFluxVents()) + newLine
                    + String.format("%s\"goalVariant\": %s,", tab, variant.isGoalVariant()) + newLine
                    + String.format("%s\"hullId\": \"%s\",", tab, variant.getHullSpec().getHullId()) + newLine
                    + createArrayString(variant.getNonBuiltInHullmods(), ArrayType.hullMods) + newLine
                    + createArrayString(variant.getPermaMods(), ArrayType.permaMods) + newLine
                    + createArrayString(variant.getSMods(), ArrayType.sMods) + newLine
                    + createArrayString(variant.getSuppressedMods(), ArrayType.suppressedMods) + newLine
                    + String.format("%s\"variantId\": \"%s_%s\",", tab, variant.getHullSpec().getHullId(), variant.getDisplayName()) + newLine
                    + createWeaponGroupString(variant) + newLine
                    + createArrayString(variant.getNonBuiltInWings(), ArrayType.wings) + newLine
                    + createModulesString(variant.getStationModules()) + newLine
                    + "}";
            log.info(data);
            Global.getSettings().writeTextFileToCommon(String.format("SCVE/%s_%s.variant", variant.getHullSpec().getHullId(), variant.getDisplayName()), data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String createArrayString(Collection<String> array, ArrayType type) {
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

    public String createModulesString(Map<String, String> stationModules) {
        String firstLine = tab + "\"modules\": [";
        String lastLine = tab + "],";
        if (stationModules.isEmpty()) {
            lastLine = "],";
            return firstLine + lastLine;
        }
        firstLine += newLine;
        String modulesString = "";
        for (String stationModule : stationModules.keySet()) {
            modulesString += String.format("%s%s{\"%s\": \"%s\"},", tab, tab, stationModule, stationModules.get(stationModule)) + newLine;
        }
        return firstLine + modulesString + lastLine;
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        /*
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return getString("hullModCampaignError");
        }
        if (ship.getVariant().getNonBuiltInHullmods().isEmpty()) {
            return getString("hullModNoHullMods");
        }
         */
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        /*
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return false;
        }
         */
        return true;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {

    }

}
