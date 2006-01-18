package net.ukrpost.storage.maildir;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;
import org.apache.commons.io.FileUtils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.util.Properties;

public class MaildirStoreTestCase extends TestCase {
    private final static Logger log = Logger.getLogger(MaildirStoreTestCase.class);

    MaildirTestHelper helper = null;

    protected void setUp() throws Exception {
        if (!Logger.getRootLogger().getAllAppenders().hasMoreElements()) {
            BasicConfigurator.resetConfiguration();
            BasicConfigurator.configure();
        }
        helper = new MaildirTestHelper();
    }

    public static Test suite() {
        return new TestSuite(MaildirStoreTestCase.class);
    }

    /*public void testGetQuota() throws Exception {
        MaildirStore store = MaildirTestHelper.setupQuotaStore(1000, 10);

        Quota q[] = store.getQuota("");
        assertEquals(1, q.length);
        assertEquals(2, q[0].resources.length);
        MaildirTestHelper.QuotaHelper qh = new MaildirTestHelper.QuotaHelper(q);

        assertTrue(qh.hasMessageResource);
        assertTrue(qh.hasStorageResource);

        assertEquals(10, qh.messageLimit);
        assertEquals(0, qh.messageUsage);

        assertEquals(1000, qh.storageLimit);
        assertEquals(0, qh.storageUsage);

        store.close();
    }*/

    /*public void testGetQuota2() throws Exception {
        MaildirStore store = MaildirTestHelper.setupQuotaStore(0,0);
        helper.doExternalDelivery((MaildirFolder)store.getFolder("inbox"), 1);
        store = MaildirTestHelper.setupQuotaStore(1000, 10);
        Quota q[] = store.getQuota("");
        assertEquals(1, q.length);
        assertEquals(2, q[0].resources.length);
        MaildirTestHelper.QuotaHelper qh = new MaildirTestHelper.QuotaHelper(q);

        assertTrue(qh.hasMessageResource);
        assertTrue(qh.hasStorageResource);

        assertEquals(10, qh.messageLimit);

        assertEquals(1000, qh.storageLimit);
        assertEquals(14517, qh.storageUsage);

        store.close();
    }*/

//    public void testFolderCache() throws Exception {
//        java.util.Properties props = new java.util.Properties();
//        props.setProperty("mail.store.maildir.autocreatedir", "true");
//        props.setProperty("mail.store.maildir.cachefolders", "true");
//        Session session = Session.getInstance(props);
//        MaildirStore store = (MaildirStore)session.getStore(
//                new URLName("maildir:///tmp/maildirtest"));
//        Folder inbox = store.getFolder("inbox");
//        Folder inbox2 = store.getFolder("inbox");
//        assertTrue(inbox == inbox2);
//    }

    public void testFolderNoCache() throws Exception {
        final java.util.Properties props = new java.util.Properties();
        props.setProperty("mail.store.maildir.autocreatedir", "true");
        props.setProperty("mail.store.maildir.cachefolders", "false");
        final Session session = Session.getInstance(props);
        final MaildirStore store = (MaildirStore) session.getStore(new URLName("maildir:///tmp/maildirtest"));
        final Folder inbox = store.getFolder("inbox");
        final Folder inbox2 = store.getFolder("inbox");
        assertTrue(inbox != inbox2);
    }

    public void testFolderPoisonedCache() throws Exception {
        final java.util.Properties props = new java.util.Properties();
        props.setProperty("mail.store.maildir.autocreatedir", "true");
        props.setProperty("mail.store.maildir.cachefolders", "true");
        final Session session = Session.getInstance(props);
        final MaildirStore store = (MaildirStore) session.getStore(new URLName("maildir:///tmp/maildirtest"));
        final Folder inbox = store.getFolder("inbox");
        helper.doExternalDelivery((MaildirFolder) inbox, 3);
        final Folder inbox2 = store.getFolder("inbox");
        assertTrue(inbox != inbox2);
    }

    protected void tearDown() {
        MaildirTestHelper.rmdir(new File("tmp/maildirtest"));
    }

    private final static long KB = 1024;
    private final static long MB = 1024 * KB;

    public void testQuota() throws Exception {
        File tmp = new File(System.getProperty("java.io.tmpdir"));
        File maildir = new File(tmp, "maildirtest");

        MaildirTestHelper.rmdir(maildir);
        assertTrue(maildir.mkdirs());

        long sizeQuota = 10 * MB;
        Properties properties = new Properties();
        properties.setProperty("mail.store.maildir.quota.size", Long.toString(sizeQuota));
        Session session = Session.getInstance(properties);
        MaildirStore maildirStore = new MaildirStore(session, new URLName("maildir:" + maildir.getCanonicalPath()));
        long sizeUsage = maildirStore.getQuota("")[0].getResourceUsage("STORAGE");
        assertEquals(0, sizeUsage);

        Folder inbox = maildirStore.getFolder("inbox");
        if (!inbox.exists())
            inbox.create(Folder.HOLDS_MESSAGES);
        MimeMessage randomMessage = getRandomMessage();
        inbox.appendMessages(new Message[]{randomMessage});

        sizeUsage = maildirStore.getQuota("")[0].getResourceUsage("STORAGE");
        assertTrue(sizeUsage > 0);
    }

    private final static MimeMessage getRandomMessage() throws MessagingException {
        MimeMessage m = new MimeMessage((Session) null);
        m.setSender(new InternetAddress("hello@world"));
        m.setRecipient(Message.RecipientType.TO, new InternetAddress("out@there"));
        m.setText("hello world");
        m.setSubject("hello world");
        m.saveChanges();
        return m;
    }

    public void testQuotaChangeAfterExpunge() throws Exception {
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

        FileUtils.writeStringToFile(new File(c, "1111.message1.eml"), "From: hello1\nSubject: subject1\n\nworld1\n", "UTF-8");
        Thread.sleep(1000);
        FileUtils.writeStringToFile(new File(c, "2222.message2.eml"), "From: hello2\nSubject: subject2\n\nworld2\n", "UTF-8");

        Properties props = new Properties();
        props.setProperty("mail.store.maildir.quota.size", "1000");
        MaildirStore store = new MaildirStore(Session.getInstance(props), new URLName("maildir:maildir"));
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_WRITE);

        long before = store.getQuota("")[0].getResourceUsage("STORAGE");
        inbox.getMessage(1).setFlag(Flags.Flag.DELETED, true);
        inbox.expunge();
        long after = store.getQuota("")[0].getResourceUsage("STORAGE");
        assertTrue(after < before);
    }
}
