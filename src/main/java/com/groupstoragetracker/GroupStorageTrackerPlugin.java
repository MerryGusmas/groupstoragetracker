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
import java.awt.Color;
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
import net.runelite.api.IconID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ColorUtil;

@PluginDescriptor(
	name = "Group Storage Tracker",
	description = "Tracks group storage items that are currently in your bank, inventory, or equipment",
	tags = {"bank", "gim", "group", "items", "storage"}
)
public class GroupStorageTrackerPlugin extends Plugin
{
	private static final String INCLUDE_OPTION = "Include in group storage tracker";
	private static final String EXCLUDE_OPTION = "Exclude from group storage tracker";
	private static final Color MENU_OPTION_COLOR = new Color(0xFFAD00);
	private static final String MINIMUM_ITEM_VALUE_KEY = "minimumItemValue";
	private static final String TRACKED_ITEMS_KEY = "trackedItems";
	private static final String MANUALLY_INCLUDED_ITEMS_KEY = "manuallyIncludedItems";
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
	private SpriteManager spriteManager;

	@Inject
	private Gson gson;

	private final Set<Integer> trackedItems = new TreeSet<>();
	private final Set<Integer> manuallyIncludedItems = new TreeSet<>();
	private final Set<Integer> excludedItems = new TreeSet<>();
	private Map<Integer, Integer> lastKnownGroupStorageItems = Collections.emptyMap();
	private volatile List<GroupStorageTrackedItem> displayItems = Collections.emptyList();
	private boolean suppressGroupStorageDiscoveryUntilClosed;

	private NavigationButton navButton;
	private volatile Object navigationIconRequest;

	@Provides
	GroupStorageTrackerConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GroupStorageTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadTrackedItems();
		loadManuallyIncludedItems();
		loadExcludedItems();
		removeLegacyManuallyIncludedExclusions();
		loadGroupStorageItems();
		panel.setExcludeHandler(this::excludeItem);
		panel.setIncludeHandler(this::includeExcludedItem);

		Object iconRequest = new Object();
		navigationIconRequest = iconRequest;
		spriteManager.getSpriteAsync(SpriteID.MOD_ICONS, IconID.GROUP_IRONMAN.getIndex(), icon ->
		{
			if (navigationIconRequest == iconRequest)
			{
				NavigationButton button = NavigationButton.builder()
					.tooltip("Group Storage Tracker")
					.icon(icon)
					.priority(8)
					.panel(panel)
					.build();
				navButton = button;
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
		manuallyIncludedItems.clear();
		excludedItems.clear();
		lastKnownGroupStorageItems = Collections.emptyMap();
		displayItems = Collections.emptyList();
		suppressGroupStorageDiscoveryUntilClosed = isGroupStorageOpen();

		configManager.unsetRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, TRACKED_ITEMS_KEY);
		configManager.unsetRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, MANUALLY_INCLUDED_ITEMS_KEY);
		configManager.unsetRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, EXCLUDED_ITEMS_KEY);
		configManager.unsetRSProfileConfiguration(GroupStorageTrackerConfig.GROUP, GROUP_STORAGE_ITEMS_KEY);
		panel.updateItems(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	@Override
	protected void shutDown()
	{
		navigationIconRequest = null;
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
		}

		mouseManager.unregisterMouseListener(mouseListener);
		overlayManager.remove(overlay);
		overlayManager.remove(inventoryOverlay);
		inventoryOverlay.clearPressedItem();
		navButton = null;
		trackedItems.clear();
		manuallyIncludedItems.clear();
		excludedItems.clear();
		lastKnownGroupStorageItems = Collections.emptyMap();
		displayItems = Collections.emptyList();
		suppressGroupStorageDiscoveryUntilClosed = false;
		panel.updateItems(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadTrackedItems();
		loadManuallyIncludedItems();
		loadExcludedItems();
		removeLegacyManuallyIncludedExclusions();
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
		clientThread.invokeLater(() ->
		{
			if (MINIMUM_ITEM_VALUE_KEY.equals(event.getKey()))
			{
				removeItemsBelowMinimumValue();
			}

			recalibrate();
		});
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
		if (!event.getOption().equals("Examine") || !isTrackingMenuWidget(event.getActionParam1()))
		{
			return;
		}

		int itemId = event.getItemId();
		if (itemId <= 0)
		{
			return;
		}

		Set<Integer> activeTrackedItems = getActiveTrackedItems(itemId);
		boolean tracked = !activeTrackedItems.isEmpty();
		MenuEntry menuEntry = client.createMenuEntry(-1)
			.setParam0(event.getActionParam0())
			.setParam1(event.getActionParam1())
			.setTarget(event.getTarget())
			.setOption(ColorUtil.prependColorTag(tracked ? EXCLUDE_OPTION : INCLUDE_OPTION, MENU_OPTION_COLOR))
			.setType(MenuAction.RUNELITE)
			.setIdentifier(event.getIdentifier())
			.setItemId(itemId);
		menuEntry.setDeprioritized(true);
		if (tracked)
		{
			menuEntry.onClick(entry -> excludeItems(activeTrackedItems));
		}
		else
		{
			menuEntry.onClick(this::includeItem);
		}
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort event)
	{
		if (client.isMenuOpen())
		{
			return;
		}

		GroupStorageTrackedItem hoveredItem = overlay.getHoveredItem();
		if (hoveredItem == null || !isTrackedInventoryItem(hoveredItem.getItemId()))
		{
			return;
		}

		MenuEntry menuEntry = client.createMenuEntry(-1)
			.setOption(ColorUtil.prependColorTag(EXCLUDE_OPTION, MENU_OPTION_COLOR))
			.setTarget(hoveredItem.getName())
			.setType(MenuAction.RUNELITE)
			.setItemId(hoveredItem.getItemId())
			.onClick(entry -> excludeItem(hoveredItem.getItemId()));
		menuEntry.setDeprioritized(true);
	}

	private static boolean isTrackingMenuWidget(int widgetId)
	{
		return widgetId == InterfaceID.Bankmain.ITEMS ||
			widgetId == InterfaceID.Bankside.ITEMS ||
			widgetId == InterfaceID.Inventory.ITEMS ||
			widgetId == InterfaceID.SharedBank.ITEMS ||
			widgetId == InterfaceID.SharedBankSide.ITEMS;
	}

	private void recalibrate()
	{
		Map<Integer, Integer> groupStorageItems = getGroupStorageItems();
		Map<Integer, Integer> bankItems = getOutsideItemQuantities(InventoryID.BANK);
		Map<Integer, Integer> inventoryItems = getInventoryItems();
		Map<Integer, Integer> wornItems = getOutsideItemQuantities(InventoryID.WORN);
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
		List<GroupStorageTrackedItem> automaticallyTracked = new ArrayList<>();
		List<GroupStorageTrackedItem> manuallyIncluded = new ArrayList<>();
		for (GroupStorageTrackedItem item : items)
		{
			if (manuallyIncludedItems.contains(item.getItemId()))
			{
				manuallyIncluded.add(item);
			}
			else
			{
				automaticallyTracked.add(item);
			}
		}

		displayItems = Collections.unmodifiableList(new ArrayList<>(items));
		panel.updateItems(automaticallyTracked, manuallyIncluded, excluded);
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
		return !getActiveTrackedItems(itemId).isEmpty();
	}

	private List<GroupStorageTrackedItem> buildDisplayItems(
		Map<Integer, Integer> groupStorageItems,
		Map<Integer, Integer> bankItems,
		Map<Integer, Integer> inventoryItems,
		Map<Integer, Integer> wornItems)
	{
		List<GroupStorageTrackedItem> items = new ArrayList<>();
		Set<Integer> combinedItems = new TreeSet<>(trackedItems);
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

			items.add(new GroupStorageTrackedItem(
				itemId,
				getItemName(itemId),
				itemManager.getItemPrice(itemId),
				itemManager.getItemComposition(itemId).isStackable(),
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
				itemManager.getItemComposition(itemId).isStackable(),
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

	private void removeItemsBelowMinimumValue()
	{
		int minimumItemValue = config.minimumItemValue();
		Set<Integer> removedItems = new HashSet<>();
		for (int itemId : trackedItems)
		{
			if (!manuallyIncludedItems.contains(itemId) && itemManager.getItemPrice(itemId) < minimumItemValue)
			{
				removedItems.add(itemId);
			}
		}

		if (removedItems.isEmpty())
		{
			return;
		}

		trackedItems.removeAll(removedItems);
		excludedItems.removeAll(removedItems);
		saveTrackedItems();
		saveExcludedItems();
	}

	private Map<Integer, Integer> getInventoryItems()
	{
		int inventoryId = isGroupStorageOpen() ? InventoryID.INV_PLAYER_TEMP : InventoryID.INV;
		return getOutsideItemQuantities(inventoryId);
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

		Map<Integer, Integer> currentGroupStorageItems = getItemQuantities(itemContainer, false);
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

	private Map<Integer, Integer> getOutsideItemQuantities(int inventoryId)
	{
		return getItemQuantities(client.getItemContainer(inventoryId), true);
	}

	private Map<Integer, Integer> getItemQuantities(ItemContainer itemContainer, boolean includeMappedItems)
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

			int canonicalItemId = itemManager.canonicalize(itemId);
			int normalizedItemId = ItemVariationMapping.map(canonicalItemId);
			itemQuantities.merge(normalizedItemId, quantity, Integer::sum);

			if (includeMappedItems)
			{
				for (int mappedItemId : getMappedTrackedItems(canonicalItemId))
				{
					if (mappedItemId != normalizedItemId)
					{
						itemQuantities.merge(mappedItemId, quantity, Integer::sum);
					}
				}
			}
		}

		return itemQuantities;
	}

	private void includeItem(MenuEntry entry)
	{
		int itemId = normalizeItemId(entry.getItemId());
		boolean trackedChanged = trackedItems.add(itemId);
		boolean manuallyIncludedChanged = manuallyIncludedItems.add(itemId);
		boolean excludedChanged = excludedItems.remove(itemId);

		if (trackedChanged)
		{
			saveTrackedItems();
		}

		if (manuallyIncludedChanged)
		{
			saveManuallyIncludedItems();
		}

		if (excludedChanged)
		{
			saveExcludedItems();
		}

		clientThread.invokeLater(this::recalibrate);
	}

	private void excludeItem(int itemId)
	{
		excludeItems(Collections.singleton(itemId));
	}

	private void excludeItems(Collection<Integer> itemIds)
	{
		Set<Integer> itemIdSnapshot = new HashSet<>(itemIds);
		clientThread.invokeLater(() ->
		{
			boolean trackedChanged = false;
			boolean manuallyIncludedChanged = false;
			boolean excludedChanged = false;
			for (int itemId : itemIdSnapshot)
			{
				int normalizedItemId = normalizeItemId(itemId);
				if (manuallyIncludedItems.remove(normalizedItemId))
				{
					manuallyIncludedChanged = true;
					trackedChanged |= trackedItems.remove(normalizedItemId);
					excludedChanged |= excludedItems.remove(normalizedItemId);
				}
				else if (trackedItems.contains(normalizedItemId))
				{
					excludedChanged |= excludedItems.add(normalizedItemId);
				}
			}

			if (trackedChanged)
			{
				saveTrackedItems();
			}

			if (manuallyIncludedChanged)
			{
				saveManuallyIncludedItems();
			}

			if (excludedChanged)
			{
				saveExcludedItems();
			}

			recalibrate();
		});
	}

	private void removeLegacyManuallyIncludedExclusions()
	{
		Set<Integer> legacyExclusions = new HashSet<>(manuallyIncludedItems);
		legacyExclusions.retainAll(excludedItems);
		if (legacyExclusions.isEmpty())
		{
			return;
		}

		manuallyIncludedItems.removeAll(legacyExclusions);
		trackedItems.removeAll(legacyExclusions);
		excludedItems.removeAll(legacyExclusions);
		saveManuallyIncludedItems();
		saveTrackedItems();
		saveExcludedItems();
	}

	private void includeExcludedItem(int itemId)
	{
		clientThread.invokeLater(() ->
		{
			int normalizedItemId = ItemVariationMapping.map(itemId);
			boolean trackedChanged = trackedItems.add(normalizedItemId);
			boolean excludedChanged = excludedItems.remove(normalizedItemId);
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

	private int normalizeItemId(int itemId)
	{
		return ItemVariationMapping.map(itemManager.canonicalize(itemId));
	}

	private Set<Integer> getActiveTrackedItems(int itemId)
	{
		int normalizedItemId = normalizeItemId(itemId);
		if (trackedItems.contains(normalizedItemId))
		{
			return excludedItems.contains(normalizedItemId) ?
				Collections.emptySet() : Collections.singleton(normalizedItemId);
		}

		Set<Integer> activeTrackedItems = getMappedTrackedItems(itemManager.canonicalize(itemId));
		activeTrackedItems.removeAll(excludedItems);
		return activeTrackedItems;
	}

	private Set<Integer> getMappedTrackedItems(int itemId)
	{
		Set<Integer> mappedTrackedItems = new HashSet<>();
		addMappedTrackedItems(itemId, mappedTrackedItems);

		int variationItemId = ItemVariationMapping.map(itemId);
		if (variationItemId != itemId)
		{
			addMappedTrackedItems(variationItemId, mappedTrackedItems);
		}

		return mappedTrackedItems;
	}

	private void addMappedTrackedItems(int itemId, Set<Integer> mappedTrackedItems)
	{
		Collection<ItemMapping> mappings = ItemMapping.map(itemId);
		if (mappings == null)
		{
			return;
		}

		for (ItemMapping mapping : mappings)
		{
			// Quantity-changing mappings describe exchange value, not interchangeable item forms.
			if (mapping.getQuantity() != 1L)
			{
				continue;
			}

			int mappedItemId = ItemVariationMapping.map(mapping.getTradeableItem());
			if (trackedItems.contains(mappedItemId))
			{
				mappedTrackedItems.add(mappedItemId);
			}
		}
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
			for (int itemId : storedItems)
			{
				trackedItems.add(ItemVariationMapping.map(itemId));
			}

			if (!trackedItems.equals(storedItems))
			{
				saveTrackedItems();
			}
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
			for (int itemId : storedItems)
			{
				excludedItems.add(ItemVariationMapping.map(itemId));
			}

			if (!excludedItems.equals(storedItems))
			{
				saveExcludedItems();
			}
		}
	}

	private void loadManuallyIncludedItems()
	{
		manuallyIncludedItems.clear();

		String json = configManager.getRSProfileConfiguration(
			GroupStorageTrackerConfig.GROUP, MANUALLY_INCLUDED_ITEMS_KEY);
		if (json == null || json.isBlank())
		{
			return;
		}

		Set<Integer> storedItems = gson.fromJson(json, TRACKED_ITEMS_TYPE);
		if (storedItems != null)
		{
			for (int itemId : storedItems)
			{
				manuallyIncludedItems.add(ItemVariationMapping.map(itemId));
			}

			boolean trackedChanged = trackedItems.addAll(manuallyIncludedItems);
			if (!manuallyIncludedItems.equals(storedItems))
			{
				saveManuallyIncludedItems();
			}

			if (trackedChanged)
			{
				saveTrackedItems();
			}
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
			Map<Integer, Integer> normalizedItems = new HashMap<>();
			for (Map.Entry<Integer, Integer> entry : storedItems.entrySet())
			{
				int itemId = ItemVariationMapping.map(entry.getKey());
				normalizedItems.merge(itemId, entry.getValue(), Integer::sum);
			}

			lastKnownGroupStorageItems = normalizedItems;
			if (!normalizedItems.equals(storedItems))
			{
				saveGroupStorageItems();
			}
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

	private void saveManuallyIncludedItems()
	{
		configManager.setRSProfileConfiguration(
			GroupStorageTrackerConfig.GROUP,
			MANUALLY_INCLUDED_ITEMS_KEY,
			gson.toJson(manuallyIncludedItems, TRACKED_ITEMS_TYPE));
	}

	private void saveGroupStorageItems()
	{
		configManager.setRSProfileConfiguration(
			GroupStorageTrackerConfig.GROUP,
			GROUP_STORAGE_ITEMS_KEY,
			gson.toJson(lastKnownGroupStorageItems, ITEM_QUANTITIES_TYPE));
	}
}
