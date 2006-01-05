package net.ukrpost.storage.maildir;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    public final Map resources = Collections.synchronizedMap(new HashMap());

    public MaildirQuota(String s) {
        quotaRoot = s;
    }

    public long getResourceUsage(String name) {
        Resource resource = (Resource) resources.get(name);
        if (resource == null) return 0L;
        return resource.usage;
    }

    public long getResourceLimit(String name) {
        Resource resource = (Resource) resources.get(name);
        if (resource == null) return 0L;
        return resource.limit;
    }

    public void setResourceUsage(String name, long usage) {
        Resource resource;
        if (!resources.containsKey(name)) {
            resource = new Resource(name, usage, 0L);
            resources.put(name, resource);
        } else {
            resource = (Resource) resources.get(name);
            resource.usage = usage;
        }
    }

    public void setResourceLimit(String name, long limit) {
        Resource resource;
        if (!resources.containsKey(name)) {
            resource = new Resource(name, 0L, limit);
            resources.put(name, resource);
        } else {
            resource = (Resource) resources.get(name);
            resource.limit = limit;
        }
    }
}
