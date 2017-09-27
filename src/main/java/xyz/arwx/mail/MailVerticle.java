package xyz.arwx.mail;

/**
 * Created by macobas on 23/07/17.
 */

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.arwx.config.MailConfig;
import xyz.arwx.util.Json;

import javax.mail.*;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.FlagTerm;
import java.io.IOException;
import java.time.*;
import java.time.temporal.TemporalUnit;
import java.util.*;

public class MailVerticle extends AbstractVerticle
{
    public static final  String OutboundAddress = MailVerticle.class.getName() + ".NewMail";
    private static final Logger logger          = LoggerFactory.getLogger(MailVerticle.class);
    private MailConfig config;

    public void start()
    {
        logger.info("Starting MailVerticle");
        config = Json.objectFromJsonObject(config(), MailConfig.class);
        vertx.setPeriodic(120 * 1000, l -> {
            try {
                pollMail();
            } catch (MessagingException e) {
                e.printStackTrace();
            }
        });
    }

    private String getTextFromMessage(Message message) throws MessagingException, IOException
    {
        String result = "";
        if (message.isMimeType("text/plain"))
        {
            result = message.getContent().toString();
        }
        else if (message.isMimeType("text/html"))
        {
            String html = (String) message.getContent();
            result += html;
        }
        else if (message.isMimeType("multipart/*"))
        {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(mimeMultipart);
        }
        else
        {
            // just mark it read
            message.getContent();
        }
        return result;
    }

    private String getTextFromMimeMultipart(
            MimeMultipart mimeMultipart) throws MessagingException, IOException
    {
        String result = "";
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++)
        {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain"))
            {
                result += bodyPart.getContent();
                break;
            }
            else if (bodyPart.isMimeType("text/html"))
            {
                String html = (String) bodyPart.getContent();
                result = html;
            }
            else if (bodyPart.getContent() instanceof MimeMultipart)
            {
                result += getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent());
            }

            result += "\n";
        }
        return result;
    }

    public List<String> getToList(Message m)
    {
        List<String> ret = new ArrayList<>();
        try
        {
            for (Address addr : m.getAllRecipients())
            {
                InternetAddress inaddr = (InternetAddress) addr;
                ret.add(inaddr.getAddress());
            }
        }
        catch (MessagingException e)
        {
            e.printStackTrace();
        }

        return ret;
    }

    private void pollMail() throws MessagingException
    {
        logger.info("Polling inbox");
        Session sesh = getMailSession();
        IMAPStore store = null;
        Folder inbox = null;

        store = (IMAPStore) sesh.getStore("imaps");
        store.connect(config.userName, config.password);
        inbox = (IMAPFolder) store.getFolder("INBOX");
        if(inbox == null || !inbox.exists())
            throw new RuntimeException("Can't connect to INBOX");
        inbox.open(Folder.READ_WRITE);
        // Only pull unread
        FlagTerm ft = new FlagTerm(new Flags(Flags.Flag.SEEN), false);
        Message[] messages = inbox.search(ft);
        // Sort messages from recent to oldest
        Arrays.sort(messages, (m1, m2 ) -> {
            try {
                return m2.getSentDate().compareTo(m1.getSentDate());
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        });
        for (Message message : messages)
        {
            try
            {
                JsonObject msg = new JsonObject()
                        .put("from", ((InternetAddress) message.getFrom()[0]).getAddress())
                        .put("to", new JsonArray(getToList(message)))
                        .put("subject", message.getSubject())
                        .put("body", getTextFromMessage(message));

                if(message.getSentDate().before(Date.from(Instant.now().minus(Duration.ofHours(2)))))
                    continue;
                logger.info("Found potentially interesting mail message. From={}, To={}, Subject={}",
                        msg.getString("from"), msg.getJsonArray("to").encode(), msg.getString("subject"));
                vertx.eventBus().publish(MailVerticle.OutboundAddress, msg);
            }
            catch (MessagingException | IOException e)
            {
                e.printStackTrace();
            }
        }

        logger.info("Done polling, {} messages retrieved", messages.length);
        inbox.setFlags(messages, new Flags(Flags.Flag.SEEN), true);
        inbox.close(false);
        store.close();
    }

    private Session getMailSession()
    {
        Properties properties = new Properties();
        properties.put("mail.store.protocol", "imaps");
        properties.put("mail.imaps.host", config.host);
        properties.put("mail.imaps.port", config.port.toString());
        Session emailSession = Session.getInstance(properties);
        return emailSession;
    }
}