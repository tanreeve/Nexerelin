/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.ids.Drops;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Tweaked version of from RemnantFleetInteractionConfigGen
public class RemnantRaidFleetInteractionConfigGen implements FleetInteractionDialogPluginImpl.FIDConfigGen {
	@Override
	public FIDConfig createConfig() {
		FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
		//config.showTransponderStatus = false;
		config.delegate = new FleetInteractionDialogPluginImpl.FIDDelegate() {
			public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
				if (!(dialog.getInteractionTarget() instanceof CampaignFleetAPI)) return;

				CampaignFleetAPI fleet = (CampaignFleetAPI) dialog.getInteractionTarget();

				FleetEncounterContextPlugin.DataForEncounterSide data = context.getDataFor(fleet);
				List<FleetMemberAPI> losses = new ArrayList<FleetMemberAPI>();
				for (FleetEncounterContextPlugin.FleetMemberData fmd : data.getOwnCasualties()) {
					losses.add(fmd.getMember());
				}

				List<SalvageEntityGenDataSpec.DropData> dropRandom = new ArrayList<SalvageEntityGenDataSpec.DropData>();

				int [] counts = new int[3];
				String [] groups = new String [] {Drops.AI_CORES1, Drops.AI_CORES2, Drops.AI_CORES3};
				//for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
				for (FleetMemberAPI member : losses) {
					if (member.isStation()) {
						counts[2] += 10;
					}

					if (member.isCapital()) {
						counts[2] += 2;
					} else if (member.isCruiser()) {
						counts[2] += 1;
					} else if (member.isDestroyer()) {
						counts[1] += 1;
					} else if (member.isFrigate()) {
						counts[0] += 1;
					}
				}

//					if (fleet.isStationMode()) {
//						counts[2] += 10;
//					}

				for (int i = 0; i < counts.length; i++) {
					int count = counts[i];
					if (count <= 0) continue;

					SalvageEntityGenDataSpec.DropData d = new SalvageEntityGenDataSpec.DropData();
					d.group = groups[i];
					d.chances = (int) Math.ceil(count * 1f);
					dropRandom.add(d);
				}

				Random salvageRandom = new Random(Misc.getSalvageSeed(fleet));
				CargoAPI extra = SalvageEntity.generateSalvage(salvageRandom, 1f, 1f, 1f, 1f, null, dropRandom);
				for (CargoStackAPI stack : extra.getStacksCopy()) {
					salvage.addFromStack(stack);
				}
			}
			public void notifyLeave(InteractionDialogAPI dialog) {
			}
			public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
				bcc.aiRetreatAllowed = false;
				//bcc.objectivesAllowed = false;
			}
		};
		return config;
	}
	
}