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
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

class GroupStorageTrackerInventoryOverlay extends WidgetItemOverlay
{
	private static final long PRESSED_DURATION_NANOS = TimeUnit.MILLISECONDS.toNanos(200);

	private final GroupStorageTrackerPlugin plugin;
	private final ItemManager itemManager;
	private final GroupStorageTrackerConfig config;
	private final Cache<Long, Image> fillCache;
	private final Cache<Long, Image> outlineCache;
	private final Set<Long> pendingImages = new HashSet<>();
	private volatile int pressedItemId = -1;
	private volatile long pressedUntilNanos;
	private long cacheGeneration;

	@Inject
	private GroupStorageTrackerInventoryOverlay(
		GroupStorageTrackerPlugin plugin,
		ItemManager itemManager,
		GroupStorageTrackerConfig config)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.config = config;
		fillCache = CacheBuilder.newBuilder()
			.concurrencyLevel(1)
			.maximumSize(64)
			.build();
		outlineCache = CacheBuilder.newBuilder()
			.concurrencyLevel(1)
			.maximumSize(64)
			.build();

		showOnInventory();
		showOnInterfaces(InterfaceID.SHARED_BANK_SIDE);
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		if (!config.tagInventoryItems() || !plugin.isGroupStorageOpen() || !plugin.isTrackedInventoryItem(itemId))
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		boolean pressed = isPressed(itemId);
		Image outline = getOutlineImage(itemId, widgetItem.getQuantity(), pressed);
		if (outline != null)
		{
			graphics.drawImage(outline, bounds.x, bounds.y, null);
		}

		Image fill = getFillImage(itemId, widgetItem.getQuantity(), pressed);
		if (fill != null)
		{
			graphics.drawImage(fill, bounds.x, bounds.y, null);
		}
	}

	private Image getFillImage(int itemId, int quantity, boolean pressed)
	{
		long key = getCacheKey(itemId, quantity, pressed);
		Image image = fillCache.getIfPresent(key);
		if (image == null)
		{
			prepareImages(itemId, quantity, pressed, key);
		}

		return image;
	}

	private Image getOutlineImage(int itemId, int quantity, boolean pressed)
	{
		long key = getCacheKey(itemId, quantity, pressed);
		Image image = outlineCache.getIfPresent(key);
		if (image == null)
		{
			prepareImages(itemId, quantity, pressed, key);
		}

		return image;
	}

	private void prepareImages(int itemId, int quantity, boolean pressed, long key)
	{
		if (!pendingImages.add(key))
		{
			return;
		}

		long generation = cacheGeneration;
		AsyncBufferedImage baseImage = itemManager.getImage(itemId, quantity, false);
		baseImage.onLoaded(() ->
		{
			if (generation == cacheGeneration)
			{
				Color fillColor = pressed ? config.inventoryFillColor().darker() : config.inventoryFillColor();
				fillCache.put(key, ImageUtil.fillImage(baseImage, fillColor));
				outlineCache.put(key, createOutlineImage(baseImage, pressed));
			}

			pendingImages.remove(key);
		});
	}

	private BufferedImage createOutlineImage(BufferedImage baseImage, boolean pressed)
	{
		BufferedImage outlinedImage = baseImage;
		Color outlineColor = pressed ? config.inventoryOutlineColor().darker() : config.inventoryOutlineColor();
		double outlineWidth = Math.max(0.0, Math.min(5.0, config.inventoryOutlineWidth()));
		int fullPixels = (int) outlineWidth;
		for (int i = 0; i < fullPixels; i++)
		{
			outlinedImage = ImageUtil.outlineImage(outlinedImage, outlineColor, true);
		}

		double fractionalPixel = outlineWidth - fullPixels;
		if (fractionalPixel > 0.0)
		{
			Color fractionalColor = new Color(
				outlineColor.getRed(),
				outlineColor.getGreen(),
				outlineColor.getBlue(),
				(int) Math.round(outlineColor.getAlpha() * fractionalPixel));
			outlinedImage = ImageUtil.outlineImage(outlinedImage, fractionalColor, true);
		}

		return outlinedImage;
	}

	private static long getCacheKey(int itemId, int quantity, boolean pressed)
	{
		long key = ((long) itemId << 32) | (quantity & 0xffffffffL);
		return pressed ? key | Long.MIN_VALUE : key;
	}

	private boolean isPressed(int itemId)
	{
		if (pressedItemId != itemId)
		{
			return false;
		}

		if (System.nanoTime() < pressedUntilNanos)
		{
			return true;
		}

		clearPressedItem();
		return false;
	}

	void pressItem(int itemId)
	{
		pressedItemId = itemId;
		pressedUntilNanos = System.nanoTime() + PRESSED_DURATION_NANOS;
	}

	void clearPressedItem()
	{
		pressedItemId = -1;
		pressedUntilNanos = 0;
	}

	void invalidateCache()
	{
		cacheGeneration++;
		fillCache.invalidateAll();
		outlineCache.invalidateAll();
	}
}
