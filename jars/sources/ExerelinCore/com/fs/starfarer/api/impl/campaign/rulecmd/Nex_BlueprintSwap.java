package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;

// TODO:
public class Nex_BlueprintSwap extends PaginatedOptions {
	
	public static final String POINTS_KEY = "$nex_BPSwapPoints";
	public static final String STOCK_ARRAY_KEY = "$nex_BPSwapStock";
	public static final float STOCK_KEEP_DAYS = 30;
	public static final int STOCK_COUNT_MIN = 6;
	public static final int STOCK_COUNT_MAX = 8;
	public static final float PRICE_POINT_MULT = 0.01f;
	
	public static final String DIALOG_OPTION_PREFIX = "nex_blueprintSwap_pick_";
	
	public static Logger log = Global.getLogger(Nex_BlueprintSwap.class);
	
	protected static PurchaseInfo toPurchase = null;
	
	// Things that count as blueprints for trade-in
	public static final Set<String> ALLOWED_IDS = new HashSet<>(Arrays.asList(new String[] {
		Items.SHIP_BP, Items.WEAPON_BP, Items.FIGHTER_BP, "industry_bp",
		"tiandong_retrofit_bp", "tiandong_retrofit_fighter_bp"
	}));
	
	protected CampaignFleetAPI playerFleet;
	protected SectorEntityToken entity;
	protected MarketAPI market;
	protected FactionAPI playerFaction;
	protected FactionAPI entityFaction;
	protected TextPanelAPI text;
	protected CargoAPI playerCargo;
	protected PersonAPI person;
	protected FactionAPI faction;
	protected float points;
	protected List<String> disabledOpts = new ArrayList<>();
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String arg = params.get(0).getString(memoryMap);
		setupVars(dialog, memoryMap);
		
		switch (arg)
		{
			case "init":
				break;
			case "hasOption":
				return hasPrism(entity.getMarket());
			case "getForSale":
				setupDelegateDialog(dialog);
				addBlueprintOptions();
				showOptions();
				break;
			case "sell":
				selectBPs();
				break;
			case "buy":
				int index = Integer.parseInt(memoryMap.get(MemKeys.LOCAL).getString("$option").substring(DIALOG_OPTION_PREFIX.length()));
				showBlueprintInfoAndPreparePurchase(index, dialog.getTextPanel());
				break;
			case "confirmPurchase":
				purchase();
				break;
		}
		
		return true;
	}
	
	/**
	 * To be called only when paginated dialog options are required. 
	 * Otherwise we get nested dialogs that take multiple clicks of the exit option to actually exit.
	 * @param dialog
	 */
	protected void setupDelegateDialog(InteractionDialogAPI dialog)
	{
		originalPlugin = dialog.getPlugin();  

		dialog.setPlugin(this);  
		init(dialog);
	}
	
	protected void setupVars(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap)
	{
		this.dialog = dialog;  
		this.memoryMap = memoryMap;
		
		entity = dialog.getInteractionTarget();
		market = entity.getMarket();
		text = dialog.getTextPanel();
		
		playerFleet = Global.getSector().getPlayerFleet();
		playerCargo = playerFleet.getCargo();
		
		playerFaction = Global.getSector().getPlayerFaction();
		entityFaction = entity.getFaction();
		
		person = dialog.getInteractionTarget().getActivePerson();
		faction = person.getFaction();
		
		updatePointsInMemory(getPoints());
	}
	
	/**
	 * Updates available blueprint points in local memory.
	 * @param newPoints
	 */
	protected void updatePointsInMemory(float newPoints)
	{
		points = newPoints;
		memoryMap.get(MemKeys.LOCAL).set("$nex_BPSwap_points", points, 0);
		memoryMap.get(MemKeys.LOCAL).set("$nex_BPSwap_pointsStr", (int)points + "", 0);
	}
	
	@Override
	public void showOptions() {
		super.showOptions();
		for (String optId : disabledOpts)
		{
			dialog.getOptionPanel().setEnabled(optId, false);
		}
		dialog.getOptionPanel().setShortcut("nex_blueprintSwapMenuReturn", Keyboard.KEY_ESCAPE, false, false, false, false);
	}
	
	protected void selectBPs() {
		CargoAPI copy = Global.getFactory().createCargo(false);
		//copy.addAll(cargo);
		for (CargoStackAPI stack : playerCargo.getStacksCopy()) {
			if (isBlueprints(stack))
				copy.addFromStack(stack);
		}
		copy.sort();
		
		final float width = 310f;
		// prevents an IllegalAccessError
		final InteractionDialogAPI dialog = this.dialog;
		final Map<String, MemoryAPI> memoryMap = this.memoryMap; 
		
		dialog.showCargoPickerDialog(StringHelper.getString("exerelin_misc", "blueprintSwapSelect"), 
				Misc.ucFirst(StringHelper.getString("confirm")), 
				Misc.ucFirst(StringHelper.getString("cancel")),
						true, width, copy, new CargoPickerListener() {
			public void pickedCargo(CargoAPI cargo) {
				cargo.sort();
				for (CargoStackAPI stack : cargo.getStacksCopy()) {
					playerCargo.removeItems(stack.getType(), stack.getData(), stack.getSize());
					if (stack.isCommodityStack()) { // should be always, but just in case
						AddRemoveCommodity.addCommodityLossText(stack.getCommodityId(), (int) stack.getSize(), text);
					}
				}
				
				int points = (int)getPointValue(cargo);
				
				if (points > 0) {
					float newPoints = addPoints(points);
					text.setFontSmallInsignia();
					String str = StringHelper.getStringAndSubstituteToken("exerelin_misc", 
							"blueprintSwapGainedPoints", "$points", (int)points + "");
					text.addPara(str, Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), (int)points + "");
					text.setFontInsignia();
					
					updatePointsInMemory(newPoints);
				}				
				
				FireBest.fire(null, dialog, memoryMap, "Nex_BlueprintsSold");
			}
			@Override
			public void cancelledCargoSelection() {
			}
			@Override
			public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp, boolean pickedUpFromSource, CargoAPI combined) {
			
				int points = (int)getPointValue(cargo);
				
				float pad = 3f;
				float small = 5f;
				float opad = 10f;

				panel.setParaOrbitronLarge();
				panel.addPara(Misc.ucFirst(faction.getDisplayName()), faction.getBaseUIColor(), opad);
				//panel.addPara(faction.getDisplayNameLong(), faction.getBaseUIColor(), opad);
				//panel.addPara(faction.getDisplayName() + " (" + entity.getMarket().getName() + ")", faction.getBaseUIColor(), opad);
				panel.setParaFontDefault();
				
				panel.addImage(faction.getLogo(), width * 1f, pad);
				
				
				//panel.setParaFontColor(Misc.getGrayColor());
				//panel.setParaSmallInsignia();
				//panel.setParaInsigniaLarge();
				String str = StringHelper.getStringAndSubstituteToken("exerelin_misc",
						"blueprintSwapMsg", "$points", (int)points + "");
				panel.addPara(str, 	opad * 1f, Misc.getHighlightColor(), (int)points + "");
			}
		});
	}
	
	/**
	 * Adds the dialog options to buy blueprints.
	 */
	protected void addBlueprintOptions()
	{
		dialog.getOptionPanel().clearOptions();
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		List<PurchaseInfo> bps = getBlueprintStock(mem);
		
		int index = 0;
		for (PurchaseInfo bp : bps)
		{
			addBlueprintOption(bp, index);
			index++;
		}
		
		addOptionAllPages(StringHelper.getString("back", true), "nex_blueprintSwapMenuReturn");
	}
	
	protected void addBlueprintOption(PurchaseInfo info, int index)
	{
		//CargoAPI temp = Global.getFactory().createCargo(true);
		String id = info.id;
		String name = info.name;
		float cost = info.cost;
		boolean alreadyHas = false;
		FactionAPI player = Global.getSector().getPlayerFaction();
		
		switch (info.type) {
			case SHIP:
				ShipHullSpecAPI hull = Global.getSettings().getHullSpec(id);
				alreadyHas = player.knowsShip(id);
				break;
			case FIGHTER:
				FighterWingSpecAPI wing = Global.getSettings().getFighterWingSpec(id);
				alreadyHas = player.knowsFighter(id);
				break;
			case WEAPON:
				WeaponSpecAPI wep = Global.getSettings().getWeaponSpec(id);
				alreadyHas = player.knowsWeapon(id);
				break;
			default:
				return;
		}
		
		cost *= ExerelinConfig.prismBlueprintPriceMult;
		cost = 5 * Math.round(cost/5f);
		
		String optId = DIALOG_OPTION_PREFIX + index;
		String str = StringHelper.getString("exerelin_misc", "blueprintSwapPurchaseOption");
		str = StringHelper.substituteToken(str, "$name", name);
		str = StringHelper.substituteToken(str, "$points", (int)cost + "");
		
		if (alreadyHas)
		{
			str = "[" + StringHelper.getString("exerelin_misc", "alreadyKnown") + "] " + str;
		}
		
		//log.info("Adding option: " + optId + ", " + str);
		addOption(str, optId);
		if (cost > points)
		{
			log.info("Item unavailable: " + optId);
			disabledOpts.add(optId);
		}
	}
	
	/**
	 * Prints the description of the selected blueprint and marks it as the desired purchase.
	 * @param index Index of the desired {@code BlueprintInfo} within the bluepritn info array
	 * @param text
	 */
	protected void showBlueprintInfoAndPreparePurchase(int index, TextPanelAPI text)
	{
		Description desc;
		toPurchase = getBlueprintStock(market.getMemoryWithoutUpdate()).get(index);
		switch (toPurchase.type) {
			case SHIP:
				ShipHullSpecAPI hull = Global.getSettings().getHullSpec(toPurchase.id);
				desc = Global.getSettings().getDescription(hull.getDescriptionId(), Description.Type.SHIP);
				break;
			case FIGHTER:
				FighterWingSpecAPI wing = Global.getSettings().getFighterWingSpec(toPurchase.id);
				desc = Global.getSettings().getDescription(wing.getVariant().getHullSpec().getDescriptionId(), Description.Type.SHIP);
				break;
			case WEAPON:
				WeaponSpecAPI wep = Global.getSettings().getWeaponSpec(toPurchase.id);
				desc = Global.getSettings().getDescription(wep.getWeaponId(), Description.Type.WEAPON);
				break;
			default:
				return;
		}
		
		text.setFontSmallInsignia();
		text.addPara(desc.getText1FirstPara());
		text.setFontInsignia();
	}
	
	protected void purchase()
	{
		playerFleet.getCargo().addSpecial(toPurchase.getItemData(), 1);
		float newPoints = addPoints(-toPurchase.cost);
		updatePointsInMemory(newPoints);
		
		text.setFontSmallInsignia();
		String str = StringHelper.getStringAndSubstituteToken("exerelin_misc", 
							"blueprintSwapPurchased", "$name", toPurchase.name);
		text.addPara(str, Misc.getPositiveHighlightColor(), Misc.getHighlightColor(), toPurchase.name);
		
		String costStr = (int)(toPurchase.cost) + "";
		str = StringHelper.getStringAndSubstituteToken("exerelin_misc", 
							"blueprintSwapLostPoints", "$points", costStr);
		text.addPara(str, Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), costStr);
		text.setFontInsignia();
		
		// remove purchased blueprint from array
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		List<PurchaseInfo> stock = getBlueprintStock(mem);
		stock.remove(toPurchase);
		setBlueprintStock(mem, stock, true);
	}
	
	
	public static List<PurchaseInfo> getBlueprintStock(MemoryAPI mem)
	{
		if (mem.contains(STOCK_ARRAY_KEY))
			return (List<PurchaseInfo>)mem.get(STOCK_ARRAY_KEY);
		
		List<PurchaseInfo> bps = generateBlueprintStock();
		setBlueprintStock(mem, bps, true);
		return bps;
	}
	
	public static void setBlueprintStock(MemoryAPI mem, List<PurchaseInfo> stock, boolean refreshTime)
	{
		float time = STOCK_KEEP_DAYS;
		if (!refreshTime && mem.contains(STOCK_ARRAY_KEY))
			time = mem.getExpire(STOCK_ARRAY_KEY);
		
		mem.set(STOCK_ARRAY_KEY, stock, time);
	}
	
	public static void unsetBlueprintStocks()
	{
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			market.getMemoryWithoutUpdate().unset(STOCK_ARRAY_KEY);
		}
	}
	
	public static List<PurchaseInfo> generateBlueprintStock()
	{
		List<PurchaseInfo> blueprints = new ArrayList<>();
		WeightedRandomPicker<PurchaseInfo> picker = new WeightedRandomPicker<>();
		FactionAPI playerFaction = Global.getSector().getPlayerFaction();
		Set<String> banned = PrismMarket.getRestrictedBlueprints();
		
		// hull.hasTag("tiandong_retrofit")
		for (ShipHullSpecAPI hull : Global.getSettings().getAllShipHullSpecs()) {
			if (!hull.hasTag("rare_bp") || hull.hasTag(Tags.NO_DROP)) 
				continue;
			String hullId = hull.getHullId();
			if (playerFaction.knowsShip(hullId) || banned.contains(hullId)) continue;
			
			PurchaseInfo info = new PurchaseInfo(hullId, PurchaseType.SHIP, 
					hull.getNameWithDesignationWithDashClass(), getBlueprintPointValue(Items.SHIP_BP, hull.getBaseValue()));
			if (hull.hasTag("tiandong_retrofit"))
				info.isTiandongRetrofit = true;
			
			picker.add(info, 3);
		}
		for (FighterWingSpecAPI wing : Global.getSettings().getAllFighterWingSpecs()) {
			if (!wing.hasTag("rare_bp") || wing.hasTag(Tags.NO_DROP))
				continue;
			String wingId = wing.getId();
			if (playerFaction.knowsWeapon(wingId) || banned.contains(wingId)) continue;
			
			PurchaseInfo info = new PurchaseInfo(wingId, PurchaseType.FIGHTER, 
					wing.getWingName(), getBlueprintPointValue(Items.FIGHTER_BP, wing.getBaseValue()));
			if (wing.hasTag("tiandong_retrofit"))
				info.isTiandongRetrofit = true;
			
			picker.add(info, 2);
		}
		for (WeaponSpecAPI wep : Global.getSettings().getAllWeaponSpecs()) {
			if (!wep.hasTag("rare_bp") || wep.hasTag(Tags.NO_DROP))
				continue;
			String weaponId = wep.getWeaponId();
			if (playerFaction.knowsWeapon(weaponId) || banned.contains(weaponId)) continue;
			
			PurchaseInfo info = new PurchaseInfo(weaponId, PurchaseType.WEAPON, 
					wep.getWeaponName(), getBlueprintPointValue(Items.WEAPON_BP, wep.getBaseValue()));
			picker.add(info, 2);
		}
		
		for (int i = 0; i < MathUtils.getRandomNumberInRange(STOCK_COUNT_MIN, STOCK_COUNT_MAX); i++)
		{
			if (picker.isEmpty()) continue;
			blueprints.add(picker.pickAndRemove());
		}
		Collections.sort(blueprints);
		return blueprints;
	}
	
	public static boolean isBlueprints(CargoStackAPI stack)
	{
		SpecialItemSpecAPI spec = stack.getSpecialItemSpecIfSpecial();
		if (spec == null) return false;
		String id = spec.getId();
		if (!spec.hasTag("package_bp") && !ALLOWED_IDS.contains(id))
			return false;
		
		return true;
	}
	
	public static float getPointValue(CargoAPI cargo)
	{
		float totalPoints = 0;
		for (CargoStackAPI stack : cargo.getStacksCopy()) {
			if (!isBlueprints(stack)) continue;
			
			float points = getPointValue(stack);
			
			totalPoints += points;
		}
		return totalPoints;
	}
	
	public static float getPointValue(CargoStackAPI stack)
	{
		SpecialItemSpecAPI spec = stack.getSpecialItemSpecIfSpecial();
		SpecialItemData data = stack.getSpecialDataIfSpecial();
		float points = 0, base = 0;
		
		switch (spec.getId())
		{
			case Items.SHIP_BP:
			case "tiandong_retrofit_bp":
				base = Global.getSettings().getHullSpec(data.getData()).getBaseValue();
				break;
			case Items.FIGHTER_BP:
			case "tiandong_retrofit_fighter_bp":
				base = Global.getSettings().getFighterWingSpec(data.getData()).getBaseValue();
				break;
			case Items.WEAPON_BP:
				base = Global.getSettings().getWeaponSpec(data.getData()).getBaseValue();
				break;
		}
		points = getBlueprintPointValue(spec.getId(), base);
		
		if (spec.hasTag("package_bp"))
			points = spec.getBasePrice() * PRICE_POINT_MULT * 5;
		
		return points;
	}
	
	/**
	 * Gets the point value of a blueprint based on its sale price.
	 * @param itemId e.g. "fighter_bp", Items.SHIP_BP
	 * @param baseCost Base cost of the hull, fighter wing or weapon
	 * @return
	 */
	public static float getBlueprintPointValue(String itemId, float baseCost)
	{
		SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(itemId);
		float cost = spec.getBasePrice() + baseCost * Global.getSettings().getFloat("blueprintPriceOriginalItemMult");
		if (spec.hasTag("tiandong_retrofit_bp"))
		{
			log.info(spec.getName() + " is retrofit, halving cost");
			cost *= 0.5f;
		}
		cost *= PRICE_POINT_MULT;
		
		// rounding
		cost = 5 * Math.round(cost/5f);
		
		return cost;
	}
	
	public static float addPoints(float points)
	{
		points += getPoints();
		Global.getSector().getPersistentData().put(POINTS_KEY, points);
		
		return points;
	}
	
	public static float getPoints()
	{
		Map<String, Object> data = Global.getSector().getPersistentData();
		if (!data.containsKey(POINTS_KEY))
			data.put(POINTS_KEY, 0f);
		
		return (float)data.get(POINTS_KEY);
	}
	
	public static boolean hasPrism(MarketAPI market)
	{
		// TODO: config for whether SCY prism has this
		return market.hasSubmarket("exerelin_prismMarket") || market.hasSubmarket("SCY_prismMarket");	// for now
	}
	
	public static class PurchaseInfo implements Comparable<PurchaseInfo>
	{
		String id;
		PurchaseType type;
		String name;
		float cost;
		boolean isTiandongRetrofit = false;
		
		public PurchaseInfo(String id, PurchaseType type, String name, float cost)
		{
			this.id = id;				
			this.type = type;
			this.name = name;
			this.cost = cost;
		}
		
		public String getItemId()
		{
			switch (type)
			{
				case SHIP:
					if (isTiandongRetrofit) return "tiandong_retrofit_bp";
					return Items.SHIP_BP;
				case FIGHTER:
					if (isTiandongRetrofit) return "tiandong_retrofit_fighter_bp";
					return Items.FIGHTER_BP;
				case WEAPON:
					return Items.WEAPON_BP;
			}
			return null;
		}
		
		public SpecialItemData getItemData()
		{
			return new SpecialItemData(getItemId(), id);
		}

		@Override
		public int compareTo(PurchaseInfo other) {
			// ships first, then fighters, then weapons
			if (type != other.type)	
				return type.compareTo(other.type);
			
			// descending cost order
			if (cost != other.cost) return Float.compare(other.cost, cost);
			
			return name.compareTo(other.name);
		}
	}
	
	public static enum PurchaseType {
		SHIP, FIGHTER, WEAPON
	}
}