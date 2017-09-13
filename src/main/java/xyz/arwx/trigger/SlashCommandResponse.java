package xyz.arwx.trigger;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.arwx.util.Json;

import static xyz.arwx.trigger.SlashCommandResponse.ResponseType.Ephemeral;
import static xyz.arwx.trigger.SlashCommandResponse.ResponseType.InChannel;

/**
 * Created by macobas on 19/07/17.
 */
public class SlashCommandResponse extends TriggerResponse
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

    public static SlashCommandResponse of(JsonObject request)
    {
        SlashCommandResponse response = new SlashCommandResponse();
        response.responseUrl = request.getString("response_url");
        response.responseType = InChannel;
        return response;
    }

    @Override
    public SlashCommandResponse setIsError(boolean error)
    {
        responseType = error ? Ephemeral : InChannel;
        return this;
    }

    @Override
    public String responseUrl()
    {
        return responseUrl;
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
