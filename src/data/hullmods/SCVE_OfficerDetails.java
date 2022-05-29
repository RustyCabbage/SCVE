package data.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.List;

import org.apache.log4j.Logger;

import static data.scripts.SCVE_Utils.getString;

public class SCVE_OfficerDetails extends BaseHullMod {

    public static Logger log = Global.getLogger(SCVE_OfficerDetails.class);
    private static final float pad = 10f;
    public static boolean firstFrame = true;

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        // this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            ship.getVariant().removeMod(spec.getId());
            ship.getVariant().removePermaMod(spec.getId());
            return;
        }
        // ship.getCaptain() returns null for the first frame
        //log.info("first frame: " + firstFrame);
        if (firstFrame) {
            firstFrame = false;
            return;
        }
        if (ship.getCaptain() != null) {
            if (ship.getCaptain().getNameString().isEmpty()) {
                //log.info("No officer detected, removing Officer Details hullmod");
                ship.getVariant().removePermaMod(spec.getId());
                firstFrame = true;
            }
        }

    }

    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        // this needs to do nothing if done in campaign
        if (Global.getSettings().getCurrentState() != GameState.TITLE) {
            return getString("hullModCampaignError");
        }
        if (ship.getCaptain() == null) {
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
        if (ship.getCaptain() != null) {
            return (!ship.getCaptain().getNameString().isEmpty());
        }
        return false;
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        PersonAPI person = ship.getCaptain();

        if (!person.isDefault()) {
            String title, imageText;
            float portraitHeight = 100;

            String shipName = ship.getName();
            String fullName = person.getNameString();
            String portrait = person.getPortraitSprite();
            String level = Integer.toString(person.getStats().getLevel());
            String personality = person.getPersonalityAPI().getDisplayName();
            List<SkillLevelAPI> skills = person.getStats().getSkillsCopy();
            //String desc = person.getMemoryWithoutUpdate().getString("$quote");

            boolean isAdmiral = false;
            /* todo maybe I'll re-add admiral skills later
            if (ship.getFleetMember().isFlagship()) {
                isAdmiral = true;
            }
             */

            if (isAdmiral) {
                title = "Admiral details";
                imageText = "The fleet is headed by the " + shipName + " whose captain is " + fullName + ", a Level " + level + " " + personality + " officer.";
            } else {
                title = "Officer details";
                imageText = "The " + shipName + " is piloted by " + fullName + ", a Level " + level + " " + personality + " officer.";
            }
            tooltip.addSectionHeading(title, Alignment.MID, -20);

            TooltipMakerAPI officerImageWithText = tooltip.beginImageWithText(portrait, portraitHeight);
            officerImageWithText.addPara(imageText,
                    -portraitHeight / 2, Color.YELLOW,
                    shipName, fullName, level, personality);
            //officerImageWithText.addPara(desc, 0);
            tooltip.addImageWithText(pad);

            if (isAdmiral) {
                tooltip.addSectionHeading("Admiral skills", Alignment.MID, pad);

                for (SkillLevelAPI skill : skills) {
                    float skillLevel = skill.getLevel();
                    if (!skill.getSkill().isAdmiralSkill() || skillLevel == 0) {
                        continue;
                    }
                    String skillSprite = skill.getSkill().getSpriteName();
                    String skillName = skill.getSkill().getName();
                    //String aptitude = skill.getSkill().getGoverningAptitudeId();

                    TooltipMakerAPI skillImageWithText = tooltip.beginImageWithText(skillSprite, 40);
                    skillImageWithText.addPara(skillName, 0);
                    tooltip.addImageWithText(pad);
                }
            }

            tooltip.addSectionHeading("Combat-related skills", Alignment.MID, pad);

            for (SkillLevelAPI skill : skills) {
                float skillLevel = skill.getLevel();
                if (!skill.getSkill().isCombatOfficerSkill() || skillLevel == 0) {
                    continue;
                }
                String skillSprite = skill.getSkill().getSpriteName();
                String skillName = skill.getSkill().getName();
                //String aptitude = skill.getSkill().getGoverningAptitudeId();

                String eliteText = "", eliteTextPre = "", eliteTextPost = "";
                if (skillLevel == 2) {
                    eliteTextPre = " (";
                    eliteText = "ELITE";
                    eliteTextPost = ")";
                }

                TooltipMakerAPI skillImageWithText = tooltip.beginImageWithText(skillSprite, 40);
                skillImageWithText.addPara(skillName + eliteTextPre + eliteText + eliteTextPost, 0, Color.GREEN, eliteText);
                tooltip.addImageWithText(pad);
            }
        }
    }

    @Override
    public int getDisplaySortOrder() {
        return -1;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return -10;
    }
}
