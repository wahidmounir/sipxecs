/*
 *
 *
 * Copyright (C) 2009 Pingtel Corp., certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 * $
 */
package org.sipfoundry.sipxconfig.rest;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import junit.framework.TestCase;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.io.IOUtils;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;
import org.sipfoundry.sipxconfig.common.CoreContext;
import org.sipfoundry.sipxconfig.common.User;
import org.sipfoundry.sipxconfig.phonebook.AddressBookEntry;
import org.sipfoundry.sipxconfig.phonebook.Phonebook;
import org.sipfoundry.sipxconfig.phonebook.PhonebookEntry;
import org.sipfoundry.sipxconfig.phonebook.PhonebookManager;
import org.sipfoundry.sipxconfig.security.TestAuthenticationToken;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.easymock.classextension.EasyMock.createMock;

public class UserPhonebookSearchResourceTest extends TestCase {
    protected PhonebookManager m_phonebookManager;
    protected UserResource m_resource;
    protected User m_user;
    protected CoreContext m_coreContext;

    @Override
    protected void setUp() throws Exception {
        m_user = new User();
        m_user.setUniqueId();
        m_user.setUserName("200");
        m_coreContext = createMock(CoreContext.class);
        m_coreContext.loadUser(m_user.getId());
        expectLastCall().andReturn(m_user);
        m_coreContext.loadUserByUserName(m_user.getUserName());
        expectLastCall().andReturn(m_user);
        m_coreContext.saveUser(m_user);
        expectLastCall().andReturn(false);
        replay(m_coreContext);

        Authentication token = new TestAuthenticationToken(m_user, false, false).authenticateToken();
        SecurityContextHolder.getContext().setAuthentication(token);

        Phonebook phonebook = new Phonebook();
        Collection<Phonebook> phonebooks = new ArrayList<Phonebook>();
        phonebooks.add(phonebook);

        m_phonebookManager = createMock(PhonebookManager.class);
        m_phonebookManager.getPhonebooksByUser(m_user);
        expectLastCall().andReturn(phonebooks);
        m_phonebookManager.search(phonebooks, "searchTerm", m_user);
        expectLastCall().andReturn(getMockPhonebookEntries());
        replay(m_phonebookManager);

        m_resource = new UserPhonebookSearchResource();
        UserPhonebookSearchResource resource = (UserPhonebookSearchResource) m_resource;
        resource.setPhonebookManager(m_phonebookManager);
        resource.setCoreContext(m_coreContext);
        Request request = new Request();
        Reference reference = new Reference();
        reference.addQueryParameter("query", "searchTerm");
        request.setResourceRef(reference);
        ChallengeResponse challengeResponse = new ChallengeResponse(null, "200", new char[0]);
        request.setChallengeResponse(challengeResponse);
        resource.setRequest(request);
        resource.init(null, request, null);
    }

    protected Collection<PhonebookEntry> getMockPhonebookEntries() {

        PhonebookEntry entry1 = createMock(PhonebookEntry.class);
        PhonebookEntry entry2 = createMock(PhonebookEntry.class);

        entry1.getFirstName();
        expectLastCall().andReturn("FirstName1");
        entry1.getLastName();
        expectLastCall().andReturn("LastName1");
        entry1.getNumber();
        expectLastCall().andReturn("200");
        entry1.getAddressBookEntry();
        expectLastCall().andReturn(new AddressBookEntry()).anyTimes();
        entry2.getFirstName();
        expectLastCall().andReturn("FirstName2");
        entry2.getLastName();
        expectLastCall().andReturn("LastName2");
        entry2.getNumber();
        expectLastCall().andReturn("201");
        entry2.getAddressBookEntry();
        expectLastCall().andReturn(new AddressBookEntry()).anyTimes();

        replay(entry1, entry2);

        return Arrays.asList(entry1, entry2);
    }

    public void testRepresentXml() throws Exception {
        Representation representation = m_resource.represent(new Variant(MediaType.TEXT_XML));

        StringWriter writer = new StringWriter();
        representation.write(writer);
        String generated = writer.toString();
        String expected = IOUtils.toString(getClass().getResourceAsStream("user-phonebook-search.rest.test.xml"));
        assertEquals(expected, generated);

        verify(m_phonebookManager);
    }

    public void testRepresentJson() throws Exception {
        Representation representation = m_resource.represent(new Variant(MediaType.APPLICATION_JSON));

        StringWriter writer = new StringWriter();
        representation.write(writer);
        String generated = writer.toString();
        String expected = IOUtils.toString(getClass().getResourceAsStream("user-phonebook-search.rest.test.json"));
        assertEquals(expected, generated);

        verify(m_phonebookManager);
    }
}
