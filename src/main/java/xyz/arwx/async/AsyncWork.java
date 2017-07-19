package xyz.arwx.async;

import io.vertx.core.AsyncResult;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static xyz.arwx.async.AsyncWork.HttpCall.BodyType.JSONOBJECT;

/**
 * Created by macobas on 18/07/17.
 */
public abstract class AsyncWork
{
    public AsyncTransaction txn;
    public String           name;
    public boolean    isFailed   = false;
    public boolean    isComplete = false;
    public JsonObject result     = null;

    public AsyncWork setName(String n)
    {
        this.name = n;
        return this;
    }

    public boolean isDone()
    {
        return isFailed || isComplete;
    }

    public void complete(JsonObject result)
    {
        txn.complete1(this, result);
    }

    public void fail(JsonObject result)
    {
        txn.fail1(this, result);
    }

    protected boolean executeOrEmit()
    {
        if (!isDone())
            return false;
        if (isFailed)
            fail(result);
        if (isComplete)
            complete(result);
        return true;
    }

    protected abstract void work();

    public void exec()
    {
        if (executeOrEmit())
            return;
        work();
    }

    public static class HttpCall extends AsyncWork
    {
        // Method - conform to HttpMethod
        public HttpMethod        method;
        // FQURL with port and everything
        public String            url;
        // Opts
        public HttpClientOptions opts;

        public HttpCall()
        {
            this(HttpMethod.GET, "http://localhost/", new HttpClientOptions());
        }

        public HttpCall(HttpMethod method, String url, HttpClientOptions opts)
        {
            this.method = method;
            this.url = url;
            this.opts = opts;
        }

        public enum BodyType
        {
            STRING,
            JSONOBJECT,
            JSONARRAY,
            RAW
        }

        public HttpCall setBodyType(BodyType type)
        {
            this.bodyType = type;
            return this;
        }

        public BodyType bodyType = JSONOBJECT;
        private HttpClientRequest req;

        public HttpClientRequest getRequest()
        {
            if (req != null)
                return req;

            req = txn.vx.createHttpClient(opts)
                        .requestAbs(method, url);

            return req;
        }

        protected void work()
        {
            getRequest().handler(this::onHttpComplete);
            getRequest().end();
        }

        private void onHttpComplete(HttpClientResponse httpClientResponse)
        {
            JsonObject response = new JsonObject()
                    .put("status", httpClientResponse.statusCode())
                    .put("statusMessage", httpClientResponse.statusMessage())
                    .put("headers", new JsonArray(httpClientResponse.headers().entries().stream()
                                                                    .map(e -> new JsonObject().put(e.getKey(), e.getValue()))
                                                                    .collect(Collectors.toList())));

            if (httpClientResponse.statusCode() / 200 == 1)
            {
                httpClientResponse.bodyHandler(buf -> {
                    switch (bodyType)
                    {
                        case STRING:
                            response.put("body", buf.toString(StandardCharsets.UTF_8));
                            break;
                        case JSONARRAY:
                            response.put("body", buf.toJsonArray());
                            break;
                        case JSONOBJECT:
                            response.put("body", buf.toJsonObject());
                            break;
                        case RAW:
                        default:
                            response.put("body", buf);
                    }
                    complete(response);
                });
            }
            else
                fail(response);
        }
    }

    public static class EventBusCall extends AsyncWork
    {
        public String     address;
        public JsonObject message;

        protected void work()
        {
            txn.vx.eventBus().send(address, message, this::onEbComplete);
        }

        public void onEbComplete(AsyncResult<Message<JsonObject>> res)
        {
            JsonObject response = new JsonObject()
                    .put("succeeded", res.succeeded())
                    .put("failed", res.failed());
            if (res.failed())
            {
                response.put("failureCause", res.cause().getMessage());
                fail(response);
            }
            else
            {
                response.put("body", res.result().body());
                complete(response);
            }
        }
    }
}
