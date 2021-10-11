package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.apache.log4j.Logger;

import java.util.ArrayList;

import static data.scripts.SCVE_Utils.getString;

public class SCVE_DisIntegrateHullmod extends BaseHullMod {

    public static Logger log = Global.getLogger(SCVE_IntegrateHullmod.class);

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getVariant().removeMod(spec.getId());
        ship.getVariant().removePermaMod(spec.getId());
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return;
        }
        ArrayList<String> permaMods = new ArrayList<>(); // use array list so that I can use .get()
        for (String permaModId : ship.getVariant().getPermaMods()) {
            if (ship.getVariant().getSMods().contains(permaModId)
                    || Global.getSettings().getHullModSpec(permaModId).hasTag(Tags.HULLMOD_DMOD)) {
                permaMods.add(permaModId); // add s-mods and d-mods only
            }
        }
        if (!permaMods.isEmpty()) {
            String last = permaMods.get(permaMods.size() - 1);
            ship.getVariant().removePermaMod(last);
            ship.getVariant().addMod(last);
            //log.info("Removed perma-mod: " + last);
        }
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return getString("hullModCampaignError");
        }
        if (ship.getVariant().getPermaMods().isEmpty()) {
            return getString("hullModNoPermaMods");
        }
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return false;
        }
        if (ship.getVariant().getPermaMods().isEmpty()) {
            return false;
        }
        return true;
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize, ShipAPI ship) {
        ArrayList<String> sMods = new ArrayList<>(ship.getVariant().getSMods());

        String lastSModName = "";
        if (!sMods.isEmpty()) {
            String id = sMods.get(sMods.size() - 1);
            lastSModName = Global.getSettings().getHullModSpec(id).getDisplayName();
        }
        if (index == 0) {
            return lastSModName;
        }
        return null;
    }
}
