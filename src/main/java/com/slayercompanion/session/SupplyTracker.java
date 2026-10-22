package com.slayercompanion.session;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.api.ItemID;

/**
 * Tracks what the player uses up during a task from snapshots of everything they carry (inventory + worn
 * equipment + rune pouch, summed per item id), so moving gear between worn and inventory nets out.
 * <p>
 * Rule: an item whose count goes down while no bank is open, after the task's first kill, was used.
 * Increases (loot, withdrawals) are ignored. Items named "Something(n)" form a dose family; usage there is
 * counted in doses, so Prayer potion(4) becoming (3) is one dose. Empty vials cost nothing.
 * <p>
 * Weapon charges (trident runes, blowpipe scales and darts, revenant ether, ...) are not items; they are
 * counted per attack by {@link WeaponCharges} and costed here through {@link #addChargeUsed}.
 * <p>
 * Known limits: items dropped on purpose count as used; a pizza or cake eaten in halves is costed per piece
 * eaten; runes or coins put into a weapon after the first kill are costed twice, once as they leave the
 * inventory and again as the charges are spent (scales, ether, vials of blood and blowpipe darts are not);
 * charges spent by attacks the tracker does not recognise (see {@link WeaponCharges}) are not seen.
 */
public class SupplyTracker
{
	/** Dose families that restore prayer, for the prayer runway. */
	public static final Set<String> PRAYER_FAMILIES = Set.of(
		"Prayer potion", "Super restore", "Sanfew serum", "Blighted super restore");

	private static final Pattern DOSE = Pattern.compile("^(.*?)\\s*\\((\\d{1,2})\\)$");

	/** An item that is one member of a dose family, e.g. Prayer potion with 3 doses. */
	@Value
	public static class Dose
	{
		String family;
		int doses;
	}

	private final IntToLongFunction price;
	private final IntFunction<String> names;

	private Map<Integer, Integer> last;
	private boolean bankOpen;
	private boolean started;
	private int kills;
	private final Map<Integer, Long> used = new HashMap<>();
	private final Map<String, Long> dosesUsed = new HashMap<>();
	private long foodUsed;
	private final Set<Integer> ignored = new HashSet<>();
	/** Items ignored while any of their carriers is carried, e.g. darts while a blowpipe is. */
	private final Map<Integer, Set<Integer>> ignoredWhile = new HashMap<>();
	/** Charge materials spent, in fractions of an item. */
	private final Map<Integer, Double> chargeUsed = new HashMap<>();

	private final Map<Integer, Optional<Dose>> doseCache = new HashMap<>();
	private final Map<Integer, Long> priceCache = new HashMap<>();
	/** Best known per-dose price for each family: the price of its highest-dose member divided by its doses. */
	private final Map<String, Dose> dosePriceFrom = new HashMap<>();
	private final Map<String, Double> dosePrice = new HashMap<>();

	/**
	 * @param price GP each for an item id; looked up when the item is first used
	 * @param names the game's name for an item id (may return null); used to find dose families
	 */
	public SupplyTracker(IntToLongFunction price, IntFunction<String> names)
	{
		this.price = price;
		this.names = names;
	}

	/** Clears usage and kills for a new task. The last snapshot is kept as the baseline. */
	public void reset()
	{
		started = false;
		kills = 0;
		used.clear();
		chargeUsed.clear();
		dosesUsed.clear();
		foodUsed = 0;
	}

	/** Records {@code n} task kills; the first one starts usage counting. */
	public void onKill(int n)
	{
		if (n > 0)
		{
			started = true;
			kills += n;
		}
	}

	/**
	 * Items whose carried count going down is not usage, because another tracker reports their use through
	 * {@link #addUsed} (cannonballs: loading them is not firing them, see {@link CannonRunway}). Kept across resets.
	 */
	public void ignore(Set<Integer> itemIds)
	{
		ignored.addAll(itemIds);
	}

	/**
	 * Like {@link #ignore}, but only while one of {@code carriers} is carried before or after the change:
	 * darts leaving the inventory with a blowpipe carried were loaded into it. Kept across resets.
	 */
	public void ignoreWhileCarried(Set<Integer> carriers, Set<Integer> itemIds)
	{
		for (int id : itemIds)
		{
			ignoredWhile.computeIfAbsent(id, k -> new HashSet<>()).addAll(carriers);
		}
	}

	/**
	 * Records a weapon charge's materials spent, in fractions of an item (2/3 of a scale per blowpipe shot);
	 * costed at the item's price. Kept apart from {@link #usedTotal}, which counts whole items carried.
	 */
	public void addChargeUsed(int itemId, double qty)
	{
		if (qty > 0)
		{
			chargeUsed.merge(itemId, qty, Double::sum);
			priceCache.computeIfAbsent(itemId, price::applyAsLong);
		}
	}

	/** Records usage seen elsewhere, e.g. cannonballs fired; costed like any other used item. */
	public void addUsed(int itemId, long qty)
	{
		if (qty > 0)
		{
			used.merge(itemId, qty, Long::sum);
			priceCache.computeIfAbsent(itemId, price::applyAsLong);
		}
	}

	/** While a bank or deposit box is open, decreases were banked, not used. */
	public void setBankOpen(boolean open)
	{
		bankOpen = open;
	}

	/** @param counts item id to quantity across inventory, worn equipment and rune pouch */
	public void onSnapshot(Map<Integer, Integer> counts)
	{
		Map<Integer, Integer> now = new HashMap<>();
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			if (e.getValue() != null && e.getValue() > 0)
			{
				now.merge(e.getKey(), e.getValue(), Integer::sum);
			}
		}
		if (last != null && started && !bankOpen)
		{
			countUsage(last, now);
		}
		last = now;
	}

	private void countUsage(Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		Set<Integer> ids = new HashSet<>(before.keySet());
		ids.addAll(after.keySet());
		Map<String, Integer> familyBefore = new HashMap<>();
		Map<String, Integer> familyAfter = new HashMap<>();
		for (int id : ids)
		{
			int b = before.getOrDefault(id, 0);
			int a = after.getOrDefault(id, 0);
			Optional<Dose> dose = doseOf(id);
			if (dose.isPresent())
			{
				Dose d = dose.get();
				familyBefore.merge(d.getFamily(), b * d.getDoses(), Integer::sum);
				familyAfter.merge(d.getFamily(), a * d.getDoses(), Integer::sum);
				notePrice(id, d);
			}
			else if (b > a && id != ItemID.VIAL && !ignored.contains(id) && !carrierHeld(id, before, after))
			{
				used.merge(id, (long) (b - a), Long::sum);
				priceCache.computeIfAbsent(id, price::applyAsLong);
			}
		}
		for (Map.Entry<String, Integer> e : familyBefore.entrySet())
		{
			int diff = e.getValue() - familyAfter.getOrDefault(e.getKey(), 0);
			if (diff > 0)
			{
				dosesUsed.merge(e.getKey(), (long) diff, Long::sum);
			}
		}
		long food = foodIn(before) - foodIn(after);
		if (food > 0)
		{
			foodUsed += food;
		}
	}

	private boolean carrierHeld(int id, Map<Integer, Integer> before, Map<Integer, Integer> after)
	{
		Set<Integer> carriers = ignoredWhile.get(id);
		if (carriers != null)
		{
			for (int c : carriers)
			{
				if (before.containsKey(c) || after.containsKey(c))
				{
					return true;
				}
			}
		}
		return false;
	}

	private void notePrice(int id, Dose d)
	{
		Dose best = dosePriceFrom.get(d.getFamily());
		if (best != null && best.getDoses() >= d.getDoses())
		{
			return;
		}
		long each = priceCache.computeIfAbsent(id, price::applyAsLong);
		if (each > 0)
		{
			dosePriceFrom.put(d.getFamily(), d);
			dosePrice.put(d.getFamily(), each / (double) d.getDoses());
		}
	}

	private static long foodIn(Map<Integer, Integer> counts)
	{
		long n = 0;
		for (Map.Entry<Integer, Integer> e : counts.entrySet())
		{
			if (FoodItems.isFood(e.getKey()))
			{
				n += e.getValue();
			}
		}
		return n;
	}

	/** The dose family an item belongs to, parsed from a name like "Prayer potion(3)". */
	public Optional<Dose> doseOf(int itemId)
	{
		return doseCache.computeIfAbsent(itemId, id -> parseDose(names.apply(id)));
	}

	static Optional<Dose> parseDose(String name)
	{
		if (name == null)
		{
			return Optional.empty();
		}
		Matcher m = DOSE.matcher(name.trim());
		if (!m.matches() || m.group(1).isEmpty())
		{
			return Optional.empty();
		}
		int doses = Integer.parseInt(m.group(2));
		return doses > 0 ? Optional.of(new Dose(m.group(1), doses)) : Optional.empty();
	}

	public int kills()
	{
		return kills;
	}

	public boolean started()
	{
		return started;
	}

	/** GP value of everything used this task. */
	public double costTotal()
	{
		double total = 0;
		for (Map.Entry<Integer, Long> e : used.entrySet())
		{
			total += e.getValue() * (double) priceCache.getOrDefault(e.getKey(), 0L);
		}
		for (Map.Entry<Integer, Double> e : chargeUsed.entrySet())
		{
			total += e.getValue() * priceCache.getOrDefault(e.getKey(), 0L);
		}
		for (Map.Entry<String, Long> e : dosesUsed.entrySet())
		{
			total += e.getValue() * dosePrice.getOrDefault(e.getKey(), 0.0);
		}
		return total;
	}

	/** Supply cost per kill in GP; empty before the first kill. */
	public OptionalDouble costPerKill()
	{
		return kills > 0 ? OptionalDouble.of(costTotal() / kills) : OptionalDouble.empty();
	}

	/** Quantity of a non-dose item used this task (dose items are counted per family, see {@link #dosesUsed}). */
	public long usedTotal(int itemId)
	{
		return used.getOrDefault(itemId, 0L);
	}

	/** Quantity of an item spent as weapon charges this task (see {@link #addChargeUsed}). */
	public double chargeMaterialUsed(int itemId)
	{
		return chargeUsed.getOrDefault(itemId, 0.0);
	}

	public OptionalDouble usedPerKill(int itemId)
	{
		return kills > 0 ? OptionalDouble.of(usedTotal(itemId) / (double) kills) : OptionalDouble.empty();
	}

	/** Non-dose item id to quantity used this task. */
	public Map<Integer, Long> usedItems()
	{
		return Collections.unmodifiableMap(used);
	}

	public long dosesUsed(String family)
	{
		return dosesUsed.getOrDefault(family, 0L);
	}

	/** Doses used per kill summed over the families, e.g. {@link #PRAYER_FAMILIES}; empty before the first kill. */
	public OptionalDouble dosesUsedPerKill(Set<String> families)
	{
		if (kills <= 0)
		{
			return OptionalDouble.empty();
		}
		long n = 0;
		for (String f : families)
		{
			n += dosesUsed(f);
		}
		return OptionalDouble.of(n / (double) kills);
	}

	/** Quantity of an item carried now (0 before the first snapshot). */
	public int count(int itemId)
	{
		return last == null ? 0 : last.getOrDefault(itemId, 0);
	}

	/** Doses carried now across the families. */
	public int doses(Set<String> families)
	{
		if (last == null)
		{
			return 0;
		}
		int n = 0;
		for (Map.Entry<Integer, Integer> e : last.entrySet())
		{
			Optional<Dose> d = doseOf(e.getKey());
			if (d.isPresent() && families.contains(d.get().getFamily()))
			{
				n += e.getValue() * d.get().getDoses();
			}
		}
		return n;
	}

	/** The food item carried in the largest stack now (lowest id on a tie), or -1 when none is carried. */
	public int mainFoodId()
	{
		int best = -1;
		int most = 0;
		if (last != null)
		{
			for (Map.Entry<Integer, Integer> e : last.entrySet())
			{
				int id = e.getKey();
				int n = e.getValue();
				if (FoodItems.isFood(id) && n > 0 && (n > most || n == most && id < best))
				{
					best = id;
					most = n;
				}
			}
		}
		return best;
	}

	/** Food items ({@link FoodItems}) carried now, or -1 before the first snapshot. */
	public int foodCount()
	{
		return last == null ? -1 : (int) foodIn(last);
	}

	/** Food items eaten this task. */
	public long foodUsed()
	{
		return foodUsed;
	}

	public OptionalInt killsUntilFoodRunsOut()
	{
		if (kills <= 0 || foodUsed <= 0 || last == null)
		{
			return OptionalInt.empty();
		}
		double perKill = (double) foodUsed / kills;
		return OptionalInt.of((int) Math.floor(foodCount() / perKill));
	}
}
