package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

import static data.scripts.SCVE_Utils.getString;

public class SCVE_RemoveOfficer extends BaseHullMod {

    public static Logger log = Global.getLogger(SCVE_RemoveOfficer.class);
    private static final String OFFICER_DETAILS_HULLMOD_ID = "SCVE_officerdetails"; // when I tried to import it stuff broke??

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE
                || stats.getFleetMember().getCaptain().getNameString().isEmpty() // easy way to tell there's no officer already on the ship
        ) {
            return;
        }
        stats.getFleetMember().setCaptain(null);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getVariant().removeMod(spec.getId());
        ship.getVariant().removePermaMod(spec.getId());
        ship.getVariant().removePermaMod(OFFICER_DETAILS_HULLMOD_ID);
        ship.getVariant().removeMod(OFFICER_DETAILS_HULLMOD_ID);
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        return (Global.getSettings().getCurrentState() != GameState.TITLE) ? getString("hullModCampaignError") : getString("hullModNoOfficer");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        return (Global.getSettings().getCurrentState() == GameState.TITLE && !ship.getCaptain().getNameString().isEmpty());
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        super.addPostDescriptionSection(tooltip, hullSize, ship, width, isForModSpec);
    }
}