
import org.apache.log4j.BasicConfigurator;

import javax.mail.*;
import java.util.Properties;

public class ChangeFlag {
    public static void main(String[] args) throws MessagingException {
//        BasicConfigurator.configure();
        Session s = Session.getDefaultInstance(new Properties());
        Folder inbox = s.getStore(new URLName("maildir:///LargeMaildir/")).getFolder("inbox");
        long start = System.currentTimeMillis();
        inbox.open(Folder.READ_WRITE);
        long end = System.currentTimeMillis();
        System.out.println("open: "+(end-start)+"ms");
        Message message = inbox.getMessage(20);
        System.out.println("is recent: "+ message.isSet(Flags.Flag.RECENT));
        System.out.println("new messages: "+inbox.getNewMessageCount());
        message.setFlag(Flags.Flag.RECENT, true);
        System.out.println("is recent: "+ message.isSet(Flags.Flag.RECENT));

        start = System.currentTimeMillis();
        System.out.println("new messages: "+inbox.getNewMessageCount());
        end = System.currentTimeMillis();
        System.out.println("getNewMessageCount: "+(end-start)+"ms");

        start = System.currentTimeMillis();
        inbox.close(true);
        end = System.currentTimeMillis();
        System.out.println("close: "+(end-start)+"ms");
    }
}
