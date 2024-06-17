package data.scripts;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.coreui.refit.auto.SavedVariantData;
import com.fs.starfarer.loading.specs.HullVariantSpec;
import org.apache.log4j.Logger;

public class SCVE_ParseVariant {

    private static final Logger log = Global.getLogger(SCVE_ParseVariant.class);

    public ShipVariantAPI parseVariant(String fileName) {
        // todo i did this in magiclib
        return null;
    }

    public void addTargetVariant(ShipVariantAPI variant) {
        /* for console command runcode
import com.fs.starfarer.coreui.refit.auto.SavedVariantData;
import com.fs.starfarer.loading.specs.HullVariantSpec;
ShipVariantAPI variant = Global.getSettings().getVariant("onslaught_Elite");
         */
        SavedVariantData autofitVariantsData = (SavedVariantData) Global.getSector().getAutofitVariants();
        ShipHullSpecAPI hullSpec = variant.getHullSpec();
        HullVariantSpec hullVariantSpec = (HullVariantSpec) variant;

        int[] safeToAdd = new int[autofitVariantsData.getVisible(hullSpec.getHullId()).getVariants().size()];
        for (int i = 0; i < autofitVariantsData.getVisible(hullSpec.getHullId()).getVariants().size(); i++) {
            SavedVariantData.VariantResolver variantResolver = (SavedVariantData.VariantResolver) autofitVariantsData.getVisible(hullSpec.getHullId()).getVariants().get(i);
            if (variantResolver == null) {
                safeToAdd[i] = 1;
            }
        }
        //log.info(Arrays.toString(safeToAdd));
        for (int j = 0; j < safeToAdd.length; j++) {
            if (safeToAdd[j] != 0) {
                autofitVariantsData.addToVisible(j, hullVariantSpec);
                break;
            }
        }

        autofitVariantsData.getTargetVariants(hullSpec.getHullId());
    }
}