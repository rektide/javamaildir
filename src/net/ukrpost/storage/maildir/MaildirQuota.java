package net.ukrpost.storage.maildir;

public final class MaildirQuota {
    public static final class Resource {
        public final String name;
        public long usage;
        public long limit;

        public Resource(String name, long usage, long limit) {
            this.name = name;
            this.usage = usage;
            this.limit = limit;
        }
    }

    public final String quotaRoot;
    public java.util.Vector resources = new java.util.Vector();

    public MaildirQuota(String s) {
        quotaRoot = s;
    }

    public MaildirQuota(MaildirQuota q) {
        this(q.quotaRoot);
        resources = q.resources;
    }

    public long getResourceUsage(String s) {
        if (resources == null)
            return 0L;

        for (int i = 0; i < resources.size(); i++)
            if (((Resource) resources.get(i)).name.equalsIgnoreCase(s))
                return ((Resource) resources.get(i)).usage;

        return 0L;
    }

    /*public String toString()
    {
        StringBuffer out = new StringBuffer();
        for (int i = 0; i < resources.size(); i++) {
            out.append("[");
            out.append(((Resource)resources.get(i)).name);
            out.append(" (");
            out.append(((Resource)resources.get(i)).limit);
            out.append(":");
            out.append(((Resource)resources.get(i)).usage);
            out.append(")]");
        }
        return out.toString();
    }*/

    public long getResourceLimit(String name) {
        if (resources == null)
            return 0L;

        for (int i = 0; i < resources.size(); i++)
            if (((Resource) resources.get(i)).name.equalsIgnoreCase(name))
                return ((Resource) resources.get(i)).limit;

        return 0L;
    }

    public void setResourceUsage(String name, long usage) {
        if (name == null)
            return;

        for (int i = 0; i < resources.size(); i++)
            if (((Resource) resources.get(i)).name.equalsIgnoreCase(name)) {
                ((Resource) resources.get(i)).usage = usage;
                break;
            }
        resources.add(new Resource(name, usage, 0L));
    }

    public void setResourceLimit(String name, long limit) {
        if (name == null)
            return;

        for (int i = 0; i < resources.size(); i++)
            if (((Resource) resources.get(i)).name.equalsIgnoreCase(name)) {
                ((Resource) resources.get(i)).limit = limit;
                break;
            }
        resources.add(new Resource(name, 0L, limit));

    }
}
