package net.ukrpost.storage.maildir;

import org.apache.log4j.Logger;

import javax.mail.Session;
import javax.mail.URLName;
import java.io.*;

public class MaildirTestHelper {
    private static Logger log = Logger.getLogger(MaildirTestHelper.class);

    InputStream testMessage1 = null;
    InputStream testMessage2 = null;
    public final static boolean OS_WINDOWS = (System.getProperty("os.name").toLowerCase().indexOf("windows") != -1);

    public MaildirTestHelper() throws Exception {
        testMessage1 = this.getClass().getClassLoader().getResourceAsStream("net/ukrpost/storage/maildir/resources/test11.eml");
        testMessage2 = this.getClass().getClassLoader().getResourceAsStream("net/ukrpost/storage/maildir/resources/test15.eml");

        if (testMessage1 == null || testMessage2 == null)
            throw new Exception("required resource not found");
    }

    public void doExternalDelivery(MaildirFolder f)
            throws IOException {
        doExternalDelivery(f, 2);
    }

    public void doExternalDelivery(MaildirFolder folder, int numberOfMessagesRequired)
            throws IOException {
        createFolder(folder);
        final long now = System.currentTimeMillis();
        final long oldest = now - (10000 * numberOfMessagesRequired) - 1000;
        for (int i = 0; i < numberOfMessagesRequired; i++) {
            final File f = new File(folder.getNewDir(), "test" + i + ".eml");
            FileOutputStream fos = null;
            final InputStream ins = (i % 2 == 0 ? testMessage2 : testMessage1);
            try {
                fos = new FileOutputStream(f);
                for (int r = ins.read(); r > 0; r = ins.read()) {
                    fos.write(r);
                }
            } finally {
                System.out.println("close streams");
                streamClose(fos);
                f.setLastModified(oldest + (i * 10000));
            }
        }
        //dumbass windows does not set lastmodified value on some types of media (e.g. usb flash drives)
        if (OS_WINDOWS)
            folder.getNewDir().setLastModified(System.currentTimeMillis());
    }

    public void closeStreams() {
        streamClose(testMessage1);
        streamClose(testMessage2);
    }

    public static void streamClose(InputStream ins) {
        try {
            ins.close();
        } catch (Exception ex) {
        }
    }

    public static void streamClose(OutputStream outs) {
        try {
            outs.close();
        } catch (Exception ex) {
        }
    }

    public static void waitForEvent() {
        try {
            Thread.sleep(250);
        } catch (Exception ex) {
        }
    }

    public static boolean rmdir(File d) {
        //log.debug("TRACE: rmdir("+d+")");

        if (d.isFile())
            return d.delete();

        final File[] list = d.listFiles();
        for (int i = 0; list != null && i < list.length; i++)
            if (list[i].isDirectory())
                rmdir(list[i]);
            else
                list[i].delete();

        return d.delete();
    }

    public static void createFolder(MaildirFolder f) {
        f.getNewDir().mkdirs();
        f.getCurDir().mkdirs();
        f.getTmpDir().mkdirs();
    }

    /*public static class QuotaHelper {
        Quota q[] = null;
        boolean hasMessageResource = false;
        boolean hasStorageResource = false;
        long storageLimit = 0;
        long storageUsage = 0;
        long messageLimit = 0;
        long messageUsage = 0;

        public QuotaHelper(Quota q[]) {
            this.q = q;
            for (int i=0; i<q[0].resources.length; i++) {
                String name = q[0].resources[i].name;
                long limit = q[0].resources[i].limit;
                long usage = q[0].resources[i].usage;

                if ( "STORAGE".equals(name) ) {
                    hasStorageResource = true;
                    storageLimit = limit;
                    storageUsage = usage;
                } else if ( "MESSAGE".equals(name) ) {
                    hasMessageResource = true;
                    messageLimit = limit;
                    messageUsage = usage;
                }
                
            }
        }
    }*/

    public static MaildirStore setupQuotaStore(long sizeQuota, long messageQuota) throws Exception {
        final java.util.Properties props = new java.util.Properties();
        props.setProperty("mail.store.maildir.autocreatedir", "true");
        props.setProperty("mail.store.maildir.quota.count", Long.toString(messageQuota));
        props.setProperty("mail.store.maildir.quota.size", Long.toString(sizeQuota));
        final Session session = Session.getDefaultInstance(props);
        final MaildirStore store = (MaildirStore) session.getStore(new URLName("maildir:///tmp/maildirtest"));

        return store;
    }

    public final static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }
}
