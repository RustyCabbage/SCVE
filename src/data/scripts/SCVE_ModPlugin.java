package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import static data.scripts.SCVE_Utils.*;

public class SCVE_ModPlugin extends BaseModPlugin {

    private static final Logger log = Global.getLogger(SCVE_ModPlugin.class);

    @Override
    public void onApplicationLoad() {
        allModules = getAllModules();
    }
}
