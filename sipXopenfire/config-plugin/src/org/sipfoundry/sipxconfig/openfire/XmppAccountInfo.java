/*
 *
 *
 * Copyright (C) 2009 Nortel, certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.openfire;

import org.dom4j.Document;
import org.dom4j.Element;
import org.sipfoundry.commons.userdb.ValidUsers;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.dialplan.config.XmlFile;
import org.sipfoundry.sipxconfig.im.ImAccount;
import org.sipfoundry.sipxconfig.imbot.ImBot;
import org.sipfoundry.sipxconfig.imbot.ImBotSettings;

public class XmppAccountInfo {
    private static final String NAMESPACE = "http://www.sipfoundry.org/sipX/schema/xml/xmpp-account-info-00-00";
    private static final String USER = "user";
    private static final String USER_NAME = "user-name";
    private static final String PASSWORD = "password";
    private static final String DISPLAY_NAME = "display-name";

    private CoreContext m_coreContext;
    private ValidUsers m_validUsers;
    private ImBot m_imbot;

    public Document getDocument() {
        ImBotSettings imbotSettings = m_imbot.getSettings();
        Document document = XmlFile.FACTORY.createDocument();
        final Element accountInfos = document.addElement("xmpp-account-info", NAMESPACE);

        createPaUserAccount(accountInfos, imbotSettings);

        return document;
    }

    private void createPaUserAccount(Element accountInfos, ImBotSettings imbotSettings) {
        String paUserName = imbotSettings.getPersonalAssistantImId();
        String paPassword = imbotSettings.getPersonalAssistantImPassword();

        User paUser = m_coreContext.newUser();
        paUser.setUserName(paUserName);
        ImAccount imAccount = new ImAccount(paUser);
        imAccount.setEnabled(true);

        createUserAccount(paUser, accountInfos, paPassword);
    }

    private void createUserAccount(User user, Element accountInfos, String paPassword) {
        ImAccount imAccount = new ImAccount(user);
        if (!imAccount.isEnabled()) {
            return;
        }

        Element userAccounts = accountInfos.addElement(USER);
        userAccounts.addElement(USER_NAME).setText(imAccount.getImId());
        userAccounts.addElement("sip-user-name").setText(user.getName());
        userAccounts.addElement(DISPLAY_NAME).setText(imAccount.getImDisplayName());
        userAccounts.addElement(PASSWORD).setText(paPassword);
        String email = imAccount.getEmailAddress();
        userAccounts.addElement("email").setText(email != null ? email : "");
        userAccounts.addElement("on-the-phone-message").setText(imAccount.getOnThePhoneMessage());
        userAccounts.addElement("advertise-on-call-status").setText(
                Boolean.toString(imAccount.advertiseSipPresence()));
        userAccounts.addElement("show-on-call-details").setText(Boolean.toString(imAccount.includeCallInfo()));
    }

    public ValidUsers getValidUsers() {
        return m_validUsers;
    }

    public void setValidUsers(ValidUsers validUsers) {
        m_validUsers = validUsers;
    }

    public void setCoreContext(CoreContext coreContext) {
        m_coreContext = coreContext;
    }

    public void setImbot(ImBot imbot) {
        m_imbot = imbot;
    }
}