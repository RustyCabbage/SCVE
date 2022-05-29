package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.apache.log4j.Logger;

import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static data.scripts.SCVE_Utils.getString;

public class SCVE_AddOfficer extends BaseHullMod {

    public static Logger log = Global.getLogger(SCVE_AddOfficer.class);
    private static final String CUSTOM_OFFICER_FILE_PATH = "custom_officer.json";
    private static final String OFFICER_DETAILS_HULLMOD_ID = "SCVE_officerdetails";

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return;
        }

        // easy way to tell there's no officer already on the ship
        if (stats.getFleetMember().getCaptain().getNameString().isEmpty()) {
            PersonAPI officer = createCustomOfficer();
            stats.getFleetMember().setCaptain(officer);
            addOfficerDetailsHullmod(stats);
            SCVE_OfficerDetails.firstFrame = true;
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ship.getVariant().removeMod(spec.getId());
        ship.getVariant().removePermaMod(spec.getId());
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return;
        }
    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return getString("hullModCampaignError");
        }
        return getString("hullModOfficerPresent");
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        //this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return false;
        }
        return (ship.getCaptain().getNameString().isEmpty());
    }

    public static PersonAPI createCustomOfficer() {
        PersonAPI officer = Global.getSector().getFaction(Factions.PLAYER).createRandomPerson();
        int officerLevel = 0;
        officer.getStats().setSkipRefresh(true);

        try {
            JSONObject settings = Global.getSettings().loadJSON(CUSTOM_OFFICER_FILE_PATH, "ShipCatalogVariantEditor");

            String personality = settings.optString("personality", "steady");
            officer.setPersonality(personality);

            JSONArray keys = settings.names();
            for (int i = 1; i < keys.length(); i++) {
                String skill = keys.getString(i);
                int level = Math.min(2,Math.max(0,settings.optInt(skill)));
                if (level > 0) {
                    officer.getStats().setSkillLevel(skill, level);
                    officerLevel++;
                }
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        officer.getStats().setLevel(officerLevel);
        officer.getStats().setSkipRefresh(false);
        officer.getStats().refreshCharacterStatsEffects();
        return officer;
    }

    // works with FleetMemberAPI or MutableShipStatsAPI.getFleetMember(), but NOT ShipAPI
    public static void addOfficerDetailsHullmod(FleetMemberAPI member) {
        if (!member.getCaptain().getNameString().isEmpty()) {
            //can someone tell me why I have to do this
            //also TODO: check if cloning the variant is necessary
            if (member.getVariant().isEmptyHullVariant()) {
                ShipVariantAPI clone = member.getVariant().clone();
                member.setVariant(clone, false, false);
                member.getVariant().addPermaMod(OFFICER_DETAILS_HULLMOD_ID);
                //log.info("Officer details hullmod added");
            } else {
                ShipVariantAPI clone = member.getVariant().clone();
                clone.setHullVariantId(clone.getHullVariantId() + "_clone");
                member.setVariant(Global.getSettings().getVariant(clone.getHullVariantId()), false, false);
                member.getVariant().addPermaMod(OFFICER_DETAILS_HULLMOD_ID);
                //log.info("Officer details hullmod added");
            }
        } else {
            log.info("Member " + member.getShipName() + " has no officer");
        }
    }

    // this one is used in conjunction with the AddOfficer hullmod
    public static void addOfficerDetailsHullmod(MutableShipStatsAPI stats) {
        FleetMemberAPI member = stats.getFleetMember();
        if (!member.getCaptain().getNameString().isEmpty()) {
            //can someone tell me why I have to do this
            if (member.getVariant().isEmptyHullVariant()) {
                ShipVariantAPI clone = member.getVariant().clone();
                member.setVariant(clone, false, false);
                stats.getVariant().addPermaMod(OFFICER_DETAILS_HULLMOD_ID);
                //log.info("Officer details hullmod added");
            } else {
                ShipVariantAPI clone = member.getVariant().clone();
                clone.setHullVariantId(clone.getHullVariantId() + "_clone");
                member.setVariant(Global.getSettings().getVariant(clone.getHullVariantId()), false, false);
                stats.getVariant().addPermaMod(OFFICER_DETAILS_HULLMOD_ID);
                //log.info("Officer details hullmod added");
            }
        } else {
            log.info("Member " + member.getShipName() + " has no officer");
        }
    }
}
