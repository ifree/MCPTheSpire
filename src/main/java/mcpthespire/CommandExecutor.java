package mcpthespire;

import basemod.ReflectionHacks;
import com.badlogic.gdx.Gdx;
import com.megacrit.cardcrawl.cards.AbstractCard;
import com.megacrit.cardcrawl.cards.CardQueueItem;
import com.megacrit.cardcrawl.characters.AbstractPlayer;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.core.EnergyManager;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.SeedHelper;
import com.megacrit.cardcrawl.helpers.TrialHelper;
import com.megacrit.cardcrawl.helpers.input.InputAction;
import com.megacrit.cardcrawl.helpers.input.InputActionSet;
import com.megacrit.cardcrawl.helpers.input.InputHelper;
import com.megacrit.cardcrawl.monsters.AbstractMonster;
import com.megacrit.cardcrawl.potions.AbstractPotion;
import com.megacrit.cardcrawl.potions.PotionSlot;
import com.megacrit.cardcrawl.random.Random;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.rooms.*;

import com.megacrit.cardcrawl.saveAndContinue.SaveAndContinue;
import com.megacrit.cardcrawl.saveAndContinue.SaveFile;
import com.megacrit.cardcrawl.helpers.ModHelper;
import com.megacrit.cardcrawl.metrics.MetricData;
import com.megacrit.cardcrawl.cards.CardSave;
import com.megacrit.cardcrawl.blights.AbstractBlight;
import com.megacrit.cardcrawl.relics.BottledFlame;
import com.megacrit.cardcrawl.relics.BottledLightning;
import com.megacrit.cardcrawl.relics.BottledTornado;
import com.megacrit.cardcrawl.helpers.BlightHelper;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.screens.stats.StatsScreen;
import com.megacrit.cardcrawl.rooms.RestRoom;

import mcpthespire.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class CommandExecutor {

    private static final Logger logger = LogManager.getLogger(CommandExecutor.class.getName());

    public static boolean executeCommand(String command) throws InvalidCommandException {
        command = command.toLowerCase();
        String [] tokens = command.split("\\s+");
        if(tokens.length == 0) {
            return false;
        }
        if (!isCommandAvailable(tokens[0])) {
            throw new InvalidCommandException("Invalid command: " + tokens[0] + ". Possible commands: " + getAvailableCommands());
        }
        String command_tail = command.substring(tokens[0].length());
        switch(tokens[0]) {
            case "play":
                executePlayCommand(tokens);
                return true;
            case "end":
                executeEndCommand();
                return true;
            case "choose":
                executeChooseCommand(tokens);
                return true;
            case "potion":
                executePotionCommand(tokens);
                return true;
            case "confirm":
            case "proceed":
                executeConfirmCommand();
                return true;
            case "skip":
            case "cancel":
            case "return":
            case "leave":
                executeCancelCommand();
                return true;
            case "start":
                executeStartCommand(tokens);
                return true;
            case "continue":
                executeContinueCommand(tokens);
                return false;
            case "abandon":
                executeAbandonCommand(tokens);
                executeStateCommand();
                return false;
            case "save":
                executeSaveCommand(tokens);
                return false;
            case "state":
                executeStateCommand();
                return false;
            case "key":
                executeKeyCommand(tokens);
                return true;
            case "click":
                executeClickCommand(tokens);
                return true;
            case "wait":
                executeWaitCommand(tokens);
                return true;

            default:
                logger.info("This should never happen.");
                throw new InvalidCommandException("Command not recognized.");
        }
    }

    public static ArrayList<String> getAvailableCommands() {
        ArrayList<String> availableCommands = new ArrayList<>();
        if (isPlayCommandAvailable()) {
            availableCommands.add("play");
        }
        if (isChooseCommandAvailable()) {
            availableCommands.add("choose");
        }
        if (isEndCommandAvailable()) {
            availableCommands.add("end");
        }
        if (isPotionCommandAvailable()) {
            availableCommands.add("potion");
        }
        if (isConfirmCommandAvailable()) {
            availableCommands.add(ChoiceScreenUtils.getConfirmButtonText());
        }
        if (isCancelCommandAvailable()) {
            availableCommands.add(ChoiceScreenUtils.getCancelButtonText());
        }
        if (isStartCommandAvailable()) {
            if (CardCrawlGame.characterManager.anySaveFileExists()) {
                availableCommands.add("continue");
                availableCommands.add("abandon");
            } else
                availableCommands.add("start");
        }
        if (isInDungeon()) {
            availableCommands.add("key");
            availableCommands.add("click");
            availableCommands.add("wait");
            availableCommands.add("save");
        }
        availableCommands.add("state");
        return availableCommands;
    }

    public static boolean isCommandAvailable(String command) {
        if(command.equals("confirm") || command.equalsIgnoreCase("proceed")) {
            return isConfirmCommandAvailable();
        } else if (command.equals("skip") || command.equals("cancel") || command.equals("return") || command.equals("leave")) {
            return isCancelCommandAvailable();
        } else {
            return getAvailableCommands().contains(command);
        }
    }

    public static boolean isInDungeon() {
        return CardCrawlGame.mode == CardCrawlGame.GameMode.GAMEPLAY && AbstractDungeon.isPlayerInDungeon() && AbstractDungeon.currMapNode != null;
    }

    public static boolean isPlayCommandAvailable() {
        if(isInDungeon()) {
            if(AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && !AbstractDungeon.isScreenUp) {
                for (AbstractCard card : AbstractDungeon.player.hand.group) {
                    if (card.canUse(AbstractDungeon.player, null)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isEndCommandAvailable() {
        return isInDungeon() && AbstractDungeon.getCurrRoom().phase == AbstractRoom.RoomPhase.COMBAT && !AbstractDungeon.isScreenUp;
    }

    public static boolean isChooseCommandAvailable() {
        if(isInDungeon()) {
            return !isPlayCommandAvailable() && !ChoiceScreenUtils.getCurrentChoiceList().isEmpty();
        } else {
            return false;
        }
    }

    public static boolean isPotionCommandAvailable() {
        if(isInDungeon()) {
            for(AbstractPotion potion : AbstractDungeon.player.potions) {
                if(!(potion instanceof PotionSlot)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isConfirmCommandAvailable() {
        if(isInDungeon()) {
            return ChoiceScreenUtils.isConfirmButtonAvailable();
        } else {
            return false;
        }
    }

    public static boolean isCancelCommandAvailable() {
        if(isInDungeon()) {
            return ChoiceScreenUtils.isCancelButtonAvailable();
        } else {
            return false;
        }
    }

    public static boolean isStartCommandAvailable() {
        return !isInDungeon();
    }

    private static void executeStateCommand() {
        MCPTheSpire.mustSendGameState = true;
    }

    private static void executePlayCommand(String[] tokens) throws InvalidCommandException {
        String usage = "\nUsage: play card_index [TargetIndex]";
        if(tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, usage);
        }
        int card_index;
        try {
            card_index = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1] + usage);
        }
        if(card_index == 0) {
            card_index = 10;
        }
        if((card_index < 1) || (card_index > AbstractDungeon.player.hand.size())) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(card_index));
        }
        int monster_index = -1;
        if(tokens.length == 3) {
            try {
                monster_index = Integer.parseInt(tokens[2]) - 1;
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
        }
        AbstractMonster target_monster = null;
        if (monster_index != -1) {
            // 获取活着的怪物列表
            ArrayList<AbstractMonster> aliveMonsters = new ArrayList<>();
            for(AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
                if (!monster.isDeadOrEscaped()) {
                    aliveMonsters.add(monster);
                }
            }
            if (monster_index < 0 || monster_index >= aliveMonsters.size()) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(monster_index + 1));
            } else {
                target_monster = aliveMonsters.get(monster_index);
            }
        } else {
            for(AbstractMonster monster: AbstractDungeon.getCurrRoom().monsters.monsters) {
                if(monster.currentHealth <= 0)
                    continue;
                if(target_monster != null) {
                    target_monster = null;
                    break;
                } else
                    target_monster = monster;
            }
        }
        if((card_index < 1) || (card_index > AbstractDungeon.player.hand.size()) || !(AbstractDungeon.player.hand.group.get(card_index - 1).canUse(AbstractDungeon.player, target_monster))) {
            throw new InvalidCommandException("Selected card cannot be played with the selected target.");
        }
        AbstractCard card = AbstractDungeon.player.hand.group.get(card_index - 1);
        if(card.target == AbstractCard.CardTarget.ENEMY || card.target == AbstractCard.CardTarget.SELF_AND_ENEMY) {
            if(target_monster == null) {
                throw new InvalidCommandException("Selected card requires an enemy target.");
            }
            if (AbstractDungeon.player.hasPower("Surrounded"))
                AbstractDungeon.player.flipHorizontal = (target_monster.drawX < AbstractDungeon.player.drawX);
            AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, target_monster));
        } else {
            AbstractDungeon.actionManager.cardQueue.add(new CardQueueItem(card, null));
        }
    }

    private static void executeEndCommand() throws InvalidCommandException {
        AbstractDungeon.overlayMenu.endTurnButton.disable(true);
    }

    private static void executeChooseCommand(String[] tokens) throws InvalidCommandException {
        ArrayList<String> validChoices = ChoiceScreenUtils.getCurrentChoiceList();
        if(validChoices.size() == 0) {
            throw new InvalidCommandException("The choice command is not implemented on this screen.");
        }
        int choice_index = getValidChoiceIndex(tokens, validChoices);
        ChoiceScreenUtils.executeChoice(choice_index);
    }

    private static void executePotionCommand(String[] tokens) throws  InvalidCommandException {
        int potion_index;
        boolean use;
        String usage = "\nUsage: potion use|discard potion_index [TargetIndex]";
        if (tokens.length < 3) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, usage);
        }
        if(tokens[1].equals("use")) {
            use = true;
        } else if (tokens[1].equals("discard")) {
            use = false;
        } else {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1] + usage);
        }
        try {
            potion_index = Integer.parseInt(tokens[2]) - 1;
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
        }
        if(potion_index < 0 || potion_index >= AbstractDungeon.player.potionSlots) {
            throw new InvalidCommandException("Potion index out of bounds.");
        }
        AbstractPotion selectedPotion = AbstractDungeon.player.potions.get(potion_index);
        if(selectedPotion instanceof PotionSlot) {
            throw new InvalidCommandException("No potion in the selected slot.");
        }
        if(use && !selectedPotion.canUse()) {
            throw new InvalidCommandException("Selected potion cannot be used.");
        }
        if(!use && !selectedPotion.canDiscard()) {
            throw new InvalidCommandException("Selected potion cannot be discarded.");
        }
        int monster_index = -1;
        if (use) {
            if (selectedPotion.targetRequired) {
                AbstractMonster target_monster = null;
                if(tokens.length >= 4) {
                    try {
                        monster_index = Integer.parseInt(tokens[3]) - 1;
                    } catch (NumberFormatException e) {
                        throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[3]);
                    }
                    // 获取活着的怪物列表
                    ArrayList<AbstractMonster> aliveMonsters = new ArrayList<>();
                    for(AbstractMonster monster : AbstractDungeon.getCurrRoom().monsters.monsters) {
                        if (!monster.isDeadOrEscaped()) {
                            aliveMonsters.add(monster);
                        }
                    }
                    if (monster_index < 0 || monster_index >= aliveMonsters.size()) {
                        throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, Integer.toString(monster_index + 1));
                    } else {
                        target_monster = aliveMonsters.get(monster_index);
                    }
                } else {
                    for(AbstractMonster monster: AbstractDungeon.getCurrRoom().monsters.monsters) {
                        if(monster.currentHealth <= 0)
                            continue;
                        if(target_monster != null)
                            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, " Selected potion requires a target.");
                        target_monster = monster;
                    }
                }
                selectedPotion.use(target_monster);
            } else {
                selectedPotion.use(AbstractDungeon.player);
            }
            for (AbstractRelic r : AbstractDungeon.player.relics) {
                r.onUsePotion();
            }
        }
        AbstractDungeon.topPanel.destroyPotion(selectedPotion.slot);
        GameStateListener.registerStateChange();
    }

    private static void executeConfirmCommand() {
        ChoiceScreenUtils.pressConfirmButton();
    }

    private static void executeCancelCommand() {
        ChoiceScreenUtils.pressCancelButton();
    }

    private static void executeSaveCommand(String[] tokens){
        CardCrawlGame.music.fadeAll();
        AbstractDungeon.getCurrRoom().clearEvent();
        AbstractDungeon.closeCurrentScreen();
        CardCrawlGame.startOver();
        if (RestRoom.lastFireSoundId != 0L)
            CardCrawlGame.sound.fadeOut("REST_FIRE_WET", RestRoom.lastFireSoundId);
        if (!AbstractDungeon.player.stance.ID.equals("Neutral") && AbstractDungeon.player.stance != null)
            AbstractDungeon.player.stance.stopIdleSfx();
    }

    private static void executeAbandonCommand(String[] tokens){
        CardCrawlGame.chosenCharacter = (CardCrawlGame.characterManager.loadChosenCharacter()).chosenClass;
        AbstractPlayer player = AbstractDungeon.player;
        AbstractPlayer.PlayerClass pClass = player.chosenClass;
        logger.info("Abandoning run with " + pClass.name());
        SaveFile file = SaveAndContinue.loadSaveFile(pClass);
        if (Settings.isStandardRun())
            if (file.floor_num >= 16) {
                CardCrawlGame.playerPref.putInteger(pClass.name() + "_SPIRITS", 1);
                CardCrawlGame.playerPref.flush();
            } else {
                CardCrawlGame.playerPref.putInteger(pClass.name() + "_SPIRITS", 0);
                CardCrawlGame.playerPref.flush();
            }
        SaveAndContinue.deleteSave(player);
        if (!file.is_ascension_mode)
            StatsScreen.incrementDeath(player.getCharStat());
        CardCrawlGame.mainMenuScreen.abandonedRun = true;
        CardCrawlGame.mainMenuScreen.isSettingsUp = true;
    }

    private static void executeContinueCommand(String[] tokens){
        int monstersSlain, elites1Slain, elites2Slain, elites3Slain, goldGained, champion, perfect, mysteryMachine;
        Boolean combo, overkill;
        float playtime;
        CardCrawlGame.loadingSave = true;
        CardCrawlGame.chosenCharacter = (CardCrawlGame.characterManager.loadChosenCharacter()).chosenClass;
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        Settings.isDailyRun = false;
        Settings.isTrial = false;
        ModHelper.setModsFalse();
        AbstractPlayer p = AbstractDungeon.player;
        SaveFile saveFile = SaveAndContinue.loadSaveFile(p.chosenClass);
        AbstractDungeon.loading_post_combat = false;
        Settings.seed = Long.valueOf(saveFile.seed);
        Settings.isFinalActAvailable = saveFile.is_final_act_on;
        Settings.hasRubyKey = saveFile.has_ruby_key;
        Settings.hasEmeraldKey = saveFile.has_emerald_key;
        Settings.hasSapphireKey = saveFile.has_sapphire_key;
        Settings.isDailyRun = saveFile.is_daily;
        if (Settings.isDailyRun)
            Settings.dailyDate = saveFile.daily_date;
        Settings.specialSeed = Long.valueOf(saveFile.special_seed);
        Settings.seedSet = saveFile.seed_set;
        Settings.isTrial = saveFile.is_trial;
        if (Settings.isTrial) {
            ModHelper.setTodaysMods(Settings.seed.longValue(), AbstractDungeon.player.chosenClass);
            AbstractPlayer.customMods = saveFile.custom_mods;
        } else if (Settings.isDailyRun) {
            ModHelper.setTodaysMods(Settings.specialSeed.longValue(), AbstractDungeon.player.chosenClass);
        }
        AbstractPlayer.customMods = saveFile.custom_mods;
        if (AbstractPlayer.customMods == null)
            AbstractPlayer.customMods = new ArrayList();
        p.currentHealth = saveFile.current_health;
        p.maxHealth = saveFile.max_health;
        p.gold = saveFile.gold;
        p.displayGold = p.gold;
        p.masterHandSize = saveFile.hand_size;
        p.potionSlots = saveFile.potion_slots;
        if (p.potionSlots == 0)
            p.potionSlots = 3;
        p.potions.clear();
        for (int i = 0; i < p.potionSlots; i++)
            p.potions.add(new PotionSlot(i));
        p.masterMaxOrbs = saveFile.max_orbs;
        p.energy = new EnergyManager(saveFile.red + saveFile.green + saveFile.blue);
        monstersSlain = saveFile.monsters_killed;
        elites1Slain = saveFile.elites1_killed;
        elites2Slain = saveFile.elites2_killed;
        elites3Slain = saveFile.elites3_killed;
        goldGained = saveFile.gold_gained;
        champion = saveFile.champions;
        perfect = saveFile.perfect;
        combo = saveFile.combo;
        overkill = saveFile.overkill;
        mysteryMachine = saveFile.mystery_machine;
        playtime = (float)saveFile.play_time;
        AbstractDungeon.ascensionLevel = saveFile.ascension_level;
        AbstractDungeon.isAscensionMode = saveFile.is_ascension_mode;
        p.masterDeck.clear();
        for (CardSave s : saveFile.cards) {
            logger.info(s.id + ", " + s.upgrades);
            p.masterDeck.addToTop(CardLibrary.getCopy(s.id, s.upgrades, s.misc));
        }
        Settings.isEndless = saveFile.is_endless_mode;
        int index = 0;
        p.blights.clear();
        if (saveFile.blights != null) {
            for (String b : saveFile.blights) {
                AbstractBlight blight = BlightHelper.getBlight(b);
                if (blight != null) {
                    int incrementAmount = ((Integer)saveFile.endless_increments.get(index)).intValue();
                    for (int j = 0; j < incrementAmount; j++)
                        blight.incrementUp();
                    blight.setIncrement(incrementAmount);
                    blight.instantObtain(AbstractDungeon.player, index, false);
                }
                index++;
            }
            if (saveFile.blight_counters != null) {
                index = 0;
                for (Integer integer : saveFile.blight_counters) {
                    ((AbstractBlight)p.blights.get(index)).setCounter(integer.intValue());
                    ((AbstractBlight)p.blights.get(index)).updateDescription(p.chosenClass);
                    index++;
                }
            }
        }
        p.relics.clear();
        index = 0;
        for (String s : saveFile.relics) {
            AbstractRelic r = RelicLibrary.getRelic(s).makeCopy();
            r.instantObtain(p, index, false);
            if (index < saveFile.relic_counters.size())
                r.setCounter(((Integer)saveFile.relic_counters.get(index)).intValue());
            r.updateDescription(p.chosenClass);
            index++;
        }
        index = 0;
        for (String s : saveFile.potions) {
            AbstractPotion potion = PotionHelper.getPotion(s);
            if (potion != null)
                AbstractDungeon.player.obtainPotion(index, potion);
            index++;
        }
        AbstractCard tmpCard = null;
        if (saveFile.bottled_flame != null) {
            for (AbstractCard abstractCard : AbstractDungeon.player.masterDeck.group) {
                if (abstractCard.cardID.equals(saveFile.bottled_flame)) {
                    tmpCard = abstractCard;
                    if (abstractCard.timesUpgraded == saveFile.bottled_flame_upgrade && abstractCard.misc == saveFile.bottled_flame_misc)
                        break;
                }
            }
            if (tmpCard != null) {
                tmpCard.inBottleFlame = true;
                ((BottledFlame)AbstractDungeon.player.getRelic("Bottled Flame")).card = tmpCard;
                ((BottledFlame)AbstractDungeon.player.getRelic("Bottled Flame")).setDescriptionAfterLoading();
            }
        }
        tmpCard = null;
        if (saveFile.bottled_lightning != null) {
            for (AbstractCard abstractCard : AbstractDungeon.player.masterDeck.group) {
                if (abstractCard.cardID.equals(saveFile.bottled_lightning)) {
                    tmpCard = abstractCard;
                    if (abstractCard.timesUpgraded == saveFile.bottled_lightning_upgrade && abstractCard.misc == saveFile.bottled_lightning_misc)
                        break;
                }
            }
            if (tmpCard != null) {
                tmpCard.inBottleLightning = true;
                ((BottledLightning)AbstractDungeon.player.getRelic("Bottled Lightning")).card = tmpCard;
                ((BottledLightning)AbstractDungeon.player.getRelic("Bottled Lightning")).setDescriptionAfterLoading();
            }
        }
        tmpCard = null;
        if (saveFile.bottled_tornado != null) {
            for (AbstractCard abstractCard : AbstractDungeon.player.masterDeck.group) {
                if (abstractCard.cardID.equals(saveFile.bottled_tornado)) {
                    tmpCard = abstractCard;
                    if (abstractCard.timesUpgraded == saveFile.bottled_tornado_upgrade && abstractCard.misc == saveFile.bottled_tornado_misc)
                        break;
                }
            }
            if (tmpCard != null) {
                tmpCard.inBottleTornado = true;
                ((BottledTornado)AbstractDungeon.player.getRelic("Bottled Tornado")).card = tmpCard;
                ((BottledTornado)AbstractDungeon.player.getRelic("Bottled Tornado")).setDescriptionAfterLoading();
            }
        }
        if (saveFile.daily_mods != null && saveFile.daily_mods.size() > 0)
            ModHelper.setMods(saveFile.daily_mods);
        MetricData metricData = new MetricData();
        metricData.clearData();
        metricData.campfire_rested = saveFile.metric_campfire_rested;
        metricData.campfire_upgraded = saveFile.metric_campfire_upgraded;
        metricData.purchased_purges = saveFile.metric_purchased_purges;
        metricData.potions_floor_spawned = saveFile.metric_potions_floor_spawned;
        metricData.current_hp_per_floor = saveFile.metric_current_hp_per_floor;
        metricData.max_hp_per_floor = saveFile.metric_max_hp_per_floor;
        metricData.gold_per_floor = saveFile.metric_gold_per_floor;
        metricData.path_per_floor = saveFile.metric_path_per_floor;
        metricData.path_taken = saveFile.metric_path_taken;
        metricData.items_purchased = saveFile.metric_items_purchased;
        metricData.items_purged = saveFile.metric_items_purged;
        metricData.card_choices = saveFile.metric_card_choices;
        metricData.event_choices = saveFile.metric_event_choices;
        metricData.damage_taken = saveFile.metric_damage_taken;
        metricData.boss_relics = saveFile.metric_boss_relics;
        if (saveFile.metric_potions_obtained != null)
            metricData.potions_obtained = saveFile.metric_potions_obtained;
        if (saveFile.metric_relics_obtained != null)
            metricData.relics_obtained = saveFile.metric_relics_obtained;
        if (saveFile.metric_campfire_choices != null)
            metricData.campfire_choices = saveFile.metric_campfire_choices;
        if (saveFile.metric_item_purchase_floors != null)
            metricData.item_purchase_floors = saveFile.metric_item_purchase_floors;
        if (saveFile.metric_items_purged_floors != null)
            metricData.items_purged_floors = saveFile.metric_items_purged_floors;
        if (saveFile.neow_bonus != null)
            metricData.neowBonus = saveFile.neow_bonus;
        if (saveFile.neow_cost != null)
            metricData.neowCost = saveFile.neow_cost;
        GameStateListener.resetStateVariables();
    }

    private static void executeStartCommand(String[] tokens) throws InvalidCommandException {
        String usage = "\nUsage: start ironclad|silent|defect|watcher [AscensionLevel] [seed]";
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, usage);
        }
        int ascensionLevel = 0;
        boolean seedSet = false;
        long seed = 0;
        AbstractPlayer.PlayerClass selectedClass = null;
        for(AbstractPlayer.PlayerClass playerClass : AbstractPlayer.PlayerClass.values()) {
            if(playerClass.name().equalsIgnoreCase(tokens[1])) {
                selectedClass = playerClass;
            }
        }
        if(tokens[1].equalsIgnoreCase("silent")) {
            selectedClass = AbstractPlayer.PlayerClass.THE_SILENT;
        }
        if(selectedClass == null) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1] + usage);
        }
        if(tokens.length >= 3) {
            try {
                ascensionLevel = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2] + usage);
            }
            if(ascensionLevel < 0 || ascensionLevel > 20) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[2]);
            }
        }
        if(tokens.length >= 4) {
            String seedString = tokens[3].toUpperCase();
            if(!seedString.matches("^[A-Z0-9]+$")) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, seedString);
            }
            seedSet = true;
            seed = SeedHelper.getLong(seedString);
            boolean isTrialSeed = TrialHelper.isTrialSeed(seedString);
            if (isTrialSeed) {
                Settings.specialSeed = seed;
                Settings.isTrial = true;
                seedSet = false;
            }
        }
        if(!seedSet) {
            seed = SeedHelper.generateUnoffensiveSeed(new Random(System.nanoTime()));
        }
        Settings.seed = seed;
        Settings.seedSet = seedSet;
        AbstractDungeon.generateSeeds();
        AbstractDungeon.ascensionLevel = ascensionLevel;
        AbstractDungeon.isAscensionMode = ascensionLevel > 0;
        CardCrawlGame.startOver = true;
        CardCrawlGame.mainMenuScreen.isFadingOut = true;
        CardCrawlGame.mainMenuScreen.fadeOutMusic();
        CharacterManager manager = new CharacterManager();
        manager.setChosenCharacter(selectedClass);
        CardCrawlGame.chosenCharacter = selectedClass;
        GameStateListener.resetStateVariables();
    }

    private static void executeKeyCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int keycode = getKeycode(tokens[1].toUpperCase());
        if (keycode == -1) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        int timeout = 100;
        if (tokens.length >= 3) {
            try {
                timeout = Integer.parseInt(tokens[2]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
            }
            if(timeout < 0) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[2]);
            }
        }
        InputActionPatch.doKeypress = true;
        InputActionPatch.key = keycode;
        InputHelper.updateFirst();
        GameStateListener.setTimeout(timeout);
    }

    private static void executeClickCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 4) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        float x = 0;
        float y = 0;
        int timeout = 100;
        try {
            x = Float.parseFloat(tokens[2]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[2]);
        }
        try {
            y = Float.parseFloat(tokens[3]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[3]);
        }
        x = x * Settings.scale;
        y = y * Settings.scale;
        Gdx.input.setCursorPosition((int)x, (int)y);
        InputHelper.updateFirst();
        String token1 = tokens[1].toUpperCase();
        if (token1.equals("LEFT")) {
            InputHelper.justClickedLeft = true;
            InputHelper.isMouseDown = true;
            ReflectionHacks.setPrivateStatic(InputHelper.class, "isPrevMouseDown", true);
        } else if (token1.equals("RIGHT")) {
            InputHelper.justClickedRight = true;
            InputHelper.isMouseDown_R = true;
            ReflectionHacks.setPrivateStatic(InputHelper.class, "isPrevMouseDown_R", true);
        } else {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if (tokens.length >= 5) {
            try {
                timeout = Integer.parseInt(tokens[4]);
            } catch (NumberFormatException e) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[4]);
            }
            if(timeout < 0) {
                throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[4]);
            }
        }
        GameStateListener.setTimeout(timeout);
    }

    private static void executeWaitCommand(String[] tokens) throws InvalidCommandException {
        if (tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT);
        }
        int timeout = 0;
        try {
            timeout = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1]);
        }
        if(timeout < 0) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[1]);
        }
        GameStateListener.setTimeout(timeout);
    }

    private static int getKeycode(String keyName) {
        InputAction action;
        switch(keyName) {
            case "CONFIRM":
                action = InputActionSet.confirm;
                break;
            case "CANCEL":
                action = InputActionSet.cancel;
                break;
            case "MAP":
                action = InputActionSet.map;
                break;
            case "DECK":
                action = InputActionSet.masterDeck;
                break;
            case "DRAW_PILE":
                action = InputActionSet.drawPile;
                break;
            case "DISCARD_PILE":
                action = InputActionSet.discardPile;
                break;
            case "EXHAUST_PILE":
                action = InputActionSet.exhaustPile;
                break;
            case "END_TURN":
                action = InputActionSet.endTurn;
                break;
            case "UP":
                action = InputActionSet.up;
                break;
            case "DOWN":
                action = InputActionSet.down;
                break;
            case "LEFT":
                action = InputActionSet.left;
                break;
            case "RIGHT":
                action = InputActionSet.right;
                break;
            case "DROP_CARD":
                action = InputActionSet.releaseCard;
                break;
            case "CARD_1":
                action = InputActionSet.selectCard_1;
                break;
            case "CARD_2":
                action = InputActionSet.selectCard_2;
                break;
            case "CARD_3":
                action = InputActionSet.selectCard_3;
                break;
            case "CARD_4":
                action = InputActionSet.selectCard_4;
                break;
            case "CARD_5":
                action = InputActionSet.selectCard_5;
                break;
            case "CARD_6":
                action = InputActionSet.selectCard_6;
                break;
            case "CARD_7":
                action = InputActionSet.selectCard_7;
                break;
            case "CARD_8":
                action = InputActionSet.selectCard_8;
                break;
            case "CARD_9":
                action = InputActionSet.selectCard_9;
                break;
            case "CARD_10":
                action = InputActionSet.selectCard_10;
                break;
            default:
                action = null;
        }
        if (action == null) {
            return -1;
        } else {
            return (int) ReflectionHacks.getPrivate(action, InputAction.class, "keycode");
        }
    }

    private static int getValidChoiceIndex(String[] tokens, ArrayList<String> validChoices) throws InvalidCommandException {
        if(tokens.length < 2) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.MISSING_ARGUMENT, " A choice index is required.");
        }
        int choice_index;
        try {
            choice_index = Integer.parseInt(tokens[1]) - 1;
        } catch (NumberFormatException e) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.INVALID_ARGUMENT, tokens[1] + " (must be a number)");
        }
        if(choice_index < 0 || choice_index >= validChoices.size()) {
            throw new InvalidCommandException(tokens, InvalidCommandException.InvalidCommandFormat.OUT_OF_BOUNDS, tokens[1]);
        }
        if(validChoices.get(choice_index).contains("add potion")) {
            Boolean full = true;
            for(AbstractPotion potion: AbstractDungeon.player.potions) {
                if(potion instanceof PotionSlot)
                    full = false;
            }
            if(full)
                throw new InvalidCommandException("You first need to discard a potion.");
        }
        return choice_index;
    }

}
