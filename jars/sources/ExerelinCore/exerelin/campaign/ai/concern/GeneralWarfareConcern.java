package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.utilities.NexConfig;
import lombok.Getter;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GeneralWarfareConcern extends BaseStrategicConcern {

    public static final float BASE_PRIORITY = SAIConstants.MIN_CONCERN_PRIORITY_TO_ACT - 10;
    public static final float PRIORITY_PER_DAY = 0.2f;

    @Getter protected Set<String> hostileFactions = new HashSet<>();
    @Getter protected float priorityFromTime = 0;

    @Override
    public boolean generate() {
        if (!getExistingConcernsOfSameType().isEmpty()) return false;
        update();

        return !hostileFactions.isEmpty();
    }

    @Override
    public void update() {
        hostileFactions.clear();
        hostileFactions.addAll(DiplomacyManager.getFactionsAtWarWithFaction(ai.getFactionId(), NexConfig.allowPirateInvasions, true, false));
        if (hostileFactions.isEmpty()) return;

        priority.modifyFlat("base", BASE_PRIORITY, StrategicAI.getString("statBase", true));

        if (isAwaitingAction()) {
            float days = ai.getDaysSinceLastUpdate();
            float strengthRatio = 1;
            DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(ai.getFactionId());
            if (brain != null) {
                float them = brain.getEnemyStrength();
                float us = brain.getOurStrength();
                if (us < 1) us = 1;
                strengthRatio = them/us;
                if (strengthRatio > 2) strengthRatio = 2;

                float priorityThisUpdate = PRIORITY_PER_DAY * days * strengthRatio;
                Global.getLogger(this.getClass()).info(String.format("Priority increment this update: %.1f", priorityThisUpdate));
                priorityFromTime += priorityThisUpdate;
                priority.modifyFlat("priorityFromTime", priorityFromTime, StrategicAI.getString("statPriorityOverTime", true));
            }
        }

        super.update();
    }

    @Override
    public List<FactionAPI> getFactions() {
        List<FactionAPI> factions = new ArrayList<>();
        for (String factionId : hostileFactions) {
            factions.add(Global.getSector().getFaction(factionId));
        }

        return factions;
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        String str = getDef().desc;
        Color hl = Misc.getHighlightColor();
        return tooltip.addPara(str, pad, hl, hostileFactions.size() + "");
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        if (otherConcern instanceof GeneralWarfareConcern) {
            return true;
        }
        return false;
    }

    @Override
    public boolean isValid() {
        return !hostileFactions.isEmpty();
    }

    @Override
    public void notifyActionUpdate(StrategicAction action, StrategicActionDelegate.ActionStatus newStatus) {
        super.notifyActionUpdate(action, newStatus);
        if (newStatus == StrategicActionDelegate.ActionStatus.STARTING) {
            Global.getLogger(this.getClass()).info("Lowering priority by " + action.getDef().cooldown + " based on action cooldown");
            priorityFromTime -= action.getDef().cooldown * 2;
            if (priorityFromTime < 0) priorityFromTime = 0;
            priority.modifyFlat("priorityFromTime", priorityFromTime, StrategicAI.getString("statPriorityOverTime", true));
        }
    }
}