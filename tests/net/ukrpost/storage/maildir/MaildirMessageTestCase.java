package net.ukrpost.storage.maildir;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.mail.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Properties;

public class MaildirMessageTestCase extends TestCase {
    private static Logger log = Logger.getLogger(MaildirMessageTestCase.class);

    private MaildirTestHelper helper = null;
    private MaildirFolder inbox = null;
    private File tmp = new File("tmp");
    private File maildirRoot = null;

    static {
//        BasicConfigurator.configure();
        final Properties props = new Properties();
        props.put("log4j.rootLogger", ", D");
        props.put("log4j.appender.D", "org.apache.log4j.ConsoleAppender");
        props.put("log4j.appender.D.layout", "org.apache.log4j.PatternLayout");
        props.put("log4j.appender.D.layout.ConversionPattern",
                "[%t] %-5p %c{1} %x - %m%n");
        props.put("log4j.logger.net.ukrpost.storage", "DEBUG");
        PropertyConfigurator.configure(props);
    }

    protected void setUp() throws Exception {
        if (!tmp.exists())
            tmp.mkdirs();

        assertTrue(tmp.exists());
        assertTrue(tmp.canWrite());
        maildirRoot = new File(tmp, "maildirtest");

        helper = new MaildirTestHelper();
        final Properties props = new Properties();
        props.setProperty("mail.store.maildir.autocreatedir", "true");
        final String url = "maildir:///"+maildirRoot.getPath();
        inbox = (MaildirFolder) Session.getInstance(props).getStore(new URLName(url)).getFolder("inbox");
        if (inbox.getCurDir().exists()) {
            MaildirTestHelper.rmdir(inbox.getCurDir().getParentFile());
        }
        //here messages are updated. no messages in inbox.
        inbox.open(Folder.READ_WRITE);
        assertEquals(0, inbox.getCurDir().list().length);
        assertEquals(0, inbox.getNewDir().list().length);
        //assertEquals(0, inbox.getMessageCount());

        MaildirTestHelper.sleep(1000);
        //some external delivery happens,
        final long inboxLastModified = inbox.getNewDir().lastModified();

        if (MaildirTestHelper.OS_WINDOWS) {
            //on windows lastmodified is one second grained 
            //so if folder creation and message delivery happens 
            //within the bounds of one second lastmodified value 
            //stays the same and maildirfolder thinks no messages were added
            //here we sleep for 10 sec and then deliver
            Thread.sleep(1000);
        }
        helper.doExternalDelivery(inbox);
        final long inboxLastModified2 = inbox.getNewDir().lastModified();
        assertTrue(inboxLastModified != inboxLastModified2);

        assertEquals(2, inbox.getNewDir().listFiles().length);
        //but MaildirFolder is not aware of that change, so getMessage(1)
        //MUST return null;
        Message m = null;
        try {
            m = inbox.getMessage(1);
        } catch (IndexOutOfBoundsException iobex) {
        }
        log.debug("m: " + m);
        assertTrue(m == null);

        //update the state of messages by calling getNewMessageCount
        final int newmsgs = inbox.getNewMessageCount();
        assertEquals(2, newmsgs);
    }

    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(MaildirMessageTestCase.class);
    }

    public void testIsSet() throws MessagingException, IOException {
        final Message m = inbox.getMessage(1);
        assertTrue(m.isSet(Flags.Flag.RECENT));
    }

    //0.4pre5 used to have a bug when you called writeTo after setting flags it failed with 
    //IOException: Bad file descriptor
    public void testWriteTo() throws MessagingException, IOException {
        final Message m = inbox.getMessage(1);
        assertTrue(m.isSet(Flags.Flag.RECENT));
        m.setFlag(Flags.Flag.SEEN, true);
        m.setFlag(Flags.Flag.RECENT, false);
        m.writeTo(System.out);
    }

    protected void tearDown() {
        try {
            inbox.close(true);
        } catch (Exception ex) {
        }
        MaildirTestHelper.rmdir(inbox.getCurDir().getParentFile());
        helper.closeStreams();
    }

    private void listFolder(MaildirFolder folder) {
        final String[] curs = folder.getCurDir().list();
        for (int i = 0; i < curs.length; i++)
            System.out.println(folder.getName() + "/cur/" + curs[i]);
        final String[] news = folder.getNewDir().list();
        for (int i = 0; i < news.length; i++)
            System.out.println(folder.getName() + "/new/" + news[i]);

    }

    public void testGetRawInputStream() throws Exception {
        final MaildirMessage mm = (MaildirMessage) inbox.getMessage(1);
        final InputStream in = mm.getRawInputStream();
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        int r = 0;
        while ((r = in.read()) >= 0)
            out.write(r);

        assertTrue(new String(out.toByteArray()).startsWith("Jeff"));
    }

}
