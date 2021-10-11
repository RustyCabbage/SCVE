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

public class SCVE_IntegrateHullmod extends BaseHullMod {

    public static Logger log = Global.getLogger(SCVE_IntegrateHullmod.class);

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		ship.getVariant().removeMod(spec.getId());
			ship.getVariant().removePermaMod(spec.getId());
		//this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return;
        }
        
        ArrayList<String> hullmods = (ArrayList<String>) ship.getVariant().getNonBuiltInHullmods();
        if (!hullmods.isEmpty()) {
            String last = hullmods.get(hullmods.size() - 1);
            ship.getVariant().removeMod(last);
            if (Global.getSettings().getHullModSpec(last).hasTag(Tags.HULLMOD_DMOD)) {
                ship.getVariant().addPermaMod(last, false);
                //log.info("Built-in hullmod: " + last + " as d-mod");
            } else {
                ship.getVariant().addPermaMod(last, true);
                //log.info("Built-in hullmod: " + last + " as s-mod");
            }
        }
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return getString("hullModCampaignError");
        }
        if (ship.getVariant().getNonBuiltInHullmods().isEmpty()) {
            return getString("hullModNoHullMods");
        }
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return false;
        }
        return !ship.getVariant().getNonBuiltInHullmods().isEmpty();
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize, ShipAPI ship) {
        ArrayList<String> hullmods = (ArrayList<String>) ship.getVariant().getNonBuiltInHullmods();

        String lastHullmod = null;
        if (!hullmods.isEmpty()) {
            String id = hullmods.get(hullmods.size() - 1);
            lastHullmod = Global.getSettings().getHullModSpec(id).getDisplayName();
        }
        String message = "";
        if (lastHullmod != null) {
            message = "Will s-mod: " + lastHullmod + ".";
        }

        if (index == 0) {
            return message;
        }
        return null;
    }
}