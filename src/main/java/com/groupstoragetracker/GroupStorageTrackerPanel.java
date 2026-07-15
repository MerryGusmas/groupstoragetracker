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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

class GroupStorageTrackerPanel extends PluginPanel
{
	private final ItemManager itemManager;
	private final JLabel summary = new JLabel("No tracked items outside storage");
	private final JPanel missingPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 5));
	private final JButton storedItemsHeader = new JButton();
	private final JPanel storedItemsPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 5));
	private final JPanel storedItemsSection = new JPanel(new BorderLayout(0, 3));
	private final JButton excludedItemsHeader = new JButton();
	private final JPanel excludedItemsPanel = new JPanel(new DynamicGridLayout(0, 1, 0, 5));
	private final JPanel excludedItemsSection = new JPanel(new BorderLayout(0, 3));
	private IntConsumer excludeHandler = itemId ->
	{
	};
	private IntConsumer includeHandler = itemId ->
	{
	};
	private boolean storedItemsExpanded;
	private boolean excludedItemsExpanded;

	@Inject
	private GroupStorageTrackerPanel(ItemManager itemManager)
	{
		super();

		this.itemManager = itemManager;

		JLabel title = new JLabel("Group Storage");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
		add(title);

		summary.setForeground(Color.LIGHT_GRAY);
		summary.setBorder(new EmptyBorder(2, 0, 8, 0));
		add(summary);

		missingPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(missingPanel);

		storedItemsHeader.setFocusable(false);
		storedItemsHeader.setBorder(new EmptyBorder(6, 6, 6, 6));
		storedItemsHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		storedItemsHeader.setForeground(Color.WHITE);
		storedItemsHeader.addActionListener(e ->
		{
			storedItemsExpanded = !storedItemsExpanded;
			setSectionExpanded(storedItemsSection, storedItemsPanel, storedItemsExpanded);
			updateStoredItemsHeader(storedItemsPanel.getComponentCount());
			revalidate();
			repaint();
		});
		storedItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		storedItemsSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		storedItemsSection.add(storedItemsHeader, BorderLayout.NORTH);

		excludedItemsHeader.setFocusable(false);
		excludedItemsHeader.setBorder(new EmptyBorder(6, 6, 6, 6));
		excludedItemsHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		excludedItemsHeader.setForeground(Color.WHITE);
		excludedItemsHeader.addActionListener(e ->
		{
			excludedItemsExpanded = !excludedItemsExpanded;
			setSectionExpanded(excludedItemsSection, excludedItemsPanel, excludedItemsExpanded);
			updateExcludedItemsHeader(excludedItemsPanel.getComponentCount());
			revalidate();
			repaint();
		});
		excludedItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		excludedItemsSection.setBackground(ColorScheme.DARK_GRAY_COLOR);
		excludedItemsSection.add(excludedItemsHeader, BorderLayout.NORTH);
	}

	void setExcludeHandler(IntConsumer excludeHandler)
	{
		this.excludeHandler = excludeHandler;
	}

	void setIncludeHandler(IntConsumer includeHandler)
	{
		this.includeHandler = includeHandler;
	}

	void updateItems(List<GroupStorageTrackedItem> items, List<GroupStorageTrackedItem> excludedItems)
	{
		List<GroupStorageTrackedItem> snapshot = new ArrayList<>(items);
		List<GroupStorageTrackedItem> excludedSnapshot = new ArrayList<>(excludedItems);
		SwingUtilities.invokeLater(() -> rebuild(snapshot, excludedSnapshot));
	}

	private void rebuild(List<GroupStorageTrackedItem> items, List<GroupStorageTrackedItem> excludedItems)
	{
		missingPanel.removeAll();
		storedItemsPanel.removeAll();
		excludedItemsPanel.removeAll();

		List<GroupStorageTrackedItem> missingItems = items.stream()
			.filter(GroupStorageTrackedItem::isMissingFromGroupStorage)
			.collect(Collectors.toList());
		List<GroupStorageTrackedItem> storedItems = items.stream()
			.filter(GroupStorageTrackedItem::isInGroupStorage)
			.collect(Collectors.toList());

		if (missingItems.size() == 1)
		{
			summary.setText("1 tracked item not in group storage");
		}
		else
		{
			summary.setText(missingItems.size() + " tracked items not in group storage");
		}

		if (missingItems.isEmpty())
		{
			missingPanel.add(createEmptyPanel("No tracked items outside storage."));
		}
		else
		{
			for (GroupStorageTrackedItem item : missingItems)
			{
				missingPanel.add(new GroupStorageTrackerItemPanel(
					itemManager, item, excludeHandler, ItemSection.MISSING));
			}
		}

		for (GroupStorageTrackedItem item : storedItems)
		{
			storedItemsPanel.add(new GroupStorageTrackerItemPanel(
				itemManager, item, excludeHandler, ItemSection.STORED));
		}

		for (GroupStorageTrackedItem item : excludedItems)
		{
			excludedItemsPanel.add(new GroupStorageTrackerItemPanel(
				itemManager, item, includeHandler, ItemSection.EXCLUDED));
		}

		boolean hasStoredItems = !storedItems.isEmpty();
		setSectionExpanded(storedItemsSection, storedItemsPanel, hasStoredItems && storedItemsExpanded);
		updateStoredItemsHeader(storedItems.size());

		boolean hasExcludedItems = !excludedItems.isEmpty();
		setSectionExpanded(excludedItemsSection, excludedItemsPanel, hasExcludedItems && excludedItemsExpanded);
		updateExcludedItemsHeader(excludedItems.size());

		remove(excludedItemsSection);
		remove(storedItemsSection);
		if (hasExcludedItems)
		{
			add(excludedItemsSection);
		}

		if (hasStoredItems)
		{
			add(storedItemsSection);
		}

		missingPanel.revalidate();
		missingPanel.repaint();
		storedItemsHeader.revalidate();
		storedItemsHeader.repaint();
		storedItemsPanel.revalidate();
		storedItemsPanel.repaint();
		excludedItemsHeader.revalidate();
		excludedItemsHeader.repaint();
		excludedItemsPanel.revalidate();
		excludedItemsPanel.repaint();
		revalidate();
		repaint();
	}

	private void updateStoredItemsHeader(int itemCount)
	{
		storedItemsHeader.setText((storedItemsExpanded ? "- " : "+ ") + "In group storage (" + itemCount + ")");
	}

	private void updateExcludedItemsHeader(int itemCount)
	{
		excludedItemsHeader.setText((excludedItemsExpanded ? "- " : "+ ") + "Excluded Items (" + itemCount + ")");
	}

	private static void setSectionExpanded(JPanel section, JPanel items, boolean expanded)
	{
		section.remove(items);
		if (expanded)
		{
			section.add(items, BorderLayout.CENTER);
		}

		section.revalidate();
		section.repaint();
	}

	private static JPanel createEmptyPanel(String text)
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.setBorder(new EmptyBorder(10, 8, 10, 8));

		JLabel label = new JLabel("<html><body style='width: 185px'>" + text + "</body></html>");
		label.setForeground(Color.LIGHT_GRAY);
		panel.add(label, BorderLayout.CENTER);

		return panel;
	}

	private enum ItemSection
	{
		MISSING,
		STORED,
		EXCLUDED
	}

	private static class GroupStorageTrackerItemPanel extends JPanel
	{
		private GroupStorageTrackerItemPanel(
			ItemManager itemManager,
			GroupStorageTrackedItem item,
			IntConsumer actionHandler,
			ItemSection section)
		{
			super(new GridBagLayout());
			setBackground(ColorScheme.DARKER_GRAY_COLOR);
			setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
				new EmptyBorder(6, 6, 6, 6)));

			JLabel icon = new JLabel();
			icon.setPreferredSize(new Dimension(36, 32));
			icon.setHorizontalAlignment(SwingConstants.CENTER);
			itemManager.getImage(item.getItemId(), Math.max(1, item.getOutsideQuantity()), true).addTo(icon);

			JLabel name = new JLabel(item.getName());
			name.setForeground(Color.WHITE);
			name.setFont(name.getFont().deriveFont(Font.BOLD));

			JLabel value = new JLabel(QuantityFormatter.quantityToStackSize(item.getGePrice()) + " gp ea");
			value.setForeground(Color.LIGHT_GRAY);

			boolean storedSection = section == ItemSection.STORED;
			JLabel location = new JLabel(storedSection ? getStoredLocationText(item) : getLocationText(item));
			location.setForeground(storedSection || section == ItemSection.EXCLUDED
				? ColorScheme.LIGHT_GRAY_COLOR : new Color(255, 188, 92));

			String actionText = section == ItemSection.EXCLUDED ? "Re-Include" : "Exclude";
			JButton actionButton = new JButton(actionText);
			actionButton.setFocusable(false);
			actionButton.addActionListener(e -> actionHandler.accept(item.getItemId()));

			JPopupMenu popupMenu = new JPopupMenu();
			JMenuItem action = new JMenuItem(actionText);
			action.addActionListener(e -> actionHandler.accept(item.getItemId()));
			popupMenu.add(action);
			setComponentPopupMenu(popupMenu);
			icon.setComponentPopupMenu(popupMenu);
			name.setComponentPopupMenu(popupMenu);
			value.setComponentPopupMenu(popupMenu);
			location.setComponentPopupMenu(popupMenu);

			GridBagConstraints c = new GridBagConstraints();
			c.gridx = 0;
			c.gridy = 0;
			c.gridheight = 3;
			c.insets = new Insets(0, 0, 0, 6);
			c.anchor = GridBagConstraints.NORTH;
			add(icon, c);

			c.gridx = 1;
			c.gridy = 0;
			c.gridheight = 1;
			c.weightx = 1;
			c.fill = GridBagConstraints.HORIZONTAL;
			c.insets = new Insets(0, 0, 2, 0);
			add(name, c);

			c.gridy = 1;
			add(location, c);

			c.gridy = 2;
			add(value, c);

			c.gridx = 2;
			c.gridy = 0;
			c.gridheight = 3;
			c.weightx = 0;
			c.fill = GridBagConstraints.NONE;
			c.insets = new Insets(0, 6, 0, 0);
			add(actionButton, c);
		}

		private static String getLocationText(GroupStorageTrackedItem item)
		{
			List<String> locations = new ArrayList<>();
			addLocation(locations, "Bank", item.getBankQuantity());
			addLocation(locations, "Inv", item.getInventoryQuantity());
			addLocation(locations, "Worn", item.getWornQuantity());

			if (locations.isEmpty())
			{
				if (item.getGroupStorageQuantity() > 0)
				{
					return "In storage" + formatQuantity(item.getGroupStorageQuantity());
				}

				return "Not seen outside";
			}

			return String.join(", ", locations);
		}

		private static String getStoredLocationText(GroupStorageTrackedItem item)
		{
			return "Storage" + formatQuantity(item.getGroupStorageQuantity());
		}

		private static void addLocation(List<String> locations, String name, int quantity)
		{
			if (quantity > 0)
			{
				locations.add(name + formatQuantity(quantity));
			}
		}

		private static String formatQuantity(int quantity)
		{
			return quantity > 1 ? " x" + QuantityFormatter.quantityToStackSize(quantity) : "";
		}
	}
}
