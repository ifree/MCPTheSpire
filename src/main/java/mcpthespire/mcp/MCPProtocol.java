package mcpthespire.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP Protocol constants and message builders for JSON-RPC 2.0 communication.
 */
public class MCPProtocol {

    public static final String JSONRPC_VERSION = "2.0";
    public static final String MCP_VERSION = "2024-11-05";

    // MCP Methods
    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_INITIALIZED = "notifications/initialized";
    public static final String METHOD_TOOLS_LIST = "tools/list";
    public static final String METHOD_TOOLS_CALL = "tools/call";
    public static final String METHOD_PING = "ping";

    // Error codes
    public static final int ERROR_PARSE = -32700;
    public static final int ERROR_INVALID_REQUEST = -32600;
    public static final int ERROR_METHOD_NOT_FOUND = -32601;
    public static final int ERROR_INVALID_PARAMS = -32602;
    public static final int ERROR_INTERNAL = -32603;

    private static final Gson gson = new GsonBuilder().create();

    private static final JsonParser jsonParser = new JsonParser();

    /**
     * Parse a JSON-RPC request from a string.
     */
    public static JsonObject parseRequest(String json) {
        return jsonParser.parse(json).getAsJsonObject();
    }

    /**
     * Get the method from a request.
     */
    public static String getMethod(JsonObject request) {
        return request.has("method") ? request.get("method").getAsString() : null;
    }

    /**
     * Get the id from a request.
     */
    public static JsonElement getId(JsonObject request) {
        return request.has("id") ? request.get("id") : null;
    }

    /**
     * Get the params from a request.
     */
    public static JsonObject getParams(JsonObject request) {
        return request.has("params") ? request.get("params").getAsJsonObject() : null;
    }

    /**
     * Build a successful response.
     */
    public static String buildResponse(JsonElement id, Object result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", JSONRPC_VERSION);
        response.add("id", id);
        response.add("result", gson.toJsonTree(result));
        return gson.toJson(response);
    }

    /**
     * Build an error response.
     */
    public static String buildErrorResponse(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", JSONRPC_VERSION);
        response.add("id", id);

        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        response.add("error", error);

        return gson.toJson(response);
    }

    /**
     * Build the initialize response.
     */
    public static Map<String, Object> buildInitializeResult(String serverName, String serverVersion) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", MCP_VERSION);

        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> tools = new HashMap<>();
        capabilities.put("tools", tools);
        result.put("capabilities", capabilities);

        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);
        result.put("serverInfo", serverInfo);

        return result;
    }

    /**
     * Build a tools/list response.
     */
    public static Map<String, Object> buildToolsListResult(List<Map<String, Object>> tools) {
        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return result;
    }

    /**
     * Build a tool call success result.
     */
    public static Map<String, Object> buildToolCallResult(String text, boolean isError) {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", text);
        content.add(textContent);

        result.put("content", content);
        if (isError) {
            result.put("isError", true);
        }

        return result;
    }

    /**
     * Build a tool call result with JSON data.
     */
    public static Map<String, Object> buildToolCallResultJson(Object data) {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> content = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");
        textContent.put("text", gson.toJson(data));
        content.add(textContent);

        result.put("content", content);

        return result;
    }

    /**
     * Helper to create a tool definition.
     */
    public static Map<String, Object> createToolDefinition(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        return tool;
    }

    /**
     * Helper to create an input schema.
     */
    public static Map<String, Object> createInputSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required != null && !required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * Helper to create a property definition.
     */
    public static Map<String, Object> createProperty(String type, String description) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("type", type);
        prop.put("description", description);
        return prop;
    }

    /**
     * Helper to create an enum property definition.
     */
    public static Map<String, Object> createEnumProperty(String description, List<String> values) {
        Map<String, Object> prop = new HashMap<>();
        prop.put("type", "string");
        prop.put("description", description);
        prop.put("enum", values);
        return prop;
    }
}
