# Hotbar Configurator

A client-side Fabric mod for Minecraft 1.21.1 that automates hotbar and inventory management. Define custom slot rules, auto-equip gear, switch profiles between dimensions, and keep items organized when looting chests.

---

## ✨ Features

- 🎯 **Custom Slot Rules:** Assign item IDs, native tags (`#minecraft:swords`), or potion/enchantment filters with priority fallbacks.
- 🧠 **Smart Sorting:** Automatically slots items by list priority, tier, durability, and enchantments without scrambling existing gear.
- 🌍 **3 Flexible Dimension Profiles:** Configure distinct layouts for any vanilla or modded dimensions (each profile can cover multiple dimensions) with optional auto-switching on portal/dimension travel.
- 🛡️ **Auto-Equip:** Equips armor, Elytras, shields, and Totems automatically.
- ⚡ **Container Quick-Move:** Shift-clicking from chests, barrels, and furnaces routes items directly into designated hotbar slots.
- 🔄 **Stack Refill:** Restocks depleted hotbar stacks from main inventory reserves with a keybind.

---

## ⌨️ Default Keybindings

Configure in **Options → Controls → Key Binds → Hotbar Configurator**:

| Action                       | Default Key    |
| :--------------------------- | :------------- |
| **Refill Hotbar**            | `,` _(Comma)_  |
| **Sort Hotbar**              | `.` _(Period)_ |
| **Cycle Profiles**           | _Unbound_      |
| **Select Profile 1 / 2 / 3** | _Unbound_      |

---

## 📦 Requirements

- **Minecraft:** `1.21.1` (Fabric)
- **Fabric API**
- **Cloth Config API** (`>= 15.0.0`)
- **Mod Menu** _(Recommended for in-game configuration)_

---

## ⚠️ Multiplayer Notice

Hotbar Configurator is 100% client-side and safe for singleplayer, Realms, and survival SMPs. Use caution on strict competitive PvP servers with anti-cheats (e.g. Hypixel), as automated inventory actions may be restricted by server rules.

---

## 📄 License

[MIT License](LICENSE)
