package xyz.arwx.trigger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.arwx.util.Json;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.stream.Collectors;

/**
 * Created by macobas on 12/09/17.
 */
public class TextCommandResponse extends TriggerResponse
{
    private static final Logger logger = LoggerFactory.getLogger(TextCommandResponse.class);

    public String  thread_ts;
    public boolean reply_broadcast;
    public String  channel;
    public String  token;

    @Override
    public String responseUrl()
    {
        return responseUrl;
    }

    @Override
    public void send(Vertx vx)
    {
        JsonObject jso = Json.objectToJsonObject(this);
        jso.remove("type");
        String attachments = "";
        try
        {
            JsonArray a = jso.getJsonArray("attachments");
            if (a != null && a.size() > 0)
                attachments = URLEncoder.encode(a.encode(), "UTF-8");
            jso.remove("attachments");
        }
        catch (UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }

        String encoded = String.join("&", jso.fieldNames().stream().map(s -> {
            try
            {
                return s + "=" + URLEncoder.encode(jso.getValue(s).toString(), "UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
                return "";
            }
        }).filter(s -> s.length() > 0).collect(Collectors.toList()));

        if (attachments.length() > 0)
            encoded += "&attachments=" + attachments;

        vx.createHttpClient().postAbs(this.responseUrl, resp -> {
            resp.bodyHandler(buf -> {
                JsonObject reply = buf.toJsonObject();
                logger.info("Received reply to postMessage: {}", reply.encodePrettily());
            });
        }).putHeader("Content-Type", "application/x-www-form-urlencoded").end(encoded);
    }

    @Override
    @JsonIgnore
    public TextCommandResponse setIsError(boolean error)
    {
        reply_broadcast = !error;
        return this;
    }
}
