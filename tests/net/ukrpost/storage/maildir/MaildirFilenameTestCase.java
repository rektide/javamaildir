package net.ukrpost.storage.maildir;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.PropertyConfigurator;

import javax.mail.Flags;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.util.Properties;

public class MaildirFilenameTestCase extends TestCase {
    MaildirFilename mfnEmpty = null;
    MaildirFilename mfnInited = null;
    MaildirFilename mfnDamaged = null;
    MaildirFilename mfnUnparsableFilename = null;
    File unparsableFilename = null;
    String hostname = "localhost";
    static boolean isWindows = false;
    private File tmp = new File(System.getProperty("java.io.tmpdir"));
    static {
        isWindows = System.getProperty("os.name").toLowerCase().indexOf("windows") != -1;
        //BasicConfigurator.configure();
        final Properties props = new Properties();
        props.put("log4j.rootLogger", ", D");
        props.put("log4j.appender.D", "org.apache.log4j.ConsoleAppender");
        props.put("log4j.appender.D.layout", "org.apache.log4j.PatternLayout");
        props.put("log4j.appender.D.layout.ConversionPattern",
                "[%t] %-5p %c{1} %x - %m%n");
//        props.put("log4j.logger.net.ukrpost.storage", "DEBUG");
        PropertyConfigurator.configure(props);
    }

    protected void setUp() throws Exception {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
        }
        mfnEmpty = new MaildirFilename();
        if (!isWindows) {
            mfnInited = new MaildirFilename("1049788638.25645_84.may.priocom.com,S=2204:2,ST");
        } else {
            mfnInited = new MaildirFilename("1049788638.25645_84.may.priocom.com,S=2204;2,ST");
        }
        mfnDamaged = new MaildirFilename("illegalname");

        unparsableFilename = new File(tmp, "MaildirFilenameTestCase.eml");
        if (unparsableFilename.exists())
            unparsableFilename.delete();
        unparsableFilename.deleteOnExit();

        PrintStream ps = null;
        try {
            ps = new PrintStream(new FileOutputStream(unparsableFilename));
            ps.print("From: test@localhost\r\n" +
                    "Subject: test\r\n" +
                    "\r\n" +
                    "data\r\n");
        } finally {
            if (ps != null)
                try {
                    ps.close();
                } catch (Exception ex) {
                }
        }

        mfnUnparsableFilename = new MaildirFilename(unparsableFilename);
    }

    public static void main(String args[]) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(MaildirFilenameTestCase.class);
    }

    public void testGetFlag() {
        assertTrue(mfnInited.getFlag(Flags.Flag.SEEN));
    }

    public void testGetSize() {
        assertEquals(-1, mfnEmpty.getSize());
        assertEquals(2204, mfnInited.getSize());
        assertEquals(-1, mfnDamaged.getSize());
    }

    public void testGetHostname() {
        String expectedhost = "localhost";
        try {
            expectedhost = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
        }

        assertEquals(expectedhost, mfnEmpty.getHostname());
        assertEquals("may.priocom.com", mfnInited.getHostname());
        assertEquals(expectedhost, mfnDamaged.getHostname());
        assertEquals(hostname, mfnUnparsableFilename.getHostname());
    }

    public void testGetTimestamp() {
        assertEquals(1049788638, mfnInited.getTimestamp());
        assertEquals(unparsableFilename.lastModified() / 1000, mfnUnparsableFilename.getTimestamp());
    }

    public void testGetDeliveryId() {
        //String expectedid = Integer.toString(Thread.currentThread().hashCode() % 65534 + 1);
        //assertEquals(expectedid, mfnEmpty.getDeliveryId());
        assertEquals("25645_84", mfnInited.getDeliveryId());
        //assertEquals(expectedid, mfnDamaged.getDeliveryId());
    }

    public void testUnusualDeliveryId() {
        final MaildirFilename mfn = new MaildirFilename("1064304004.H307526P25838.may.priocom.com,S=1405");
        assertEquals("H307526P25838", mfn.getDeliveryId());
        assertEquals("1064304004.H307526P25838.may.priocom.com,S=1405", mfn.toString());
        mfn.setSize(2000);
        assertEquals("1064304004.H307526P25838.may.priocom.com,S=2000", mfn.toString());
    }

    public void testToString() {
        if (!isWindows)
            assertEquals("1049788638.25645_84.may.priocom.com,S=2204:2,ST", mfnInited.toString());
        else
            assertEquals("1049788638.25645_84.may.priocom.com,S=2204;2,ST", mfnInited.toString());
    }

    public void testCompareTo() {
        final MaildirFilename ten = new MaildirFilename("1065563892.609_10.localhost");
        final MaildirFilename two = new MaildirFilename("1065563892.609_2.localhost");
        assertTrue(ten.compareTo(two) > 0);
    }

    //public void testEquals() {
    //}
}
