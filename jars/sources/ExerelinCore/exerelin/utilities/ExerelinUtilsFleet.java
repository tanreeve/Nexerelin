package exerelin.utilities;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.loading.FleetCompositionDoctrineAPI;
import data.scripts.campaign.SSP_FleetFactory;
import data.scripts.variants.SSP_VariantRandomizer;
import data.scripts.campaign.fleets.SSP_FleetInjector;
import data.scripts.campaign.fleets.SSP_FleetInjector.CommanderType;
import data.scripts.campaign.fleets.SSP_FleetInjector.CrewType;
import data.scripts.campaign.fleets.SSP_FleetInjector.FleetStyle;
import static data.scripts.campaign.fleets.SSP_FleetInjector.getArchetypeWeights;
import static data.scripts.campaign.fleets.SSP_FleetInjector.randomizeVariants;
import org.apache.log4j.Logger;


public class ExerelinUtilsFleet
{
    public static Logger log = Global.getLogger(ExerelinUtilsFleet.class);
   
    /**
     * Used by Starsector Plus to create its custom fleets
     * @param fleet
     * @param market
     * @param stability
     * @param qualityFactor
     * @param type 
     */
    public static void injectFleet(CampaignFleetAPI fleet, MarketAPI market, Float stability, Float qualityFactor, String type) {      
        String factionId = fleet.getFaction().getId();
                
        //log.info("Fleet " + fleet.getName() + ": stability " + stability + "; qf " + qualityFactor);
        //float qualityFactorOriginal = qualityFactor;
        //qualityFactor = Math.max(qualityFactor, 0.7f);
        
        SSP_VariantRandomizer.Archetype theme = SSP_FleetInjector.pickTheme(factionId);
        SSP_FleetInjector.setThemeName(fleet, theme);
        
        switch (type)
        {
            case "exerelinInvasionFleet":
            case "exerelinRespawnFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(FleetStyle.ELITE, factionId), CommanderType.ELITE);
                SSP_FleetInjector.levelFleet(fleet, CrewType.MILITARY, factionId, CommanderType.ELITE);
                break;
            case "exerelinInvasionSupportFleet":
            case "exerelinDefenceFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(FleetStyle.MILITARY, factionId), CommanderType.MILITARY);
                SSP_FleetInjector.levelFleet(fleet, CrewType.MILITARY, factionId, CommanderType.MILITARY);
                break;
            case "exerelinResponseFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(FleetStyle.MILITARY, factionId), CommanderType.MILITARY);
                SSP_FleetInjector.levelFleet(fleet, CrewType.MILITARY, factionId, CommanderType.ELITE);
                break;  
            case "exerelinMiningFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(FleetStyle.CIVILIAN, factionId), CommanderType.CIVILIAN);
                SSP_FleetInjector.levelFleet(fleet, CrewType.CIVILIAN, factionId, CommanderType.CIVILIAN);
                break;
            default:    // fallback taken from SS+
                randomizeVariants(fleet, factionId, qualityFactor, null, getArchetypeWeights(FleetStyle.STANDARD, factionId), CommanderType.STANDARD);
                SSP_FleetInjector.levelFleet(fleet, CrewType.STANDARD, factionId, CommanderType.STANDARD);
        }
        SSP_FleetFactory.finishFleetNonIntrusive(fleet, factionId);
    }
    
    public static FleetMemberAPI addMiningShipToFleet(CampaignFleetAPI fleet)
    {
        String variantId = "mining_drone_wing";
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId());
        if (config != null && config.miningVariantsOrWings != null && !config.miningVariantsOrWings.isEmpty()) 
            variantId = (String) ExerelinUtils.getRandomListElement(config.miningVariantsOrWings);
        FleetMemberType type = FleetMemberType.SHIP;
        if (variantId.contains("_wing")) type = FleetMemberType.FIGHTER_WING;
        FleetMemberAPI miner = Global.getFactory().createFleetMember(type, variantId);
        fleet.getFleetData().addFleetMember(miner);
        return miner;
    }
	
	/**
	 * Makes a fleet where larger fleets prefer big ships over small ones (taken from SS+)
	 * @param faction
	 * @param params
	 * @return 
	 */
	public static CampaignFleetAPI createFleetWithSSPDoctrineHax(FactionAPI faction, FleetParams params) {
		if (!ExerelinUtils.isSSPInstalled()) {
			return FleetFactoryV2.createFleet(params);
		}

		FleetCompositionDoctrineAPI doctrine = faction.getCompositionDoctrine();
		float preInterceptors = doctrine.getInterceptors();
		float preFighters = doctrine.getFighters();
		float preBombers = doctrine.getBombers();
		float preSmall = doctrine.getSmall();
		float preFast = doctrine.getFast();
		float preMedium = doctrine.getMedium();
		float preLarge = doctrine.getLarge();
		float preCapital = doctrine.getCapital();
		float preSmallCarrierProbability = doctrine.getSmallCarrierProbability();
		float preMediumCarrierProbability = doctrine.getMediumCarrierProbability();
		float preLargeCarrierProbability = doctrine.getLargeCarrierProbability();

		float total = params.combatPts + params.tankerPts + params.freighterPts;
		if (total > 25 && total <= 50) {
			doctrine.setInterceptors(preInterceptors * 0.5f);
			doctrine.setFighters(preFighters * 0.5f);
			doctrine.setBombers(preBombers * 0.5f);
			doctrine.setSmall(preSmall * 0.5f);
			doctrine.setFast(preFast * 0.5f);
			doctrine.setMedium(preMedium);
			doctrine.setLarge(preLarge * 1.25f);
			doctrine.setCapital(preCapital * 1.5f);
			doctrine.setSmallCarrierProbability(preSmallCarrierProbability * 0.8f);
			doctrine.setMediumCarrierProbability(preMediumCarrierProbability * 0.9f);
			doctrine.setLargeCarrierProbability(preLargeCarrierProbability);
		} else if (total > 50) {
			doctrine.setInterceptors(preInterceptors * 0.25f);
			doctrine.setFighters(preFighters * 0.25f);
			doctrine.setBombers(preBombers * 0.25f);
			doctrine.setSmall(preSmall * 0.25f);
			doctrine.setFast(preFast * 0.25f);
			doctrine.setMedium(preMedium * 0.75f);
			doctrine.setLarge(preLarge);
			doctrine.setCapital(preCapital * 1.25f);
			doctrine.setSmallCarrierProbability(preSmallCarrierProbability * 0.5f);
			doctrine.setMediumCarrierProbability(preMediumCarrierProbability * 0.65f);
			doctrine.setLargeCarrierProbability(preLargeCarrierProbability * 0.8f);
		}
		CampaignFleetAPI fleet = FleetFactoryV2.createFleet(params);

		doctrine.setInterceptors(preInterceptors);
		doctrine.setFighters(preFighters);
		doctrine.setBombers(preBombers);
		doctrine.setSmall(preSmall);
		doctrine.setFast(preFast);
		doctrine.setMedium(preMedium);
		doctrine.setLarge(preLarge);
		doctrine.setCapital(preCapital);
		doctrine.setSmallCarrierProbability(preSmallCarrierProbability);
		doctrine.setMediumCarrierProbability(preMediumCarrierProbability);
		doctrine.setLargeCarrierProbability(preLargeCarrierProbability);
		
		return fleet;
	}

    public static void sortByFleetCost(CampaignFleetAPI fleet)
    {
        // local reference to be sorted
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        // Remove all members from the fleet
        for(FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().removeFleetMember(member);

        // Sort descending by fleet cost so that more expensive ships are first
        Collections.sort(initialFleetMembers, new Comparator<FleetMemberAPI>() {
            @Override
            public int compare(FleetMemberAPI o1, FleetMemberAPI o2) {
                return Float.compare(o2.getFleetPointCost(), o1.getFleetPointCost());
            }
        });

        // Re-add members to fleet from sorted list
        for (FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().addFleetMember(member);
    }

    public static void sortByHullSize(CampaignFleetAPI fleet)
    {
        // local reference to be sorted
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        // Remove all members from the fleet
        for(FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().removeFleetMember(member);

        // Sort descending by hull size so that larger hulls are first
        Collections.sort(initialFleetMembers, new Comparator<FleetMemberAPI>() {
            @Override
            public int compare(FleetMemberAPI o1, FleetMemberAPI o2) {
                return o2.getHullSpec().getHullSize().compareTo(o1.getHullSpec().getHullSize());
            }
        });

        // Re-add members to fleet from sorted list
        for (FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().addFleetMember(member);
    }
    
    public static float getDaysToOrbit(CampaignFleetAPI fleet)
    {
        float daysToOrbit = 0.0F;
        if (fleet.getFleetPoints() <= 50.0F) {
            daysToOrbit = 2.0F;
        } else if (fleet.getFleetPoints() <= 100.0F) {
            daysToOrbit = 4.0F;
        } else if (fleet.getFleetPoints() <= 150.0F) {
            daysToOrbit = 6.0F;
        } else {
            daysToOrbit = 8.0F;
        }
        daysToOrbit *= (0.5F + (float)Math.random() * 0.5F);
        return daysToOrbit;
    }
}
