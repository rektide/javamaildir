package net.ukrpost.storage.maildir;

import org.apache.log4j.Logger;

import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;

public class SimpleCountListener extends MessageCountAdapter {
    private final static Logger log = Logger.getLogger(SimpleCountListener.class);
    MessageCountEvent lastReceivedEvent = null;
    int addedMessageEvents = 0;
    int removedMessageEvents = 0;

    public void messagesAdded(MessageCountEvent e) {
        //log.debug("MessageCountEvent: "+e);
        //log.debug("msgs: "+e.getMessages().length);

        lastReceivedEvent = e;
        addedMessageEvents++;
    }

    public void messagesRemoved(MessageCountEvent e) {
        lastReceivedEvent = e;
        removedMessageEvents++;
    }

    public MessageCountEvent getLastReceivedEvent() {
        return lastReceivedEvent;
    }

    public int getAddedEventsCount() {
        return addedMessageEvents;
    }

    public int getRemovedEventsCount() {
        return removedMessageEvents;
    }

    public void resetCounters() {
        addedMessageEvents = removedMessageEvents = 0;
    }
}
