package exerelin.campaign.events;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.HashMap;
import java.util.List;

public class MarketCapturedEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(MarketCapturedEvent.class);
	
	private static final int DAYS_TO_KEEP = 120;
	
	private FactionAPI newOwner;
	private FactionAPI oldOwner;
	private List<String> factionsToNotify;
	private float repChangeStrength;
	private boolean playerInvolved;
	private Map<String, Object> params;
	
	private boolean done;
	private boolean transmitted;
	private float age;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
		done = false;
		transmitted = false;
		age = 0;
		//log.info("Capture event created");
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		newOwner = (FactionAPI)params.get("newOwner");
		oldOwner = (FactionAPI)params.get("oldOwner");
		repChangeStrength = (Float)params.get("repChangeStrength");
		playerInvolved = (Boolean)params.get("playerInvolved");
		factionsToNotify = (List<String>)params.get("factionsToNofify");
		//log.info("Params newOwner: " + newOwner);
		//log.info("Params oldOwner: " + oldOwner);
		//log.info("Params playerInvolved: " + playerInvolved);
	}
		
	@Override
	public void advance(float amount)
	{
		if (done)
			return;
		
		if (newOwner == oldOwner)
		{
			done = true;
			return;
		}
		
		age = age + Global.getSector().getClock().convertToDays(amount);
		if (age > DAYS_TO_KEEP)
		{
			done = true;
			return;
		}
		if (!transmitted)
		{
			String stage = "report";
			//MessagePriority priority = MessagePriority.SECTOR;
			MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;
			if (playerInvolved) 
			{
				stage = "report_player";
				//priority = MessagePriority.ENSURE_DELIVERY;
			}
			// factionsToNotify is null for some reason -> causes NPE
			/*
			Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority, new BaseOnMessageDeliveryScript() {
					final List<String> factions = factionsToNotify;
					public void beforeDelivery(CommMessageAPI message) {
					    if (playerInvolved)
						for (String factionId : factions)
						    Global.getSector().adjustPlayerReputation(
							new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.COMBAT_WITH_ENEMY, repChangeStrength),
							factionId);
					}
				});
			*/
			Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
			//log.info("Capture event reported");
			transmitted = true;
		}
	}

	@Override
	public String getEventName() {
		return (newOwner.getDisplayName() + " captures " + market.getName());
	}
	
	@Override
	public String getCurrentImage() {
		return newOwner.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return newOwner.getLogo();
	}
		
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.EVENT;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		String newOwnerStr = newOwner.getDisplayName();
		String oldOwnerStr = oldOwner.getDisplayName();
		String theNewOwnerStr = newOwner.getDisplayNameWithArticle();
		String theOldOwnerStr = oldOwner.getDisplayNameWithArticle();
		map.put("$newOwner", newOwnerStr);
		map.put("$oldOwner", oldOwnerStr);
		map.put("$NewOwner", Misc.ucFirst(newOwnerStr));
		map.put("$OldOwner", Misc.ucFirst(oldOwnerStr));
		map.put("$theOldOwner", theNewOwnerStr);
		map.put("$theOldOwner", theOldOwnerStr);
		map.put("$TheOldOwner", Misc.ucFirst(theNewOwnerStr));
		map.put("$TheOldOwner", Misc.ucFirst(theOldOwnerStr));
		
		map.put("$marketsRemaining", "" + ExerelinUtilsFaction.getFactionMarkets(oldOwner.getId()).size());
		return map;
	}

	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
}
