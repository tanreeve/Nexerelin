package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.SpecialForcesTask;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.TaskType;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;

@Log4j
public class SpecialForcesAssignmentAI extends RouteFleetAssignmentAI {
	
	public static final Object CUSTOM_DELAY_BEFORE_RAID = new Object();
	
	protected SpecialForcesIntel intel;
	
	public SpecialForcesAssignmentAI(SpecialForcesIntel intel, CampaignFleetAPI fleet, 
			RouteManager.RouteData route, FleetActionDelegate delegate) 
	{
		super(fleet, route, delegate);
		this.intel = intel;
	}
	
	// no automatic return-to-location-and-despawn, do it only if event is over
	@Override
	public void advance(float amount) {
		if (gaveReturnAssignments == null && (intel.isEnding() || intel.isEnded())) 
		{
			fleet.clearAssignments();
			SectorEntityToken origin = intel.origin.getPrimaryEntity();
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, origin, 1000f,
								StringHelper.getFleetAssignmentString("returningTo", origin.getName()));
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, intel.origin.getPrimaryEntity(), 3);
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, origin, 1000f,
								StringHelper.getFleetAssignmentString("returningTo", intel.origin.getName()));
			gaveReturnAssignments = true;
			return;
		}
		
		checkPlayerInterrogate();
		
		if (fleet.getCurrentAssignment() == null) {
			pickNext();
		}
	}
	
	/**
	 * When having defend vs. player assignments, special task groups will try to interrogate player if seen with transponder off.
	 * @return
	 */
	protected boolean checkPlayerInterrogate() {
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
		if (mem.contains(MemFlags.FLEET_BUSY)) return false;
		if (mem.contains(MemFlags.MEMORY_KEY_PURSUE_PLAYER)) return false;
		
		SpecialForcesTask task = intel.routeAI.currentTask;
		if (task == null) return false;
		if (task.type != TaskType.DEFEND_VS_PLAYER) return false;
		
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		if (playerFleet == null) return false;
		
		/*
		FleetAssignmentDataAPI assign = fleet.getCurrentAssignment();
		if (fleet.getCurrentAssignment() != null) {
			if (assign.getAssignment() == FleetAssignment.INTERCEPT && assign.getTarget() == playerFleet)
				return false;
		}
		*/
		
		if (fleet.getContainingLocation() != playerFleet.getContainingLocation()) return false;
		if (!playerFleet.isVisibleToSensorsOf(fleet)) return false;
		
		log.info(fleet.getName() + " moving to interrogate player");
		
		Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_PURSUE_PLAYER, "nex_sfInterrogatePlayer", true, 2);
		Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET, "nex_sfInterrogatePlayer", true, 2);
		return true;
	}
	
	@Override
	protected void pickNext(boolean justSpawned) {
		RouteManager.RouteSegment current = route.getCurrent();
		if (current == null) return;
		
		//log.info(fleet.getName() + " picking next assignment, current: " + fleet.getCurrentAssignment());
		//NexUtils.printStackTrace(log, 10);
		
		// custom handling when pursuing a fleet		
		if (SpecialForcesRouteAI.ROUTE_PURSUIT_SEGMENT.equals(current.custom)) {
			SectorEntityToken target = current.to;
			if (fleet.getContainingLocation() == target.getContainingLocation() && target.isVisibleToSensorsOf(fleet)) 
			{
				//log.info(fleet.getName() + " receiving local assignment");
				addLocalAssignment(current, false);
			} else {
				//log.info(fleet.getName() + " receiving goto assignment");
				addGoToFleetLocationAssignment(current);
			}
			return;
		}
		
		super.pickNext(justSpawned);
	}
	
	/**
	 * Send the fleet to a token representing the latest position of the target fleet.
	 * Needed since pursuing fleets doesn't work properly if the pursuee isn't in same system,
	 * unless it was seen jumping. (see https://fractalsoftworks.com/forum/index.php?topic=5061.msg326888#msg326888)
	 * @param current
	 */
	protected void addGoToFleetLocationAssignment(RouteManager.RouteSegment current) {
		SectorEntityToken target = current.to;
		SectorEntityToken locToken = target.getContainingLocation().createToken(target.getLocation());
		fleet.addAssignment(FleetAssignment.DELIVER_CREW, locToken, 2, 
				StringHelper.getFleetAssignmentString("trailing", target.getName().toLowerCase()));
	}
	
	@Override
	protected void addLocalAssignment(RouteManager.RouteSegment current, boolean justSpawned) 
	{
		if (intel == null) {	// giveInitialAssignments() gets called in superclass constructor, so this may not have been set yet
			super.addLocalAssignment(current, justSpawned);
			return;
		}
		
		SpecialForcesTask task = intel.routeAI.currentTask;
		if (task == null) {
			super.addLocalAssignment(current, justSpawned);
			return;			
		}
		switch (task.type) {
			case REBUILD:
				fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, current.from,	
						current.daysMax - current.elapsed, getInSystemActionText(current),
						goNextScript(current));
				break;
			case DEFEND_RAID:
			case COUNTER_GROUND_BATTLE:
				fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, current.from,
						current.daysMax - current.elapsed, getInSystemActionText(current),
						goNextScript(current));
				break;
				
			case DEFEND_VS_PLAYER:
				if (fleet.getContainingLocation() == Global.getSector().getPlayerFleet().getContainingLocation()) 
				{
					fleet.addAssignment(FleetAssignment.FOLLOW, Global.getSector().getPlayerFleet(),
							current.daysMax - current.elapsed, getInSystemActionText(current),
							goNextScript(current));
					break;
				}
				// else fall through to next level
			case PATROL:
				fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, current.from,
						current.daysMax - current.elapsed, getInSystemActionText(current),
						goNextScript(current));
				break;
			case WAIT_ORBIT:
				fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, current.from, 999, new Script() {
					@Override
					public void run() {
						intel.sendUpdateIfPlayerHasIntel(SpecialForcesIntel.ARRIVED_UPDATE, false, false);
					}
				});
				fleet.addAssignment(current.from.hasTag("nex_player_location_token") ? 
						FleetAssignment.HOLD : FleetAssignment.ORBIT_PASSIVE, current.from,
						current.daysMax - current.elapsed, getInSystemActionText(current),
						goNextScript(current));
				break;
			case FOLLOW_PLAYER:
				// only one day, so it gets updated promptly if player changes location
				// also don't go to next route segment
				fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, current.to,
						1, getInSystemActionText(current));
				break;
			case ASSIST_RAID:
				fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, current.from,
						current.daysMax - current.elapsed, getInSystemActionText(current),
						goNextScript(current));
				break;
			default:
				super.addLocalAssignment(current, justSpawned);
				break;
		}
	}
	
	@Override
	protected void addStartingAssignment(RouteManager.RouteSegment current, boolean justSpawned) 
	{
		if (intel == null) {	// giveInitialAssignments() gets called in superclass constructor, so this may not have been set yet
			super.addStartingAssignment(current, justSpawned);
			return;
		}
		
		// fixes the issue where it idles if told to patrol own origin market
		if (intel.routeAI.currentTask != null && intel.routeAI.currentTask.type != TaskType.ASSEMBLE) 
		{
			addLocalAssignment(current, justSpawned);
			return;
		}
		super.addStartingAssignment(current, justSpawned);
	}
	
	@Override
	protected String getInSystemActionText(RouteManager.RouteSegment segment) 
	{
		if (intel == null) return super.getInSystemActionText(segment);
		SpecialForcesTask task = intel.routeAI.currentTask;
		if (task == null) return super.getInSystemActionText(segment);
		
		if (intel.route.getCurrent() != null && intel.route.getCurrent().custom == CUSTOM_DELAY_BEFORE_RAID) 
		{
			return super.getInSystemActionText(segment);
		}
		
		// "fake" travel action text
		// is this needed?
		/*
		LocationAPI dest = task.system;
		if (dest != fleet.getContainingLocation())
			return StringHelper.getFleetAssignmentString("travellingToStarSystem", dest.getName());
		*/
		
		switch (task.type) {
			case ASSEMBLE:
				return StringHelper.getFleetAssignmentString("orbiting", 
						task.getMarket().getName());
			case RAID:
			case ASSIST_RAID:
				return StringHelper.getFleetAssignmentString("attackingAroundStarSystem", 
						fleet.getContainingLocation().getNameWithLowercaseType());
			case DEFEND_RAID:
			case PATROL:
			case DEFEND_VS_PLAYER:
				return StringHelper.getFleetAssignmentString("patrollingStarSystem", 
						fleet.getContainingLocation().getNameWithLowercaseType());
			case REBUILD:
				return StringHelper.getFleetAssignmentString("reconstituting", 
						task.getMarket().getName());
			case WAIT_ORBIT:
				if (task.getEntity().hasTag("nex_player_location_token")) {
					return StringHelper.getFleetAssignmentString("holding", null);
				}
				return StringHelper.getFleetAssignmentString("orbiting", 
						task.getEntity().getName());
			case FOLLOW_PLAYER:
				return StringHelper.getFleetAssignmentString("following", 
						task.getEntity().getName());
			default:
				return super.getInSystemActionText(segment);
		}
	}
}
