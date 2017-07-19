package xyz.arwx.async;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

/**
 * Created by macobas on 17/07/17.
 *
 * The AsyncTransaction represents a bundle of work. When it completes, it will call your callback.
 */
public class AsyncTransaction
{
    Logger logger = LoggerFactory.getLogger(AsyncTransaction.class);
    private class ExecutableTransaction extends AbstractVerticle
    {
        public void start()
        {
            logger.info("Starting txn");
            latch = new CountDownLatch(workMap.size());
            logger.info("Created latch, size {}", latch.getCount());
            vx.executeBlocking(fut -> {
                logger.info("ExecuteBlocking");
                while(true)
                {
                    try
                    {
                        latch.await();
                        logger.info("Done waiting");
                        break;
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }

                fut.complete();
            }, res -> {
                logger.info("Blocking CB");
                finalResult = new JsonObject();
                workMap.values().forEach(w -> {
                    finalResult.put(w.name, w.result);
                });

                List<AsyncWork> fails = workMap.values().stream().filter(w->w.isFailed).collect(Collectors.toList());
                if(fails != null && fails.size() > 0 && failureCb != null)
                    failureCb.handle(finalResult);
                else if(completeCb != null)
                    completeCb.handle(finalResult);
                vx.eventBus().publish(txid(), new JsonObject().put("done", true));
            });

            logger.info("Executing");
            workMap.values().forEach(AsyncWork::exec);
        }
    }

    private Map<String, AsyncWork> workMap = new HashMap<>();
    private boolean isStarted = false;
    public Vertx vx;
    private String txnId = UUID.randomUUID().toString();
    private Map<String, MessageConsumer> consumers = new HashMap<>();
    private CountDownLatch latch;
    private String execId = "";

    private Handler<JsonObject> failureCb;
    private Handler<JsonObject> completeCb;
    private Handler<JsonObject> eachCompleteCb;
    private Handler<JsonObject> eachFailCb;

    private JsonObject finalResult;

    public AsyncTransaction(Vertx vx)
    {
        this.vx = vx;
        vx.eventBus().consumer(txid(), this::txMsgs);
    }

    private void txMsgs(Message<JsonObject> msg)
    {
        JsonObject b = msg.body();
        if(b.getBoolean("done") && execId.length() > 0)
        {
            vx.undeploy(execId, r->{
                isStarted = false;
                execId = "";
            });
        }
    }

    public void complete1(AsyncWork asyncWork, JsonObject result)
    {
        vx.eventBus().publish(id(asyncWork), result);
    }

    public void fail1(AsyncWork asyncWork, JsonObject result)
    {
        vx.eventBus().publish(fid(asyncWork), result);
    }

    public String txid()
    {
        return "AsyncWork." + txnId;
    }

    public String id(AsyncWork asyncWork)
    {
        return txid() + "." + asyncWork.name;
    }

    public String fid(AsyncWork asyncWork)
    {
        return id(asyncWork) + ".FAIL";
    }

    public void onComplete(Handler<JsonObject> completeCb)
    {
        this.completeCb = completeCb;
    }

    public void onFailure(Handler<JsonObject> failureCb)
    {
        this.failureCb = failureCb;
    }

    public void onEach(Handler<JsonObject> eachCompleteCb, Handler<JsonObject> eachFailCb)
    {
        this.eachCompleteCb = eachCompleteCb; this.eachFailCb = eachFailCb;
    }

    public void execute()
    {
        if(!isStarted)
        {
            isStarted = true;
            vx.deployVerticle(new ExecutableTransaction(), r -> {
                execId = r.result();
            });
        }
    }

    public void addWork(AsyncWork work)
    {
        if(!isStarted)
        {
            work.txn = this;
            workMap.put(work.name, work);
            String cid = id(work), fid = fid(work);
            consumers.put(cid, vx.eventBus().consumer(cid, this::onComplete1));
            consumers.put(fid, vx.eventBus().consumer(fid, this::onFail1));
        }
        else
            throw new RuntimeException("Can't add work to a started / completed transaction");
    }

    private void onFail1(Message<JsonObject> msg)
    {
        _done(msg, false);
    }

    private void onComplete1(Message<JsonObject> msg)
    {
        _done(msg, true);
    }

    private void _done(Message<JsonObject> msg, boolean success)
    {
        String workName = msg.address().split("\\.")[2];
        AsyncWork w = workMap.get(workName);
        w.isFailed = !success;
        w.isComplete = success;
        w.result = msg.body();
        Handler<JsonObject> eachCb = success ? eachCompleteCb : eachFailCb;
        if(eachCb != null)
            eachCb.handle(w.result);
        logger.info("{} isComplete={}/isFailed={}", workName, w.isComplete, w.isFailed);
        latch.countDown();
    }

    public AsyncWork.HttpCall httpWork()
    {
        AsyncWork.HttpCall call = new AsyncWork.HttpCall();
        call.txn = this;
        return call;
    }

    public AsyncWork.HttpCall httpWork(HttpMethod method, String url, HttpClientOptions opts)
    {
        AsyncWork.HttpCall call = new AsyncWork.HttpCall(method, url, opts);
        call.txn = this;
        return call;
    }

    public JsonObject getResult()
    {
        return finalResult;
    }

    public AsyncWork getWorker(String name)
    {
        return workMap.get(name);
    }
}
