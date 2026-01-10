package mcpthespire;

import basemod.ReflectionHacks;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.core.AbstractCreature;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.events.AbstractEvent;
import com.megacrit.cardcrawl.map.MapEdge;
import com.megacrit.cardcrawl.map.MapRoomNode;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.monsters.EnemyMoveInfo;
import com.megacrit.cardcrawl.neow.NeowEvent;
import com.megacrit.cardcrawl.orbs.AbstractOrb;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.powers.AbstractPower;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.RunicDome;
import com.megacrit.cardcrawl.rewards.RewardItem;
import com.megacrit.cardcrawl.rooms.*;
import com.megacrit.cardcrawl.screens.DeathScreen;
import com.megacrit.cardcrawl.screens.VictoryScreen;
import com.megacrit.cardcrawl.screens.select.GridCardSelectScreen;
import com.megacrit.cardcrawl.shop.ShopScreen;
import com.megacrit.cardcrawl.shop.StorePotion;
import com.megacrit.cardcrawl.shop.StoreRelic;
import com.megacrit.cardcrawl.ui.buttons.LargeDialogOptionButton;
import com.megacrit.cardcrawl.ui.panels.EnergyPanel;
import mcpthespire.patches.UpdateBodyTextPatch;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class GameStateConverter {

    public static HashMap<String, Object> getCommunicationState() {
        return getCommunicationState(null);
    }

    public static HashMap<String, Object> getCommunicationState(Set<String> include) {
        HashMap<String, Object> response = new HashMap<>();
        response.put("ready_for_command", GameStateListener.isWaitingForCommand());
        boolean isInGame = CommandExecutor.isInDungeon();
        response.put("in_game", isInGame);
        if(isInGame) {
            response.put("game_state", getGameState(include));
        }
        return response;
    }

    /**
     * Get only the current screen state without full game state.
     * This is a lighter alternative to getCommunicationState.
     */
    public static HashMap<String, Object> getScreenOnlyState() {
        HashMap<String, Object> response = new HashMap<>();
        response.put("ready_for_command", GameStateListener.isWaitingForCommand());
        boolean isInGame = CommandExecutor.isInDungeon();
        response.put("in_game", isInGame);

        if(isInGame) {
            ChoiceScreenUtils.ChoiceType screenType = ChoiceScreenUtils.getCurrentChoiceType();
            response.put("screen_type", screenType.name());
            response.put("screen_name", AbstractDungeon.screen.name());
            response.put("is_screen_up", AbstractDungeon.isScreenUp);
            response.put("room_phase", AbstractDungeon.getCurrRoom().phase.toString());
            response.put("room_type", AbstractDungeon.getCurrRoom().getClass().getSimpleName());

            // Current player status (minimal)
            response.put("current_hp", AbstractDungeon.player.currentHealth);
            response.put("max_hp", AbstractDungeon.player.maxHealth);
            response.put("floor", AbstractDungeon.floorNum);
            response.put("gold", AbstractDungeon.player.gold);

            // Choice information
            if(CommandExecutor.isChooseCommandAvailable()) {
                response.put("choice_list", ChoiceScreenUtils.getCurrentChoiceList());
            }

            // Button availability
            response.put("can_proceed", ChoiceScreenUtils.isConfirmButtonAvailable());
            response.put("can_cancel", ChoiceScreenUtils.isCancelButtonAvailable());
            if(ChoiceScreenUtils.isConfirmButtonAvailable()) {
                response.put("proceed_button", ChoiceScreenUtils.getConfirmButtonText());
            }
            if(ChoiceScreenUtils.isCancelButtonAvailable()) {
                response.put("cancel_button", ChoiceScreenUtils.getCancelButtonText());
            }

            // Screen-specific state
            response.put("screen_state", getScreenState());

            // Combat hand if in combat
            if(AbstractDungeon.getCurrRoom().phase.equals(AbstractRoom.RoomPhase.COMBAT)) {
                ArrayList<Object> hand = new ArrayList<>();
                for(AbstractCard card : AbstractDungeon.player.hand.group) {
                    hand.add(convertCardToJson(card));
                }
                response.put("hand", hand);
                response.put("energy", AbstractDungeon.player.energy.energy);

                ArrayList<Object> monsters = new ArrayList<>();
                for(AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
                    if (!monster.isDeadOrEscaped()) {
                        HashMap<String, Object> m = new HashMap<>();
                        m.put("name", monster.name);
                        m.put("current_hp", monster.currentHealth);
                        m.put("max_hp", monster.maxHealth);
                        m.put("intent", monster.intent.name());
                        m.put("is_gone", monster.isDeadOrEscaped());
                        monsters.add(m);
                    }
                }
                response.put("monsters", monsters);
            }
        } else {
            // Main menu state
            response.put("screen_type", "MAIN_MENU");
            response.put("available_commands", CommandExecutor.getAvailableCommands());
        }

        return response;
    }

    /**
     * Get detailed card information by card ID.
     * Returns card description and full stats.
     */
    public static HashMap<String, Object> getCardInfo(String cardId) {
        HashMap<String, Object> result = new HashMap<>();
        AbstractCard card = CardLibrary.getCard(cardId);
        if (card == null) {
            result.put("error", "Card not found: " + cardId);
            return result;
        }
        result.put("id", card.cardID);
        result.put("name", card.name);
        result.put("type", card.type.name());
        result.put("rarity", card.rarity.name());
        result.put("cost", card.cost);
        result.put("description", card.rawDescription);
        if (card.baseDamage > 0) result.put("base_damage", card.baseDamage);
        if (card.baseBlock > 0) result.put("base_block", card.baseBlock);
        if (card.baseMagicNumber > 0) result.put("base_magic_number", card.baseMagicNumber);
        if (card.exhaust) result.put("exhausts", true);
        boolean hasTarget = card.target == AbstractCard.CardTarget.SELF_AND_ENEMY || card.target == AbstractCard.CardTarget.ENEMY;
        if (hasTarget) result.put("has_target", true);

        // Also show upgraded version info
        AbstractCard upgraded = card.makeCopy();
        upgraded.upgrade();
        HashMap<String, Object> upgradedInfo = new HashMap<>();
        upgradedInfo.put("name", upgraded.name);
        upgradedInfo.put("description", upgraded.rawDescription);
        if (upgraded.cost != card.cost) upgradedInfo.put("cost", upgraded.cost);
        if (upgraded.baseDamage != card.baseDamage) upgradedInfo.put("base_damage", upgraded.baseDamage);
        if (upgraded.baseBlock != card.baseBlock) upgradedInfo.put("base_block", upgraded.baseBlock);
        if (upgraded.baseMagicNumber != card.baseMagicNumber) upgradedInfo.put("base_magic_number", upgraded.baseMagicNumber);
        result.put("upgraded", upgradedInfo);

        return result;
    }

    private static HashMap<String, Object> getGameState(Set<String> include) {
        HashMap<String, Object> state = new HashMap<>();
        boolean all = (include == null || include.isEmpty());

        // Always include basic screen info
        state.put("screen_type", ChoiceScreenUtils.getCurrentChoiceType());
        state.put("room_phase", AbstractDungeon.getCurrRoom().phase.toString());

        // Player section: hp, gold, class, floor, etc.
        if (all || include.contains("player")) {
            state.put("current_hp", AbstractDungeon.player.currentHealth);
            state.put("max_hp", AbstractDungeon.player.maxHealth);
            state.put("floor", AbstractDungeon.floorNum);
            state.put("act", AbstractDungeon.actNum);
            state.put("gold", AbstractDungeon.player.gold);
            state.put("class", AbstractDungeon.player.chosenClass.name());
            state.put("ascension_level", AbstractDungeon.ascensionLevel);
            state.put("seed", Settings.seed);
            if (!AbstractDungeon.bossList.isEmpty()) {
                state.put("act_boss", AbstractDungeon.bossList.get(0));
            }
        }

        // Deck section
        if (all || include.contains("deck")) {
            state.put("deck", convertDeckToJson(AbstractDungeon.player.masterDeck.group));
        }

        // Relics section
        if (all || include.contains("relics")) {
            state.put("relics", convertRelicsToJson(AbstractDungeon.player.relics));
        }

        // Potions section
        if (all || include.contains("potions")) {
            state.put("potions", convertPotionsToJson(AbstractDungeon.player.potions));
        }

        // Map section - only include when explicitly requested (map data is large and static)
        if (include != null && include.contains("map")) {
            state.put("map", convertMapToJson());
        }

        // Combat section: hand, monsters, energy, etc.
        if (all || include.contains("combat")) {
            if (AbstractDungeon.getCurrRoom().phase.equals(AbstractRoom.RoomPhase.COMBAT)) {
                state.put("combat_state", getCombatState());
            }
        }

        // Screen section: choices, screen_state, buttons
        if (all || include.contains("screen")) {
            state.put("screen_name", AbstractDungeon.screen.name());
            state.put("is_screen_up", AbstractDungeon.isScreenUp);
            state.put("room_type", AbstractDungeon.getCurrRoom().getClass().getSimpleName());
            if (CommandExecutor.isChooseCommandAvailable()) {
                state.put("choice_list", ChoiceScreenUtils.getCurrentChoiceList());
            }
            state.put("screen_state", getScreenState());
        }

        return state;
    }

    private static ArrayList<Object> convertRelicsToJson(ArrayList<AbstractRelic> relics) {
        ArrayList<Object> result = new ArrayList<>();
        for (AbstractRelic relic : relics) {
            result.add(convertRelicToJson(relic));
        }
        return result;
    }

    private static ArrayList<Object> convertDeckToJson(ArrayList<AbstractCard> cards) {
        ArrayList<Object> result = new ArrayList<>();
        for (AbstractCard card : cards) {
            result.add(convertCardToJson(card));
        }
        return result;
    }

    private static ArrayList<Object> convertPotionsToJson(ArrayList<AbstractPotion> potions) {
        ArrayList<Object> result = new ArrayList<>();
        for (AbstractPotion potion : potions) {
            result.add(convertPotionToJson(potion));
        }
        return result;
    }

    private static HashMap<String, Object> getRoomState() {
        AbstractRoom currentRoom = AbstractDungeon.getCurrRoom();
        HashMap<String, Object> state = new HashMap<>();
        if(currentRoom instanceof TreasureRoom) {
            state.put("chest_type", ((TreasureRoom)currentRoom).chest.getClass().getSimpleName());
            state.put("chest_open", ((TreasureRoom) currentRoom).chest.isOpen);
        } else if(currentRoom instanceof TreasureRoomBoss) {
            state.put("chest_type", ((TreasureRoomBoss)currentRoom).chest.getClass().getSimpleName());
            state.put("chest_open", ((TreasureRoomBoss) currentRoom).chest.isOpen);
        } else if(currentRoom instanceof RestRoom) {
            state.put("has_rested", currentRoom.phase == AbstractRoom.RoomPhase.COMPLETE);
            state.put("rest_options", ChoiceScreenUtils.getRestRoomChoices());
        }
        return state;
    }

    private static String removeTextFormatting(String text) {
        text = text.replaceAll("~|@(\\S+)~|@", "$1");
        return text.replaceAll("#.|NL", "");
    }

    private static HashMap<String, Object> getEventState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> options = new ArrayList<>();
        ChoiceScreenUtils.EventDialogType eventDialogType = ChoiceScreenUtils.getEventDialogType();
        AbstractEvent event = AbstractDungeon.getCurrRoom().event;
        int choice_index = 0;
        if (eventDialogType == ChoiceScreenUtils.EventDialogType.IMAGE || eventDialogType == ChoiceScreenUtils.EventDialogType.ROOM) {
            for (LargeDialogOptionButton button : ChoiceScreenUtils.getEventButtons()) {
                HashMap<String, Object> json_button = new HashMap<>();
                json_button.put("text", removeTextFormatting(button.msg));
                json_button.put("disabled", button.isDisabled);
                json_button.put("label", ChoiceScreenUtils.getOptionName(button.msg));
                if (!button.isDisabled) {
                    json_button.put("choice_index", choice_index);
                    choice_index += 1;
                }
                options.add(json_button);
            }
            state.put("body_text", removeTextFormatting(UpdateBodyTextPatch.bodyText));
        } else {
            for (String misc_option : ChoiceScreenUtils.getEventScreenChoices()) {
                HashMap<String, Object> json_button = new HashMap<>();
                json_button.put("text", misc_option);
                json_button.put("disabled", false);
                json_button.put("label", misc_option);
                json_button.put("choice_index", choice_index);
                choice_index += 1;
                options.add(json_button);
            }
            state.put("body_text", "");
        }
        state.put("event_name", ReflectionHacks.getPrivateStatic(event.getClass(), "NAME"));
        if (event instanceof NeowEvent) {
            state.put("event_id", "Neow Event");
        } else {
            try {
                Field targetField = event.getClass().getDeclaredField("ID");
                state.put("event_id", (String)targetField.get(null));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                state.put("event_id", "");
            }
            state.put("event_id", ReflectionHacks.getPrivateStatic(event.getClass(), "ID"));
        }
        state.put("options", options);
        return state;
    }

    private static HashMap<String, Object> getCardRewardState() {
        HashMap<String, Object> state = new HashMap<>();
        state.put("bowl_available", ChoiceScreenUtils.isBowlAvailable());
        state.put("skip_available", ChoiceScreenUtils.isCardRewardSkipAvailable());
        ArrayList<Object> cardRewardJson = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.cardRewardScreen.rewardGroup) {
            cardRewardJson.add(convertCardToJson(card));
        }
        state.put("cards", cardRewardJson);
        return state;
    }

    private static HashMap<String, Object> getCombatRewardState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> rewards = new ArrayList<>();
        for(RewardItem reward : AbstractDungeon.combatRewardScreen.rewards) {
            HashMap<String, Object> jsonReward = new HashMap<>();
            jsonReward.put("reward_type", reward.type.name());
            switch(reward.type) {
                case GOLD:
                case STOLEN_GOLD:
                    jsonReward.put("gold", reward.goldAmt + reward.bonusGold);
                    break;
                case RELIC:
                    jsonReward.put("relic", convertRelicToJson(reward.relic));
                    break;
                case POTION:
                    jsonReward.put("potion", convertPotionToJson(reward.potion));
                    break;
                case SAPPHIRE_KEY:
                    jsonReward.put("link", convertRelicToJson(reward.relicLink.relic));
            }
            rewards.add(jsonReward);
        }
        state.put("rewards", rewards);
        return state;
    }

    private static HashMap<String, Object> getMapScreenState() {
        HashMap<String, Object> state = new HashMap<>();
        if (AbstractDungeon.getCurrMapNode() != null) {
            state.put("current_node", convertMapRoomNodeToJson(AbstractDungeon.getCurrMapNode()));
        }
        ArrayList<Object> nextNodesJson = new ArrayList<>();
        for(MapRoomNode node : ChoiceScreenUtils.getMapScreenNodeChoices()) {
            nextNodesJson.add(convertMapRoomNodeToJson(node));
        }
        state.put("next_nodes", nextNodesJson);
        state.put("first_node_chosen", AbstractDungeon.firstRoomChosen);
        state.put("boss_available", ChoiceScreenUtils.bossNodeAvailable());
        return state;
    }

    private static HashMap<String, Object> getBossRewardState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> bossRelics = new ArrayList<>();
        for(AbstractRelic relic : AbstractDungeon.bossRelicScreen.relics) {
            bossRelics.add(convertRelicToJson(relic));
        }
        state.put("relics", bossRelics);
        return state;
    }

    private static HashMap<String, Object> getShopScreenState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> shopCards = new ArrayList<>();
        ArrayList<Object> shopRelics = new ArrayList<>();
        ArrayList<Object> shopPotions = new ArrayList<>();
        for(AbstractCard card : ChoiceScreenUtils.getShopScreenCards()) {
            HashMap<String, Object> jsonCard = convertCardToJson(card);
            jsonCard.put("price", card.price);
            shopCards.add(jsonCard);
        }
        for(StoreRelic relic : ChoiceScreenUtils.getShopScreenRelics()) {
            HashMap<String, Object> jsonRelic = convertRelicToJson(relic.relic);
            jsonRelic.put("price", relic.price);
            shopRelics.add(jsonRelic);
        }
        for(StorePotion potion : ChoiceScreenUtils.getShopScreenPotions()) {
            HashMap<String, Object> jsonPotion = convertPotionToJson(potion.potion);
            jsonPotion.put("price", potion.price);
            shopPotions.add(jsonPotion);
        }
        state.put("cards", shopCards);
        state.put("relics", shopRelics);
        state.put("potions", shopPotions);
        state.put("purge_available", AbstractDungeon.shopScreen.purgeAvailable);
        state.put("purge_cost", ShopScreen.actualPurgeCost);
        return state;
    }

    private static HashMap<String, Object> getGridState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> gridJson = new ArrayList<>();
        ArrayList<Object> gridSelectedJson = new ArrayList<>();
        ArrayList<AbstractCard> gridCards = ChoiceScreenUtils.getGridScreenCards();
        GridCardSelectScreen screen = AbstractDungeon.gridSelectScreen;
        for(AbstractCard card : gridCards) {
            gridJson.add(convertCardToJson(card));
        }
        for(AbstractCard card : screen.selectedCards) {
            gridSelectedJson.add(convertCardToJson(card));
        }
        int numCards = (int) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "numCards");
        boolean forUpgrade = (boolean) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "forUpgrade");
        boolean forTransform = (boolean) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "forTransform");
        boolean forPurge = (boolean) ReflectionHacks.getPrivate(screen, GridCardSelectScreen.class, "forPurge");
        state.put("cards", gridJson);
        state.put("selected_cards", gridSelectedJson);
        state.put("num_cards", numCards);
        state.put("any_number", screen.anyNumber);
        state.put("for_upgrade", forUpgrade);
        state.put("for_transform", forTransform);
        state.put("for_purge", forPurge);
        state.put("confirm_up", screen.confirmScreenUp || screen.isJustForConfirming);
        return state;
    }

    private static HashMap<String, Object> getHandSelectState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> handJson = new ArrayList<>();
        ArrayList<Object> selectedJson = new ArrayList<>();
        ArrayList<AbstractCard> handCards = AbstractDungeon.player.hand.group;
        for(AbstractCard card : handCards) {
            handJson.add(convertCardToJson(card));
        }
        state.put("hand", handJson);
        ArrayList<AbstractCard> selectedCards = AbstractDungeon.handCardSelectScreen.selectedCards.group;
        for(AbstractCard card : selectedCards) {
            selectedJson.add(convertCardToJson(card));
        }
        state.put("selected", selectedJson);
        state.put("max_cards", AbstractDungeon.handCardSelectScreen.numCardsToSelect);
        state.put("can_pick_zero", AbstractDungeon.handCardSelectScreen.canPickZero);
        return state;
    }

    private static HashMap<String, Object> getGameOverState() {
        HashMap<String, Object> state = new HashMap<>();
        int score = 0;
        boolean victory = false;
        if(AbstractDungeon.deathScreen != null) {
            score = (int) ReflectionHacks.getPrivate(AbstractDungeon.deathScreen, DeathScreen.class, "score");
            victory = AbstractDungeon.deathScreen.isVictory;
        } else if(AbstractDungeon.victoryScreen != null) {
            score = (int) ReflectionHacks.getPrivate(AbstractDungeon.victoryScreen, VictoryScreen.class, "score");
            victory = true;
        }
        state.put("score", score);
        state.put("victory", victory);
        return state;
    }

    private static HashMap<String, Object> getScreenState() {
        ChoiceScreenUtils.ChoiceType screenType = ChoiceScreenUtils.getCurrentChoiceType();
        switch (screenType) {
            case EVENT:
                return getEventState();
            case CHEST:
            case REST:
                return getRoomState();
            case CARD_REWARD:
                return getCardRewardState();
            case COMBAT_REWARD:
                return getCombatRewardState();
            case MAP:
                return getMapScreenState();
            case BOSS_REWARD:
                return getBossRewardState();
            case SHOP_SCREEN:
                return getShopScreenState();
            case GRID:
                return getGridState();
            case HAND_SELECT:
                return getHandSelectState();
            case GAME_OVER:
                return getGameOverState();
        }
        return new HashMap<>();
    }

    private static HashMap<String, Object> getCombatState() {
        HashMap<String, Object> state = new HashMap<>();
        ArrayList<Object> monsters = new ArrayList<>();
        for(AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
            if (!monster.isDeadOrEscaped()) {
                monsters.add(convertMonsterToJson(monster));
            }
        }
        state.put("monsters", monsters);
        ArrayList<Object> draw_pile = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.drawPile.group) {
            draw_pile.add(convertCardToJson(card));
        }
        ArrayList<Object> discard_pile = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.discardPile.group) {
            discard_pile.add(convertCardToJson(card));
        }
        ArrayList<Object> exhaust_pile = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.exhaustPile.group) {
            exhaust_pile.add(convertCardToJson(card));
        }
        ArrayList<Object> hand = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.hand.group) {
            hand.add(convertCardToJson(card));
        }
        ArrayList<Object> limbo = new ArrayList<>();
        for(AbstractCard card : AbstractDungeon.player.limbo.group) {
            limbo.add(convertCardToJson(card));
        }
        state.put("draw_pile", draw_pile);
        state.put("discard_pile", discard_pile);
        state.put("exhaust_pile", exhaust_pile);
        state.put("hand", hand);
        state.put("limbo", limbo);
        if (AbstractDungeon.player.cardInUse != null) {
            state.put("card_in_play", convertCardToJson(AbstractDungeon.player.cardInUse));
        }
        state.put("player", convertPlayerToJson(AbstractDungeon.player));
        state.put("turn", GameActionManager.turn);
        state.put("cards_discarded_this_turn", GameActionManager.totalDiscardedThisTurn);
        return state;
    }

    private static ArrayList<Object> convertMapToJson() {
        ArrayList<ArrayList<MapRoomNode>> map = AbstractDungeon.map;
        ArrayList<Object> jsonMap = new ArrayList<>();
        for(ArrayList<MapRoomNode> layer : map) {
            for(MapRoomNode node : layer) {
                if(node.hasEdges()) {
                    HashMap<String, Object> json_node = convertMapRoomNodeToJson(node);
                    ArrayList<Object> json_children = new ArrayList<>();
                    ArrayList<Object> json_parents = new ArrayList<>();
                    for(MapEdge edge : node.getEdges()) {
                        if (edge.srcX == node.x && edge.srcY == node.y) {
                            json_children.add(convertCoordinatesToJson(edge.dstX, edge.dstY));
                        } else {
                            json_parents.add(convertCoordinatesToJson(edge.srcX, edge.srcY));
                        }
                    }

                    json_node.put("parents", json_parents);
                    json_node.put("children", json_children);
                    jsonMap.add(json_node);
                }
            }
        }
        return jsonMap;
    }

    private static HashMap<String, Object> convertCoordinatesToJson(int x, int y) {
        HashMap<String, Object> jsonNode = new HashMap<>();
        jsonNode.put("x", x);
        jsonNode.put("y", y);
        return jsonNode;
    }

    private static HashMap<String, Object> convertMapRoomNodeToJson(MapRoomNode node) {
        HashMap<String, Object> jsonNode = convertCoordinatesToJson(node.x, node.y);
        jsonNode.put("symbol", node.getRoomSymbol(true));
        return jsonNode;
    }

    private static HashMap<String, Object> convertCardToJson(AbstractCard card) {
        HashMap<String, Object> jsonCard = new HashMap<>();
        // Essential fields - always include
        jsonCard.put("name", card.name);
        jsonCard.put("uuid", card.uuid.toString());
        jsonCard.put("id", card.cardID);
        jsonCard.put("type", card.type.name());
        jsonCard.put("cost", card.costForTurn);

        // Upgrades - only if upgraded
        if (card.timesUpgraded > 0) {
            jsonCard.put("upgrades", card.timesUpgraded);
        }

        // Playability - only in combat context
        if (AbstractDungeon.getMonsters() != null) {
            jsonCard.put("is_playable", card.canUse(AbstractDungeon.player, null));
        }

        // Targeting
        boolean hasTarget = card.target == AbstractCard.CardTarget.SELF_AND_ENEMY || card.target == AbstractCard.CardTarget.ENEMY;
        if (hasTarget) {
            jsonCard.put("has_target", true);
        }

        // Exhaust - only if true
        if (card.exhaust) {
            jsonCard.put("exhausts", true);
        }

        // Numeric values - only include if non-zero
        if (card.damage > 0) {
            jsonCard.put("damage", card.damage);
            // Only include base if different from current (indicates modification)
            if (card.baseDamage != card.damage) {
                jsonCard.put("base_damage", card.baseDamage);
            }
        }
        if (card.block > 0) {
            jsonCard.put("block", card.block);
            if (card.baseBlock != card.block) {
                jsonCard.put("base_block", card.baseBlock);
            }
        }
        if (card.magicNumber > 0) {
            jsonCard.put("magic_number", card.magicNumber);
            if (card.baseMagicNumber != card.magicNumber) {
                jsonCard.put("base_magic_number", card.baseMagicNumber);
            }
        }
        if (card.heal > 0) {
            jsonCard.put("heal", card.heal);
        }
        if (card.draw > 0) {
            jsonCard.put("draw", card.draw);
        }
        if (card.discard > 0) {
            jsonCard.put("discard", card.discard);
        }
        if (card.misc != 0) {
            jsonCard.put("misc", card.misc);
        }

        return jsonCard;
    }

    private static HashMap<String, Object> convertMonsterToJson(AbstractMonster monster) {
        HashMap<String, Object> jsonMonster = new HashMap<>();
        // Essential fields
        jsonMonster.put("id", monster.id);
        jsonMonster.put("name", monster.name);
        jsonMonster.put("current_hp", monster.currentHealth);
        jsonMonster.put("max_hp", monster.maxHealth);
        jsonMonster.put("is_gone", monster.isDeadOrEscaped());

        // Intent - hidden with Runic Dome
        if (AbstractDungeon.player.hasRelic(RunicDome.ID)) {
            jsonMonster.put("intent", AbstractMonster.Intent.NONE);
        } else {
            jsonMonster.put("intent", monster.intent.name());
            EnemyMoveInfo moveInfo = (EnemyMoveInfo)ReflectionHacks.getPrivate(monster, AbstractMonster.class, "move");
            if (moveInfo != null) {
                // Combine move info into a single object for cleaner output
                HashMap<String, Object> move = new HashMap<>();
                int intentDmg = (int)ReflectionHacks.getPrivate(monster, AbstractMonster.class, "intentDmg");
                int adjustedDamage = moveInfo.baseDamage > 0 ? intentDmg : moveInfo.baseDamage;
                if (adjustedDamage > 0) {
                    move.put("damage", adjustedDamage);
                    int hits = moveInfo.isMultiDamage ? moveInfo.multiplier : 1;
                    if (hits > 1) {
                        move.put("hits", hits);
                    }
                }
                if (!move.isEmpty()) {
                    jsonMonster.put("move", move);
                }
            }
        }

        // Conditional fields - only include if relevant
        if (monster.halfDead) {
            jsonMonster.put("half_dead", true);
        }
        if (monster.currentBlock > 0) {
            jsonMonster.put("block", monster.currentBlock);
        }

        // Powers - only include if monster has any
        if (!monster.powers.isEmpty()) {
            jsonMonster.put("powers", convertCreaturePowersToJson(monster));
        }

        return jsonMonster;
    }

    private static HashMap<String, Object> convertPlayerToJson(AbstractPlayer player) {
        HashMap<String, Object> jsonPlayer = new HashMap<>();
        // Essential fields
        jsonPlayer.put("current_hp", player.currentHealth);
        jsonPlayer.put("max_hp", player.maxHealth);
        jsonPlayer.put("energy", EnergyPanel.totalCount);

        // Conditional fields
        if (player.currentBlock > 0) {
            jsonPlayer.put("block", player.currentBlock);
        }
        if (!player.powers.isEmpty()) {
            jsonPlayer.put("powers", convertCreaturePowersToJson(player));
        }

        // Orbs - only for Defect (or characters with orb slots)
        if (player.orbs != null && !player.orbs.isEmpty() && player.maxOrbs > 0) {
            jsonPlayer.put("orbs", convertOrbsToJson(player.orbs));
        }

        // Stance - only if not neutral (Watcher mechanic)
        if (player.stance != null && !"Neutral".equals(player.stance.ID)) {
            jsonPlayer.put("stance", player.stance.ID);
        }

        return jsonPlayer;
    }

    private static ArrayList<Object> convertOrbsToJson(ArrayList<AbstractOrb> orbs) {
        ArrayList<Object> result = new ArrayList<>();
        for (AbstractOrb orb : orbs) {
            result.add(convertOrbToJson(orb));
        }
        return result;
    }

    private static Object getFieldIfExists(Object object, String fieldName) {
        Class objectClass = object.getClass();
        for (Field field : objectClass.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                try {
                    field.setAccessible(true);
                    return field.get(object);
                } catch(IllegalAccessException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }
        return null;
    }

    private static ArrayList<Object> convertCreaturePowersToJson(AbstractCreature creature) {
        ArrayList<Object> powers = new ArrayList<>();
        for (AbstractPower power : creature.powers) {
            HashMap<String, Object> json_power = new HashMap<>();
            json_power.put("id", power.ID);

            // Amount - only include if non-zero (many powers use amount)
            if (power.amount != 0) {
                json_power.put("amount", power.amount);
            }

            // Special fields for specific powers - only include if present
            Object damage = getFieldIfExists(power, "damage");
            if (damage != null) {
                json_power.put("damage", (int) damage);
            }
            Object card = getFieldIfExists(power, "card");
            if (card != null) {
                json_power.put("card", convertCardToJson((AbstractCard) card));
            }

            // Misc fields for special powers
            String[] miscFieldNames = {"basePower", "maxAmt", "storedAmount", "hpLoss", "cardsDoubledThisTurn"};
            for (String fieldName : miscFieldNames) {
                Object misc = getFieldIfExists(power, fieldName);
                if (misc != null) {
                    json_power.put("misc", (int) misc);
                    break;
                }
            }

            // Just applied flag
            String[] justAppliedNames = {"justApplied", "skipFirst"};
            for (String fieldName : justAppliedNames) {
                Object justApplied = getFieldIfExists(power, fieldName);
                if (justApplied != null && (boolean) justApplied) {
                    json_power.put("just_applied", true);
                    break;
                }
            }

            powers.add(json_power);
        }
        return powers;
    }

    private static HashMap<String, Object> convertRelicToJson(AbstractRelic relic) {
        HashMap<String, Object> jsonRelic = new HashMap<>();
        jsonRelic.put("id", relic.relicId);
        jsonRelic.put("name", relic.name);
        // Counter: -1 means unused, -2 means special, only include if active
        if (relic.counter >= 0) {
            jsonRelic.put("counter", relic.counter);
        }
        return jsonRelic;
    }

    private static HashMap<String, Object> convertPotionToJson(AbstractPotion potion) {
        HashMap<String, Object> jsonPotion = new HashMap<>();
        jsonPotion.put("id", potion.ID);
        jsonPotion.put("name", potion.name);

        // For empty potion slots, just return minimal info
        if (potion instanceof PotionSlot) {
            jsonPotion.put("is_empty", true);
            return jsonPotion;
        }

        // Only include if usable/discardable
        if (potion.canUse()) {
            jsonPotion.put("can_use", true);
            if (potion.isThrown) {
                jsonPotion.put("requires_target", true);
            }
        }
        if (potion.canDiscard()) {
            jsonPotion.put("can_discard", true);
        }
        return jsonPotion;
    }

    private static HashMap<String, Object> convertOrbToJson(AbstractOrb orb) {
        HashMap<String, Object> jsonOrb = new HashMap<>();
        jsonOrb.put("id", orb.ID);

        // Only include amounts if they're meaningful (non-zero)
        if (orb.evokeAmount > 0) {
            jsonOrb.put("evoke", orb.evokeAmount);
        }
        if (orb.passiveAmount > 0) {
            jsonOrb.put("passive", orb.passiveAmount);
        }
        return jsonOrb;
    }

}
