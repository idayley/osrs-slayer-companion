package com.slayercompanion.gear;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/** Items the player has: bank + inventory + worn. Plain Java so it is unit-testable. */
public class OwnedItems
{
	private static final Gson GSON = new Gson();

	private transient IntUnaryOperator canonical;
	private Map<Integer, Integer> bank = new HashMap<>();
	private Map<Integer, Integer> inventory = new HashMap<>();
	private Map<Integer, Integer> worn = new HashMap<>();
	private Long bankSeenEpochMillis;

	public OwnedItems()
	{
		this(IntUnaryOperator.identity());
	}

	/** @param canonical maps noted/placeholder ids to the base item id (ItemManager.canonicalize). */
	public OwnedItems(IntUnaryOperator canonical)
	{
		this.canonical = canonical;
	}

	public void updateBank(Map<Integer, Integer> items, Instant seen)
	{
		bank = canon(items);
		bankSeenEpochMillis = seen.toEpochMilli();
	}

	public void updateInventory(Map<Integer, Integer> items)
	{
		inventory = canon(items);
	}

	public void updateWorn(Map<Integer, Integer> items)
	{
		worn = canon(items);
	}

	public int count(int itemId)
	{
		int id = canonical.applyAsInt(itemId);
		return bank.getOrDefault(id, 0) + inventory.getOrDefault(id, 0) + worn.getOrDefault(id, 0);
	}

	/** Whether the item (or a variant of it) is equipped right now. */
	public boolean isWorn(int itemId)
	{
		return worn.getOrDefault(canonical.applyAsInt(itemId), 0) > 0;
	}

	public boolean bankSeen()
	{
		return bankSeenEpochMillis != null;
	}

	public Optional<Instant> bankSeenAt()
	{
		return bankSeenEpochMillis == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(bankSeenEpochMillis));
	}

	public String toJson()
	{
		return GSON.toJson(this);
	}

	public static OwnedItems fromJson(String json)
	{
		return fromJson(json, IntUnaryOperator.identity());
	}

	public static OwnedItems fromJson(String json, IntUnaryOperator canonical)
	{
		OwnedItems o = json == null || json.isEmpty() ? null : GSON.fromJson(json, OwnedItems.class);
		if (o == null)
		{
			o = new OwnedItems();
		}
		o.canonical = canonical;
		if (o.bank == null)
		{
			o.bank = new HashMap<>();
		}
		if (o.inventory == null)
		{
			o.inventory = new HashMap<>();
		}
		if (o.worn == null)
		{
			o.worn = new HashMap<>();
		}
		return o;
	}

	private Map<Integer, Integer> canon(Map<Integer, Integer> in)
	{
		Map<Integer, Integer> out = new HashMap<>();
		in.forEach((id, qty) -> out.merge(canonical.applyAsInt(id), qty, Integer::sum));
		return out;
	}
}
