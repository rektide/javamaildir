package net.ukrpost.storage.maildir;

import javax.mail.event.MessageChangedEvent;
import javax.mail.event.MessageChangedListener;

public class SimpleStateListener implements MessageChangedListener {
    MessageChangedEvent lastReceivedEvent = null;
    int events = 0;

    public void messageChanged(MessageChangedEvent e) {
        lastReceivedEvent = e;
        events++;
    }

    public MessageChangedEvent getLastReceivedEvent() {
        return lastReceivedEvent;
    }

    public int getEventsCount() {
        return events;
    }

    public void resetCounters() {
        events = 0;
    }
}
