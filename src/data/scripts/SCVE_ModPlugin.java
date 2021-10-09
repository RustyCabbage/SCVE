package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.ListMap;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

import static data.scripts.SCVE_Utils.*;

public class SCVE_ModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(SCVE_ModPlugin.class);
    public static Set<String> allModules = new HashSet<>();
    public static ListMap<String> modToHull = new ListMap<>();

    @Override
    public void onApplicationLoad() {
        allModules = getAllModules();
        modToHull = getModToHullListMap(allModules);
    }
}
