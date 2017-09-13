package xyz.arwx.trigger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.arwx.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by macobas on 12/09/17.
 */
public abstract class TriggerResponse
{
    private static final Logger logger = LoggerFactory.getLogger(TriggerResponse.class);

    public abstract String responseUrl();

    public String responseUrl;

    public TriggerResponse setResponseUrl(String responseUrl)
    {
        this.responseUrl = responseUrl;
        return this;
    }

    @JsonIgnore
    public TriggerResponse setIsError(boolean isError)
    {
        return this;
    }

    @JsonIgnore
    public TriggerResponse setResponseText(String responseText)
    {
        this.responseText = responseText;
        return this;
    }

    @JsonProperty("text")
    public String responseText;

    @JsonProperty("attachments")
    public List<Map<String, Object>> attachments = new ArrayList<>();

    public TriggerResponse addAttachment(Map<String, Object> attachment)
    {
        attachments.add(attachment);
        return this;
    }

    public void send(Vertx vx)
    {
        JsonObject jso = Json.objectToJsonObject(this);
        jso.remove("responseUrl");
        vx.createHttpClient().postAbs(responseUrl(), resp -> {
            logger.info("Posted to {}, received response: {} {}", responseUrl(), resp.statusCode(), resp.statusMessage());
        }).end(jso.encode());
    }
}
