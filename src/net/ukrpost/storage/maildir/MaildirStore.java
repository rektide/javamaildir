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

import javax.mail.*;
import java.io.File;

public class MaildirStore extends Store {
    private static final Logger log = Logger.getLogger(MaildirStore.class);
    protected final boolean useFolderCache = false;
    protected final boolean alwaysCacheFolders = false;

    private MaildirQuota quota = null;
    private URLName myurlname = null;

    public MaildirStore(Session session, URLName urlname) {
        super(session, urlname);
        myurlname = urlname;
        log.debug("mail.store.maildir.autocreatedir: " + session.getProperty("mail.store.maildir.autocreatedir"));

        if (!"true".equals(session.getProperty("mail.store.maildir.autocreatedir")))
            return;

        log.debug("request to create store: " + myurlname.getFile());
        final File f = new File(myurlname.getFile());
//if ( !"Maildir".equals(f.getName()) )
//    f = new File(myurlname.getFile() + "/Maildir/");

        log.debug("creating store: " + f.getAbsolutePath());
        final boolean result = f.mkdirs();
        log.debug("created?: " + result);

    }

    public Folder getFolder(String s)
            throws MessagingException {
        return new MaildirFolder(s, this);
    }

    public Folder getFolder(URLName urlname)
            throws MessagingException {
        return getFolder(urlname.getFile());
    }

    public Folder getDefaultFolder()
            throws MessagingException {
        return new MaildirFolder(".", this);
    }

    protected boolean protocolConnect(String host, int port, String user, String password) {
        return true;
    }

    public String getSessionProperty(String s) {
        return session.getProperty(s);
    }

    public MaildirQuota[] getQuota(java.lang.String root)
            throws MessagingException {
        log.debug("quota root: " + root);

        if (quota != null) {
            //recalculate quota usage?
            return new MaildirQuota[]{quota};
        }

        final String qcnt = session.getProperty("mail.store.maildir.quota.count");
        final String qsz = session.getProperty("mail.store.maildir.quota.size");

        log.debug("mail.store.maildir.quota.count: " + qcnt);
        log.debug("mail.store.maildir.quota.size: " + qsz);
        int defaultQuotaCount = 0;
        int defaultQuotaSize = 0;
        try {
            defaultQuotaCount = (new Integer((qcnt == null) ? "0" : qcnt)).intValue();
            defaultQuotaSize = (new Long((qsz == null) ? "0" : qsz)).intValue();
        } catch (Exception ex) {
            throw new MessagingException("Incorrect default quota value specified");
        }

        final MaildirQuota q = new MaildirQuota(root);
        q.setResourceUsage("MESSAGE", 0L);
        q.setResourceUsage("STORAGE", 0L);
        q.setResourceLimit("MESSAGE", (long) defaultQuotaCount);
        q.setResourceLimit("STORAGE", (long) defaultQuotaSize);

        if (defaultQuotaSize != 0)
            q.setResourceUsage("STORAGE", (long) du(new File(url.getFile())));

        return new MaildirQuota[]{q};
    }

    //setQuota sets the _limit_ only, usage is (should it be?) ignored if specified
    public void setQuota(MaildirQuota q)
            throws MessagingException {
        quota = q;
    }

    //like unix "du" command
    private int du(File root) {
        log.debug("du: " + root.toString());
        int size = 0;

        if (root.isFile())
            return (int) root.length();

        final File dirlist[] = root.listFiles();

        if (dirlist == null)
            return 0;

        for (int i = 0; i < dirlist.length; i++) {
            if (dirlist[i].isFile())
                size += dirlist[i].length();

            if (dirlist[i].isDirectory())
                size += du(dirlist[i]);
        }

        return size;
    }

    //default getURLName in Service doesnot return file which is vital for Maildir
    public URLName getURLName() {
        return myurlname;
    }


}
