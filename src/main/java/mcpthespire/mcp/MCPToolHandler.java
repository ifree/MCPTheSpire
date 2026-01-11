package mcpthespire.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import mcpthespire.ChoiceScreenUtils;
import mcpthespire.CommandExecutor;
import mcpthespire.GameStateConverter;
import mcpthespire.GameStateListener;
import mcpthespire.InvalidCommandException;
import mcpthespire.MCPTheSpire;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;

import java.util.*;

/**
 * Handles MCP tool definitions and tool call execution.
 */
public class MCPToolHandler {

    private static final Logger logger = LogManager.getLogger(MCPToolHandler.class.getName());
    private static final Gson gson = new Gson();

    // Read-only tools that can be executed on any thread
    private static final Set<String> READ_ONLY_TOOLS = new HashSet<>(Arrays.asList(
        "get_game_state",
        "get_screen_state",
        "get_available_commands",
        "get_card_info",
        "get_relic_info"
    ));

    /**
     * Check if a tool is read-only and safe to execute on any thread.
     */
    public boolean isReadOnlyTool(String toolName) {
        return READ_ONLY_TOOLS.contains(toolName);
    }

    /**
     * Get all available tool definitions.
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // get_game_state - Get game state with optional filtering
        Map<String, Object> getGameStateProps = new HashMap<>();
        Map<String, Object> includeProp = new HashMap<>();
        includeProp.put("type", "array");
        includeProp.put("items", MCPProtocol.createProperty("string", "Section name"));
        includeProp.put("description", "Sections: player, deck, relics, potions, combat, screen (default=all). 'map' must be explicitly requested.");
        getGameStateProps.put("include", includeProp);
        tools.add(MCPProtocol.createToolDefinition(
            "get_game_state",
            "Get game state. Sections: player, deck, relics, potions, combat, screen (default=all). 'map' excluded by default (large/static) - request explicitly when needed.",
            MCPProtocol.createInputSchema(getGameStateProps, null)
        ));

        // get_screen_state - Recommended for most queries
        tools.add(MCPProtocol.createToolDefinition(
            "get_screen_state",
            "RECOMMENDED: Get current screen state (screen_type, choices, hand, monsters, buttons). Use this instead of get_game_state for routine checks.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // get_available_commands - Quick check what's available
        tools.add(MCPProtocol.createToolDefinition(
            "get_available_commands",
            "Get available tools for current screen with descriptions. Returns screen_type and available_tools list.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // get_card_info - Get detailed card information
        Map<String, Object> cardInfoProps = new HashMap<>();
        Map<String, Object> cardIdsProp = new HashMap<>();
        cardIdsProp.put("type", "array");
        cardIdsProp.put("items", MCPProtocol.createProperty("string", "Card ID"));
        cardIdsProp.put("description", "Card IDs to query (e.g., [\"Strike_R\", \"Defend_R\", \"Bash\"])");
        cardInfoProps.put("card_ids", cardIdsProp);
        tools.add(MCPProtocol.createToolDefinition(
            "get_card_info",
            "Get detailed card info (description, stats, upgraded version). Use when you need to know what a card does.",
            MCPProtocol.createInputSchema(cardInfoProps, Arrays.asList("card_ids"))
        ));

        // get_relic_info - Get detailed relic information
        Map<String, Object> relicInfoProps = new HashMap<>();
        Map<String, Object> relicIdsProp = new HashMap<>();
        relicIdsProp.put("type", "array");
        relicIdsProp.put("items", MCPProtocol.createProperty("string", "Relic ID"));
        relicIdsProp.put("description", "Relic IDs to query (e.g., [\"Burning Blood\", \"Vajra\", \"Snecko Eye\"])");
        relicInfoProps.put("relic_ids", relicIdsProp);
        tools.add(MCPProtocol.createToolDefinition(
            "get_relic_info",
            "Get detailed relic info (description, tier, flavor text). Use when you need to know what a relic does.",
            MCPProtocol.createInputSchema(relicInfoProps, Arrays.asList("relic_ids"))
        ));

        // execute_actions - PREFERRED for multiple actions
        Map<String, Object> executeActionsProps = new HashMap<>();
        Map<String, Object> actionsArrayProp = new HashMap<>();
        actionsArrayProp.put("type", "array");
        actionsArrayProp.put("description",
            "Array of action objects. Each object has 'action' field plus action-specific params. " +
            "Available actions: " +
            "play_card(card_index OR card_name OR card_id, target_index?), " +
            "end_turn, " +
            "choose(choice_index - 1-indexed position in choices list), " +
            "proceed, skip, cancel, confirm, " +
            "select_cards(drop:[cards] OR keep:[cards] - specify cards by index/name/id, auto-confirms), " +
            "use_potion(potion_slot, target_index?), " +
            "discard_potion(potion_slot), " +
            "wait(ms? - default 100ms, max 500ms).");
        executeActionsProps.put("actions", actionsArrayProp);
        tools.add(MCPProtocol.createToolDefinition(
            "execute_actions",
            "Execute multiple actions in sequence. INDICES ARE STABLE - use positions at call time, don't recalculate! " +
            "Examples: " +
            "(1) Play cards by name: [{action:'play_card',card_name:'Bash',target_index:1},{action:'play_card',card_name:'Strike',target_index:1}]. " +
            "(2) Play by index (stable): Hand [1:Strike,2:Defend,3:Bash] -> play 1 and 3: [{action:'play_card',card_index:1,target_index:1},{action:'play_card',card_index:3,target_index:1}]. " +
            "(3) Collect rewards: [{action:'choose',choice_index:1},{action:'choose',choice_index:2},{action:'proceed'}]. " +
            "(4) Survivor discard: [{action:'play_card',card_name:'Survivor'},{action:'select_cards',drop:['Strike']}]. " +
            "(5) Gambler's Chip keep best: [{action:'select_cards',keep:['Bash','Inflame']}].",
            MCPProtocol.createInputSchema(executeActionsProps, Arrays.asList("actions"))
        ));

        // --- Single action tools (use execute_actions when possible) ---

        // play_card
        Map<String, Object> playCardProps = new HashMap<>();
        playCardProps.put("card_index", MCPProtocol.createProperty("integer", "1-indexed position in hand (mutually exclusive with card_name/card_id)"));
        playCardProps.put("card_name", MCPProtocol.createProperty("string", "Card display name, e.g., 'Strike', 'Strike+' (mutually exclusive with card_index/card_id)"));
        playCardProps.put("card_id", MCPProtocol.createProperty("string", "Card internal ID, e.g., 'Strike_R', 'Defend_G' (mutually exclusive with card_index/card_name)"));
        playCardProps.put("target_index", MCPProtocol.createProperty("integer", "1-indexed monster position (required for targeted cards like Strike)"));
        tools.add(MCPProtocol.createToolDefinition(
            "play_card",
            "Play a card from hand. Specify card by ONE of: card_index, card_name, or card_id. Add target_index for attack cards. " +
            "Examples: {card_name:'Bash',target_index:1}, {card_index:3,target_index:1}, {card_name:'Defend'}. " +
            "Prefer execute_actions for playing multiple cards.",
            MCPProtocol.createInputSchema(playCardProps, null)  // None required, but one of index/name/id needed
        ));

        // end_turn
        tools.add(MCPProtocol.createToolDefinition(
            "end_turn",
            "End turn. Prefer execute_actions to combine with card plays.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // choose
        Map<String, Object> chooseProps = new HashMap<>();
        chooseProps.put("choice_index", MCPProtocol.createProperty("integer", "1-indexed position in the choices list (required)"));
        tools.add(MCPProtocol.createToolDefinition(
            "choose",
            "Select an option from the current screen's choices list by index. Use get_screen_state to see available choices first. Prefer execute_actions for multiple sequential choices.",
            MCPProtocol.createInputSchema(chooseProps, Arrays.asList("choice_index"))
        ));

        // use_potion
        Map<String, Object> usePotionProps = new HashMap<>();
        usePotionProps.put("potion_slot", MCPProtocol.createProperty("integer", "1-indexed potion slot position (required)"));
        usePotionProps.put("target_index", MCPProtocol.createProperty("integer", "1-indexed monster position (required for targeted potions like Fire Potion)"));
        tools.add(MCPProtocol.createToolDefinition(
            "use_potion",
            "Use a potion from your potion slots. Targeted potions (e.g., Fire Potion) require target_index.",
            MCPProtocol.createInputSchema(usePotionProps, Arrays.asList("potion_slot"))
        ));

        // discard_potion
        Map<String, Object> discardPotionProps = new HashMap<>();
        discardPotionProps.put("potion_slot", MCPProtocol.createProperty("integer", "1-indexed potion slot position (required)"));
        tools.add(MCPProtocol.createToolDefinition(
            "discard_potion",
            "Discard a potion to free up a slot for a new potion reward.",
            MCPProtocol.createInputSchema(discardPotionProps, Arrays.asList("potion_slot"))
        ));

        // proceed
        tools.add(MCPProtocol.createToolDefinition(
            "proceed",
            "Click the proceed/continue button to leave current screen. Available on: COMBAT_REWARD (after collecting), REST (after resting), SHOP_ROOM, CHEST, COMPLETE screens.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // confirm
        tools.add(MCPProtocol.createToolDefinition(
            "confirm",
            "Confirm current selection. Available on: GRID (card selection), HAND_SELECT (discard/exhaust selection) screens.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // skip
        tools.add(MCPProtocol.createToolDefinition(
            "skip",
            "Skip without selecting. Available on: CARD_REWARD (skip card pick), BOSS_REWARD (skip boss relic) screens.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // cancel
        tools.add(MCPProtocol.createToolDefinition(
            "cancel",
            "Go back or cancel current action. Available on: SHOP_SCREEN (leave shop), MAP (close map), GRID (cancel selection) screens.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // start_game - Start a new game
        Map<String, Object> startGameProps = new HashMap<>();
        startGameProps.put("character", MCPProtocol.createEnumProperty(
            "Character class to play (required)",
            Arrays.asList("IRONCLAD", "SILENT", "DEFECT", "WATCHER")
        ));
        startGameProps.put("ascension", MCPProtocol.createProperty("integer", "Ascension level 0-20 (optional, default: 0)"));
        startGameProps.put("seed", MCPProtocol.createProperty("string", "Alphanumeric seed for reproducible runs (optional)"));
        tools.add(MCPProtocol.createToolDefinition(
            "start_game",
            "Start a new run with the specified character. Only available from main menu (not in dungeon).",
            MCPProtocol.createInputSchema(startGameProps, Arrays.asList("character"))
        ));

        // continue_game - Continue saved game
        tools.add(MCPProtocol.createToolDefinition(
            "continue_game",
            "Continue a previously saved run. Only available from main menu when a save file exists.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // abandon_run - Abandon current run
        tools.add(MCPProtocol.createToolDefinition(
            "abandon_run",
            "Abandon the current run and delete the save file. Only available from main menu when a save file exists.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        // save_game - Save game
        tools.add(MCPProtocol.createToolDefinition(
            "save_game",
            "Save the current run and return to main menu. Only available while in a dungeon run.",
            MCPProtocol.createInputSchema(new HashMap<>(), null)
        ));

        return tools;
    }

    /**
     * Execute a tool call and return the result.
     */
    public Map<String, Object> executeTool(String toolName, JsonObject arguments) {
        try {
            switch (toolName) {
                case "get_game_state":
                    return executeGetGameState(arguments);

                case "get_screen_state":
                    return executeGetScreenState();

                case "get_available_commands":
                    return executeGetAvailableCommands();

                case "get_card_info":
                    return executeGetCardInfo(arguments);

                case "get_relic_info":
                    return executeGetRelicInfo(arguments);

                case "play_card":
                    return executePlayCard(arguments);

                case "end_turn":
                    return executeEndTurn();

                case "choose":
                    return executeChoose(arguments);

                case "use_potion":
                    return executeUsePotion(arguments);

                case "discard_potion":
                    return executeDiscardPotion(arguments);

                case "proceed":
                    return executeProceed();

                case "confirm":
                    return executeConfirm();

                case "skip":
                    return executeSkip();

                case "cancel":
                    return executeCancel();

                case "execute_actions":
                    // Handled by MCPServer for async execution
                    return MCPProtocol.buildToolCallResult("Error: execute_actions should be handled by MCPServer", true);

                case "start_game":
                    return executeStartGame(arguments);

                case "continue_game":
                    return executeContinueGame();

                case "abandon_run":
                    return executeAbandonRun();

                case "save_game":
                    return executeSaveGame();

                default:
                    return MCPProtocol.buildToolCallResult("Unknown tool: " + toolName, true);
            }
        } catch (InvalidCommandException e) {
            return MCPProtocol.buildToolCallResult("Error: " + e.getMessage(), true);
        } catch (Exception e) {
            logger.error("Error executing tool: " + toolName, e);
            return MCPProtocol.buildToolCallResult("Internal error: " + e.getMessage(), true);
        }
    }

    private Map<String, Object> executeGetGameState(JsonObject args) {
        Set<String> include = new HashSet<>();
        if (args != null && args.has("include") && args.get("include").isJsonArray()) {
            for (com.google.gson.JsonElement elem : args.getAsJsonArray("include")) {
                include.add(elem.getAsString().toLowerCase());
            }
        }
        HashMap<String, Object> state = GameStateConverter.getCommunicationState(include);
        return MCPProtocol.buildToolCallResultJson(state);
    }

    private Map<String, Object> executeGetScreenState() {
        HashMap<String, Object> state = GameStateConverter.getScreenOnlyState();
        return MCPProtocol.buildToolCallResultJson(state);
    }

    private Map<String, Object> executeGetAvailableCommands() {
        Map<String, Object> result = new HashMap<>();
        result.put("ready_for_command", GameStateListener.isWaitingForCommand());

        boolean isInGame = CommandExecutor.isInDungeon();
        result.put("in_game", isInGame);

        if (isInGame) {
            ChoiceScreenUtils.ChoiceType screenType = ChoiceScreenUtils.getCurrentChoiceType();
            result.put("screen_type", screenType.name());
            result.put("room_phase", AbstractDungeon.getCurrRoom().phase.toString());

            // Available tools based on current screen
            List<Map<String, Object>> availableTools = new ArrayList<>();

            // Combat actions
            if (AbstractDungeon.getCurrRoom().phase.equals(AbstractRoom.RoomPhase.COMBAT) && !AbstractDungeon.isScreenUp) {
                if (CommandExecutor.isPlayCommandAvailable()) {
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("tool", "play_card");
                    tool.put("description", "Play a card from hand (1-indexed)");
                    availableTools.add(tool);
                }
                if (CommandExecutor.isEndCommandAvailable()) {
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("tool", "end_turn");
                    tool.put("description", "End your turn");
                    availableTools.add(tool);
                }
                if (CommandExecutor.isPotionCommandAvailable()) {
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("tool", "use_potion");
                    tool.put("description", "Use a potion");
                    availableTools.add(tool);
                }
            }

            // Choice screen
            if (CommandExecutor.isChooseCommandAvailable()) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("tool", "choose");
                tool.put("description", "Make a choice from: " + ChoiceScreenUtils.getCurrentChoiceList());
                availableTools.add(tool);
            }

            // Proceed button
            if (ChoiceScreenUtils.isConfirmButtonAvailable()) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("tool", "proceed");
                tool.put("description", "Click " + ChoiceScreenUtils.getConfirmButtonText() + " button");
                availableTools.add(tool);
            }

            // Cancel button
            if (ChoiceScreenUtils.isCancelButtonAvailable()) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("tool", ChoiceScreenUtils.getCancelButtonText().equals("skip") ? "skip" : "cancel");
                tool.put("description", "Click " + ChoiceScreenUtils.getCancelButtonText() + " button");
                availableTools.add(tool);
            }

            result.put("available_tools", availableTools);
        } else {
            // Main menu
            result.put("screen_type", "MAIN_MENU");
            List<Map<String, Object>> availableTools = new ArrayList<>();

            if (CardCrawlGame.characterManager.anySaveFileExists()) {
                Map<String, Object> continueT = new HashMap<>();
                continueT.put("tool", "continue_game");
                continueT.put("description", "Continue saved game");
                availableTools.add(continueT);

                Map<String, Object> abandonT = new HashMap<>();
                abandonT.put("tool", "abandon_run");
                abandonT.put("description", "Abandon saved run");
                availableTools.add(abandonT);
            }

            Map<String, Object> startT = new HashMap<>();
            startT.put("tool", "start_game");
            startT.put("description", "Start new game with character");
            availableTools.add(startT);

            result.put("available_tools", availableTools);
        }

        return MCPProtocol.buildToolCallResultJson(result);
    }

    private Map<String, Object> executeGetCardInfo(JsonObject args) {
        Map<String, Object> result = new HashMap<>();
        List<Object> cards = new ArrayList<>();

        if (args != null && args.has("card_ids") && args.get("card_ids").isJsonArray()) {
            for (com.google.gson.JsonElement elem : args.getAsJsonArray("card_ids")) {
                String cardId = elem.getAsString();
                cards.add(GameStateConverter.getCardInfo(cardId));
            }
        }

        result.put("cards", cards);
        return MCPProtocol.buildToolCallResultJson(result);
    }

    private Map<String, Object> executeGetRelicInfo(JsonObject args) {
        Map<String, Object> result = new HashMap<>();
        List<Object> relics = new ArrayList<>();

        if (args != null && args.has("relic_ids") && args.get("relic_ids").isJsonArray()) {
            for (com.google.gson.JsonElement elem : args.getAsJsonArray("relic_ids")) {
                String relicId = elem.getAsString();
                relics.add(GameStateConverter.getRelicInfo(relicId));
            }
        }

        result.put("relics", relics);
        return MCPProtocol.buildToolCallResultJson(result);
    }

    private Map<String, Object> executePlayCard(JsonObject args) throws InvalidCommandException {
        int cardIndex = resolveCardIndex(args);
        StringBuilder command = new StringBuilder("play " + cardIndex);

        if (args.has("target_index") && !args.get("target_index").isJsonNull()) {
            command.append(" ").append(args.get("target_index").getAsInt());
        }

        boolean stateChanged = CommandExecutor.executeCommand(command.toString());
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }

        return MCPProtocol.buildToolCallResult("Card played successfully", false);
    }

    private Map<String, Object> executeEndTurn() throws InvalidCommandException {
        boolean stateChanged = CommandExecutor.executeCommand("end");
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }
        return MCPProtocol.buildToolCallResult("Turn ended", false);
    }

    private Map<String, Object> executeChoose(JsonObject args) throws InvalidCommandException {
        if (!args.has("choice_index") || args.get("choice_index").isJsonNull()) {
            throw new InvalidCommandException("choice_index is required");
        }

        String command = "choose " + args.get("choice_index").getAsInt();
        boolean stateChanged = CommandExecutor.executeCommand(command);
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }

        return MCPProtocol.buildToolCallResult("Choice made successfully", false);
    }

    private Map<String, Object> executeUsePotion(JsonObject args) throws InvalidCommandException {
        int potionSlot = args.get("potion_slot").getAsInt();
        StringBuilder command = new StringBuilder("potion use " + potionSlot);

        if (args.has("target_index") && !args.get("target_index").isJsonNull()) {
            command.append(" ").append(args.get("target_index").getAsInt());
        }

        boolean stateChanged = CommandExecutor.executeCommand(command.toString());
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }

        return MCPProtocol.buildToolCallResult("Potion used successfully", false);
    }

    private Map<String, Object> executeDiscardPotion(JsonObject args) throws InvalidCommandException {
        int potionSlot = args.get("potion_slot").getAsInt();
        String command = "potion discard " + potionSlot;

        boolean stateChanged = CommandExecutor.executeCommand(command);
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }

        return MCPProtocol.buildToolCallResult("Potion discarded", false);
    }

    private Map<String, Object> executeProceed() throws InvalidCommandException {
        boolean stateChanged = CommandExecutor.executeCommand("proceed");
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }
        return MCPProtocol.buildToolCallResult("Proceeded to next screen", false);
    }

    private Map<String, Object> executeConfirm() throws InvalidCommandException {
        boolean stateChanged = CommandExecutor.executeCommand("confirm");
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }
        return MCPProtocol.buildToolCallResult("Confirmed", false);
    }

    private Map<String, Object> executeSkip() throws InvalidCommandException {
        boolean stateChanged = CommandExecutor.executeCommand("skip");
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }
        return MCPProtocol.buildToolCallResult("Skipped", false);
    }

    private Map<String, Object> executeCancel() throws InvalidCommandException {
        boolean stateChanged = CommandExecutor.executeCommand("cancel");
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }
        return MCPProtocol.buildToolCallResult("Cancelled", false);
    }

    /**
     * Execute multiple actions in sequence.
     * Stops on first error or screen change.
     */
    private Map<String, Object> executeBatchActions(JsonObject params) {
        if (!params.has("actions") || !params.get("actions").isJsonArray()) {
            return MCPProtocol.buildToolCallResult("Error: 'actions' array is required", true);
        }

        com.google.gson.JsonArray actions = params.getAsJsonArray("actions");
        int total = actions.size();
        int successCount = 0;
        int failedIndex = -1;
        String failedAction = null;
        String errorMessage = null;
        boolean screenChanged = false;

        // Track initial screen state
        ChoiceScreenUtils.ChoiceType initialScreen = null;
        boolean initialInDungeon = CommandExecutor.isInDungeon();
        if (initialInDungeon) {
            initialScreen = ChoiceScreenUtils.getCurrentChoiceType();
        }

        for (int i = 0; i < total; i++) {
            JsonObject action = actions.get(i).getAsJsonObject();
            if (!action.has("action")) {
                failedIndex = i;
                errorMessage = "Missing 'action' field";
                break;
            }

            String actionType = action.get("action").getAsString();

            try {
                if ("wait".equals(actionType)) {
                    int waitMs = action.has("ms") ? action.get("ms").getAsInt() : 100;
                    Thread.sleep(Math.min(waitMs, 500));
                } else {
                    executeSingleBatchAction(actionType, action);
                }
                successCount++;

                // Check if screen changed after action (except for expected transitions)
                if (initialInDungeon && CommandExecutor.isInDungeon()) {
                    ChoiceScreenUtils.ChoiceType currentScreen = ChoiceScreenUtils.getCurrentChoiceType();
                    if (initialScreen != currentScreen) {
                        // Screen changed - stop batch execution
                        screenChanged = true;
                        if (i < total - 1) {
                            // There are more actions, but we stopped due to screen change
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                failedIndex = i;
                failedAction = actionType;
                errorMessage = e.getMessage();
                break;
            }
        }

        // Build minimal response
        Map<String, Object> response = new HashMap<>();

        if (errorMessage == null) {
            response.put("success", true);
            if (screenChanged && successCount < total) {
                response.put("message", "Executed " + successCount + "/" + total + " (screen changed)");
                response.put("partial", true);
            } else {
                response.put("message", "Executed " + successCount + " actions");
            }
        } else {
            response.put("success", false);
            response.put("executed", successCount);
            response.put("failed_at", failedIndex);
            if (failedAction != null) {
                response.put("failed_action", failedAction);
            }
            response.put("error", errorMessage);
        }

        // Include minimal state info
        if (CommandExecutor.isInDungeon()) {
            ChoiceScreenUtils.ChoiceType screenType = ChoiceScreenUtils.getCurrentChoiceType();
            response.put("screen", screenType.name());

            if (AbstractDungeon.getCurrRoom().phase.equals(AbstractRoom.RoomPhase.COMBAT) && !AbstractDungeon.isScreenUp) {
                response.put("hand_size", AbstractDungeon.player.hand.size());
                response.put("energy", AbstractDungeon.player.energy.energy);
            } else if (CommandExecutor.isChooseCommandAvailable()) {
                response.put("choices", ChoiceScreenUtils.getCurrentChoiceList().size());
            }

            response.put("can_proceed", ChoiceScreenUtils.isConfirmButtonAvailable());
        }

        return MCPProtocol.buildToolCallResultJson(response);
    }

    /**
     * Execute a single action from batch.
     * Public so MCPServer can call it for async batch execution.
     */
    public void executeSingleBatchAction(String actionType, JsonObject params) throws InvalidCommandException {
        switch (actionType) {
            case "play_card": {
                int cardIndex = resolveCardIndex(params);
                StringBuilder command = new StringBuilder("play " + cardIndex);
                if (params.has("target_index") && !params.get("target_index").isJsonNull()) {
                    command.append(" ").append(params.get("target_index").getAsInt());
                }
                CommandExecutor.executeCommand(command.toString());
                break;
            }
            case "end_turn":
                CommandExecutor.executeCommand("end");
                break;
            case "choose": {
                if (!params.has("choice_index") || params.get("choice_index").isJsonNull()) {
                    throw new InvalidCommandException("choose requires choice_index");
                }
                String command = "choose " + params.get("choice_index").getAsInt();
                CommandExecutor.executeCommand(command);
                break;
            }
            case "proceed":
                CommandExecutor.executeCommand("proceed");
                break;
            case "skip":
                CommandExecutor.executeCommand("skip");
                break;
            case "cancel":
                CommandExecutor.executeCommand("cancel");
                break;
            case "use_potion": {
                int potionSlot = params.get("potion_slot").getAsInt();
                StringBuilder command = new StringBuilder("potion use " + potionSlot);
                if (params.has("target_index") && !params.get("target_index").isJsonNull()) {
                    command.append(" ").append(params.get("target_index").getAsInt());
                }
                CommandExecutor.executeCommand(command.toString());
                break;
            }
            case "discard_potion": {
                int potionSlot = params.get("potion_slot").getAsInt();
                CommandExecutor.executeCommand("potion discard " + potionSlot);
                break;
            }
            case "confirm":
                CommandExecutor.executeCommand("confirm");
                break;
            case "select_cards":
                executeSelectCards(params);
                break;
            default:
                throw new InvalidCommandException("Unknown action: " + actionType);
        }
        GameStateListener.registerCommandExecution();
    }

    /**
     * Execute select_cards action for HAND_SELECT screen.
     * Supports both 'drop' (select these cards) and 'keep' (select all except these).
     * Cards can be specified by index (int), name (string), or id (string).
     */
    private void executeSelectCards(JsonObject params) throws InvalidCommandException {
        // Verify we're on HAND_SELECT screen
        ChoiceScreenUtils.ChoiceType screenType = ChoiceScreenUtils.getCurrentChoiceType();
        if (screenType != ChoiceScreenUtils.ChoiceType.HAND_SELECT) {
            throw new InvalidCommandException("select_cards requires HAND_SELECT screen, current: " + screenType);
        }

        // Get current hand cards
        ArrayList<AbstractCard> handCards = AbstractDungeon.player.hand.group;
        if (handCards.isEmpty()) {
            throw new InvalidCommandException("No cards in hand to select");
        }

        // Parse drop or keep parameter
        boolean hasDropparam = params.has("drop") && !params.get("drop").isJsonNull();
        boolean hasKeep = params.has("keep") && !params.get("keep").isJsonNull();

        if (!hasDropparam && !hasKeep) {
            throw new InvalidCommandException("select_cards requires 'drop' or 'keep' parameter");
        }
        if (hasDropparam && hasKeep) {
            throw new InvalidCommandException("select_cards: use either 'drop' or 'keep', not both");
        }

        // Resolve which cards to select (drop mode = select these, keep mode = select others)
        Set<Integer> indicesToSelect = new HashSet<>();

        if (hasDropparam) {
            // Drop mode: select the specified cards
            indicesToSelect = resolveCardIndices(params.getAsJsonArray("drop"), handCards);
        } else {
            // Keep mode: select all cards EXCEPT the specified ones
            Set<Integer> keepIndices = resolveCardIndices(params.getAsJsonArray("keep"), handCards);
            for (int i = 0; i < handCards.size(); i++) {
                if (!keepIndices.contains(i)) {
                    indicesToSelect.add(i);
                }
            }
        }

        // Select the cards (in order to avoid index shifting issues)
        List<Integer> sortedIndices = new ArrayList<>(indicesToSelect);
        Collections.sort(sortedIndices);

        for (int index : sortedIndices) {
            if (index >= 0 && index < handCards.size()) {
                ChoiceScreenUtils.makeHandSelectScreenChoice(index);
            }
        }

        // Auto-confirm if button is available
        if (ChoiceScreenUtils.isConfirmButtonAvailable()) {
            ChoiceScreenUtils.pressConfirmButton();
        }
    }

    /**
     * Resolve a JsonArray of card specifiers to hand indices.
     * Supports: integers (1-indexed), strings (name or id).
     */
    private Set<Integer> resolveCardIndices(com.google.gson.JsonArray cards, ArrayList<AbstractCard> handCards) throws InvalidCommandException {
        Set<Integer> indices = new HashSet<>();

        for (com.google.gson.JsonElement elem : cards) {
            if (elem.isJsonPrimitive()) {
                if (elem.getAsJsonPrimitive().isNumber()) {
                    // Index (1-indexed)
                    int idx = elem.getAsInt() - 1;  // Convert to 0-indexed
                    if (idx < 0 || idx >= handCards.size()) {
                        throw new InvalidCommandException("Card index " + (idx + 1) + " out of bounds (hand has " + handCards.size() + " cards)");
                    }
                    indices.add(idx);
                } else if (elem.getAsJsonPrimitive().isString()) {
                    // Name or ID
                    String spec = elem.getAsString();
                    int foundIndex = findCardInHand(spec, handCards);
                    if (foundIndex == -1) {
                        throw new InvalidCommandException("Card not found in hand: " + spec);
                    }
                    indices.add(foundIndex);
                }
            }
        }

        return indices;
    }

    /**
     * Find a card in hand by name, id, or uuid.
     * Returns 0-indexed position, or -1 if not found.
     */
    private int findCardInHand(String spec, ArrayList<AbstractCard> handCards) {
        // Check for UUID prefix (from stable index conversion)
        if (spec.startsWith("uuid:")) {
            String uuidStr = spec.substring(5);
            for (int i = 0; i < handCards.size(); i++) {
                if (handCards.get(i).uuid.toString().equals(uuidStr)) {
                    return i;
                }
            }
            return -1;
        }

        // First try exact name match
        for (int i = 0; i < handCards.size(); i++) {
            if (handCards.get(i).name.equalsIgnoreCase(spec)) {
                return i;
            }
        }
        // Then try ID match
        for (int i = 0; i < handCards.size(); i++) {
            if (handCards.get(i).cardID.equalsIgnoreCase(spec)) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, Object> executeStartGame(JsonObject args) throws InvalidCommandException {
        String character = args.get("character").getAsString().toLowerCase();
        StringBuilder command = new StringBuilder("start " + character);

        if (args.has("ascension") && !args.get("ascension").isJsonNull()) {
            command.append(" ").append(args.get("ascension").getAsInt());
        }

        if (args.has("seed") && !args.get("seed").isJsonNull()) {
            command.append(" ").append(args.get("seed").getAsString());
        }

        boolean stateChanged = CommandExecutor.executeCommand(command.toString());
        if (stateChanged) {
            GameStateListener.registerCommandExecution();
        }

        return MCPProtocol.buildToolCallResult("Game started with " + character, false);
    }

    private Map<String, Object> executeContinueGame() throws InvalidCommandException {
        CommandExecutor.executeCommand("continue");
        return MCPProtocol.buildToolCallResult("Continuing saved game", false);
    }

    private Map<String, Object> executeAbandonRun() throws InvalidCommandException {
        CommandExecutor.executeCommand("abandon");
        return MCPProtocol.buildToolCallResult("Run abandoned", false);
    }

    private Map<String, Object> executeSaveGame() throws InvalidCommandException {
        CommandExecutor.executeCommand("save");
        return MCPProtocol.buildToolCallResult("Game saved", false);
    }

    /**
     * Resolve card index from params. Supports:
     * - card_uuid: match by unique card instance UUID (used internally for stable batch execution)
     * - card_index: direct 1-indexed position
     * - card_name: match by display name (e.g., "Strike", "Strike+")
     * - card_id: match by internal ID (e.g., "Strike_R", "Defend_G")
     * If multiple cards match by name/id, returns the first (leftmost) one.
     */
    private int resolveCardIndex(JsonObject params) throws InvalidCommandException {
        // Need hand access for all lookups except direct index
        ArrayList<AbstractCard> hand = null;
        if (CommandExecutor.isInDungeon() && AbstractDungeon.player != null) {
            hand = AbstractDungeon.player.hand.group;
        }

        // Priority 1: Card UUID (for stable batch execution - internal use)
        if (params.has("card_uuid") && !params.get("card_uuid").isJsonNull()) {
            if (hand == null) {
                throw new InvalidCommandException("Cannot resolve card: not in dungeon");
            }
            String uuidStr = params.get("card_uuid").getAsString();
            try {
                java.util.UUID targetUuid = java.util.UUID.fromString(uuidStr);
                for (int i = 0; i < hand.size(); i++) {
                    if (hand.get(i).uuid.equals(targetUuid)) {
                        return i + 1; // 1-indexed
                    }
                }
                throw new InvalidCommandException("Card no longer in hand (was it already played?)");
            } catch (IllegalArgumentException e) {
                throw new InvalidCommandException("Invalid card UUID: " + uuidStr);
            }
        }

        // Priority 2: Direct index
        if (params.has("card_index") && !params.get("card_index").isJsonNull()) {
            return params.get("card_index").getAsInt();
        }

        if (hand == null) {
            throw new InvalidCommandException("Cannot resolve card: not in dungeon");
        }

        // Priority 3: Card name (display name, case-insensitive)
        if (params.has("card_name") && !params.get("card_name").isJsonNull()) {
            String cardName = params.get("card_name").getAsString();
            for (int i = 0; i < hand.size(); i++) {
                if (hand.get(i).name.equalsIgnoreCase(cardName)) {
                    return i + 1; // 1-indexed
                }
            }
            throw new InvalidCommandException("Card not found in hand: " + cardName +
                ". Available: " + getHandCardNames(hand));
        }

        // Priority 4: Card ID (internal ID, case-insensitive)
        if (params.has("card_id") && !params.get("card_id").isJsonNull()) {
            String cardId = params.get("card_id").getAsString();
            for (int i = 0; i < hand.size(); i++) {
                if (hand.get(i).cardID.equalsIgnoreCase(cardId)) {
                    return i + 1; // 1-indexed
                }
            }
            throw new InvalidCommandException("Card not found in hand with ID: " + cardId);
        }

        throw new InvalidCommandException("play_card requires card_index, card_name, or card_id");
    }

    /**
     * Get a comma-separated list of card names in hand for error messages.
     */
    private String getHandCardNames(ArrayList<AbstractCard> hand) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < hand.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(hand.get(i).name);
        }
        sb.append("]");
        return sb.toString();
    }
}
