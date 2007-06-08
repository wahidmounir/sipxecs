/*
 * 
 * 
 * Copyright (C) 2007 Pingtel Corp., certain elements licensed under a Contributor Agreement.  
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 * 
 * $
 */
package org.sipfoundry.sipxconfig.site.vm;

import java.util.Collection;

import org.apache.tapestry.annotations.Bean;
import org.apache.tapestry.annotations.InjectObject;
import org.apache.tapestry.annotations.Lifecycle;
import org.apache.tapestry.bean.EvenOdd;
import org.apache.tapestry.event.PageEvent;
import org.sipfoundry.sipxconfig.components.RowInfo;
import org.sipfoundry.sipxconfig.components.TapestryUtils;
import org.sipfoundry.sipxconfig.permission.PermissionName;
import org.sipfoundry.sipxconfig.site.user_portal.UserBasePage;
import org.sipfoundry.sipxconfig.vm.DistributionList;
import org.sipfoundry.sipxconfig.vm.Mailbox;
import org.sipfoundry.sipxconfig.vm.MailboxManager;

public abstract class EditDistributionLists extends UserBasePage {
    public static final String PAGE = "vm/EditDistributionLists";

    @InjectObject(value = "spring:mailboxManager")
    public abstract MailboxManager getMailboxManager();

    public abstract DistributionList[] getDistributionLists();

    public abstract void setDistributionLists(DistributionList[] lists);

    public abstract DistributionList getDistributionList();

    @Bean()
    public RowInfo getRowInfo() {
        return RowInfo.UNSELECTABLE;
    }

    @Bean(lifecycle = Lifecycle.PAGE)
    public abstract EvenOdd getRowClass();

    public String getExtensionsString() {
        return TapestryUtils.joinBySpace(getDistributionList().getExtensions());
    }

    public void save() {
        DistributionList[] dls = getDistributionLists();
        if (dls != null) {
            Collection<String> aliases = DistributionList.getUniqueExtensions(dls);
            getCoreContext().checkForValidExtensions(aliases, PermissionName.VOICEMAIL);
        }
        if (!getValidator().getHasErrors()) {
            Mailbox mailbox = getMailboxManager().getMailbox(getUser().getUserName());
            getMailboxManager().saveDistributionLists(mailbox, dls);
        }
    }

    public void setExtensionsString(String extensions) {
        getDistributionList().setExtensions(TapestryUtils.splitBySpace(extensions));
    }

    @Override
    public void pageBeginRender(PageEvent event) {
        super.pageBeginRender(event);

        DistributionList[] lists = getDistributionLists();
        if (lists == null) {
            Mailbox mailbox = getMailboxManager().getMailbox(getUser().getUserName());
            lists = getMailboxManager().loadDistributionLists(mailbox);
            setDistributionLists(lists);
        }
    }
}
