
import org.apache.log4j.BasicConfigurator;

import javax.mail.*;
import java.util.Properties;
import java.util.Arrays;
import java.io.IOException;
import java.io.InputStream;

public class Bad {
    public static void main(String[] args) throws MessagingException, IOException {
        BasicConfigurator.configure();
        Session session = Session.getInstance(new Properties());
        Store store = session.getStore(new URLName("maildir:///Bad/"));
        store.connect();
        Folder badFolder = store.getFolder("BadAttach");
        badFolder.open(Folder.READ_WRITE);
        Message[] messages = badFolder.getMessages();
        for (int i = 0; i < messages.length; i++) {
            final Message message = messages[i];
            final Object content = message.getContent();
            System.out.println(message.getMessageNumber() + ": " + message.getSubject() + " " + Arrays.asList(message.getFrom()) + " " + content);

            if (content instanceof InputStream) {
                InputStream in = (InputStream) content;
                int r = 0;
                while ((r = in.read()) >= 0) {
                    System.out.write(r);
                }
            }

        }

    }
}
