package net.ukrpost.storage.maildir;

import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.event.MessageChangedEvent;

public final class FlagChangedEvent extends MessageChangedEvent {
    public static final int ANSWERED = 4;
    public static final int DELETED = 8;
    public static final int DRAFT = 16;
    public static final int FLAGGED = 32;
    public static final int RECENT = 64;
    public static final int SEEN = 128;
    public static final int USER = 256;

    public static final int ISSET = 65536;

    public FlagChangedEvent(Object o, int i, Message message) {
        super(o, i, message);
    }

    public static int getEventType(Flags.Flag flag, boolean flagState) {
        int eventType = FLAGS_CHANGED;

        if (flag.equals(Flags.Flag.DELETED))
            eventType |= DELETED;
        else if (flag.equals(Flags.Flag.SEEN))
            eventType |= SEEN;
        else if (flag.equals(Flags.Flag.RECENT))
            eventType |= RECENT;
        else if (flag.equals(Flags.Flag.DRAFT))
            eventType |= DRAFT;
        else if (flag.equals(Flags.Flag.ANSWERED))
            eventType |= ANSWERED;
        else if (flag.equals(Flags.Flag.FLAGGED))
            eventType |= FLAGGED;
        else if (flag.equals(Flags.Flag.USER))
            eventType |= USER;
        if (flagState)
            eventType |= ISSET;
        return eventType;
    }

    public static int getEventType(Flags fl, boolean flagState) {
        final Flags.Flag[] flags = fl.getSystemFlags();
        int eventType = FLAGS_CHANGED;
        for (int i = 0; i < flags.length; i++) {
            eventType |= getEventType(flags[i], flagState);
        }
        return eventType;
    }
}
