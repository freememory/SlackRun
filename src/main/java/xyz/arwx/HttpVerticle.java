package xyz.arwx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.arwx.slack.SlackVerticle;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Created by macobas on 19/07/17.
 */
public class HttpVerticle extends AbstractVerticle
{
    private HttpServer server;
    private Router     router;
    private static final Logger logger = LoggerFactory.getLogger(HttpVerticle.class);

    private JsonObject decodeRequest(String request)
    {
        JsonObject ret = new JsonObject();

        Arrays.stream(request.split("&")).forEach(param -> {
            String[] split = param.split("=");
            if (split.length != 2)
                return;

            try
            {
                ret.put(split[0], URLDecoder.decode(split[1], StandardCharsets.UTF_8.toString()));
            }
            catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }
        });

        return ret;
    }

    public void start()
    {
        server = vertx.createHttpServer();
        router = Router.router(vertx);
        router.route("/slack").handler(BodyHandler.create());
        router.post("/slack").handler(ctx -> {
            String body = ctx.getBodyAsString();
            JsonObject r = decodeRequest(body);
            logger.info("Request received: {}", r.encodePrettily());
            vertx.eventBus().publish(SlackVerticle.InboundSlashCommand, r);
            ctx.response().setStatusCode(200).putHeader("Content-Type", "application/json").end(new JsonObject().
                                                                                                                        put("response_type", "in_channel").put("text", "").encode());
        });

        server.requestHandler(router::accept).listen(8080);
    }
}
