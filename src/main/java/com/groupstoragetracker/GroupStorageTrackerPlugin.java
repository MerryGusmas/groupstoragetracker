/*
 * Copyright (c) 2026, OpenAI
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.groupstoragetracker;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;

@PluginDescriptor(
	name = "Group Storage Tracker",
	description = "Tracks group storage items that are currently in your bank, inventory, or equipment",
	tags = {"bank", "gim", "group", "items", "storage"}
)
public class GroupStorageTrackerPlugin extends Plugin
{
	private static final String INCLUDE_OPTION = "Include in group storage tracker";
	private static final String TRACKED_ITEMS_KEY = "trackedItems";
	private static final String EXCLUDED_ITEMS_KEY = "excludedItems";
	private static final String GROUP_STORAGE_ITEMS_KEY = "groupStorageItems";
	private static final Type TRACKED_ITEMS_TYPE = new TypeToken<Set<Integer>>()
	{
	}.getType();
	private static final Type ITEM_QUANTITIES_TYPE = new TypeToken<Map<Integer, Integer>>()
	{
	}.getType();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private MouseManager mouseManager;

	@Inject
	private GroupStorageTrackerMouseListener mouseListener;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GroupStorageTrackerConfig config;

	@Inject
	private GroupStorageTrackerPanel panel;

	@Inject
	private GroupStorageTrackerOverlay overlay;

	@Inject
	private GroupStorageTrackerInventoryOverlay inventoryOverlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	private final Set<Integer> trackedItems = new TreeSet<>();
	private final Set<Integer> excludedItems = new TreeSet<>();
	private Map<Integer, Integer> lastKnownGroupStorageItems = Collections.emptyMap();
	private volatile List<GroupStorageTrackedItem> displayItems = Collections.emptyList();
	private boolean suppressGroupStorageDiscoveryUntilClosed;

	private NavigationButton navButton;

	@Provides
	GroupStorageTrackerConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupStorageTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadTrackedItems();
		loadExcludedItems();
		loadGroupStorageItems();
		panel.setExcludeHandler(this::excludeItem);
		panel.setIncludeHandler(this::includeExcludedItem);

		AsyncBufferedImage icon = itemManager.getImage(ItemID.GROUP_IRONMAN_HELM);
		navButton = NavigationButton.builder()
			.tooltip("Group Storage Tracker")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();

		NavigationButton button = navButton;
		icon.onLoaded(() ->
		{
			if (navButton == button)
			{
				clientToolbar.addNavigation(button);
			}
		});
		overlayManager.add(overlay);
		overlayManager.add(inventoryOverlay);
		mouseManager.registerMouseListener(mouseListener);
		clientThread.invokeLater(this::recalibrate);
	}

	@Override
	public void resetConfiguration()
	{
		trackedItems.clear();
		excludedItems.clear();
		lastKnownGroupStorageItems = Collections.emptyMap();
		displayItems = Collections.emptyList();
		suppressGroupStorageDiscoveryUntilClosed = isGroupStorageOpen();

		configManager.unsetRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, TRACKED_ITEMS_KEY);
		configManager.unsetRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, EXCLUDED_ITEMS_KEY);
		configManager.unsetRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, GROUP_STORAGE_ITEMS_KEY);
		panel.updateItems(Collections.emptyList(), Collections.emptyList());
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		mouseManager.unregisterMouseListener(mouseListener);
		overlayManager.remove(overlay);
		overlayManager.remove(inventoryOverlay);
		inventoryOverlay.clearPressedItem();
		navButton = null;
		trackedItems.clear();
		excludedItems.clear();
		lastKnownGroupStorageItems = Collections.emptyMap();
		displayItems = Collections.emptyList();
		suppressGroupStorageDiscoveryUntilClosed = false;
		panel.updateItems(Collections.emptyList(), Collections.emptyList());
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadTrackedItems();
		loadExcludedItems();
		loadGroupStorageItems();
		clientThread.invokeLater(this::recalibrate);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!GroupStorageTrackerConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		inventoryOverlay.invalidateCache();
		clientThread.invokeLater(this::recalibrate);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		int groupId = event.getGroupId();
		if (groupId == InterfaceID.BANKMAIN || groupId == InterfaceID.BANKSIDE ||
			groupId == InterfaceID.SHARED_BANK || groupId == InterfaceID.SHARED_BANK_SIDE)
		{
			clientThread.invokeLater(this::recalibrate);
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		int scriptId = event.getScriptId();
		if (scriptId == ScriptID.GROUP_IRONMAN_STORAGE_BUILD)
		{
			refreshGroupStorageItems(client.getItemContainer(InventoryID.INV_GROUP_TEMP), true);
			recalibrate();
		}
		else if (scriptId == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			recalibrate();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (containerId == InventoryID.INV_GROUP_TEMP)
		{
			refreshGroupStorageItems(event.getItemContainer(), false);
		}

		if (containerId == InventoryID.INV_GROUP_TEMP || containerId == InventoryID.INV_PLAYER_TEMP ||
			containerId == InventoryID.BANK || containerId == InventoryID.WORN || containerId == InventoryID.INV)
		{
			recalibrate();
		}
	}

	void onStoreMousePressed(MenuEntry entry)
	{
		MenuAction action = entry.getType();
		String option = entry.getOption();
		int itemId = entry.getItemId();
		if (!config.tagInventoryItems() ||
			(action != MenuAction.CC_OP && action != MenuAction.CC_OP_LOW_PRIORITY) ||
			WidgetUtil.componentToInterface(entry.getParam1()) != InterfaceID.SHARED_BANK_SIDE ||
			(!option.startsWith("Store") && !option.startsWith("Deposit-") && !option.startsWith("Donate")) ||
			itemId <= 0)
		{
			return;
		}

		inventoryOverlay.pressItem(itemId);
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!client.isKeyPressed(KeyCode.KC_SHIFT) ||
			event.getActionParam1() != InterfaceID.Bankmain.ITEMS ||
			!event.getOption().equals("Examine"))
		{
			return;
		}

		int itemId = event.getItemId();
		if (itemId <= 0)
		{
			return;
		}

		client.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setTarget(event.getTarget())
			.setOption(INCLUDE_OPTION)
			.setType(MenuAction.RUNELITE)
			.setIdentifier(event.getIdentifier())
			.setItemId(itemId)
			.onClick(this::includeItem);
	}

	private void recalibrate()
	{
		Map<Integer, Integer> groupStorageItems = getGroupStorageItems();
		Map<Integer, Integer> bankItems = getItemQuantities(InventoryID.BANK);
		Map<Integer, Integer> inventoryItems = getInventoryItems();
		Map<Integer, Integer> wornItems = getItemQuantities(InventoryID.WORN);
		boolean groupStorageOpen = isGroupStorageOpen();

		if (!groupStorageOpen)
		{
			suppressGroupStorageDiscoveryUntilClosed = false;
		}

		if (!suppressGroupStorageDiscoveryUntilClosed && addEligibleGroupStorageItems(groupStorageItems.keySet()))
		{
			saveTrackedItems();
		}

		List<GroupStorageTrackedItem> items = buildDisplayItems(groupStorageItems, bankItems, inventoryItems, wornItems);
		List<GroupStorageTrackedItem> excluded = buildExcludedItems(
			groupStorageItems, bankItems, inventoryItems, wornItems);
		displayItems = Collections.unmodifiableList(new ArrayList<>(items));
		panel.updateItems(items, excluded);
	}

	List<GroupStorageTrackedItem> getMissingItems()
	{
		List<GroupStorageTrackedItem> missingItems = new ArrayList<>();
		for (GroupStorageTrackedItem item : displayItems)
		{
			if (item.isMissingFromGroupStorage())
			{
				missingItems.add(item);
			}
		}

		return missingItems;
	}

	boolean isTrackedInventoryItem(int itemId)
	{
		int canonicalItemId = itemManager.canonicalize(itemId);
		return trackedItems.contains(canonicalItemId) && !excludedItems.contains(canonicalItemId);
	}

	private List<GroupStorageTrackedItem> buildDisplayItems(
		Map<Integer, Integer> groupStorageItems,
		Map<Integer, Integer> bankItems,
		Map<Integer, Integer> inventoryItems,
		Map<Integer, Integer> wornItems)
	{
		List<GroupStorageTrackedItem> items = new ArrayList<>();
		Set<Integer> combinedItems = new TreeSet<>(trackedItems);
		Set<Integer> outsideItemIds = new HashSet<>();
		outsideItemIds.addAll(bankItems.keySet());
		outsideItemIds.addAll(inventoryItems.keySet());
		outsideItemIds.addAll(wornItems.keySet());
		boolean groupStorageOpen = isGroupStorageOpen();

		for (int itemId : combinedItems)
		{
			int bankQuantity = bankItems.getOrDefault(itemId, 0);
			int inventoryQuantity = config.showInventory() ? inventoryItems.getOrDefault(itemId, 0) : 0;
			int wornQuantity = wornItems.getOrDefault(itemId, 0);
			int outsideQuantity = bankQuantity + inventoryQuantity + wornQuantity;
			int groupStorageQuantity = groupStorageItems.getOrDefault(itemId, 0);
			if (!groupStorageOpen)
			{
				groupStorageQuantity = Math.max(0, groupStorageQuantity - outsideQuantity);
			}

			if (excludedItems.contains(itemId))
			{
				continue;
			}

			if (!groupStorageOpen && outsideQuantity <= 0 && hasMappedProductOutside(itemId, outsideItemIds))
			{
				continue;
			}

			items.add(new GroupStorageTrackedItem(
				itemId,
				getItemName(itemId),
				itemManager.getItemPrice(itemId),
				bankQuantity,
				inventoryQuantity,
				wornQuantity,
				groupStorageQuantity));
		}

		sortDisplayItems(items);
		return items;
	}

	private List<GroupStorageTrackedItem> buildExcludedItems(
		Map<Integer, Integer> groupStorageItems,
		Map<Integer, Integer> bankItems,
		Map<Integer, Integer> inventoryItems,
		Map<Integer, Integer> wornItems)
	{
		List<GroupStorageTrackedItem> items = new ArrayList<>();
		boolean groupStorageOpen = isGroupStorageOpen();
		for (int itemId : excludedItems)
		{
			int bankQuantity = bankItems.getOrDefault(itemId, 0);
			int inventoryQuantity = config.showInventory() ? inventoryItems.getOrDefault(itemId, 0) : 0;
			int wornQuantity = wornItems.getOrDefault(itemId, 0);
			int groupStorageQuantity = groupStorageItems.getOrDefault(itemId, 0);
			if (!groupStorageOpen)
			{
				groupStorageQuantity = Math.max(
					0, groupStorageQuantity - bankQuantity - inventoryQuantity - wornQuantity);
			}

			items.add(new GroupStorageTrackedItem(
				itemId,
				getItemName(itemId),
				itemManager.getItemPrice(itemId),
				bankQuantity,
				inventoryQuantity,
				wornQuantity,
				groupStorageQuantity));
		}

		sortDisplayItems(items);
		return items;
	}

	private static void sortDisplayItems(List<GroupStorageTrackedItem> items)
	{
		items.sort((a, b) ->
		{
			int outsideCompare = Boolean.compare(b.isOutsideStorage(), a.isOutsideStorage());
			if (outsideCompare != 0)
			{
				return outsideCompare;
			}

			int valueCompare = Integer.compare(b.getGePrice(), a.getGePrice());
			if (valueCompare != 0)
			{
				return valueCompare;
			}

			return a.getName().compareToIgnoreCase(b.getName());
		});
	}

	private boolean addEligibleGroupStorageItems(Collection<Integer> itemIds)
	{
		boolean changed = false;
		int minimumItemValue = config.minimumItemValue();

		for (int itemId : itemIds)
		{
			if (trackedItems.contains(itemId))
			{
				continue;
			}

			if (itemManager.getItemPrice(itemId) >= minimumItemValue)
			{
				changed |= trackedItems.add(itemId);
			}
		}

		return changed;
	}

	private Map<Integer, Integer> getInventoryItems()
	{
		int inventoryId = isGroupStorageOpen() ? InventoryID.INV_PLAYER_TEMP : InventoryID.INV;
		return getItemQuantities(inventoryId);
	}

	private Map<Integer, Integer> getGroupStorageItems()
	{
		return lastKnownGroupStorageItems;
	}

	private void refreshGroupStorageItems(ItemContainer itemContainer, boolean allowEmpty)
	{
		if (!isGroupStorageOpen() || itemContainer == null)
		{
			return;
		}

		Map<Integer, Integer> currentGroupStorageItems = getItemQuantities(itemContainer);
		if (currentGroupStorageItems.isEmpty() && !allowEmpty)
		{
			return;
		}

		if (!currentGroupStorageItems.equals(lastKnownGroupStorageItems))
		{
			lastKnownGroupStorageItems = currentGroupStorageItems;
			saveGroupStorageItems();
		}
	}

	boolean isGroupStorageOpen()
	{
		Widget groupStorageItems = client.getWidget(InterfaceID.SharedBank.ITEMS);
		return groupStorageItems != null && !groupStorageItems.isHidden();
	}

	private boolean hasMappedProductOutside(int itemId, Set<Integer> outsideItemIds)
	{
		for (int outsideItemId : outsideItemIds)
		{
			Collection<ItemMapping> mappedItems = ItemMapping.map(outsideItemId);
			if (mappedItems == null)
			{
				continue;
			}

			for (ItemMapping mappedItem : mappedItems)
			{
				if (mappedItem.getTradeableItem() == itemId)
				{
					return true;
				}
			}
		}

		return false;
	}

	private Map<Integer, Integer> getItemQuantities(int inventoryId)
	{
		return getItemQuantities(client.getItemContainer(inventoryId));
	}

	private Map<Integer, Integer> getItemQuantities(ItemContainer itemContainer)
	{
		if (itemContainer == null)
		{
			return Collections.emptyMap();
		}

		Map<Integer, Integer> itemQuantities = new HashMap<>();
		for (Item item : itemContainer.getItems())
		{
			int itemId = item.getId();
			int quantity = item.getQuantity();
			if (itemId <= 0 || quantity <= 0)
			{
				continue;
			}

			ItemComposition itemComposition = itemManager.getItemComposition(itemId);
			if (itemComposition.getPlaceholderTemplateId() != -1)
			{
				continue;
			}

			itemId = itemManager.canonicalize(itemId);
			itemQuantities.merge(itemId, quantity, Integer::sum);
		}

		return itemQuantities;
	}

	private void includeItem(MenuEntry entry)
	{
		int itemId = itemManager.canonicalize(entry.getItemId());
		boolean trackedChanged = trackedItems.add(itemId);
		boolean excludedChanged = excludedItems.remove(itemId);

		if (trackedChanged)
		{
			saveTrackedItems();
		}

		if (excludedChanged)
		{
			saveExcludedItems();
		}

		clientThread.invokeLater(this::recalibrate);
	}

	private void excludeItem(int itemId)
	{
		int canonicalItemId = itemId;
		clientThread.invokeLater(() ->
		{
			if (excludedItems.add(canonicalItemId))
			{
				saveExcludedItems();
			}

			recalibrate();
		});
	}

	private void includeExcludedItem(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			boolean trackedChanged = trackedItems.add(itemId);
			boolean excludedChanged = excludedItems.remove(itemId);
			if (trackedChanged)
			{
				saveTrackedItems();
			}

			if (excludedChanged)
			{
				saveExcludedItems();
			}

			recalibrate();
		});
	}

	private String getItemName(int itemId)
	{
		ItemComposition itemComposition = itemManager.getItemComposition(itemId);
		String name = itemComposition.getName();
		return name == null || name.equalsIgnoreCase("null") ? "Item " + itemId : name;
	}

	private void loadTrackedItems()
	{
		trackedItems.clear();

		String json = configManager.getRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, TRACKED_ITEMS_KEY);
		if (json == null || json.isBlank())
		{
			return;
		}

		Set<Integer> storedItems = gson.fromJson(json, TRACKED_ITEMS_TYPE);
		if (storedItems != null)
		{
			trackedItems.addAll(storedItems);
		}
	}

	private void loadExcludedItems()
	{
		excludedItems.clear();

		String json = configManager.getRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, EXCLUDED_ITEMS_KEY);
		if (json == null || json.isBlank())
		{
			return;
		}

		Set<Integer> storedItems = gson.fromJson(json, TRACKED_ITEMS_TYPE);
		if (storedItems != null)
		{
			excludedItems.addAll(storedItems);
		}
	}

	private void loadGroupStorageItems()
	{
		lastKnownGroupStorageItems = Collections.emptyMap();

		String json = configManager.getRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, GROUP_STORAGE_ITEMS_KEY);
		if (json == null || json.isBlank())
		{
			return;
		}

		Map<Integer, Integer> storedItems = gson.fromJson(json, ITEM_QUANTITIES_TYPE);
		if (storedItems != null)
		{
			lastKnownGroupStorageItems = new HashMap<>(storedItems);
		}
	}

	private void saveTrackedItems()
	{
		configManager.setRSProfileConfiguration(
			GroupStorageTrackerConfig.GROUP,
			TRACKED_ITEMS_KEY,
			gson.toJson(trackedItems, TRACKED_ITEMS_TYPE));
	}

	private void saveExcludedItems()
	{
		configManager.setRSProfileConfiguration(
			GroupStorageTrackerConfig.GROUP,
			EXCLUDED_ITEMS_KEY,
			gson.toJson(excludedItems, TRACKED_ITEMS_TYPE));
	}

	private void saveGroupStorageItems()
	{
		configManager.setRSProfileConfiguration(
			GroupStorageTrackerConfig.GROUP,
			GROUP_STORAGE_ITEMS_KEY,
			gson.toJson(lastKnownGroupStorageItems, ITEM_QUANTITIES_TYPE));
	}
}
