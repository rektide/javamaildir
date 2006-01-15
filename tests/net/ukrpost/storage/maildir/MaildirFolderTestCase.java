package net.ukrpost.storage.maildir;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.ukrpost.utils.QuotaExceededException;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.mail.*;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Properties;

public class MaildirFolderTestCase extends TestCase {

    Store currentStore = null;
    MaildirFolder inbox = null;
    MaildirTestHelper helper = null;
    InputStream testMessage1 = null;
    InputStream testMessage2 = null;
    private static Logger log = Logger.getLogger(MaildirFolderTestCase.class);

    static {
        //BasicConfigurator.configure();
        final Properties props = new Properties();
        props.put("log4j.rootLogger", ", D");
        props.put("log4j.appender.D", "org.apache.log4j.ConsoleAppender");
        props.put("log4j.appender.D.layout", "org.apache.log4j.PatternLayout");
        props.put("log4j.appender.D.layout.ConversionPattern",
                "[%t] %-5p %c{1} %x - %m%n");
        props.put("log4j.logger.net.ukrpost.storage", "DEBUG");
        PropertyConfigurator.configure(props);
    }

    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    MimeMessage helloWorldMessage = null;

    protected void setUp() throws Exception {

        final Properties props = new Properties();
        props.setProperty("mail.store.maildir.autocreatedir", "true");
        final Session session = Session.getInstance(props);
        currentStore = session.getStore(new URLName("maildir:///tmp/maildirtest"));
        final Folder f1 = currentStore.getFolder("subone");
        f1.create(Folder.HOLDS_MESSAGES);
        final Folder f2 = currentStore.getFolder("subtwo");
        f2.create(Folder.HOLDS_MESSAGES);
        final Folder f3 = currentStore.getFolder("subthree");
        f3.create(Folder.HOLDS_MESSAGES);
        final Folder f4 = f3.getFolder("stsubOne");
        f4.create(Folder.HOLDS_MESSAGES);
        final Folder f5 = currentStore.getFolder("Входящие сообщения");
        f5.create(Folder.HOLDS_MESSAGES);

        inbox = (MaildirFolder) currentStore.getFolder("inbox");
        helper = new MaildirTestHelper();

        helloWorldMessage = new MimeMessage(session);
        helloWorldMessage.setFrom();
        helloWorldMessage.setSubject("test");
        helloWorldMessage.setRecipients(Message.RecipientType.TO, "zhukov@ukrpost.net");
        helloWorldMessage.setText("hello world");
    }

    public static Test suite() {
        return new TestSuite(MaildirFolderTestCase.class);
    }

    public void testAppendMessages() throws Exception {
        final Folder f1 = currentStore.getFolder("subone");
        f1.open(Folder.READ_ONLY);
        final SimpleCountListener listener = new SimpleCountListener();

        f1.open(Folder.READ_WRITE);

        f1.addMessageCountListener(listener);

        final int origCount = f1.getMessageCount();
        final int origUnread = f1.getUnreadMessageCount();
        System.out.println("count: " + origCount);
        System.out.println("unread: " + origUnread);
        final MimeMessage mm = new MimeMessage((Session) null);
        mm.setText("Test content.");
        mm.setFrom(new InternetAddress("test@example.com"));
        mm.setRecipients(Message.RecipientType.TO, "test@example.com");
        final String subjectString = "test message.";
        mm.setSubject(subjectString);

        final MimeMessage duplicate = new MimeMessage(mm);

        final Message[] messages = new Message[]{
                mm, duplicate
        };

        f1.appendMessages(messages);

        assertEquals(origCount + 2, f1.getMessageCount());
        assertEquals(origUnread + 2, f1.getUnreadMessageCount());

        // sleep just in case there are threading problems.
        if (listener.getLastReceivedEvent() == null) {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
        }

        final MessageCountEvent mce = listener.getLastReceivedEvent();

        if (mce == null)
            fail("appendMessages: no MessageCountEvent for listener.");

        log.debug("mce.getMessages: " + mce.getMessages().length);
        assertEquals(MessageCountEvent.ADDED, mce.getType());
        assertEquals(2, mce.getMessages().length);
        assertEquals(subjectString, mce.getMessages()[0].getSubject());
        log.debug("--- done");

        f1.close(false);
    }

    public void testList() {
        try {
            assertEquals(5, currentStore.getDefaultFolder().list("%").length);
            assertEquals(6, currentStore.getDefaultFolder().list("*").length);
            assertEquals(2, currentStore.getDefaultFolder().list("subt%").length);
            assertEquals(3, currentStore.getDefaultFolder().list("subt*").length);
            assertEquals(1, currentStore.getDefaultFolder().list("subthree").length);
            assertEquals(1, currentStore.getDefaultFolder().list("INBOX").length);
            assertEquals(1, currentStore.getDefaultFolder().list("IN*").length);
            assertEquals(1, currentStore.getDefaultFolder().list("IN*X").length);
            assertEquals(1, currentStore.getDefaultFolder().list("%ne").length);
            assertEquals(2, currentStore.getDefaultFolder().list("*ne").length);
            assertEquals(1, currentStore.getDefaultFolder().list("subthree.s%").length);
            assertEquals(1, currentStore.getDefaultFolder().list("%ee.stsubOne").length);
            assertEquals(1, currentStore.getDefaultFolder().list("Входящие%").length);
            final Folder subThree = currentStore.getFolder("subthree");
            assertEquals(1, subThree.list("*").length);
        } catch (MessagingException me) {
            fail("caught exception in list:  " + me.toString());
            me.printStackTrace();
        }
    }

    public void testGetType() {
        try {
            assertEquals(Folder.HOLDS_FOLDERS, currentStore.getDefaultFolder().getType());
            assertEquals(Folder.HOLDS_MESSAGES, currentStore.getFolder("INBOX").getType());
            assertEquals(Folder.HOLDS_FOLDERS, (currentStore.getFolder("subone").getType() & Folder.HOLDS_FOLDERS));
            assertEquals(Folder.HOLDS_MESSAGES, (currentStore.getFolder("subone").getType() & Folder.HOLDS_MESSAGES));
        } catch (MessagingException me) {
            fail("caught exception in list:  " + me.toString());
            me.printStackTrace();
        }

    }

    public void testExternalDelivery() throws IOException, MessagingException {
        //zero messages before external delivery
        assertEquals(0, inbox.getNewMessageCount());

        final SimpleCountListener listener = new SimpleCountListener();
        final SimpleStateListener stateListener = new SimpleStateListener();
        inbox.addMessageCountListener(listener);
        inbox.addMessageChangedListener(stateListener);

        //inbox is intentionaly not opened yet
        //external delivery to "new"
        helper.doExternalDelivery(inbox);

        //listener should have received zero events,
        //because MaildirFolder doesnt know of the external delivery yet
        assertEquals(0, listener.getAddedEventsCount());
        assertEquals(0, stateListener.getEventsCount());

        //two new messages after external delivery
        assertEquals(2, inbox.getNewDir().listFiles().length);
        assertEquals(2, inbox.getNewMessageCount());
        assertEquals(2, inbox.getMessageCount());
        assertEquals(2, inbox.getUnreadMessageCount());

        // we don't get the event, since the folder isn't open.
        //MaildirTestHelper.waitForEvent();
        //listener should have received one event
        //assertEquals(1, listener.getAddedEventsCount());

        listener.resetCounters();
        listFolder(inbox);
        inbox.open(Folder.READ_ONLY);

        //get most recent message
        final Message[] allMsgs = inbox.getMessages();
        final Message m = allMsgs[allMsgs.length - 1];
        final String expectedSubj = "Re: [devel] Boxes for authors";
        assertEquals(expectedSubj, m.getSubject());
        assertTrue(m.isSet(Flags.Flag.RECENT));
        assertTrue(!m.isSet(Flags.Flag.SEEN));

        //unset RECENT flag
        m.setFlag(Flags.Flag.RECENT, false);
        System.out.println("inbox is open: " + inbox.isOpen());
        listFolder(inbox);
        assertEquals(1, inbox.getNewMessageCount());
        assertEquals(2, inbox.getMessageCount());
        assertEquals(2, inbox.getUnreadMessageCount());
        //assertEquals(1, inbox.getCurDir().listFiles().length);
        //assertEquals(1, inbox.getNewDir().listFiles().length);

        MaildirTestHelper.waitForEvent();
        assertEquals(0, listener.getAddedEventsCount());
        assertEquals(0, listener.getRemovedEventsCount());

        MaildirTestHelper.waitForEvent();
        assertEquals(1, stateListener.getEventsCount());

    }

    private void listFolder(MaildirFolder folder) {
        final String[] curs = folder.getCurDir().list();
        for (int i = 0; i < curs.length; i++)
            System.out.println(folder.getName() + "/cur/" + curs[i]);
        final String[] news = folder.getNewDir().list();
        for (int i = 0; i < news.length; i++)
            System.out.println(folder.getName() + "/new/" + news[i]);

    }

    /**
     * make sure that messages added while the folder is closed are recorded
     * properly.  they should be recent until the folder is closed.
     */
    public void testRecentAfterClose() throws IOException, MessagingException {
        helper.doExternalDelivery(inbox);
        assertEquals(2, inbox.getNewMessageCount());
        inbox.open(Folder.READ_WRITE);
        assertEquals(2, inbox.getNewMessageCount());
        inbox.close(true);
        assertEquals(0, inbox.getNewMessageCount());
    }

    public void testExpunge() throws IOException, MessagingException {
        helper.doExternalDelivery(inbox, 5);
        inbox.open(Folder.READ_WRITE);
        final Message[] msgs = inbox.getMessages();
        assertEquals(5, msgs.length);
        msgs[2].setFlag(Flags.Flag.DELETED, true);
        final Message[] removedMessages = inbox.expunge();
        //lets assume no external removal occured
        assertEquals(1, removedMessages.length);
        final boolean sameInstance = (msgs[2] == removedMessages[0]);
        assertTrue(sameInstance);
        assertEquals(3, removedMessages[0].getMessageNumber());

        listFolder(inbox);
        final Message[] newMessages = inbox.getMessages();
        assertEquals(4, newMessages.length);
        assertTrue(msgs[0] == newMessages[0]);
        assertTrue(msgs[3] == newMessages[2]);
        assertEquals(3, newMessages[2].getMessageNumber());

    }

    public void testGetUnreadMessageCount() throws IOException, MessagingException {
        helper.doExternalDelivery(inbox, 1);
        inbox.open(Folder.READ_WRITE);
        assertEquals(1, inbox.getUnreadMessageCount());
        final Message m = inbox.getMessage(1);
        m.setFlag(Flags.Flag.SEEN, true);
        assertEquals(0, inbox.getUnreadMessageCount());
        inbox.close(true);
    }

    public void testAppendMessages2() throws IOException, MessagingException {
        final MimeMessage mm = new MimeMessage((Session) null);
        mm.setFrom();
        mm.setSubject("test");
        mm.setRecipients(Message.RecipientType.TO, "zhukov@ukrpost.net");
        mm.setText("hello world");
        mm.setFlag(Flags.Flag.RECENT, true);

        inbox.open(Folder.READ_WRITE);
        assertEquals(0, inbox.getMessageCount());
        inbox.appendMessages(new Message[]{mm});
        final Message m = inbox.getMessage(1);
        assertTrue(m.isSet(Flags.Flag.RECENT));
        inbox.close(true);
    }

    public void testAppendMessages3() throws Exception {
        helper.doExternalDelivery(inbox, 1);
        final MaildirStore store = MaildirTestHelper.setupQuotaStore(100, 10);
        final MaildirFolder quotaInbox = (MaildirFolder) store.getFolder("inbox");

        quotaInbox.open(Folder.READ_WRITE);
        try {
            quotaInbox.appendMessages(new Message[]{helloWorldMessage});
            fail("should throw QuotaExceededException");
        } catch (MessagingException mex) {
            final QuotaExceededException qeex =
                    (QuotaExceededException) mex.getNextException();

            assertEquals("mailbox is full", qeex.getMessage());
        }
    }

    /**
     * appendMessages() method can be invoked on a closed Folder.
     */
    public void testAppendMessages4() throws IOException, MessagingException {
        helloWorldMessage.setFlag(Flags.Flag.RECENT, true);

        assertTrue(!inbox.exists());
        assertTrue(inbox.create(Folder.HOLDS_MESSAGES));
        assertTrue(!inbox.isOpen());
        inbox.appendMessages(new Message[]{helloWorldMessage});
    }

    /**
     * make sure that we retain the correct number of messages after
     * closing and reoping the folder.
     */
    public void testCloseAndReopen() throws IOException, MessagingException {
        final int origCount = inbox.getMessageCount();

        helper.doExternalDelivery(inbox, 1);

        assertEquals(origCount + 1, inbox.getMessageCount());

        inbox.open(Folder.READ_WRITE);
        assertEquals(origCount + 1, inbox.getMessageCount());
        inbox.close(true);
        assertEquals(origCount + 1, inbox.getMessageCount());

        inbox.open(Folder.READ_WRITE);
        assertEquals(origCount + 1, inbox.getMessageCount());
        inbox.close(true);
    }


    protected void tearDown() {
        try {
            inbox.close(true);
        } catch (Exception ex) {
        }
        MaildirTestHelper.rmdir(inbox.getCurDir().getParentFile());
        helper.closeStreams();
    }

    public void testCreate() throws MessagingException {
        MaildirTestHelper.rmdir(inbox.getCurDir().getParentFile());
        final Folder f = currentStore.getFolder("тест");
        f.create(Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
        assertEquals(1, ((MaildirFolder) f).getDir().getParentFile().list().length);

    }

    public void testGetName() throws MessagingException {
        Folder f = currentStore.getFolder("тест");
        f.create(Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
        assertEquals("тест", f.getName());
        f = currentStore.getDefaultFolder();
        final Folder[] fs = f.list();
        boolean testFolderFound = false;
        for (int i = 0; i < fs.length; i++) {
            f = fs[i];
            if ("тест".equals(f.getName()))
                testFolderFound = true;
        }
        assertTrue(testFolderFound);
    }

    public void testIllegalMessageNumber() throws MessagingException {
        MaildirTestHelper.rmdir(inbox.getCurDir().getParentFile());
        inbox.create(Folder.HOLDS_MESSAGES);
        inbox.open(Folder.READ_ONLY);
        try {
            inbox.getMessage(0);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException iobex) {
        }
    }

    public void testTemporaryQuotaOff() throws Exception {
        helper.doExternalDelivery(inbox, 1);

        final java.util.Properties props = new java.util.Properties();
        props.setProperty("mail.store.maildir.autocreatedir", "true");
        props.setProperty("mail.store.maildir.quota.count", Long.toString(10));
        props.setProperty("mail.store.maildir.quota.size", Long.toString(100));
        final Session session = Session.getDefaultInstance(props);
        final MaildirStore store = (MaildirStore) session.getStore(new URLName("maildir:///tmp/maildirtest"));

        final MaildirFolder quotaInbox = (MaildirFolder) store.getFolder("inbox");
        session.getProperties().put("mail.store.maildir.noquota", "true");
        //should not throw QuotaExceededException
        quotaInbox.appendMessages(new Message[]{helloWorldMessage});

        session.getProperties().put("mail.store.maildir.noquota", "false");
        //MUST throw QuotaExceededException
        try {
            quotaInbox.appendMessages(new Message[]{helloWorldMessage});
            fail("should throw QuotaExceededException");
        } catch (MessagingException mex) {
            final QuotaExceededException qeex =
                    (QuotaExceededException) mex.getNextException();

            assertEquals("mailbox is full", qeex.getMessage());
        }
    }

    private static final void sleep(long usec) {
        try {
            Thread.sleep(usec);
        } catch (Exception ex) {
        }
    }

    //TODO: think of sane test for parallel delivery with shared Store and/or Folder object between threads
    //public void testParallelDelivery() throws Exception {
    //}

    public void testMessageCounts() throws Exception {
        File maildir = new File("maildir");
        FileUtils.deleteDirectory(maildir);
        maildir.mkdirs();
        assertTrue(maildir.exists());
        File n = new File(maildir, "new");
        File c = new File(maildir, "cur");
        File t = new File(maildir, "tmp");
        n.mkdirs();
        c.mkdirs();
        t.mkdirs();

        FileUtils.writeStringToFile(new File(c, "1234.message1.eml"), "From: hello1\nSubject: subject1\n\nworld1\n", "UTF-8");
        FileUtils.writeStringToFile(new File(c, "5678.message2.eml"), "From: hello2\nSubject: subject2\n\nworld2\n", "UTF-8");

        MaildirStore store = new MaildirStore(Session.getInstance(new Properties()), new URLName("maildir:maildir"));
        Folder inbox = store.getFolder("INBOX");
        assertTrue(inbox.exists());
        inbox.open(Folder.READ_WRITE);
        assertEquals(2, inbox.getMessageCount());
        assertEquals(2, inbox.getUnreadMessageCount());
        inbox.getMessage(1).setFlag(Flags.Flag.SEEN, true);
        assertEquals(2, inbox.getMessageCount());
        assertEquals(1, inbox.getUnreadMessageCount());

        Thread.sleep(1000);
        
        //external delivery
        File m3 = new File(n, "3333.deliveredwhilerunning.eml");
        FileUtils.writeStringToFile(m3, "From: hello3\nSubject: subject3\n\nworld3\n", "UTF-8");
        assertEquals(3, inbox.getMessageCount());
        assertEquals(2, inbox.getUnreadMessageCount());
    }

    public void testFileNotFoundAfterDelivery() throws Exception {
        File maildir = new File("maildir");
        FileUtils.deleteDirectory(maildir);
        maildir.mkdirs();
        assertTrue(maildir.exists());
        File n = new File(maildir, "new");
        File c = new File(maildir, "cur");
        File t = new File(maildir, "tmp");
        n.mkdirs();
        c.mkdirs();
        t.mkdirs();

        FileUtils.writeStringToFile(new File(c, "1234.message1.eml"), "From: hello1\nSubject: subject1\n\nworld1\n", "UTF-8");
        Thread.sleep(1000);

        FileUtils.writeStringToFile(new File(c, "5678.message2.eml"), "From: hello2\nSubject: subject2\n\nworld2\n", "UTF-8");

        MaildirStore store = new MaildirStore(Session.getInstance(new Properties()), new URLName("maildir:maildir"));
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);
        MimeMessage newMessage = new MimeMessage(null, new ByteArrayInputStream("From: hello3\nSubject: subject3\n\nworld3\n".getBytes()));
        newMessage.setFlag(Flags.Flag.RECENT, true);

        Thread.sleep(1000);
        inbox.appendMessages(new Message[]{newMessage});
        assertEquals(3, inbox.getMessageCount());
        MimeMessage message = (MimeMessage) inbox.getMessage(1);
        message.getSubject();
    }
}
