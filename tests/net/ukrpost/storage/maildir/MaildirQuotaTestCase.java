package net.ukrpost.storage.maildir;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.log4j.PropertyConfigurator;

import java.util.Properties;

public class MaildirQuotaTestCase extends TestCase {

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

    MaildirTestHelper helper = null;

    protected void setUp() throws Exception {
    }

    public static Test suite() {
        return new TestSuite(MaildirQuotaTestCase.class);
    }

    public void testSetResourceUsage() {
        final MaildirQuota q = new MaildirQuota("");
        q.setResourceUsage("STORAGE", 123L);
        assertEquals(123L, q.getResourceUsage("STORAGE"));
    }

    public void testGetResourceLimit() {
        final MaildirQuota q = new MaildirQuota("");
        assertEquals(0L, q.getResourceLimit("UNKNOWN"));
    }

    public void testGetResourceLimit2() {
        final MaildirQuota q = new MaildirQuota("");
        q.setResourceLimit("STORAGE", 1234L);
        assertEquals(1234L, q.getResourceLimit("STORAGE"));
    }
}
