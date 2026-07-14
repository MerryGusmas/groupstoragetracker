# Group Storage Tracker

Group Storage Tracker is a RuneLite plugin for Group Ironman accounts that helps track valuable shared-storage items which have been withdrawn and not returned.

The plugin learns items from Group Storage, watches bank, inventory, and equipment state, and shows a group storage/bank-only item viewer so you can quickly see what needs to go back into Group Storage.

## Features

- Automatically tracks items seen in Group Storage above a configurable GE value.
- Default minimum item value is `100,000 gp`.
- Supports manually included items by item name or item ID.
- Shows tracked items that are currently in the bank, inventory, or equipped.

## Bank Viewer

When the bank is open, the plugin displays an inventory-viewer-style overlay containing tracked items that are not currently in Group Storage.

Items are outlined by location:

- **Red**: item is in the bank
- **Green**: item is in inventory
- **Orange**: item is equipped

If an item appears in multiple locations, the overlay can show multiple outlines.

## Sidebar Panel

The plugin is available via the 'Group Ironman' icon in the RuneLite sidebar.

Clicking on the sidebar icon shows a window with:

- items currently not in Group Storage
- a collapsible section for items currently detected in Group Storage
- item name, location, quantity, and GE value
- an `Exclude` action for excluding items from the tracking system

## Including Items

Open Group Storage. Any item seen there with a GE value greater than the configured minimum value will be tracked.

You can also hold `Shift` and right-click a bank item, then choose:

```text
Include in group storage tracker
```

## Excluding Items

Items can be excluded from the tracker UI from the sidebar panel.

Right-click an item row or use the visible `Exclude` button.

Excluded items are persisted per RuneScape profile until the plugin is reset or the item is manually included again.

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
100,000 gp
```

Stacks are ignored for this calculation. For example, a stack of cheap runes will not be tracked just because the stack total is high.

### Manual Tracked Items

A comma-separated or newline-separated list of item names or item IDs that should always be tracked.

### Show Inventory Items

When enabled, items currently in your inventory are included in the missing-items display.

### Hide Stored Items

When enabled, the sidebar focuses on items outside Group Storage and hides items that are only stored.

## Screenshots

### Bank Overlay

![Bank overlay showing missing group storage items](screenshots/bank-overlay.png)

### Sidebar Panel

![Sidebar panel with missing items and exclude controls](screenshots/sidebar-panel.png)

### Configuration

![Plugin configuration options](screenshots/configuration.png)
## Notes

This plugin uses local RuneLite client state. It can only compare against Group Storage contents after Group Storage has been opened and seen by the client.

If Group Storage has not been opened during the session, the plugin may only know about previously cached tracked items and current bank, inventory, and equipment state.

## Plugin Hub

Main plugin class:

```text
com.groupstoragetracker.GroupStorageTrackerPlugin
```