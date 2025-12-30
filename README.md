# MCPTheSpire

[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)

A Slay the Spire mod that enables AI agents to play the game through the Model Context Protocol (MCP). This allows AI assistants like Claude, GPT, or other MCP-compatible clients to interact with and control Slay the Spire gameplay programmatically.

## Features

- **MCP Protocol Support**: Full implementation of Model Context Protocol using Streamable HTTP transport
- **Real-time Game Control**: AI agents can play cards, make choices, use potions, and navigate menus
- **Batch Actions**: Execute multiple actions in a single call for efficient gameplay
- **Game State Access**: Comprehensive access to player stats, hand, monsters, screen information, and card details on demand
- **Thread-safe Operations**: Read-only operations execute immediately, game-changing actions are queued for safe execution on the game thread
- **Configurable Server**: Customizable host and port settings

## Requirements

- **Java 8+**
- **Slay the Spire** (base game)
- **ModTheSpire** (mod loader)
- **BaseMod** (required dependency)
- An MCP-compatible AI client (Claude Desktop, custom MCP clients, etc.)

## Installation

1. Install [ModTheSpire](https://github.com/kiooeht/ModTheSpire) following their installation guide
2. Install [BaseMod](https://github.com/daviscook477/BaseMod) if not already installed
3. Download the latest `MCPTheSpire.jar` from the releases page
4. Place the JAR file in your ModTheSpire mods directory
5. Launch Slay the Spire through ModTheSpire
6. Enable the "MCP The Spire" mod in the ModTheSpire menu

## Configuration

The mod can be configured through the config file at `MCPTheSpire/config.properties`:

```properties
host=127.0.0.1
port=8080
```

## Usage

### Connecting an AI Client

Configure your MCP client to connect to:
```
http://127.0.0.1:8080/mcp
```

For Claude Desktop, add to your MCP config:
```json
{
  "mcpServers": {
    "slay-the-spire": {
      "url": "http://127.0.0.1:8080/mcp",
      "transport": "streamable-http"
    }
  }
}
```

## MCP Tools Reference

### State Query Tools (Read-only, instant response)

#### `get_game_state`
Get complete game state with optional filtering.

**Parameters:**
- `include` (optional): Array of sections to include: `player`, `deck`, `relics`, `potions`, `combat`, `screen`
- Note: `map` is **excluded by default** (large/static data) - add `"map"` to include array if needed

**Example:**
```json
{"include": ["player", "combat", "map"]}
```

#### `get_screen_state`
Get lightweight current screen state. **Recommended for most use cases.**

**Returns:**
- `screen_type`: Current screen (COMBAT_REWARD, CARD_REWARD, MAP, etc.)
- `room_phase`: Room phase (COMBAT, COMPLETE, etc.)
- `choice_list`: Available choices
- `can_proceed` / `can_cancel`: Button availability
- `hand`, `monsters`, `energy`: Combat info (if in combat)

#### `get_available_commands`
Get context-aware list of available tools for current screen.

**Returns:**
```json
{
  "screen_type": "COMBAT_REWARD",
  "available_tools": [
    {"tool": "choose", "description": "Make a choice from: [gold, card]"},
    {"tool": "proceed", "description": "Click proceed button"}
  ]
}
```

#### `get_card_info`
Get detailed card information including description and upgraded version.

**When to use:** Card objects in game state are optimized for size and don't include descriptions. Use this tool when you need to know what a card does.

**Parameters:**
- `card_ids` (required): Array of card IDs to query

**Example:**
```json
{"card_ids": ["Strike_R", "Bash", "Defend_R"]}
```

**Returns:**
```json
{
  "cards": [
    {
      "id": "Bash",
      "name": "Bash",
      "type": "ATTACK",
      "rarity": "BASIC",
      "cost": 2,
      "description": "Deal !D! damage. Apply !M! Vulnerable.",
      "base_damage": 8,
      "base_magic_number": 2,
      "has_target": true,
      "upgraded": {
        "name": "Bash+",
        "description": "Deal !D! damage. Apply !M! Vulnerable.",
        "base_damage": 10,
        "base_magic_number": 3
      }
    }
  ]
}
```

### Batch Actions (Recommended)

#### `execute_actions`
Execute multiple actions in sequence. **This is the most efficient way to play.**

Actions wait for the game to be ready (animations complete, state updated) before each execution. This ensures reliable execution without manual timing.

**Key Feature: Stable Indices** - All `card_index` and `choice_index` values refer to positions **at the time you called the tool**, not after each action executes. You don't need to recalculate indices as cards are played or choices are made.

**Example - Stable card_index:**
```
Your hand: [1: Strike, 2: Defend, 3: Bash, 4: Strike, 5: Defend]
You want to play: Strike(1), Bash(3), Strike(4)

CORRECT - use original positions:
  card_index: 1, 3, 4

WRONG - don't recalculate after each play:
  card_index: 1, 2, 2  ← This is incorrect!
```

**Example - Stable choice_index:**
```
Rewards: [1: gold, 2: potion, 3: card, 4: relic]
You want: gold(1) and card(3)

CORRECT: choice_index: 1, 3
WRONG:   choice_index: 1, 2  ← Don't recalculate!
```

**Parameters:**
- `actions`: Array of action objects

**Supported Actions:**

| Action | Parameters | Description |
|--------|------------|-------------|
| `play_card` | `card_index` OR `card_name` OR `card_id`, `target_index?` | Play a card by position, name, or ID |
| `end_turn` | - | End turn |
| `choose` | `choice_index` | Make a choice (1-indexed) |
| `select_cards` | `drop` OR `keep` (array) | Select cards on HAND_SELECT screen |
| `proceed` | - | Click proceed button |
| `confirm` | - | Confirm selection (grid/hand select) |
| `skip` | - | Skip card reward |
| `cancel` | - | Cancel/return |
| `use_potion` | `potion_slot`, `target_index?` | Use potion |
| `discard_potion` | `potion_slot` | Discard potion |
| `wait` | `ms?` | Wait (max 500ms) |

**Action Parameters Detail:**

`play_card` - Play a card from hand
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `card_index` | integer | One of three | 1-indexed position in hand (stable) |
| `card_name` | string | One of three | Card display name (e.g., "Strike", "Strike+") |
| `card_id` | string | One of three | Card internal ID (e.g., "Strike_R") |
| `target_index` | integer | For targeted | 1-indexed monster target |

`choose` - Make a choice on current screen
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `choice_index` | integer | Yes | 1-indexed choice position (stable) |

`select_cards` - Select cards on HAND_SELECT screen
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `drop` | array | One of two | Cards to select (by index/name/id) |
| `keep` | array | One of two | Cards to keep (selects all others) |

`use_potion` - Use a potion
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `potion_slot` | integer | Yes | 1-indexed potion slot |
| `target_index` | integer | For targeted | 1-indexed monster target |

`discard_potion` - Discard a potion
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `potion_slot` | integer | Yes | 1-indexed potion slot |

`wait` - Wait for animations/state
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `ms` | integer | No | Milliseconds to wait (default: 100, max: 500) |

**Example - Play cards by name (recommended):**
```json
{
  "actions": [
    {"action": "play_card", "card_name": "Defend"},
    {"action": "play_card", "card_name": "Strike", "target_index": 1},
    {"action": "play_card", "card_name": "Bash", "target_index": 1},
    {"action": "end_turn"}
  ]
}
```

**Using card_name is recommended** - it automatically finds the card in your current hand, so you don't need to track index changes.

**Alternative - Play by index (indices are stable):**
```json
{
  "actions": [
    {"action": "play_card", "card_index": 1},
    {"action": "play_card", "card_index": 3},
    {"action": "play_card", "card_index": 5},
    {"action": "end_turn"}
  ]
}
```
Indices are **automatically stabilized** - `card_index: 1, 3, 5` plays the cards at those positions **when you called the tool**. No need to account for hand changes as cards are played.

**Example - Take rewards and proceed (indices are stable):**
```json
{
  "actions": [
    {"action": "choose", "choice_index": 1},
    {"action": "choose", "choice_index": 2},
    {"action": "proceed"}
  ]
}
```

**Example - select_cards for HAND_SELECT screen (e.g., after Survivor):**
```json
{
  "actions": [
    {"action": "play_card", "card_name": "Survivor"},
    {"action": "select_cards", "drop": ["Strike"]}
  ]
}
```

The `select_cards` action supports two modes:
- `drop`: Select these cards (to be discarded/affected)
- `keep`: Keep these cards, select all others

**Note:** `select_cards` automatically confirms after selection. No need for a separate `confirm` action.

Cards can be specified by:
- **Index**: `[1, 3]` (1-indexed, uses stable initial positions)
- **Name**: `["Strike", "Defend"]`
- **ID**: `["Strike_R", "Defend_G"]`
- **Mixed**: `[1, "Bash", "Defend_R"]`

**Example - Gambler's Chip (keep specific cards, discard rest):**
```json
{
  "actions": [
    {"action": "select_cards", "keep": ["Bash", "Inflame"]},
    {"action": "play_card", "card_name": "Bash"}
  ]
}
```

**Returns:**
```json
{
  "success": true,
  "message": "Executed 4 actions",
  "screen": "NONE",
  "hand_size": 2,
  "energy": 0,
  "can_proceed": false
}
```

On error:
```json
{
  "success": false,
  "executed": 2,
  "failed_at": 2,
  "failed_action": "play_card",
  "error": "Card not playable"
}
```

### Individual Action Tools

These execute single actions. Use `execute_actions` for efficiency when possible.

#### `play_card`
Play a card from hand. Specify ONE of:
- `card_name`: Card display name (e.g., "Strike", "Strike+", "Defend")
- `card_id`: Card internal ID (e.g., "Strike_R", "Defend_G")
- `card_index`: 1-indexed card position

Optional:
- `target_index`: 1-indexed monster target (for targeted cards)

**Examples:** `{card_name:'Bash',target_index:1}`, `{card_index:3,target_index:1}`, `{card_name:'Defend'}`

#### `end_turn`
End your current turn in combat.

#### `choose`
Make a choice from the current screen.
- `choice_index` (required): 1-indexed choice position

#### `use_potion`
Use a potion.
- `potion_slot` (required): 1-indexed potion slot
- `target_index` (optional): 1-indexed target

#### `discard_potion`
Discard a potion.
- `potion_slot` (required): 1-indexed potion slot

### Navigation Tools

| Tool | Description | Use When |
|------|-------------|----------|
| `proceed` | Leave current screen | Combat rewards, rest site, shop room, chest |
| `confirm` | Confirm selection | Grid select, hand select |
| `skip` | Skip without selecting | Card reward screen |
| `cancel` | Go back | Shop screen, map, boss reward |

### Game Management Tools

#### `start_game`
Start a new game.
- `character` (required): IRONCLAD, SILENT, DEFECT, or WATCHER
- `ascension` (optional): 0-20
- `seed` (optional): Alphanumeric seed

#### `continue_game`
Continue from save file.

#### `abandon_run`
Abandon current run.

#### `save_game`
Save and return to menu.

## Screen Types Reference

| Screen Type | Available Actions |
|-------------|-------------------|
| `NONE` (Combat) | `play_card`, `end_turn`, `use_potion` |
| `COMBAT_REWARD` | `choose` (rewards), `proceed` |
| `CARD_REWARD` | `choose` (cards), `skip` |
| `MAP` | `choose` (nodes), `cancel` |
| `REST` | `choose` (rest/smith), `proceed` |
| `EVENT` | `choose` (options) |
| `SHOP_SCREEN` | `choose` (items), `cancel` |
| `SHOP_ROOM` | `choose`, `proceed` |
| `BOSS_REWARD` | `choose` (relics), `skip` |
| `GRID` | `choose`, `confirm` |
| `HAND_SELECT` | `select_cards` (auto-confirms) |

## Data Structures

Game state objects are optimized for minimal size. Fields are only included when meaningful.

### Card Object
| Field | Always | Description |
|-------|--------|-------------|
| `name`, `uuid`, `id`, `type`, `cost` | Yes | Core identity |
| `upgrades` | If > 0 | Times upgraded |
| `is_playable` | In combat | Can be played |
| `has_target` | If true | Requires target |
| `exhausts` | If true | Exhausts on play |
| `damage`, `block`, `magic_number` | If > 0 | Current values |
| `base_damage`, `base_block`, `base_magic_number` | If different | Base values (when modified) |

**Note:** Card descriptions are not included. Use `get_card_info` to query descriptions.

### Monster Object
| Field | Always | Description |
|-------|--------|-------------|
| `id`, `name`, `current_hp`, `max_hp`, `is_gone` | Yes | Core info |
| `intent` | Yes | Current intent |
| `move.damage`, `move.hits` | If attacking | Attack details |
| `block` | If > 0 | Current block |
| `powers` | If any | Active powers |
| `half_dead` | If true | Half dead state |

## Building from Source

```bash
# Clone repository
git clone <repo-url>
cd MCPTheSpire

# Set up dependencies in lib/
# - desktop-1.0.jar (from Slay the Spire)
# - ModTheSpire.jar
# - BaseMod.jar

# Build
mvn clean package

# Output: target/MCPTheSpire.jar
```

## Architecture

- **`MCPServer`**: HTTP server with Streamable HTTP transport
- **`MCPToolHandler`**: Tool definitions and execution
- **`GameStateConverter`**: Game state serialization
- **`CommandExecutor`**: Safe command execution on game thread
- **Patches**: SpirePatch hooks for game events

## License

MIT License - see [LICENSE](LICENSE) file.

## Acknowledgments

- [CommunicationMod](https://github.com/ForgottenArbiter/CommunicationMod) - design inspiration
- [TelnetTheSpire](https://github.com/cdaymand/TelnetTheSpire/tree/master) - design inspiration
- [ModTheSpire](https://github.com/kiooeht/ModTheSpire) - Mod loading framework
- [BaseMod](https://github.com/daviscook477/BaseMod) - Essential modding library
