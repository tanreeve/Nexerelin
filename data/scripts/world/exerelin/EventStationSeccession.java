package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.awt.*;

public class EventStationSeccession extends EventBase
{

	public EventStationSeccession()
	{
		setType(this.getClass().getName());
	}

	public void makeStationSecedeToOutSystemFaction(StarSystemAPI starSystemAPI)
	{
		if(ExerelinData.getInstance().systemManager.stationManager.getNumFactionsInSystem() >= ExerelinData.getInstance().systemManager.maxFactionsInExerelin)
		{
			System.out.println(ExerelinData.getInstance().systemManager.stationManager.getNumFactionsInSystem() + " of " + ExerelinData.getInstance().systemManager.maxFactionsInExerelin + " already in system.");
			return;
		}

		String[] factions = ExerelinData.getInstance().getAvailableFactions(Global.getSector());
		String[] factionsInSystem = ExerelinUtils.getFactionsInSystem(starSystemAPI);
		int attempts = 0;
		String factionId = "";
		while((factionId.equalsIgnoreCase("")) && attempts < 20)
		{
			attempts = attempts + 1;
			factionId = factions[ExerelinUtils.getRandomInRange(0, factions.length - 1)];

			Boolean inSystem = false;
			for(int j = 0; j < factionsInSystem.length; j = j + 1)
			{
				if(factionId.equalsIgnoreCase(factionsInSystem[j]))
				{
					inSystem = true;
					break;
				}
			}
			if(inSystem)
				factionId = "";
		}

		if(factionId.equalsIgnoreCase(""))
			return; // No faction has 0 stations in system

		StationRecord[] stations = ExerelinData.getInstance().systemManager.stationManager.getStationRecords();
		attempts = 0;
		StationRecord station = null;
		while(station == null & attempts < 20)
		{
			attempts = attempts + 1;
			station = stations[ExerelinUtils.getRandomInRange(0, stations.length - 1)];
			if(station.getOwner() == null
					|| !station.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().systemManager.stationManager.getFactionLeader())
					|| ExerelinData.getInstance().systemManager.stationManager.getNumStationsOwnedByFaction(ExerelinData.getInstance().systemManager.stationManager.getFactionLeader()) <= 1)
				station = null;
		}

		if(station != null)
		{
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				Global.getSector().addMessage(station.getStationToken().getFullName() + " has secceded to " + factionId + "!", Color.MAGENTA);
			else
				Global.getSector().addMessage(station.getStationToken().getFullName() + " has secceded to " + factionId + "!");

			System.out.println("EVENT : Station secession at " + station.getStationToken().getFullName() + " to " + factionId + "(out system)");

			ExerelinData.getInstance().systemManager.diplomacyManager.declarePeaceWithAllFactions(factionId);
			ExerelinData.getInstance().systemManager.diplomacyManager.createWarIfNoneExists(factionId);
			station.setOwner(factionId,  false, false);

			station.getStationToken().getCargo().clear();
			station.getStationToken().getCargo().addCrew(CargoAPI.CrewXPLevel.REGULAR, 1600);
			station.getStationToken().getCargo().addMarines(800);
			station.getStationToken().getCargo().addFuel(1600);
			station.getStationToken().getCargo().addSupplies(6400);

			station.setEfficiency(3);
		}
	}
}
