package com.fs.starfarer.api.impl.campaign.rulecmd.newgame;

import com.fs.starfarer.api.Global;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.StringHelper;
import java.awt.Color;


public class Nex_NGCStartResources extends BaseCommandPlugin {
	
	public static final float BAR_WIDTH = 320;
	
	protected String getString(String id)
	{
		return StringHelper.getString("exerelin_ngc", id);
	}
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		switch (arg) {
			case "createOptions":
				createSliders(dialog.getOptionPanel());
				return true;
			case "save":
				saveValues(dialog.getOptionPanel(), dialog.getTextPanel(), memoryMap.get(MemKeys.LOCAL));
				return true;
			default:
				return false;
		}
	}
	
	protected void createSliders(OptionPanelAPI opts)
	{
		ExerelinSetupData data = ExerelinSetupData.getInstance();
		
		opts.addSelector(getString("startingLevelTitle"), "startLevelSelector", Color.GREEN, BAR_WIDTH, 48, 
				1, 20,	// min, max
				ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("startLevelSelector", 1);
		
		opts.addSelector(getString("startingCreditsTitle"), "startCreditsSelector", Color.YELLOW, BAR_WIDTH, 48, 
				0, 300,	// min, max
				ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("startCreditsSelector", 20);
		
		opts.addSelector(getString("startingOfficersTitle"), "startOfficersSelector", Color.CYAN, BAR_WIDTH, 48, 
				0, 4,	// min, max
				ValueDisplayMode.VALUE, null);
		opts.setSelectorValue("startOfficersSelector", data.numStartingOfficers);
		
		opts.addOption(StringHelper.getString("back", true), "nex_NGCStartBack");
	}
	
	protected void saveValues(OptionPanelAPI opts, TextPanelAPI text, MemoryAPI mem)
	{
		CharacterCreationData charData = (CharacterCreationData) mem.get("$characterData");
		ExerelinSetupData setupData = ExerelinSetupData.getInstance();
		
		Global.getLogger(this.getClass()).info(String.format("wololo: %s, %s, %s", 
				opts.hasSelector("startLevelSelector"),
				opts.hasSelector("startCreditsSelector"),
				opts.hasSelector("startOfficersSelector")
		));
		
		int level = Math.round(opts.getSelectorValue("startLevelSelector"));
		long xp = Global.getSettings().getLevelupPlugin().getXPForLevel(level);
		int credits = Math.round(opts.getSelectorValue("startCreditsSelector")) * 1000;
		int officers = Math.round(opts.getSelectorValue("startOfficersSelector"));
		Global.getLogger(this.getClass()).info(String.format("bla: %s, %s, %s, %s", level, xp, credits, officers));
		
		charData.getPerson().getStats().addXP(xp);
		Nex_NGCAddLevel.addXPGainText(xp, text);
		
		charData.getStartingCargo().getCredits().add(credits);
		AddRemoveCommodity.addCreditsGainText(credits, text);
		
		setupData.numStartingOfficers = officers;
		NGCSetNumStartingOfficers.addOfficersGainText(officers, text);
		
		opts.clearOptions();
		opts.addOption(StringHelper.getString("done", true), "nex_NGCDone");
	}
}