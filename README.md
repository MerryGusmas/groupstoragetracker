# Group Storage Tracker

Group Storage Tracker is a RuneLite plugin for Group Ironman accounts that helps track valuable shared-storage items which have been withdrawn and not returned.

The plugin learns items from Group Storage, watches bank, inventory, and equipment state, and shows a group storage/bank-only item viewer so you can quickly see what needs to go back into Group Storage.

## Features

- Automatically tracks items seen in Group Storage at or above a configurable GE value.
- Default minimum item value is `50,000 gp`.
- Supports manually included items by item name or item ID.
- Shows tracked items that are currently in the bank, inventory, or equipped.
- Optionally tags tracked inventory items while Group Storage is open.

## Bank Viewer

When the bank is open, the plugin displays an inventory-viewer-style overlay containing tracked items that are not currently in Group Storage.

Items are outlined based on the following ruleset:

- **Red**: item is in the bank
- **Green**: item is in inventory
- **Orange**: item is equipped

If an item appears in multiple locations, the overlay can show multiple outlines.

## Inventory Tags

While Group Storage is open, tracked items in the player's inventory can be highlighted with configurable outline and fill colours.

## Sidebar Panel

The plugin is available via the 'Group Ironman' icon in the RuneLite sidebar.

Clicking on the sidebar icon shows a window with:

- items currently not in Group Storage
- a collapsible section for items currently detected in Group Storage
- a collapsible `Excluded Items` section with a `Re-Include` button for each item
- item name, location, quantity, and GE value
- an `Exclude` action for excluding items from the tracking system

## Including Items

Open Group Storage. Any item seen there with a GE value equal to or greater than the configured minimum value will be tracked.

You can also hold `Shift` and right-click a bank item, then choose:

```text
Include in group storage tracker
```

Items can also be restored from the sidebar's `Excluded Items` section with the visible `Re-Include` button or context-menu action.

## Excluding Items

Items can be excluded from the tracker UI from the sidebar panel.

Right-click an item row or use the visible `Exclude` button.

Excluded items are persisted per RuneScape profile and remain in the sidebar's `Excluded Items` section until the plugin is reset or the item is manually re-included.

## Resetting

The RuneLite plugin reset button clears:

- automatically learned tracked items
- excluded items
- cached Group Storage state
- current sidebar and overlay display state

After resetting, close and reopen Group Storage before letting the plugin learn items again.

## Configuration

### Minimum Item Value

The minimum unit GE value required for automatic tracking.

Default:

```text
50,000 gp
```

Stacks are ignored for this calculation. For example, a stack of cheap runes will not be tracked just because the stack total is high.

### Display Group Storage Bank View

When enabled, tracked Group Storage items currently in your inventory are included in the bank-view display.s

### Inventory Tags

`Outline tracked items in inventory` enables highlighting for tracked items while Group Storage is open.

Defaults:

```text
Outline colour: FF00FF3A
Fill colour:    3D00FF13
Thickness:      0.5 pixels
```

The outline colour, fill colour, and decimal outline thickness are configurable.

## Screenshots

### Bank/Inventory Overlay

![Bank overlay showing missing group storage items](screenshots/bank-overlay.png)
![Inventory showing missing group storage items](screenshots/inventory-overlay.png)

### Sidebar Panel

![Sidebar panel with missing items and exclude controls](screenshots/sidebar-panel.png)

### Configuration

![Plugin configuration options](screenshots/configuration.png)

## Notes

This plugin uses local RuneLite client state. It remembers the last confirmed Group Storage contents across game sessions and refreshes that snapshot whenever Group Storage is opened or changed.

Until Group Storage is opened again, the plugin compares the saved snapshot with the current bank, inventory, and equipment state.

## Plugin Hub

Main plugin class:

```text
com.groupstoragetracker.GroupStorageTrackerPlugin
```
