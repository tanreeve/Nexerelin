package exerelin.campaign.intel.raid;

import exerelin.campaign.intel.fleets.NexAssembleStage;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import exerelin.campaign.intel.OffensiveFleetIntel;
import org.lazywizard.lazylib.MathUtils;

public class NexRaidAssembleStage extends NexAssembleStage {
	
	public NexRaidAssembleStage(OffensiveFleetIntel intel, SectorEntityToken gatheringPoint) {
		super(intel, gatheringPoint);
	}
	
	@Override
	protected String pickNextType() {
		return "exerelinInvasionSupportFleet";
	}
	
	@Override
	protected float getFP(String type) {
		float base = 120f;
		
		if (Math.random() < 0.33f)
			base *= 1.5f;
		
		base *= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			
		if (spawnFP < base * 1.5f) {
			base = spawnFP;
		}
		if (base > spawnFP) base = spawnFP;
		
		spawnFP -= base;
		return base;
	}
}