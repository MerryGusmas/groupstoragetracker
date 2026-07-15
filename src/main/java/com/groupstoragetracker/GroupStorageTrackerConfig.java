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

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(GroupStorageTrackerConfig.GROUP)
public interface GroupStorageTrackerConfig extends Config
{
	String GROUP = "groupstoragetracker";

	@ConfigSection(
		name = "Inventory tags",
		description = "Configure tracked item highlights while group storage is open.",
		position = 2
	)
	String inventoryTagsSection = "inventoryTags";

	@Range(
		min = 0
	)
	@Units(" gp")
	@ConfigItem(
		keyName = "minimumItemValue",
		name = "Minimum item value",
		description = "Automatically track items first seen in group storage with a unit GE value at or above this amount.",
		position = 0
	)
	default int minimumItemValue()
	{
		return 50_000;
	}

	@ConfigItem(
		keyName = "showInventory",
		name = "Display group storage bank view",
		description = "Display tracked group storage items currently in your inventory in the bank view.",
		position = 1
	)
	default boolean showInventory()
	{
		return true;
	}

	@ConfigItem(
		keyName = "tagInventoryItems",
		name = "Outline tracked items in inventory",
		description = "Highlight tracked items in your inventory while group storage is open.",
		position = 0,
		section = inventoryTagsSection
	)
	default boolean tagInventoryItems()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "inventoryOutlineColor",
		name = "Outline colour",
		description = "Colour of the tracked inventory item outline.",
		position = 1,
		section = inventoryTagsSection
	)
	default Color inventoryOutlineColor()
	{
		return new Color(0xFF00FF3A, true);
	}

	@Alpha
	@ConfigItem(
		keyName = "inventoryFillColor",
		name = "Fill colour",
		description = "Colour of the tracked inventory item fill.",
		position = 2,
		section = inventoryTagsSection
	)
	default Color inventoryFillColor()
	{
		return new Color(0x3D00FF13, true);
	}

	@ConfigItem(
		keyName = "inventoryOutlineWidth",
		name = "Outline thickness",
		description = "Thickness of the tracked inventory item outline, from 0.0 to 5.0 pixels.",
		position = 3,
		section = inventoryTagsSection
	)
	default double inventoryOutlineWidth()
	{
		return 0.5;
	}
}
