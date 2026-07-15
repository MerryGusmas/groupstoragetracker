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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentOrientation;
import net.runelite.client.ui.overlay.components.ImageComponent;
import net.runelite.client.util.ImageUtil;

class GroupStorageTrackerOverlay extends OverlayPanel
{
	private static final Color BANK_COLOR = Color.RED;
	private static final Color WORN_COLOR = Color.ORANGE;
	private static final Color INVENTORY_COLOR = Color.GREEN;
	private final Client client;
	private final GroupStorageTrackerPlugin plugin;
	private final ItemManager itemManager;
	private final Cache<GroupStorageTrackedItem, BufferedImage> imageCache;

	@Inject
	private GroupStorageTrackerOverlay(Client client, GroupStorageTrackerPlugin plugin, ItemManager itemManager)
	{
		setPosition(OverlayPosition.BOTTOM_RIGHT);
		panelComponent.setWrap(true);
		panelComponent.setGap(new Point(6, 4));
		panelComponent.setPreferredSize(new Dimension(4 * (Constants.ITEM_SPRITE_WIDTH + 6), 0));
		panelComponent.setOrientation(ComponentOrientation.HORIZONTAL);

		this.client = client;
		this.plugin = plugin;
		this.itemManager = itemManager;
		imageCache = CacheBuilder.newBuilder()
			.concurrencyLevel(1)
			.maximumSize(256)
			.build();
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!isBankOpen() && !isGroupStorageOpen())
		{
			return null;
		}

		List<GroupStorageTrackedItem> items = plugin.getMissingItems();
		if (items.isEmpty())
		{
			return null;
		}

		for (GroupStorageTrackedItem item : items)
		{
			BufferedImage image = getImage(item);
			if (image != null)
			{
				panelComponent.getChildren().add(new ImageComponent(image));
			}
		}

		return super.render(graphics);
	}

	private boolean isBankOpen()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank != null && !bank.isHidden();
	}

	private boolean isGroupStorageOpen()
	{
		Widget groupStorageItems = client.getWidget(InterfaceID.SharedBank.ITEMS);
		return groupStorageItems != null && !groupStorageItems.isHidden();
	}

	private BufferedImage getImage(GroupStorageTrackedItem item)
	{
		BufferedImage image = imageCache.getIfPresent(item);
		if (image != null)
		{
			return image;
		}

		ItemComposition itemComposition = itemManager.getItemComposition(item.getItemId());
		image = itemManager.getImage(
			item.getItemId(), item.getOutsideQuantity(), itemComposition.isStackable());
		for (Color color : getOutlineColors(item))
		{
			image = ImageUtil.outlineImage(image, color, true);
		}

		imageCache.put(item, image);
		return image;
	}

	private static List<Color> getOutlineColors(GroupStorageTrackedItem item)
	{
		List<Color> colors = new ArrayList<>();
		if (item.getBankQuantity() > 0)
		{
			colors.add(BANK_COLOR);
		}

		if (item.getWornQuantity() > 0)
		{
			colors.add(WORN_COLOR);
		}

		if (item.getInventoryQuantity() > 0)
		{
			colors.add(INVENTORY_COLOR);
		}

		return colors;
	}

}
