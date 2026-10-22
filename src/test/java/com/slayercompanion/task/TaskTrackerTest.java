package com.slayercompanion.task;

import com.google.gson.Gson;
import com.slayercompanion.data.GameData;
import com.slayercompanion.data.SlayerTask;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import net.runelite.api.Client;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TaskTrackerTest
{
	private static final Gson GSON = new Gson();

	private static final int TASK_ROW = 7001;
	private static final int AREA_ROW = 8001;

	private Client client;
	private GameData data;
	private TaskTracker tracker;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		data = GameData.load(GSON, "/com/slayercompanion/fixture");
		tracker = new TaskTracker(client, data);
	}

	private void givenTask(int enumId, String name, int amount)
	{
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(amount);
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(enumId);
		when(client.getDBRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, 0, enumId))
			.thenReturn(Collections.singletonList(TASK_ROW));
		when(client.getDBTableField(TASK_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
			.thenReturn(new Object[]{name});
	}

	@Test
	public void readsFireGiantTask()
	{
		givenTask(42, "Fire giants", 142);
		when(client.getVarpValue(VarPlayerID.SLAYER_AREA)).thenReturn(5);
		when(client.getDBRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, 0, 5))
			.thenReturn(Collections.singletonList(AREA_ROW));
		when(client.getDBTableField(AREA_ROW, DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, 0))
			.thenReturn(new Object[]{"Waterfall Dungeon"});
		when(client.getVarbitValue(VarbitID.SLAYER_TASKS_COMPLETED)).thenReturn(37);

		CurrentTask t = tracker.read().get();
		assertEquals("Fire giants", t.getTask().getName());
		assertEquals("fire_giants", t.getTask().getId());
		assertEquals(142, t.getRemaining());
		assertEquals(142, t.getInitialAmount());
		assertEquals(Optional.of("Waterfall Dungeon"), t.getKonarLocation());
		assertEquals(37, t.getStreak());
		assertEquals(Optional.empty(), t.getMaster());
	}

	@Test
	public void noTaskWhenAmountZero()
	{
		givenTask(42, "Fire giants", 0);
		assertFalse(tracker.read().isPresent());
	}

	@Test
	public void toleratesCaseAndPluralDifferences()
	{
		givenTask(42, "FIRE GIANT", 10);
		assertEquals("fire_giants", tracker.read().get().getTask().getId());
	}

	@Test
	public void bossTaskResolvesThroughBossSublist()
	{
		when(client.getVarpValue(VarPlayerID.SLAYER_COUNT)).thenReturn(30);
		when(client.getVarpValue(VarPlayerID.SLAYER_TARGET)).thenReturn(98);
		when(client.getVarbitValue(VarbitID.SLAYER_TARGET_BOSSID)).thenReturn(3);
		when(client.getDBRowsByValue(DBTableID.SlayerTaskSublist.ID,
			DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, 0, 3)).thenReturn(Collections.singletonList(9));
		when(client.getDBTableField(9, DBTableID.SlayerTaskSublist.COL_TASK, 0)).thenReturn(new Object[]{TASK_ROW});
		when(client.getDBTableField(TASK_ROW, DBTableID.SlayerTask.COL_NAME_UPPERCASE, 0))
			.thenReturn(new Object[]{"Fire giants"});
		assertEquals("Fire giants", tracker.read().get().getTask().getName());
	}

	@Test
	public void unknownTaskNameStillReturnsTaskWithoutData()
	{
		givenTask(99, "Zzyzx beasts", 25);
		CurrentTask t = tracker.read().get();
		assertEquals("Zzyzx beasts", t.getTask().getName());
		assertTrue(t.getTask().getMonsterIds().isEmpty());
		assertEquals(25, t.getRemaining());
	}

	@Test
	public void otherMastersTaskResolvesMonstersByName()
	{
		givenTask(55, "Hill giants", 30);
		SlayerTask t = tracker.read().get().getTask();
		assertEquals("Hill giants", t.getName());
		assertEquals(Arrays.asList(2098, 2099), t.getMonsterIds());
		assertTrue(t.getLocationIds().isEmpty());
		assertTrue(TaskTracker.isTaskNpcName("Hill Giant", t, data));
		assertFalse(TaskTracker.isTaskNpcName("Fire giant", t, data));
	}

	@Test
	public void taskWithoutMonsterIdsMatchesNoNpc()
	{
		givenTask(99, "Zzyzx beasts", 25);
		SlayerTask t = tracker.read().get().getTask();
		assertFalse(TaskTracker.isTaskNpcName("Zzyzx beast", t, data));
		assertFalse(TaskTracker.isTaskNpcName("Fire giant", t, data));
	}

	@Test
	public void newTaskWhenNameChanges()
	{
		givenTask(42, "Fire giants", 100);
		Optional<CurrentTask> a = tracker.read();
		givenTask(99, "Zzyzx beasts", 90);
		Optional<CurrentTask> b = tracker.read();
		assertTrue(tracker.isNewTask(a, b));
		assertTrue(tracker.isNewTask(Optional.empty(), b));
		assertFalse(tracker.isNewTask(a, Optional.empty()));
	}

	@Test
	public void newTaskWhenRemainingIncreases()
	{
		givenTask(42, "Fire giants", 50);
		Optional<CurrentTask> a = tracker.read();
		givenTask(42, "Fire giants", 120);
		Optional<CurrentTask> b = tracker.read();
		assertTrue(tracker.isNewTask(a, b));
		assertFalse(tracker.isNewTask(b, a));
		assertFalse(tracker.isNewTask(a, a));
	}

	@Test
	public void initialAmountKeepsMaxSeenAndRememberedValue()
	{
		givenTask(42, "Fire giants", 100);
		tracker.read();
		givenTask(42, "Fire giants", 60);
		assertEquals(100, tracker.read().get().getInitialAmount());
		tracker.rememberInitialAmount("Fire giants", 150);
		assertEquals(150, tracker.read().get().getInitialAmount());
		givenTask(99, "Zzyzx beasts", 40);
		assertEquals(40, tracker.read().get().getInitialAmount());
	}

	@Test
	public void chatMessageSetsMasterForNewTask()
	{
		givenTask(42, "Fire giants", 100);
		tracker.read();
		tracker.onChatMessage("Chaeldar has assigned you a new task.");
		givenTask(99, "Zzyzx beasts", 40);
		assertEquals(Optional.of("chaeldar"), tracker.read().get().getMaster());
		// master does not leak into the next task without a new assignment message
		givenTask(42, "Fire giants", 100);
		assertEquals(Optional.empty(), tracker.read().get().getMaster());
	}

	@Test
	public void unrelatedChatMessageIgnored()
	{
		tracker.onChatMessage("Nieve is a fine name for a goat.");
		givenTask(42, "Fire giants", 100);
		assertEquals(Optional.empty(), tracker.read().get().getMaster());
	}
}
