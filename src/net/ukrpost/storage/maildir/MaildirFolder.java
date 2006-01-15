/**
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

import net.ukrpost.utils.UidsBidiMap;
import net.ukrpost.utils.NewlineOutputStream;
import net.ukrpost.utils.QuotaAwareOutputStream;
import net.ukrpost.utils.QuotaExceededException;
import org.apache.log4j.Logger;

import javax.mail.*;
import javax.mail.event.FolderEvent;
import javax.mail.event.MessageChangedEvent;
import java.io.*;
import java.util.*;

/**
 * <b>Approach to external deliveries.</b>
 * <p/>
 * External deliveries are only detected in methods that are used to poll maildir:
 * <ul>
 * <li>getMessageCount</li>
 * <li>getNewMessageCount</li>
 * <li>getDeletedMessageCount</li>
 * <li>getUnreadMessageCount</li>
 * </ul>
 * Any other methods expect internal state to be equal to external state.
 * For folder to be aware of externaly delivered messages you should either close and reopen
 * MaildirFolder (slow on large Maildirs) or perform continious polling using methods specified above.
 */
public class MaildirFolder extends Folder implements UIDFolder {
    private static final Logger log = Logger.getLogger(MaildirFolder.class);

    private static final Flags supportedFlags = new Flags();

    static {
        supportedFlags.add(Flags.Flag.ANSWERED);
        supportedFlags.add(Flags.Flag.DELETED);
        supportedFlags.add(Flags.Flag.DRAFT);
        supportedFlags.add(Flags.Flag.FLAGGED);
        supportedFlags.add(Flags.Flag.RECENT);
        supportedFlags.add(Flags.Flag.SEEN);
    }

    private static final Message[] EMPTY_MESSAGES = new Message[0];
    private final File rootdir;
    private final File dir;
    private final File curd;
    private final File newd;
    private final File tmpd;
    final long curLastModified = -1;
    final long newLastModified = -1;

    private String str;
    private String root;
    private boolean isdefault = false;
    private boolean isopen = false;

    private int unreadMessages = 0;
    private int recentMessages = 0;
    private int deletedMessages = 0;

    private ArrayList messages = null;
    private TreeMap uniqToMessageMap = new TreeMap();

    private UidsBidiMap uids = null;

    public MaildirFolder(String s, MaildirStore store) {
        super(store);

        root = getStore().getURLName().getFile();

        if (!root.endsWith(File.separator)) {
            root += File.separator;
        }

        str = s.replace(File.separatorChar, '.');
        str = BASE64MailboxEncoder.encode(str);

        if (str.equals(".")) {
            isdefault = true;
        }

        if (str.toUpperCase().equals("INBOX")) {
            str = ".";
            isdefault = false;
        }

        if (str.charAt(0) != '.') {
            str = '.' + str;
        }

        //FIXME
        rootdir = new File(root);
        dir = new File(root + str);
        curd = new File(dir, "cur");
        newd = new File(dir, "new");
        tmpd = new File(dir, "tmp");
    }

    private void updatemsgs() throws MessagingException {
        updatemsgs(true, false);
    }

    /**
     * Checks whether underlying folders "cur", "new" and "tmp" were modified.
     * Checks lastModified values for the folders and decides whether messages were addded or removed.
     *
     * @return modification state
     */
    private boolean isFoldersModified(LastModified lm) {
        final long curLm = getCurDir().lastModified();
        final long newLm = getNewDir().lastModified();
        if (curLm == lm.curLm
                && newLm == lm.newLm) {
            //folders were not modified so internal state stays the same
            //maybe we should make this check optional
            return false;
        }
        lm.curLm = curLm;
        lm.newLm = newLm;
        if (lm == updateLm) {
            newCountLm = new LastModified(updateLm);
            unreadCountLm = new LastModified(updateLm);
            deletedCountLm = new LastModified(updateLm);
            messageCountLm = new LastModified(updateLm);
        }
        return true;
    }

    /**
     * Updates message maps and counters: recentMessages, deletedMessages, unreadMessages based on flags set in MaildirFilename.
     */
    private MaildirMessage addMessage(MaildirFilename mfn) {
        MaildirMessage mm = null;
        if (!uniqToMessageMap.containsKey(mfn.getUniq())) {
            mm = new MaildirMessage(this, mfn.getFile(),
                    mfn, -1);
            uniqToMessageMap.put(mfn.getUniq(), mm);
        } else {
            mm = (MaildirMessage) uniqToMessageMap.get(mfn.getUniq());
        }

        if (!mm.isSet(Flags.Flag.SEEN))
            unreadMessages++;

        if (mm.isSet(Flags.Flag.DELETED))
            deletedMessages++;

        if (!uids.containsKey(mfn.getUniq()))
            uids.addUid(mfn.getUniq());
        mm.setFile(mfn.getFile());
        return mm;
    }

    LastModified updateLm = new LastModified();

    private void updatemsgs(boolean doNotify) throws MessagingException {
        updatemsgs(doNotify, false);
    }

    private void updatemsgs(boolean doNotify, boolean forceUpdate)
            throws MessagingException {
        if (!forceUpdate && (!isFoldersModified(updateLm)))
            return;

        if (!isOpen() || isdefault) {
            return;
        }

        synchronized (this) {
            if (!exists()) {
                return;
            }
            unreadMessages = deletedMessages = recentMessages = 0;
            ArrayList oldMessages = null;
            deletedMessages = recentMessages = totalMessages = unreadMessages = 0;
            if (doNotify) {
                oldMessages = new ArrayList(uniqToMessageMap.values());

            }

            final MaildirFilename[] newMfns = MaildirUtils.listMfns(newd);
            final MaildirFilename[] curMfns = MaildirUtils.listMfns(curd);

            if (messages == null) {
                messages = new ArrayList(newMfns.length + curMfns.length);
            }

            for (int i = 0; i < newMfns.length; i++) {
                final MaildirFilename mfn = newMfns[i];

                //according to Maildir spec on folder open
                //all files from "new" should be moved to "cur"
                final File target = new File(curd, mfn.toString());
                if (mfn.getFile().renameTo(target))
                    mfn.setFile(target);

                mfn.setFlag(Flags.Flag.RECENT);
                recentMessages++;
                addMessage(mfn);
            }

            for (int i = 0; i < curMfns.length; i++) {
                addMessage(curMfns[i]);
            }

            final Collection newMessages = uniqToMessageMap.values();

            //log.info("messages after update: "+newMessages);
            if (doNotify) {
                log.debug("old messages: " + oldMessages);

                //log.debug("new messages: "+sortedMessages.toString());
                log.debug("new messages: " + newMessages);

                final Collection removedMessages = collectionsSubtract(oldMessages,
                        newMessages);
                log.debug("removedMessages: " + removedMessages);

                final Collection addedMessages = collectionsSubtract(newMessages,
                        oldMessages);
                log.debug("addedMessages: " + addedMessages);

                final Message[] added = (Message[]) addedMessages.toArray(EMPTY_MESSAGES);
                final Message[] removed = (Message[]) removedMessages.toArray(EMPTY_MESSAGES);

                if (removedMessages.size() > 0) {
                    notifyMessageRemovedListeners(true, removed);
                }

                if (addedMessages.size() > 0) {
                    notifyMessageAddedListeners(added);
                }
            }

            messages = new ArrayList(newMessages);

            final Iterator it = messages.iterator();

            for (int i = 1; it.hasNext(); i++) {
                final MaildirMessage m = (MaildirMessage) it.next();
                m.setMsgNum(i);
            }
            isFoldersModified(updateLm);
        }
    }

    LastModified unreadCountLm = new LastModified();

    public int getUnreadMessageCount() throws MessagingException {
        if (isOpen()) {
            updatemsgs();
            return unreadMessages;
        }

        if (!isFoldersModified(unreadCountLm))
            return unreadMessages;

        final int unreadNew = MaildirUtils.getFlaggedCount(newd, Flags.Flag.SEEN, false);
        final int unreadCur = MaildirUtils.getFlaggedCount(curd, Flags.Flag.SEEN, false);
        unreadMessages = unreadNew + unreadCur;
        return unreadMessages;
    }

    LastModified newCountLm = new LastModified();

    public int getNewMessageCount() throws MessagingException {
        if (isOpen()) {
            updatemsgs();
            return recentMessages;
        }

        if (!isFoldersModified(newCountLm))
            return recentMessages;

        final String[] newf = getNewDir().list();
        recentMessages = newf == null ? 0 : newf.length;
        return recentMessages;
    }

    LastModified deletedCountLm = new LastModified();

    public int getDeletedMessageCount() throws MessagingException {
        if (isOpen()) {
            updatemsgs();
            return deletedMessages;
        }

        if (!isFoldersModified(deletedCountLm))
            return deletedMessages;

        final int deletedNew = MaildirUtils.getFlaggedCount(newd, Flags.Flag.DELETED, true);
        final int deletedCur = MaildirUtils.getFlaggedCount(curd, Flags.Flag.DELETED, true);
        deletedMessages = deletedNew + deletedCur;
        return deletedMessages;

    }

    int totalMessages = 0;
    LastModified messageCountLm = new LastModified();

    public int getMessageCount() throws MessagingException {
        if (isOpen()) {
            updatemsgs();
            return messages.size();
        }

        if (!isFoldersModified(messageCountLm))
            return totalMessages;

        final String[] curf = getCurDir().list();
        final String[] newf = getNewDir().list();
        totalMessages = 0;

        if (curf != null) {
            totalMessages += curf.length;
        }

        if (newf != null) {
            totalMessages += newf.length;
        }

        return totalMessages;

    }

    static class LastModified implements Cloneable {
        long curLm = -1;
        long newLm = -1;

        public LastModified() {
        }

        public LastModified(LastModified lm) {
            curLm = lm.curLm;
            newLm = lm.newLm;
        }

        public String toString() {
            return "cur: " + curLm + " new: " + newLm;
        }
    }

    public boolean hasNewMessages() throws MessagingException {
        return (getNewMessageCount() > 0);
    }

    private boolean doAutoCreateDir() {
        return Boolean.valueOf(getMaildirStore().getSessionProperty("mail.store.maildir.autocreatedir")).booleanValue();
    }

    private boolean checkMessageSizeBeforeAppend() {
        return Boolean.valueOf(getMaildirStore().getSessionProperty("mail.store.maildir.checkmessagesizebeforeappend")).booleanValue();
    }

    private boolean noQuotaEnforcement() {
        //log.debug("noquota: "+getMaildirStore().getSessionProperty("mail.store.maildir.noquota"));
        return Boolean.valueOf(getMaildirStore().getSessionProperty("mail.store.maildir.noquota")).booleanValue();
    }

    //handles more than one delivery per second
    //todo: 1. deliver to file with name = mfn.getUniq() then rename to name = mfn.toString()
    private static final File newUniqFilename(MaildirFilename mfn, File dir, boolean useUniqOnly) throws MessagingException {
        File target = new File(dir, useUniqOnly ? mfn.getUniq() : mfn.toString());
        //log.debug("newUniqFilename: "+target);
        int attempt = 0;
        for (attempt = 0; attempt < 100 && target.exists(); attempt++) {
            final int dc = mfn.getDeliveryCounter();
            mfn.setDeliveryCounter(dc + 1);
            target = new File(dir, useUniqOnly ? mfn.getUniq() : mfn.toString());
            //log.debug("newUniqFilename: "+target);
            if (target.exists())
                sleep(1500);
        }
        log.debug("newUniqFilename: " + target);
        if (attempt >= 100) {
            throw new MessagingException("cannot deliver message after 100 attempts");
        }
        return target;
    }

    private final OutputStream getTmpFileOutputStream(File tmpFilename, MaildirQuota quota)
            throws IOException {
        final OutputStream os = new NewlineOutputStream(new BufferedOutputStream(new FileOutputStream(tmpFilename), 4096));

        if (quota == null
                || noQuotaEnforcement()
                || quota.getResourceLimit("STORAGE") == 0)
            return os;

        final int sizeLimit = (int) quota.getResourceLimit("STORAGE");
        final int mailboxSize = (int) quota.getResourceUsage("STORAGE");

        return new QuotaAwareOutputStream(os, sizeLimit - mailboxSize);
    }

    private final void checkBeforeAppend(Message m, MaildirQuota quota) throws MessagingException, QuotaExceededException {
        if (quota == null || !checkMessageSizeBeforeAppend())
            return;

        final int messageSize = m.getSize();
        final int sizeLimit = (int) quota.getResourceLimit("STORAGE");
        final int mailboxSize = (int) quota.getResourceUsage("STORAGE");

        if ((messageSize != -1) &&
                ((mailboxSize + messageSize) > sizeLimit)) {
            throw new QuotaExceededException("message (" +
                    messageSize + "bytes) does not fit into mailbox");
        }
    }

    public void appendMessages(Message[] msgs) throws MessagingException {
        if (doAutoCreateDir() && !isOpen() && !exists()) {
            create(Folder.HOLDS_MESSAGES | Folder.HOLDS_FOLDERS);
        }

        final MaildirQuota quota = getMaildirStore().getQuota("")[0];
        final int mailboxSize = (int) quota.getResourceUsage("STORAGE");
        final int sizeLimit = (int) quota.getResourceLimit("STORAGE");
        final ArrayList addedMessages = new ArrayList(msgs.length);

        if (!noQuotaEnforcement()
                && sizeLimit > 0
                && mailboxSize > sizeLimit)
            throw new MessagingException("quota exceeded", new QuotaExceededException("mailbox is full"));
        log.debug("mailboxSize: " + mailboxSize + " sizeLimit: " + sizeLimit);

        try {
            int timestamp = 0;
            for (int i = 0; i < msgs.length; i++) {
                checkBeforeAppend(msgs[i], quota);
                final MaildirFilename mfn = new MaildirFilename();
                if (mfn.getTimestamp() == timestamp) {
                    mfn.setDeliveryCounter((int) System.currentTimeMillis() % 1000);
                }
                File tmptarget = null;
                OutputStream output = null;

                try {
                    tmptarget = newUniqFilename(mfn, getTmpDir(), true);
                    output = getTmpFileOutputStream(tmptarget, quota);

                    msgs[i].writeTo(output);
                } catch (QuotaExceededException qeex) {
                    tmptarget.delete();
//                    throw new MessagingException("quota exceeded", qeex);
                    throw qeex;
                } catch (IOException ioex) {
                    //this handles errors like "No space left on device"
                    log.error("unrecoverable io error: " + ioex.toString());
                    tmptarget.delete();
                    throw new MessagingException("unrecoverable io error",
                            ioex);
                } finally {
                    streamClose(output);
                }

                mfn.setSize(tmptarget.length());

                final boolean deliverToNew = (!isOpen() || msgs[i].isSet(Flags.Flag.RECENT));
                final File target = (deliverToNew)
                        ? newUniqFilename(mfn, getNewDir(), false)
                        : newUniqFilename(mfn, getCurDir(), false);

                log.debug("rename '" + tmptarget + "' -> '" + target + '\'');

                if (doAutoCreateDir() && !target.getParentFile().exists()) {
                    target.getParentFile().mkdirs();
                }

                final boolean movedFromTmpToNew = tmptarget.renameTo(target);

                if (!movedFromTmpToNew) {
                    log.error("cannot rename " + tmptarget + " to " + target);
                    tmptarget.delete();
                    target.delete();
                    throw new MessagingException("cant rename " + tmptarget + " to " + target);
                }

                mfn.setFlags(msgs[i].getFlags());

                //FIXME: possible bug:
                //Folder inbox = Store.getFolder()
                //inbox.appendMessages(2 messages)
                //inbox.getNewMessageCount()
                // returns 2
                //inbox.appendMessage(3 messages)
                //inbox.getNewMessageCount()
                // returns 3 not 5

                quota.setResourceUsage("MESSAGE", quota.getResourceUsage("MESSAGES") + 1);
                quota.setResourceUsage("STORAGE", mailboxSize + target.length());
                getMaildirStore().setQuota(quota);
                timestamp = mfn.getTimestamp();
                //todo: write testcase for the following
                if (msgs[i].getReceivedDate() != null)
                    target.setLastModified(msgs[i].getReceivedDate().getTime());
                mfn.setFile(target);
                if (isOpen()) {
                    //notifications work only for opened folders
                    final MaildirMessage mdm = addMessage(mfn);
                    mdm.setMsgNum(messages.size() + 1);
                    messages.add(mdm);
                    addedMessages.add(mdm);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new MessagingException("cant append message", ex);
        } finally {
            if (addedMessages.size() > 0)
                notifyMessageAddedListeners((Message[]) addedMessages.toArray(EMPTY_MESSAGES));
        }
    }

    /**
     * Unlike Folder objects, repeated calls to getMessage with the same message
     * number will return the same Message object, as long as no messages in this
     * folder have been expunged.
     */
    public Message getMessage(int msgnum) throws MessagingException {
        log.debug("msgnum: " + msgnum);

        return getMessages(new int[]{msgnum})[0];
    }

    public Message[] getMessages(int[] msgs) throws MessagingException {
        if (!isOpen()) {
            throw new IllegalStateException("folder closed");
        }

        if (!exists()) {
            throw new FolderNotFoundException(this);
        }

        if (isdefault) {
            throw new MessagingException("no messages under root folder allowed");
        }

        final ArrayList outmsgs = new ArrayList(msgs.length);

        for (int i = 0; i < msgs.length; i++) {
            if (messages.size() < msgs[i] || (msgs[i] <= 0)) {
                throw new IndexOutOfBoundsException("message " + msgs[i] +
                        " not available");
            }

            final MaildirMessage mdm = (MaildirMessage) messages.get(msgs[i] -
                    1);
            outmsgs.add(mdm);
        }

        return (Message[]) outmsgs.toArray(EMPTY_MESSAGES);
    }

    public Message[] getMessages() throws MessagingException {
        return messages == null ? EMPTY_MESSAGES : (Message[]) messages.toArray(EMPTY_MESSAGES);
    }

    public boolean isOpen() {
        return isopen;
    }

    public synchronized void close(boolean expunge) throws MessagingException {
        if (expunge) {
            expunge();
        }

        //update message filenames
        if (getMode() != Folder.READ_ONLY) {
            final int msgsize = messages.size();
            for (int i = 0; i < msgsize; i++) {
                final MaildirMessage mdm = (MaildirMessage) messages.get(i);
                final MaildirFilename mfn = mdm.getMaildirFilename();
                final File file = mfn.getFile();
                if (!file.getName().equals(mfn.toString())
                        || (mdm.isSet(Flags.Flag.RECENT) && file.getParentFile().getName().equals("new")))
                    file.renameTo(new File(getCurDir(), mfn.toString()));
            }
        }
        isopen = false;

        messages = null;
        recentMessages = deletedMessages = unreadMessages = 0;
        updateLm = new LastModified();
        newCountLm = new LastModified();
        unreadCountLm = new LastModified();
        deletedCountLm = new LastModified();
        messageCountLm = new LastModified();
        uniqToMessageMap = new TreeMap();
        saveUids();
        uids = null;
    }

    public synchronized void open(int mode) throws MessagingException {
        log.debug("open");

        if (isopen) {
            return;
        }

        if (doAutoCreateDir()) {
            create(Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES);
        }

        if (!exists()) {
            throw new FolderNotFoundException(this,
                    "folder '" + getName() + "' not found");
        }
        this.mode = mode;
        if (isdefault) {
            return;
        }

        //read in uids from .uidvalidity file
        loadUids();
        isopen = true;
        updatemsgs(false);
        final String[] keys = (String[]) uids.keySet().toArray(new String[0]);
        if (keys != null) {
            for (int i = 0; i < keys.length; i++) {
                final String uniq = keys[i];
                if (!uniqToMessageMap.containsKey(uniq)) {
                    uids.remove(uniq);
                    log.debug("removed stale uniq from uidvalidity : " + uniq);
                }
            }
        }
        saveUids();
    }

    private void loadUids() {
        if (!getUIDVFile().exists()) {
            uids = new UidsBidiMap();
            return;
        }

        try {
            uids = new UidsBidiMap(getUIDVFile());
        } catch (IOException e) {
            log.error(e);
        }
    }

    private void saveUids() {
        if (mode != Folder.READ_ONLY && getDir().canWrite()) {
            OutputStream uidout = null;
            try {
                uidout = new FileOutputStream(getUIDVFile());
                uids.save(uidout);
            } catch (IOException ioex) {
                log.error("cannot save uids to .uidvalidity: " + ioex);
            } finally {
                streamClose(uidout);
            }
        }
    }

    protected MaildirStore getMaildirStore() {
        return (MaildirStore) getStore();
    }

    public boolean renameTo(Folder f) throws MessagingException {
        log.debug("TRACE: '" + getFullName() + "' renameTo('" + f.getFullName() + "')");

        if (!(f instanceof MaildirFolder) || (f.getStore() != super.store)) {
            throw new MessagingException("cant rename across stores");
        }

        final boolean result = dir.renameTo(((MaildirFolder) f).getDir());

        if (result) {
            notifyFolderRenamedListeners(f);
        }

        return result;
    }

    public boolean delete(boolean recurse) throws MessagingException {
        if (isOpen())
            throw new IllegalStateException("cannot delete open folder");
        if (isdefault || str.equals(".")) {
            throw new MessagingException("cant delete root and INBOX folder");
        }

        if (!exists())
            throw new FolderNotFoundException(this);

        boolean result = true;
        final String[] list = rootdir.list();
        if (!recurse) {
            boolean hasSubfolders = false;
            for (int i = 0; i < list.length; i++)
                if (list[i].startsWith(str) && !list[i].equals(str)) {
                    hasSubfolders = true;
                    break;
                }
            result = hasSubfolders ? false : rmdir(getDir());
        } else {
            for (int i = 0; i < list.length; i++)
                if (list[i].startsWith(str)) {
                    final String path = root + list[i] + File.separatorChar;
                    result = result & rmdir(new File(path));
                }
        }
        if (result)
            notifyFolderListeners(FolderEvent.DELETED);

        return result;
    }


    public Folder getFolder(String name) throws MessagingException {
        String folderfullname = ".";

        name = name.replace(File.separatorChar, '.');

        if (name.charAt(0) == '.') {
            folderfullname = name;
        } else if (name.equals("INBOX")) {
            folderfullname = "INBOX";
        } else {
            if (str.endsWith(".")) {
                folderfullname = str + name;
            } else {
                folderfullname = str + '.' + name;
            }
        }

        return new MaildirFolder(folderfullname, (MaildirStore) super.store);
    }

    public boolean create(int type) throws MessagingException {
        log.debug("create (" + getFullName() + ')');

        if (exists()) {
            return false;
        }

        log.debug("request to create folder: " + dir);
        log.debug("creating folder: " + dir.getAbsolutePath());
        dir.mkdirs();
        curd.mkdir();
        newd.mkdir();
        tmpd.mkdir();

        final boolean result = exists();

        if (result) {
            notifyFolderListeners(FolderEvent.CREATED);
        }

        return result;
    }

    public int getType() {
        // treat the default folder and the INBOX specially.
        if (isdefault)
            return Folder.HOLDS_FOLDERS;

        if ("INBOX".equals(getFullName()))
            return Folder.HOLDS_MESSAGES;

        // otherwise all maildir folders can hold both folders and messages.
        return (Folder.HOLDS_FOLDERS | Folder.HOLDS_MESSAGES); //
    }

    public char getSeparator() {
        return '.';
    }

    public Folder[] list(String pattern) throws MessagingException {
        log.debug("pattern: " + pattern);

        // first check to see if we support this search.
        if (pattern == null) {
            pattern = "%";
        }

        final int firstStar = pattern.indexOf('*');
        final int firstPercent = pattern.indexOf('%');

        // check to make sure this is a supported pattern
        if (((firstStar > -1) && (pattern.indexOf('*', firstStar + 1) > -1)) ||
                ((firstPercent > -1) &&
                (pattern.indexOf('%', firstPercent + 1) > -1)) ||
                ((firstStar > -1) && (firstPercent > -1))) {
            throw new MessagingException("list pattern not supported");
        }

        final ArrayList folders = new ArrayList(3);

        if (!exists()) {
            return new Folder[0];
        }

        //no subfolders under INBOX
        if (str.equals(".") && !isdefault) {
            return new Folder[0];
        }

        final File[] matchingFiles;

        matchingFiles = rootdir.listFiles(new MaildirFileFilter(pattern));

        final String rootPath = rootdir.getAbsolutePath();

        for (int i = 0; i < matchingFiles.length; i++) {
            String fileName = matchingFiles[i].getAbsolutePath();

            if (fileName.startsWith(rootPath)) {
                fileName = fileName.substring(rootPath.length());
            }

            if (fileName.startsWith(File.separator)) {
                fileName = fileName.substring(File.separator.length());
            }

            fileName.replace(File.separatorChar, getSeparator());

            fileName = BASE64MailboxDecoder.decode(fileName);
            folders.add(new MaildirFolder(fileName,
                    (MaildirStore) store));
        }

        // inbox is a special case.
        if (isdefault) {
            boolean includeInbox = true;
            final int wildcardLocation = Math.max(firstStar, firstPercent);
            final String inbox = "INBOX";

            if (wildcardLocation == -1) {
                includeInbox = pattern.equals(inbox);
            } else {
                if ((wildcardLocation > 0) &&
                        (!inbox.startsWith(pattern.substring(0, wildcardLocation)))) {
                    includeInbox = false;
                } else {
                    if ((wildcardLocation < (pattern.length() - 1)) &&
                            (!inbox.endsWith(pattern.substring(wildcardLocation +
                            1, pattern.length() - 1)))) {
                        includeInbox = false;
                    }
                }
            }

            if (includeInbox) {
                folders.add(new MaildirFolder("INBOX",
                        (MaildirStore) store));
            }
        }

        log.debug("folders.size: " + folders.size());

        return (Folder[]) (folders.toArray(new Folder[]{}));
    }

    public boolean exists() throws MessagingException {
        boolean direxists = false;

        if (isdefault) {
            //direxists = dir.exists() && dir.isDirectory();
            log.debug("dir: " + dir);
            direxists = dir.isDirectory();
        } else {
            /*direxists = dir.exists() && dir.isDirectory() && curd.exists() &&
                curd.isDirectory() && newd.exists() && newd.isDirectory() &&
                tmpd.exists() && tmpd.isDirectory();*/
            direxists = curd.isDirectory() && newd.isDirectory() &&
                    tmpd.isDirectory();
        }

        log.debug("exists ?: " + direxists);

        return direxists;
    }

    public Folder getParent() throws MessagingException {
        if (dir.equals(rootdir)) {
            throw new MessagingException("already at rootdir cant getParent");
        }

        if (!hasParent()) {
            return new MaildirFolder(".", (MaildirStore) store);
        }

        final int lastdot = str.lastIndexOf(".");
        String parentstr = "";

        if (lastdot > 0) {
            parentstr = str.substring(0, lastdot);

            return new MaildirFolder(parentstr, (MaildirStore) store);
        }

        return null;
    }

    public String getFullName() {
        String out = "";

        if (isdefault) {
            return out;
        }

        if (str.equals(".")) {
            out = "INBOX";
        } else {
            //if ( str.lastIndexOf(".") > 0 || hasParent() )
            if (hasParent()) {
                out = str;
            } else {
                out = str.substring(1);
            }
        }

        out = BASE64MailboxDecoder.decode(out);

        return out;
    }

    private boolean hasParent() {
        final String tmpparent = root + str.substring(0, str.lastIndexOf("."));
        final boolean result = (!tmpparent.equals(root)) &&
                new File(tmpparent).isDirectory();
        log.debug("checking for parent of " + str);
        log.debug("possible parent: " + tmpparent);
        log.debug("hasparent?: " + result);

        return result;
    }

    public String getName() {
        String out = "";

        if (isdefault) {
            out = "";
        }

        if (str.equals(".")) {
            out = "INBOX";
        } else {
            if (hasParent()) {
                out = str.substring(str.lastIndexOf(".") + 1);
            } else {
                out = str.substring(1);
            }
        }

        out = BASE64MailboxDecoder.decode(out);

        return out;
    }

    public Message[] expunge() throws MessagingException {
        if (!isOpen())
            throw new FolderClosedException(this);
        if (messages == null)
            throw new RuntimeException("internal error: messages == null");

        final List removedMessagesList = new ArrayList();
        boolean forceUpdate = false;
        final int msgsSize = messages.size();
        for (int i = msgsSize - 1; i >= 0; i--) {
            final MaildirMessage mdm = (MaildirMessage) messages.get(i);

            if (mdm.isSet(Flags.Flag.DELETED)) {
                final String uniq = mdm.getMaildirFilename().getUniq();
                uids.remove(uniq);
                uniqToMessageMap.remove(uniq);
                log.debug("uniq2message: " + uniqToMessageMap.toString());
                messages.remove(mdm);

                final boolean result = mdm.getFile().delete();
                log.debug("removing " + mdm.getFile() + ": " + result);
                removedMessagesList.add(mdm);
                forceUpdate = true;
            }
        }
        updatemsgs(true, forceUpdate);

        final Message[] removedMessages = (Message[]) removedMessagesList.toArray(EMPTY_MESSAGES);
        notifyMessageRemovedListeners(true, removedMessages);

        return removedMessages;
    }

    /**
     * Exposes notifyMessageChangedListeners to package members.
     */
    void localNotifyMessageChangedListeners(int eventType, int eventDetails,
                                            MaildirMessage changedMessage) throws MessagingException {
        //FIXME: JavaMails Provider Guide says that messageids must stay the same during
        //the whole session and only be updated in expunge() and added in the appendMessages().
        //But when a flag in the maildirmessage changes you broadcast the event,
        //which is ok, _and_ update the messages, this is where ids may get
        //corrupted (shifted up or down) if external delivery (eg from non-java MTA)
        //to this maildir happened.
        //updatemsgs();
        if (eventType == MessageChangedEvent.FLAGS_CHANGED) {
            if ((eventDetails & FlagChangedEvent.ISSET) != 0) {
                if ((eventDetails & FlagChangedEvent.DELETED) != 0)
                    deletedMessages++;
                else if ((eventDetails & FlagChangedEvent.RECENT) != 0)
                    recentMessages++;
                else if ((eventDetails & FlagChangedEvent.SEEN) != 0)
                    unreadMessages--;
            } else {
                if ((eventDetails & FlagChangedEvent.DELETED) != 0)
                    deletedMessages--;
                else if ((eventDetails & FlagChangedEvent.RECENT) != 0)
                    recentMessages--;
                else if ((eventDetails & FlagChangedEvent.SEEN) != 0)
                    unreadMessages++;
            }
        }
        notifyMessageChangedListeners(eventType, changedMessage);
    }

    public Flags getPermanentFlags() {
        return supportedFlags;
    }

    private File uidVFile = null;

    private File getUIDVFile() {
        if (uidVFile == null)
            uidVFile = new File(getDir(), ".uidvalidity");
        return uidVFile;
    }

    public long getUIDValidity() throws MessagingException {
        return uids.getUidValidity();
    }

    /**
     * The next unique identifier value is the predicted value that will be
     * assigned to a new message in the mailbox.  Unless the unique
     * identifier validity also changes (see below), the next unique
     * identifier value MUST have the following two characteristics.  First,
     * the next unique identifier value MUST NOT change unless new messages
     * are added to the mailbox; and second, the next unique identifier
     * value MUST change whenever new messages are added to the mailbox,
     * even if those new messages are subsequently expunged.
     * <p/>
     * Note: The next unique identifier value is intended to
     * provide a means for a client to determine whether any
     * messages have been delivered to the mailbox since the
     * previous time it checked this value.  It is not intended to
     * provide any guarantee that any message will have this
     * unique identifier.  A client can only assume, at the time
     * that it obtains the next unique identifier value, that
     * messages arriving after that time will have a UID greater
     * than or equal to that value.
     *
     * @return next unique identifier value.
     */
    public long getUIDNext() {
        return uids.getLastUid() + 1;
    }

    public Message getMessageByUID(long uid) throws MessagingException {
        final String uniq = (String) uids.getKey(Long.toString(uid));
        if (uniq == null)
            return null;
        else
            return (Message) uniqToMessageMap.get(uniq);
    }

    public Message[] getMessagesByUID(long start, long end)
            throws MessagingException {
        if (end == LASTUID)
            end = uids.getLastUid();
        log.debug("getMessagesByUID " + start + ".." + end);
        if (end < start) {
//            throw new IndexOutOfBoundsException("end cannot be lesser than start");
            return EMPTY_MESSAGES;
        }
        if (end == start) {
            final Message m = getMessageByUID(start);
            return m != null ? new Message[]{m} : EMPTY_MESSAGES;
        }

        final ArrayList messages = new ArrayList();
        for (long i = start; i <= end; i++) {
            final Message m = getMessageByUID(i);
            if (m != null)
                messages.add(m);
        }
        return (Message[]) messages.toArray(EMPTY_MESSAGES);
    }

    public Message[] getMessagesByUID(long[] uidArray) throws MessagingException {
        if (uidArray.length == 1) {
            final Message m = getMessageByUID(uidArray[0]);
            return m != null ? new Message[]{m} : EMPTY_MESSAGES;
        }

        final long[] sortedUidArray = new long[uidArray.length];
        System.arraycopy(uidArray, 0, sortedUidArray, 0, uidArray.length);
        Arrays.sort(sortedUidArray);

        final ArrayList messageList = new ArrayList();
        long prevUid = -1;
        for (int i = 0; i < sortedUidArray.length; i++) {
            final long uid = sortedUidArray[i];
            if (uid == prevUid)
                continue;
            final Message m = getMessageByUID(uid);
            if (m != null)
                messageList.add(m);
            prevUid = uid;
        }
        return (Message[]) messageList.toArray(EMPTY_MESSAGES);
    }

    public long getUID(Message message) throws MessagingException {
        if (!(message instanceof MaildirMessage))
            throw new NoSuchElementException("message does not belong to this folder");
        final MaildirMessage mdm = (MaildirMessage) message;
        final String uidStr = (String) uids.get(mdm.getMaildirFilename().getUniq());
        if (uidStr == null)
            throw new NoSuchElementException("message does not belong to this folder");

        int uid = 0;
        try {
            uid = Integer.parseInt(uidStr);
        } catch (NumberFormatException nfex) {
            throw new NoSuchElementException("message does not belong to this folder: " + nfex.getMessage());
        }
        return uid;
    }

    protected File getDir() {
        return dir;
    }

    protected File getCurDir() {
        return curd;
    }

    protected File getTmpDir() {
        return tmpd;
    }

    protected File getNewDir() {
        return newd;
    }

    private final static Collection collectionsSubtract(final Collection a,
                                                        final Collection b) {
        final ArrayList list = new ArrayList(a);
        final Iterator it = b.iterator();

        while (it.hasNext()) {
            list.remove(it.next());
        }

        return list;
    }

    /**
     * Finds only matching valid maildir directories.
     */
    class MaildirFileFilter implements FileFilter {
        final String pattern;

        /**
         * Creates a new MaildirFileFilter to match the given pattern.
         */
        public MaildirFileFilter(String pPattern) {
            if (pPattern == null) {
                pPattern = "%";
            }

            if (str.endsWith(".")) {
                pattern = str + pPattern;
            } else {
                pattern = str + '.' + pPattern;
            }
        }

        /**
         * Tests whether or not the specified abstract pathname should be
         * included in a pathname list.
         */
        public boolean accept(File f) {
            // first, only match if it's a directory that has cur, new, and
            // tmp directories under it.
            if (!(f.isDirectory() && (new File(f, "cur")).isDirectory() &&
                    (new File(f, "new")).isDirectory() &&
                    (new File(f, "tmp")).isDirectory())) {
                return false;
            }

            String fileName = f.getName();

            // ...and only match directories which match the given string.
            // this is really annoying.  it's a shame that regexp doesn't show up
            // until jdk 1.4
            // to work with non-ascii mailbox names
            fileName = BASE64MailboxDecoder.decode(fileName); //

            boolean noRecurse = false;

            int wildcard = pattern.indexOf('*');

            if (wildcard < 0) {
                wildcard = pattern.indexOf('%');
                noRecurse = true;
            }

            if (wildcard < 0) {
                return fileName.equals(pattern);
            }

            if (wildcard > 0) {
                // test the left side.
                if (!fileName.startsWith(pattern.substring(0, wildcard))) {
                    return false;
                }
            }

            if (wildcard != (pattern.length() - 1)) {
                // test the right side.
                if (!fileName.endsWith(pattern.substring(wildcard + 1))) {
                    return false;
                }
            }

            if (noRecurse) {
                if (fileName.substring(wildcard,
                        fileName.length() - (pattern.length() - wildcard) +
                        1).indexOf(getSeparator()) > -1) {
                    return false;
                }
            }

            return true;
        }
    }

    private final static void streamClose(OutputStream outs) {
        if (outs != null)
            try {
                outs.close();
            } catch (Exception ex) {
            }
    }

    private final static void streamClose(InputStream ins) {
        if (ins != null)
            try {
                ins.close();
            } catch (Exception ex) {
            }
    }

    private static final void sleep(long usec) {
        try {
            Thread.sleep(usec);
        } catch (Exception ex) {
        }
    }

    //quick hack for recursive deletion
    private final static boolean rmdir(File d) {
        log.debug("TRACE: rmdir(" + d + ')');
        if (!d.exists())
            return false;

        if (d.isFile()) {
            return d.delete();
        }

        final File[] list = d.listFiles();

        if (list.length == 0) {
            return d.delete();
        }

        for (int i = 0; i < list.length; i++)
            if (list[i].isDirectory()) {
                return rmdir(list[i]);
            } else {
                list[i].delete();
            }

        return false;
    }

    protected void finalize() throws Throwable {
        close(true);
        super.finalize();
    }
}
