package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.ListMap;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

import static data.scripts.SCVE_FilterUtils.*;
import static data.scripts.SCVE_Utils.*;

public class SCVE_ModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(SCVE_ModPlugin.class);
    public static Set<String> allModules = new HashSet<>();
    public static ListMap<String> modToHull = new ListMap<>();

    @Override
    public void onApplicationLoad() {
        allModules = getAllModules();
        //log.info("Loaded allModules: " + allModules);
        modToHull = getModToHullListMap(allModules);
        //log.info("Loaded modToHull: " + modToHull);
        SCVE_FilterUtils.getOriginalData();
        //log.info(ORIGINAL_WEAPON_TAGS_MAP);
        //log.info(ORIGINAL_WING_TAGS_MAP);
        //log.info(ORIGINAL_WING_OP_COST_MAP);
        //log.info(ORIGINAL_HULLMOD_IS_DMOD_MAP);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        SCVE_FilterUtils.restoreOriginalData(true, true, true);
    }
}
