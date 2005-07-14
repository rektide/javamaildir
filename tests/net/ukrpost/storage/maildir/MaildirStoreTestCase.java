package net.ukrpost.storage.maildir;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.PropertyConfigurator;

import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.URLName;
import java.io.File;
import java.util.Properties;

public class MaildirStoreTestCase extends TestCase {

    static {
        //BasicConfigurator.configure();
        final Properties props = new Properties();
        props.put("log4j.rootLogger", ", D");
        props.put("log4j.appender.D", "org.apache.log4j.ConsoleAppender");
        props.put("log4j.appender.D.layout", "org.apache.log4j.PatternLayout");
        props.put("log4j.appender.D.layout.ConversionPattern",
                "[%t] %-5p %c{1} %x - %m%n");
        props.put("log4j.logger.net.ukrpost.storage", "INFO");
        PropertyConfigurator.configure(props);
    }

    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    MaildirTestHelper helper = null;

    protected void setUp() throws Exception {
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
    public void testContinuum() throws Exception {
        throw new RuntimeException("hello world");
    }
    protected void tearDown() {
        MaildirTestHelper.rmdir(new File("tmp/maildirtest"));
    }
}
