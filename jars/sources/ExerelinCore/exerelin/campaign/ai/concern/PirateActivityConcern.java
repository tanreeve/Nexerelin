package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivity;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.bases.VayraRaiderActivityCondition;
import exerelin.campaign.ai.StrategicAI;
import lombok.Getter;

import java.awt.*;
import java.util.*;

public class PirateActivityConcern extends BaseStrategicConcern {

    @Getter protected float rage = 0;
    Set<RageEntry> affectedMarkets = new HashSet<>();

    @Override
    public boolean generate() {
        if (!getExistingConcernsOfSameType().isEmpty()) return false;

        update();

        return !affectedMarkets.isEmpty();
    }

    @Override
    public void update() {
        float rageIncrement = Global.getSettings().getFloat("nex_pirateRageIncrement");
        float rageThisUpdate = 0;
        affectedMarkets.clear();

        for (MarketAPI market : Misc.getFactionMarkets(ai.getFaction())) {

            String factionId = market.getFactionId();

            if (market.hasCondition(Conditions.PIRATE_ACTIVITY) && market.getFaction().isHostileTo(Factions.PIRATES)) {
                MarketConditionAPI cond = market.getCondition(Conditions.PIRATE_ACTIVITY);
                PirateActivity plugin = (PirateActivity)cond.getPlugin();

                float thisRage = plugin.getIntel().getStabilityPenalty();
                if (thisRage > 0) {
                    thisRage *= rageIncrement;
                    rageThisUpdate += thisRage;
                }
                affectedMarkets.add(new RageEntry(market, cond.getIdForPluginModifications(), thisRage));
            }
            if (market.hasCondition(Conditions.PATHER_CELLS) && market.getFaction().isHostileTo(Factions.LUDDIC_PATH)) {
                MarketConditionAPI cond = market.getCondition(Conditions.PATHER_CELLS);
                LuddicPathCells cellCond = (LuddicPathCells)(cond.getPlugin());
                LuddicPathCellsIntel cellIntel = cellCond.getIntel();
                LuddicPathBaseIntel base;

                boolean sleeper = cellIntel.getSleeperTimeout() > 0;
                if (sleeper) continue;
                base = LuddicPathCellsIntel.getClosestBase(market);
                if (base == null) continue;

                float thisRage = 1 * rageIncrement;
                rageThisUpdate += thisRage;
                affectedMarkets.add(new RageEntry(market, cond.getIdForPluginModifications(), thisRage));
            }

            if (market.hasCondition("vayra_raider_activity")) {
                MarketConditionAPI cond = market.getCondition("vayra_raider_activity");
                VayraRaiderActivityCondition vrCond = (VayraRaiderActivityCondition)(cond.getPlugin());
                if (!vrCond.getIntel().getMarket().getFaction().isHostileTo(ai.getFaction()))
                    continue;

                float thisRage = vrCond.getIntel().getStabilityPenalty();
                if (thisRage > 0) {
                    thisRage *= rageIncrement;
                    rageThisUpdate += thisRage;
                }
                affectedMarkets.add(new RageEntry(market, cond.getIdForPluginModifications(), thisRage));
            }
        }
        Global.getLogger(this.getClass()).info("Rage this update: " + rageThisUpdate);
        rage += rageThisUpdate;
        priority.modifyFlat("rage", rage, StrategicAI.getString("statRage", true));
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        String str = getDef().desc;
        Color hl = Misc.getHighlightColor();
        return tooltip.addPara(str, pad, hl, affectedMarkets.size() + "");
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof PirateActivityConcern) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        return !affectedMarkets.isEmpty();
    }

    public static class RageEntry {
        public MarketAPI market;
        public String conditionId;
        public float effect;

        public RageEntry(MarketAPI market, String conditionId, float effect) {
            this.market = market;
            this.conditionId = conditionId;
            this.effect = effect;
        }
    }
}