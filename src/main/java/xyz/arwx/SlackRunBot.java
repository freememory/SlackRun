package xyz.arwx;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import xyz.arwx.config.MasterConfig;
import xyz.arwx.livetrack.LiveTrackVerticle;
import xyz.arwx.mail.MailVerticle;
import xyz.arwx.slack.SlackVerticle;
import xyz.arwx.util.Json;

/**
 * Created by macobas on 17/07/17.
 */
public class SlackRunBot
{
    public static Vertx        vertx;
    public static MasterConfig config;

    public static void main(String[] args) throws InterruptedException
    {
        vertx = Vertx.vertx();
        vertx.deployVerticle(HttpVerticle.class.getName());
        Config c = ConfigFactory.parseResources("slackrun.conf");
        config = Json.objectFromJsonObject(new JsonObject(c.root().render(ConfigRenderOptions.concise())), MasterConfig.class);
        vertx.deployVerticle(SlackVerticle.class.getName(), new DeploymentOptions().setConfig(Json.objectToJsonObject(config.slackConfig)));
        vertx.deployVerticle(MailVerticle.class.getName(), new DeploymentOptions().setConfig(Json.objectToJsonObject(config.mailConfig)).setWorker(true));
        vertx.deployVerticle(LiveTrackVerticle.class.getName());

    }
}
