package net.ukrpost.storage.maildir;

import javax.mail.Flags;
import java.io.File;
import java.util.ArrayList;

public class MaildirUtils {
    private final static MaildirFilename[] EMPTY_MFNS = new MaildirFilename[]{};

    public final static MaildirFilename[] listMfns(File dir) {
        final File[] files = dir.listFiles();
        if (files == null)
            return EMPTY_MFNS;

        final ArrayList messages = new ArrayList(files.length);

        for (int i = 0; i < files.length; i++) {
            //check ".nfs*" files to avoid adding deleted NFS files
            if (!files[i].isFile()
                    || files[i].getName().startsWith(".nfs")) {
                continue;
            }
            //TODO: should we extend MaildirFilename from File?
            final MaildirFilename mfn = new MaildirFilename(files[i]);
            if (!files[i].getName().startsWith(mfn.getUniq()))
                mfn.setHostname(files[i].getName());
            messages.add(mfn);
        }

        return (MaildirFilename[]) messages.toArray(EMPTY_MFNS);
    }

    /**
     * Checks whether given flag is set or unset.
     * Example: to check for unseen messages call: getFlaggedCount(dir, Flags.Flag.SEEN, false)
     *
     * @param dir
     * @param flag
     * @param flagState
     * @return
     */
    public final static int getFlaggedCount(File dir, Flags.Flag flag, boolean flagState) {
        final File[] files = dir.listFiles();
        if (files == null)
            return 0;

        int result = 0;
        for (int i = 0; i < files.length; i++) {
            //check ".nfs*" files to avoid adding deleted NFS files
            if (!files[i].isFile()
                    || files[i].getName().startsWith(".nfs")) {
                continue;
            }
            //TODO: should we extend MaildirFilename from File?
            final MaildirFilename mfn = new MaildirFilename(files[i]);

            if (mfn.getFlag(flag) == flagState)
                result++;
        }

        return result;
    }

}
