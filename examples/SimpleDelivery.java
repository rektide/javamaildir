package examples;

import javax.mail.*;
import javax.mail.internet.*;

public class SimpleDelivery {
    public static void main(String args[]) throws Exception {
        MimeMessage mm = new MimeMessage((Session)null);
        mm.setText("hello\nworld\n");
        java.util.Properties p = new java.util.Properties();
        p.put("mail.store.maildir.autocreatedir", "true");
        Session session = Session.getDefaultInstance(p);
        Store store = session.getStore(new URLName("maildir:///tmp/Maildir"));

        Folder inbox = store.getFolder("INBOX");
        inbox.appendMessages(new Message[]{mm});
    }
}
