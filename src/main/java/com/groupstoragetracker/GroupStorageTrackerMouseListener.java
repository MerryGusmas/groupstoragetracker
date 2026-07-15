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

import java.awt.event.MouseEvent;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.client.input.MouseAdapter;

class GroupStorageTrackerMouseListener extends MouseAdapter
{
	private final Client client;
	private final GroupStorageTrackerPlugin plugin;

	@Inject
	private GroupStorageTrackerMouseListener(Client client, GroupStorageTrackerPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (event.getButton() != MouseEvent.BUTTON1)
		{
			return event;
		}

		MenuEntry[] entries = client.getMenuEntries();
		if (entries.length == 0)
		{
			return event;
		}

		MenuEntry entry = client.isMenuOpen() ? getHoveredEntry(entries) : entries[entries.length - 1];
		if (entry != null)
		{
			plugin.onStoreMousePressed(entry);
		}

		return event;
	}

	private MenuEntry getHoveredEntry(MenuEntry[] entries)
	{
		Point mousePosition = client.getMouseCanvasPosition();
		int menuX = client.getMenuX();
		int menuY = client.getMenuY();
		int menuWidth = client.getMenuWidth();
		int index = entries.length - 1 - (mousePosition.getY() - menuY - 19) / 15;

		if (mousePosition.getX() <= menuX || mousePosition.getX() >= menuX + menuWidth ||
			mousePosition.getY() < menuY + 19 || index < 0 || index >= entries.length)
		{
			return null;
		}

		return entries[index];
	}
}
