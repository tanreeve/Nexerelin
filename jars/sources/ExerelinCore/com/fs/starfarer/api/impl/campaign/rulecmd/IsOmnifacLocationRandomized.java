package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinConfig;
import java.util.List;
import java.util.Map;


public class IsOmnifacLocationRandomized extends BaseCommandPlugin {
		public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
                        return ExerelinConfig.randomOmnifactoryLocation;
        }
}
