package com.slayercompanion.locations;

import static org.junit.Assert.assertEquals;

import com.slayercompanion.data.ItemNeed;
import com.slayercompanion.data.Location;
import com.slayercompanion.gear.OwnedItems;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;

public class RequirementsTest
{
	private static Location loc()
	{
		Location l = new Location();
		l.setId("x");
		l.setName("X");
		return l;
	}

	private static Optional<String> reason(Location l, int slayer, boolean questDone, OwnedItems owned)
	{
		return Requirements.lockReason(l, s -> "slayer".equals(s) ? slayer : -1, q -> questDone,
			q -> "Waterfall Quest", owned);
	}

	@Test
	public void usableWhenNothingRequired()
	{
		assertEquals(Optional.empty(), reason(loc(), 1, false, new OwnedItems()));
	}

	@Test
	public void skillReason()
	{
		Location l = loc();
		l.setSkillReqs(Collections.singletonMap("slayer", 62));
		assertEquals(Optional.of("Needs 62 Slayer"), reason(l, 61, true, new OwnedItems()));
		assertEquals(Optional.empty(), reason(l, 62, true, new OwnedItems()));
	}

	@Test
	public void unknownSkillDoesNotLock()
	{
		Location l = loc();
		l.setSkillReqs(Collections.singletonMap("sailing", 50));
		assertEquals(Optional.empty(), reason(l, 1, true, new OwnedItems()));
	}

	@Test
	public void questReason()
	{
		Location l = loc();
		l.setQuestReqs(Collections.singletonList("WATERFALL_QUEST"));
		assertEquals(Optional.of("Needs Waterfall Quest"), reason(l, 99, false, new OwnedItems()));
		assertEquals(Optional.empty(), reason(l, 99, true, new OwnedItems()));
	}

	@Test
	public void itemReasonSatisfiedByAnyId()
	{
		Location l = loc();
		ItemNeed boots = new ItemNeed();
		boots.setName("boots of stone");
		boots.setItemIds(Arrays.asList(23037, 22951));
		l.setItemReqs(Collections.singletonList(boots));
		assertEquals(Optional.of("Needs boots of stone"), reason(l, 99, true, new OwnedItems()));
		OwnedItems owned = new OwnedItems();
		owned.updateBank(Collections.singletonMap(22951, 1), Instant.now());
		assertEquals(Optional.empty(), reason(l, 99, true, owned));
	}
}
