package examples;

import java.util.*;
import javax.mail.*;
import javax.mail.event.*;
import javax.mail.internet.*;
import org.apache.log4j.*;
import net.ukrpost.storage.maildir.*;

public class listener {
    private static Logger log = Logger.getLogger(listener.class);
    public class MyListener extends MessageCountAdapter {
        public MessageCountEvent receivedEvent = null;
        public void messagesAdded(MessageCountEvent e) {
            try {
            receivedEvent = e;
            System.out.println("start messagesAdded "+Thread.currentThread().hashCode());
            Message msgs[] = e.getMessages();
            for(int i=0; i<msgs.length;i++) {
                Message m = msgs[i];
                System.out.println("message: "+m.getSubject());
                m.setFlag(Flags.Flag.DELETED,true);
                m.getFolder().expunge();
                sleep(5000);
            }
            System.out.println("end messagesAdded "+Thread.currentThread().hashCode());
            } catch (MessagingException mex) {
                mex.printStackTrace();
            }
        }

        private void sleep(long t) {
            try { Thread.sleep(t); }catch(Exception ex) {}
        }
    }

    public static void main(String args[]) throws Exception {
        BasicConfigurator.configure();
        listener l = new listener();
        l.run();
    }

    public void run() throws Exception {
        Properties props = new Properties();
        props.setProperty("mail.store.maildir.autocreatedir", "true");
        Session session = Session.getInstance(props);
        Store store = session.getStore(new URLName("maildir:///tmp/maildirtest"));
        Folder folder = store.getFolder("inbox");
        folder.open(Folder.READ_WRITE);
        folder.addMessageCountListener(new MyListener());
        MaildirFolder mfolder = (MaildirFolder)folder;
        int msgs = folder.getMessageCount();
        System.out.println("total message number: "+msgs);
        if ( msgs >= 2 ) {
            Message m = folder.getMessage(2);
            System.out.println(m.getSubject());
        }

        for(;;Thread.sleep(1000)) {
            System.out.println("hasNewMessages?: "+folder.hasNewMessages());
            //mfolder.debugMessages(System.out);
        }
    }
}
