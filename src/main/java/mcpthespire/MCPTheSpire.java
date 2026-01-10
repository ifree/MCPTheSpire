package mcpthespire;

import basemod.*;
import basemod.interfaces.PostDungeonUpdateSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostRenderSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import basemod.eventUtil.EventUtils;
import basemod.eventUtil.AddEventParams;
import com.evacipated.cardcrawl.modthespire.lib.SpireConfig;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.FontHelper;
import com.megacrit.cardcrawl.helpers.ImageMaster;
import com.megacrit.cardcrawl.events.shrines.FaceTrader;
import mcpthespire.mcp.MCPServer;
import mcpthespire.patches.InputActionPatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Properties;

@SpireInitializer
public class MCPTheSpire implements PostInitializeSubscriber, PostUpdateSubscriber, PostDungeonUpdateSubscriber, PreUpdateSubscriber, PostRenderSubscriber {

    private static final Logger logger = LogManager.getLogger(MCPTheSpire.class.getName());
    private static final String MODNAME = "MCP The Spire";
    private static final String AUTHOR = "ifree";
    private static final String DESCRIPTION = "This mod allows AI agents to play the game via MCP";

    // Config keys
    private static final String CONFIG_HOST = "host";
    private static final String CONFIG_PORT = "port";

    // Default values
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;

    // Current config values
    private static String configHost = DEFAULT_HOST;
    private static int configPort = DEFAULT_PORT;
    private static SpireConfig config;

    private static Thread mcpServerThread;
    private static MCPServer mcpServer;
    public static boolean mustSendGameState = false;

    public MCPTheSpire() {
        BaseMod.subscribe(this);
        loadConfig();
        startMCPServer();
    }

    public static void initialize() {
        MCPTheSpire mod = new MCPTheSpire();
    }

    private void loadConfig() {
        try {
            Properties defaults = new Properties();
            defaults.setProperty(CONFIG_HOST, DEFAULT_HOST);
            defaults.setProperty(CONFIG_PORT, String.valueOf(DEFAULT_PORT));

            config = new SpireConfig("MCPTheSpire", "config", defaults);

            configHost = config.getString(CONFIG_HOST);
            configPort = config.getInt(CONFIG_PORT);

            logger.info("Loaded config: host=" + configHost + ", port=" + configPort);
        } catch (IOException e) {
            logger.error("Failed to load config, using defaults", e);
            configHost = DEFAULT_HOST;
            configPort = DEFAULT_PORT;
        }
    }

    public void receivePreUpdate() {
        // Process any pending MCP tool calls on the game thread
        if (mcpServer != null && mcpServer.hasPendingToolCalls()) {
            logger.info("Processing pending MCP tool call on game thread");
            mcpServer.processPendingToolCalls();
            logger.info("Finished processing MCP tool call");
        }
    }

    public void receivePostInitialize() {
        setUpOptionsMenu();
        BaseMod.addEvent((new AddEventParams.Builder(FaceTrader.ID, FaceTrader.class))
            .eventType(EventUtils.EventType.FULL_REPLACE).overrideEvent("Match and Keep!")
            .create());
    }

    public void receivePostUpdate() {
        // Also process MCP tool calls here in case PreUpdate isn't called
        if (mcpServer != null && mcpServer.hasPendingToolCalls()) {
            logger.info("Processing pending MCP tool call in PostUpdate");
            mcpServer.processPendingToolCalls();
        }

        if (!mustSendGameState && GameStateListener.checkForMenuStateChange()) {
            mustSendGameState = true;
        }
        if (mustSendGameState) {
            // State is sent through tool calls, so just reset the flag
            mustSendGameState = false;
        }
        InputActionPatch.doKeypress = false;
    }

    public void receivePostDungeonUpdate() {
        if (GameStateListener.checkForDungeonStateChange()) {
            mustSendGameState = true;
        }
        if (AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateListener.signalTurnEnd();
        }
    }

    @Override
    public void receivePostRender(SpriteBatch sb) {
        // Process MCP tool calls during render - this is called even in menus
        if (mcpServer != null && mcpServer.hasPendingToolCalls()) {
            mcpServer.processPendingToolCalls();
        }
    }

    private void setUpOptionsMenu() {
        ModPanel settingsPanel = new ModPanel();

        float yPos = 750;
        float xPos = 350;
        float lineHeight = 50;

        // Title
        ModLabel titleLabel = new ModLabel(
            "MCP The Spire Configuration", xPos, yPos, Settings.GOLD_COLOR, FontHelper.charDescFont,
            settingsPanel, modLabel -> {});
        settingsPanel.addUIElement(titleLabel);
        yPos -= lineHeight;

        // Status
        ModLabel statusLabel = new ModLabel(
            "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
            settingsPanel, modLabel -> {
                if (mcpServer != null) {
                    modLabel.text = "Status: Running on " + mcpServer.getHost() + ":" + mcpServer.getPort();
                } else {
                    modLabel.text = "Status: Not started";
                }
            });
        settingsPanel.addUIElement(statusLabel);
        yPos -= lineHeight;

        // MCP URL
        ModLabel mcpLabel = new ModLabel(
            "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
            settingsPanel, modLabel -> {
                if (mcpServer != null) {
                    modLabel.text = "MCP URL: http://" + mcpServer.getHost() + ":" + mcpServer.getPort() + "/mcp";
                } else {
                    modLabel.text = "MCP URL: http://" + configHost + ":" + configPort + "/mcp";
                }
            });
        settingsPanel.addUIElement(mcpLabel);
        yPos -= lineHeight * 1.5f;

        // Host config
        ModLabel hostLabel = new ModLabel(
            "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
            settingsPanel, modLabel -> {
                modLabel.text = "Host: " + configHost;
            });
        settingsPanel.addUIElement(hostLabel);
        yPos -= lineHeight;

        // Port config
        ModLabel portLabel = new ModLabel(
            "", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
            settingsPanel, modLabel -> {
                modLabel.text = "Port: " + configPort;
            });
        settingsPanel.addUIElement(portLabel);
        yPos -= lineHeight * 1.5f;

        // Config file note
        ModLabel configNote = new ModLabel(
            "Edit config at: MCPTheSpire/config.properties", xPos, yPos, Settings.CREAM_COLOR, FontHelper.charDescFont,
            settingsPanel, modLabel -> {});
        settingsPanel.addUIElement(configNote);

        BaseMod.registerModBadge(ImageMaster.loadImage("icon.png"), "MCP The Spire", "ifree",
            "Allows AI agents to play via MCP protocol", settingsPanel);
    }

    private void startMCPServer() {
        mcpServer = new MCPServer(configHost, configPort);
        mcpServerThread = new Thread(mcpServer, "MCPServer");
        mcpServerThread.setDaemon(true);
        mcpServerThread.start();
        logger.info("MCP Server thread started on " + configHost + ":" + configPort);

        if (GameStateListener.isWaitingForCommand()) {
            mustSendGameState = true;
        }
    }

    public static void dispose() {
        logger.info("Shutting down MCP server...");
        if (mcpServer != null) {
            mcpServer.stop();
        }
    }
}
