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
import javax.mail.MessagingException;
import javax.mail.event.MessageChangedEvent;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.util.Date;
import java.util.Enumeration;

public class MaildirMessage extends MimeMessage implements Comparable {
    private static final Logger log = Logger.getLogger(MaildirMessage.class);
    private File msgfile;
    private MaildirFilename mfn;
    private boolean isparsed = false;

    protected MaildirMessage(MaildirFolder folder, int msgnum) {
        super(folder, msgnum);
    }

    protected MaildirMessage(MaildirFolder folder, File f, int msgnum) {
        this(folder, f, new MaildirFilename(f), msgnum);
    }

    protected MaildirMessage(MaildirFolder folder, File f, MaildirFilename mfn, int msgnum) {
        super(folder, msgnum);

        isparsed = false;

        msgfile = f;
        mfn.setSize(f.length());
        this.mfn = mfn;
    }

    public int getSize() {
        return (int) mfn.getSize();
    }

    protected void parse(InputStream is)
            throws MessagingException {

        if (!isparsed) {
//            updateFilename();
            super.parse(is);
            isparsed = true;
        }
    }

    public String getHeader(String name, String delim)
            throws MessagingException {
        parse();
        return super.getHeader(name, delim);
    }

    public String[] getHeader(String name)
            throws MessagingException {
        parse();
        return super.getHeader(name);
    }

    protected void parse()
            throws MessagingException {
        if (isparsed)
            return;

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(getFile());
            parse(fis);
        } catch (FileNotFoundException fnfex) {
            throw new MessagingException("file not found");
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException ioex) {
                log.error(ioex.toString());
            }
        }
    }

    public MaildirFilename getMaildirFilename() {
        return mfn;
    }

    protected void setFile(File f) {
        msgfile = f;
    }

    protected File getFile() {
        return msgfile;
    }

    public String toString() {
        return "" + getMessageNumber() + ":'" + mfn.toString() + '\'';
    }

    //duplicate method of Message.setMessageNumber() ?
    void setMsgNum(int mn) {
        msgnum = mn;
    }

    public void writeTo(OutputStream os)
            throws IOException, MessagingException {
        writeTo(os, null);
    }

    public void writeTo(OutputStream os, String as[])
            throws IOException, MessagingException {
        if (isparsed) {
            super.writeTo(os, as);
            return;
        }

        if (as != null) {
            parse();
            super.writeTo(os, as);
            return;
        }

        FileInputStream fis = null;
        try {
            fis = new FileInputStream(getFile());
            final byte[] buff = new byte[8192];
            int i = 0;
            while ((i = fis.read(buff)) > 0)
                os.write(buff, 0, i);
        } catch (Exception ex) {
            throw new MessagingException("unable to retrieve message stream", ex);
        } finally {
            if (fis != null)
                try {
                    fis.close();
                } catch (Exception ex) {
                }
            if (os != null)
                try {
                    os.flush();
                } catch (Exception ex) {
                }
        }
    }

    public void setFlags(Flags f, boolean set)
            throws MessagingException {
        final Flags.Flag[] flags = f.getSystemFlags();
        boolean changed = false;

        for (int i = 0; i < flags.length; i++)
            changed = changed | (_setFlag(flags[i], set));

        log.debug("setFlags() changed: " + changed);

        if (!changed)
            return;

//        try {
//            updateFilename();
//        } catch (MessagingException mex) {
//            log.error(mex.toString());
//        }

        ((MaildirFolder) getFolder()).localNotifyMessageChangedListeners(MessageChangedEvent.FLAGS_CHANGED, FlagChangedEvent.getEventType(f, set), this);
    }

    public void setFlag(Flags.Flag f, boolean set)
            throws MessagingException {

        if (!_setFlag(f, set))
            return;

//        try {
//            updateFilename();
//        } catch (MessagingException mex) {
//            log.error(mex.toString());
//        }

        log.debug("notifying MessageChangedListeners");
        ((MaildirFolder) getFolder()).localNotifyMessageChangedListeners(MessageChangedEvent.FLAGS_CHANGED, FlagChangedEvent.getEventType(f, set), this);
    }

    private boolean _setFlag(Flags.Flag f, boolean set) {
        boolean changed = false;

        if (set) {
            changed = (!getFlags().contains(f));
            mfn.setFlag(f);
        } else {
            changed = (getFlags().contains(f));
            mfn.removeFlag(f);
        }

        return changed;
    }

    public Flags getFlags() {
        return mfn.getFlags();
    }

    public boolean isSet(Flags.Flag f) {
        return mfn.getFlag(f);
    }

    public int compareTo(Object o) {
        if (!(o instanceof MaildirMessage) || o == null)
            return 0;
        final MaildirMessage m = (MaildirMessage) o;

        return getMaildirFilename().compareTo(m.getMaildirFilename());
    }

    public boolean isFileStateUpdated() {
        return (getFile().getName().equals(mfn.toString()));
    }

    public boolean equals(Object o) {
        if (!(o instanceof MaildirMessage) || o == null)
            return false;
        final MaildirMessage m = (MaildirMessage) o;
        if (!isFileStateUpdated())
            return getFile().getName().equals(m.getFile().getName());

        return getMaildirFilename().equals(m.getMaildirFilename());

    }

    public Date getReceivedDate() {
        return new Date(getFile().lastModified());
    }

    public void setReceivedDate(Date receivedDate) {
        getFile().setLastModified(receivedDate.getTime());
    }

    //todo: write testcase
    public InputStream getInputStream() throws MessagingException, IOException {
        parse();
        return super.getInputStream();
    }

    /**
     * Writes text of the message only, but no header.
     */
    public InputStream getRawInputStream() throws MessagingException {
        try {
            //todo: fixme: suboptimal
            FileInputStream in = new FileInputStream(getFile());
            final BufferedReader reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(in), "US-ASCII"));
            int bytesread = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                bytesread += line.length() + 1;
                if ("".equals(line))
                    break;
            }
            in.close();
            in = new FileInputStream(getFile());
            in.skip(bytesread);
            return in;
        } catch (IOException e) {
            throw new MessagingException("cannot retrieve message", e);
        }
    }

    public Enumeration getAllHeaderLines() throws MessagingException {
        parse();
        return super.getAllHeaderLines();
    }

    public Enumeration getMatchingHeaders(String[] strings) throws MessagingException {
        parse();
        return super.getMatchingHeaders(strings);
    }

    public Enumeration getMatchingHeaderLines(String as[]) throws MessagingException {
        parse();
        return super.getMatchingHeaderLines(as);
    }
}
