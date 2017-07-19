package xyz.arwx.slack;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.arwx.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static xyz.arwx.slack.SlashCommandResponse.ResponseType.InChannel;

/**
 * Created by macobas on 19/07/17.
 */
public class SlashCommandResponse
{
    private static Logger logger = LoggerFactory.getLogger(SlashCommandResponse.class);
    public enum ResponseType
    {
        @JsonProperty("ephemeral")
        Ephemeral,

        @JsonProperty("in_channel")
        InChannel;
    }

    @JsonProperty("response_type")
    public ResponseType responseType;
    public String responseUrl;

    @JsonProperty("text")
    public String responseText;

    @JsonProperty("attachments")
    public List<Map<String, Object>> attachments = new ArrayList<>();

    public static SlashCommandResponse of(JsonObject request)
    {
        SlashCommandResponse response = new SlashCommandResponse();
        response.responseUrl = request.getString("response_url");
        response.responseType = InChannel;
        return response;
    }

    public void send(Vertx vx)
    {
        JsonObject jso = Json.objectToJsonObject(this);
        jso.remove("responseUrl");
        vx.createHttpClient().postAbs(this.responseUrl, resp -> {
            logger.info("Posted to {}, received response: {} {}", responseUrl, resp.statusCode(), resp.statusMessage());
        }).end(jso.encode());
    }
}
