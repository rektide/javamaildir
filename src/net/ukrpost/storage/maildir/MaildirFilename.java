/*
 * JavaMaildir - a JavaMail service provider for Maildir mailboxes.
 * Copyright (C) 2002 Alexander Zhukov
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package net.ukrpost.storage.maildir;

import org.apache.log4j.Logger;

import javax.mail.Flags;
import java.io.File;
import java.net.InetAddress;

//TODO: maybe we should extend MaildirFilename from File

public class MaildirFilename implements Comparable {
    private static final Logger log = Logger.getLogger(MaildirFilename.class);
    /**
     * deliveryCounter should be incremented each time a new delivery comes
     * from the same thread.
     */
    protected int deliveryCounter = 0;
    private String originalfilename = null;
    private int timestamp = 0;

    private String deliveryid = null;
    private boolean modifiedDeliveryId = false;
    private static String theHostname = null;

    static {
        try {
            theHostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            theHostname = "localhost";
        }
    }

    private String hostname = null;
    protected int deliveryProcessId = Math.abs((Thread.currentThread().hashCode() % 65534 + 1));

    /**
     * <tt>uniq</tt> and <tt>info</tt> are defined in Maildir specification
     * as two parts of any maildir messages filename.
     * Here they are used to speedup toString() and similar operations.
     */
    private String uniq = null;
    private String info = null;

    private long size = -1;
    protected final Flags flags = new Flags();
    private boolean modified = true;

    private static char colon = ':';

    static {
        //windows does not support colons in file names
        if (System.getProperty("os.name").toLowerCase().indexOf("windows") != -1) {
            colon = ';';
        }
    }

    public MaildirFilename() {
        hostname = theHostname;
        timestamp = (int) (System.currentTimeMillis() / 1000);
    }

    private File theFile = null;

    File getFile() {
        return theFile;
    }

    void setFile(File file) {
        theFile = file;
    }

    public MaildirFilename(File f) {
        this(f.getName());
        theFile = f;
        if (!f.getName().startsWith(getUniq())) {
            setTimestamp((int) (f.lastModified() / 1000));
        }

        log.debug("constructed from file: " + f.getName());
        log.debug("file last modified: " + f.lastModified());
        log.debug("uniq: " + getUniq());
        log.debug("host: " + getHostname());
        log.debug("tstamp: " + getTimestamp());
    }

    public MaildirFilename(String str) {
        this();
        if (str != null)
            originalfilename = str;
        else
            return;

        log.debug("MaildirFilename(" + str + ')');

        final int dot_one = str.indexOf(".");
        if (dot_one == -1)
            return;

        final int dot_two = str.indexOf(".", dot_one + 1);
        if (dot_two == -1)
            return;

        final int dot_three = str.indexOf(colon, dot_two + 1);

        final String stamp = str.substring(0, dot_one);
        try {
            timestamp = Integer.parseInt(stamp);
        } catch (NumberFormatException nfex) {
            return;
        }

        setDeliveryId(str.substring(dot_one + 1, dot_two));
        modifiedDeliveryId = false;

        final String host = str.substring(dot_two + 1, (dot_three == -1 ? str.length() : dot_three));
        info = (dot_three == -1 ? null : str.substring(dot_three + 1));

        //log.debug("stamp: "+stamp);
        //log.debug("id: "+deliveryid);
//        log.debug("host: " + host);
        //log.debug("info: "+info);

        final int sizeidx = host.indexOf(",S=");
        if (sizeidx != -1) {
            final String sizestr = host.substring(sizeidx + 3);
            hostname = host.substring(0, sizeidx);
            try {
                size = Long.parseLong(sizestr);
            } catch (NumberFormatException nfex) {
            }

            //log.debug("sizestr: "+sizestr);
        } else {
            hostname = host;
        }

        //log.debug("size: "+size);
//        log.debug("hostname: "+hostname);
        setHostname(hostname);
        if (info != null) {
            final int flagsidx = info.indexOf("2,");
            if (flagsidx != -1)
                for (int i = 2; i < info.length(); i++) {
                    final char flag = info.charAt(i);
                    log.debug("flag: " + flag);
                    switch (flag) {
                        case 'S':
                            flags.add(Flags.Flag.SEEN);
                            break;
                        case 'R':
                            flags.add(Flags.Flag.ANSWERED);
                            break;
                        case 'T':
                            flags.add(Flags.Flag.DELETED);
                            break;
                        case 'D':
                            flags.add(Flags.Flag.DRAFT);
                            break;
                        case 'F':
                            flags.add(Flags.Flag.FLAGGED);
                            break;
                    }
                }
        }

        //TODO: redesign parsing
        uniq = getUniq();
        modified = false;
    }

    public boolean getFlag(Flags.Flag f) {
        return flags.contains(f);
    }

    public Flags getFlags() {
        return new Flags(flags);
    }

    public void setFlags(Flags f) {
        modified = true;
        flags.add(f);
    }

    public void setFlag(Flags.Flag f) {
        modified = true;
        flags.add(f);
    }

    public void removeFlag(Flags.Flag f) {
        modified = true;
        flags.remove(f);
    }

    public void removeFlags(Flags f) {
        modified = true;
        flags.remove(f);
    }

    public long getSize() {
        return size;
    }

    public void setSize(long sz) {
        if (sz == size)
            return;
        modified = true;
        size = sz;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String host) {
        modified = true;
        hostname = host;
    }

    public String getDeliveryId() {
        if (deliveryid == null || modifiedDeliveryId)
            deliveryid =
                    Integer.toString(deliveryProcessId) +
                    (deliveryCounter == 0 ? "" : '_' + Integer.toString(deliveryCounter));

        return deliveryid;
    }

    public void setDeliveryId(String id) {
        if (id.equals(deliveryid))
            return;

        modified = true;
        modifiedDeliveryId = true;

        try {
            deliveryProcessId = Integer.parseInt(id);
            return;
        } catch (NumberFormatException nfex) {
        }

        //parse deliveryid to get deliverycounter
        final int underscore = id.lastIndexOf("_");
        if (underscore != -1) {
            final String procId = id.substring(0, underscore);
            final String delivCnt = id.substring(underscore + 1);
            try {
                deliveryProcessId = Integer.parseInt(procId);
                deliveryCounter = Integer.parseInt(delivCnt);
            } catch (NumberFormatException nfex) {
            }
        }

        deliveryid = id;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int ts) {
        if (ts == timestamp)
            return;
        modified = true;
        timestamp = ts;
    }

    public int getDeliveryCounter() {
        return deliveryCounter;
    }

    public void setDeliveryCounter(int dc) {
        modified = true;
        modifiedDeliveryId = true;
        deliveryCounter = dc;
    }

    //TODO: maybe we should have separate modifiedUniq and modifiedInfo
    public String getUniq() {
        if (modified || modifiedDeliveryId) {
            final StringBuffer sb = new StringBuffer();
            sb.append(getTimestamp()).append('.');
            sb.append(getDeliveryId()).append('.');
            sb.append(getHostname());
            uniq = sb.toString();
        }
        return uniq;
    }

    //TODO: maybe we should have separate modifiedUniq and modifiedInfo
    public String getInfo() {
        if (modified) {
            final StringBuffer sb = new StringBuffer();
            final Flags.Flag[] flgs = flags.getSystemFlags();
            if (flgs.length > 0)
                sb.append(colon).append("2,");

            for (int i = flgs.length - 1; i >= 0; i--) {
                if (flgs[i] == Flags.Flag.SEEN)
                    sb.append('S');
                else if (flgs[i] == Flags.Flag.ANSWERED)
                    sb.append('R');
                else if (flgs[i] == Flags.Flag.DELETED)
                    sb.append('T');
                else if (flgs[i] == Flags.Flag.DRAFT)
                    sb.append('D');
                else if (flgs[i] == Flags.Flag.FLAGGED)
                    sb.append('F');
            }
            info = sb.toString();
        }
        return info;
    }

    /**
     * Compares two MaildirFilenames taking deliveryid and deliverycounter to account.
     */
    public int compareTo(Object o) {
        if (!(o instanceof MaildirFilename) || o == null || o == this)
            return 0;
        final MaildirFilename m = (MaildirFilename) o;

        final int timestampRes = getTimestamp() - m.getTimestamp();
        if (timestampRes != 0)
            return timestampRes;

        final int procRes = deliveryProcessId - m.deliveryProcessId;
        if (procRes != 0)
            return procRes;

        final int counterRes = deliveryCounter - m.deliveryCounter;
        if (counterRes != 0)
            return counterRes;

        final int delivRes = getDeliveryId().compareTo(m.getDeliveryId());
        if (delivRes != 0)
            return delivRes;

        final int hostnameRes = getHostname().compareTo(m.getHostname());
        if (hostnameRes != 0)
            return hostnameRes;

        return 0;
    }

    public boolean equals(Object o) {
        if (!(o instanceof MaildirFilename) || o == null)
            return false;
        if (o == this)
            return true;

        final MaildirFilename omfn = (MaildirFilename) o;
        /*
        return ( getTimestamp() == omfn.getTimestamp()
                && getDeliveryId().equals(omfn.getDeliveryId())
                && getHostname().equals(omfn.getHostname()) );
                */
        return toString().equals(omfn.toString());
    }

    public String toString() {
        if (!modified)
            return originalfilename;

        final StringBuffer sb = new StringBuffer();

        sb.append(getUniq());

        if (size > 0)
            sb.append(",S=").append(size);

        sb.append(getInfo());
        return sb.toString();
    }
}
