package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ai.*;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.*;
import java.util.List;

@Log4j
public abstract class BaseStrategicConcern implements StrategicConcern {

    @Getter @Setter protected String id;
    protected StrategicAI ai;
    protected StrategicAIModule module;
    @Getter protected MutableStat priority = new MutableStat(0);
    @Getter protected MarketAPI market;
    protected FactionAPI faction;
    @Getter protected StrategicAction currentAction;
    @Getter protected boolean ended;
    @Getter @Setter protected float actionCooldown;

    @Override
    public void setAI (StrategicAI ai, StrategicAIModule module) {
        this.ai = ai;
        this.module = module;
    }
    @Override
    public StrategicAI getAI() {
        return ai;
    }

    @Override
    public abstract boolean generate();

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        return false;
    }

    @Override
    public Set getExistingConcernItems() {
        return new HashSet();
    }

    @Override
    public void update() {}

    @Override
    public void reapplyPriorityModifiers() {
        StrategicDefManager.StrategicConcernDef def = getDef();
        if (def.hasTag(SAIConstants.TAG_MILITARY)) {
            SAIUtils.applyPriorityModifierForAlignment(ai.getFactionId(), priority, Alignment.MILITARIST);
        }
        if (def.hasTag(SAIConstants.TAG_DIPLOMACY)) {
            //log.info("Applying diplomacy modifier for concern " + this.getName());
            SAIUtils.applyPriorityModifierForAlignment(ai.getFactionId(), priority, Alignment.DIPLOMATIC);

            if (faction != null) {
                Boolean wantPositive = null;
                if (def.hasTag("diplomacy_positive")) wantPositive = true;
                else if (def.hasTag("diplomacy_negative")) wantPositive = false;

                if (wantPositive != null) SAIUtils.applyPriorityModifierForDisposition(ai.getFactionId(), wantPositive, priority);
            }
        }
        if (def.hasTag(SAIConstants.TAG_ECONOMY)) {
            SAIUtils.applyPriorityModifierForTrait(ai.getFactionId(), priority, DiplomacyTraits.TraitIds.MONOPOLIST, 1.4f, false);
        }
        if (def.hasTag(SAIConstants.TAG_COVERT)) {
            SAIUtils.applyPriorityModifierForTrait(ai.getFactionId(), priority, DiplomacyTraits.TraitIds.DEVIOUS, 1.4f, false);
        }
    }

    @Override
    public CustomPanelAPI createPanel(final CustomPanelAPI holder) {
        final float pad = 3;
        final float opad = 10;

        CustomPanelAPI myPanel = holder.createCustomPanel(SAIConstants.CONCERN_ITEM_WIDTH, SAIConstants.CONCERN_ITEM_HEIGHT,
                new FramedCustomPanelPlugin(0.25f, ai.getFaction().getBaseUIColor(), true));

        TooltipMakerAPI tooltip = myPanel.createUIElement(SAIConstants.CONCERN_ITEM_WIDTH, SAIConstants.CONCERN_ITEM_HEIGHT, true);
        TooltipMakerAPI iwt = tooltip.beginImageWithText(this.getIcon(), 32);

        iwt.addPara(getName(), Misc.getHighlightColor(), 0);

        //createTooltipDesc(iwt, holder, 3);

        int prioVal = (int)getPriorityFloat();
        String prio = String.format(StringHelper.getString("priority", true) + ": %s", prioVal);
        iwt.addPara(prio, 0, Misc.getHighlightColor(), prioVal + "");
        tooltip.addImageWithText(pad);
        tooltip.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                createTooltipDesc(tooltip, holder, pad);
                tooltip.addPara(StringHelper.getString("priority", true), opad);
                tooltip.addStatModGrid(360, 60, 10, 0, priority,
                        true, NexUtils.getStatModValueGetter(true, 0));
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);

        if (this.getCurrentAction() != null) {

        } else {

        }

        myPanel.addUIElement(tooltip).inTL(0, 0);
        return myPanel;
    }

    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        String str = getDef().desc;
        String marketName = "";
        Color hl = Misc.getHighlightColor();
        if (market != null) {
            marketName = getMarket().getName();
            str = StringHelper.substituteToken(str, "$market", marketName);
            hl = market.getFaction().getBaseUIColor();
        }

        return tooltip.addPara(str, pad, hl, marketName);
    }

    @Override
    public List<StrategicAction> generateActions() {
        return null;
    }

    @Override
    public StrategicAction pickAction() {
        StrategicAction bestAction = null;
        float bestPrio = 0;

        for (StrategicDefManager.StrategicActionDef possibleActionDef : module.getRelevantActionDefs(this)) {
            StrategicAction action = StrategicDefManager.instantiateAction(possibleActionDef);
            if (faction != null && !faction.isHostileTo(ai.getFaction()) && action.getDef().hasTag("wartime")) {
                continue;
            }
            if (!action.canUseForConcern(this)) continue;
            if (!action.isValid()) continue;
            action.updatePriority();
            float priority = action.getPriorityFloat();
            if (priority < SAIConstants.MIN_ACTION_PRIORITY_TO_USE) continue;

            if (priority > bestPrio) {
                bestAction = action;
                bestPrio = priority;
            } else continue;
        }

        return bestAction;
    }

    @Override
    public boolean initAction(StrategicAction action) {
        boolean success = action.generate();
        if (!success) return false;
        this.currentAction = action;
        action.init();
        notifyActionUpdate(action, StrategicActionDelegate.ActionStatus.STARTING);
        return true;
    }

    @Override
    public void notifyActionUpdate(StrategicAction action, StrategicActionDelegate.ActionStatus newStatus) {
        if (newStatus == StrategicActionDelegate.ActionStatus.SUCCESS || newStatus == StrategicActionDelegate.ActionStatus.FAILURE) {
            actionCooldown += action.getDef().cooldown;
        }
        else if (newStatus == StrategicActionDelegate.ActionStatus.STARTING) {
            ai.getExecModule().reportRecentAction(action);
        }
    };

    @Override
    public boolean canTakeAction(StrategicAction action) {
        return true;    // usually handled by the action's code
    }

    @Override
    public void advance(float days) {
        if (ended) return;
        if (currentAction != null) currentAction.advance(days);
        actionCooldown -= days;
        if (actionCooldown < 0) actionCooldown = 0;
    }

    @Override
    public void end() {
        ended = true;
        // Notify module? or maybe just let it remove on its own in advance();
    }

    public float getPriorityFloat() {
        if (priority == null) return 0;
        return priority.getModifiedValue();
    }

    @Override
    public String getIcon() {
        String icon = getDef().icon;
        if (icon == null || icon.isEmpty()) {
            if (market != null) icon = market.getFaction().getCrest();
            else if (faction != null) icon = faction.getCrest();
            else icon = ai.getFaction().getCrest();
        }

        return icon;
    }

    @Override
    public FactionAPI getFaction() {
        if (faction != null) return faction;
        if (market != null) return market.getFaction();
        return ai.getFaction();
    }

    @Override
    public List<FactionAPI> getFactions() {
        return new ArrayList<>(Arrays.asList(new FactionAPI[] {getFaction()}));
    }

    @Override
    public String getName() {
        String name = getDef().name;
        if (ended) name = "[FIXME ended] " + name;
        return name;
    }

    @Override
    public String getDesc() {
        return getDef().desc;
    }

    @Override
    public StrategicDefManager.StrategicConcernDef getDef() {
        return StrategicDefManager.getConcernDef(getId());
    }

    public List<StrategicConcern> getExistingConcernsOfSameType() {
        List<StrategicConcern> results = new ArrayList<>();
        for (StrategicConcern concern : module.getCurrentConcerns()) {
            if (concern.isEnded()) continue;
            if (concern.getClass() == this.getClass()) {
                results.add(concern);
            }
        }
        return results;
    }

    protected float getMarketValue(MarketAPI market) {
        float value = NexUtilsMarket.getMarketIndustryValue(market);
        for (Industry ind : market.getIndustries()) {
            if (ind.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) {
                value += ind.getBuildCost();
            }
        }
        value += NexUtilsMarket.getIncomeNetPresentValue(market, 6, 0.02f);

        return value;
    }

    public boolean isAwaitingAction() {
        if (actionCooldown > 0) return false;
        return currentAction == null || currentAction.isEnded();
    }

    @Override
    public String toString() {
        return getName();
    }

    public static float getSpaceDefenseValue(MarketAPI market) {
        MilitaryInfoHelper helper = MilitaryInfoHelper.getInstance();
        MilitaryInfoHelper.PatrolStrengthEntry patrolStr = helper.getPatrolStrength(market.getContainingLocation());
        Float space = 0f;
        if (patrolStr != null && patrolStr.strByFaction.containsKey(market.getFaction().getId())) {
            space += patrolStr.strByFaction.get(market.getFaction().getId());
        }
        CampaignFleetAPI station = Misc.getStationFleet(market);
        float stationStr = 0;
        if (station != null) {
            stationStr = NexUtilsFleet.getFleetStrength(station, true, true, true);
        }
        space += stationStr;
        return space;
    }

    public static float getGroundDefenseValue(MarketAPI market) {
        return market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0);
    }
}
