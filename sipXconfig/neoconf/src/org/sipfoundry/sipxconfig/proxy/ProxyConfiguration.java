/*
 *
 *
 * Copyright (C) 2009 Nortel, certain elements licensed under a Contributor Agreement.
 * Contributors retain copyright to elements licensed under a Contributor Agreement.
 * Licensed to the User under the LGPL license.
 *
 *
 */
package org.sipfoundry.sipxconfig.proxy;

import static java.lang.String.format;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.sipfoundry.sipxconfig.admin.AbstractResLimitsConfig;
import org.sipfoundry.sipxconfig.admin.AdminContext;
import org.sipfoundry.sipxconfig.admin.AdminSettings;
import org.sipfoundry.sipxconfig.cdr.CdrManager;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigManager;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigProvider;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigRequest;
import org.sipfoundry.sipxconfig.cfgmgt.ConfigUtils;
import org.sipfoundry.sipxconfig.cfgmgt.KeyValueConfiguration;
import org.sipfoundry.sipxconfig.commserver.Location;
import org.sipfoundry.sipxconfig.dialplan.config.XmlFile;
import org.sipfoundry.sipxconfig.domain.Domain;
import org.sipfoundry.sipxconfig.feature.FeatureManager;
import org.sipfoundry.sipxconfig.setting.Setting;
import org.sipfoundry.sipxconfig.tls.TlsPeer;
import org.sipfoundry.sipxconfig.tls.TlsPeerManager;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class ProxyConfiguration implements ConfigProvider, ApplicationContextAware {
    private static final String NAMESPACE = "http://www.sipfoundry.org/sipX/schema/xml/peeridentities-00-00";
    private static final String PROXY = "sipxproxy";
    private static final String PROXY_CFDAT = "sipxproxy.cfdat";
    private TlsPeerManager m_tlsPeerManager;
    private ProxyManager m_proxyManager;
    private ApplicationContext m_context;
    private AbstractResLimitsConfig m_proxyLimitsConfig;
    private String m_libDir;
    private AdminContext m_adminContext;
    private String m_etcDir;

    @Override
    public void replicate(ConfigManager manager, ConfigRequest request) throws IOException {
        if (!request.applies(ProxyManager.FEATURE, TlsPeerManager.FEATURE)) {
            return;
        }

        FeatureManager fm = manager.getFeatureManager();
        Set<Location> locations = request.locations(manager);
        boolean isCdrOn = manager.getFeatureManager().isFeatureEnabled(CdrManager.FEATURE);
        ProxySettings settings = m_proxyManager.getSettings();
        Domain domain = manager.getDomainManager().getDomain();
        Collection<TlsPeer> peers = m_tlsPeerManager.getTlsPeers();
        for (Location location : locations) {
            File dir = getLocationDataDirectory(location);
            boolean enabled = fm.isFeatureEnabled(ProxyManager.FEATURE, location);
            if (!enabled) {
                ConfigUtils.enableCfengineClass(dir, PROXY_CFDAT, enabled, PROXY);
            } else {
                ConfigUtils.enableCfengineClass(dir, PROXY_CFDAT, enabled, PROXY, "postgres");
            }

            // always generate only because sipxbridge needs file and harmless to generate
            // even if not every machine needs it.
            Writer peersConfig = new FileWriter(new File(dir, "peeridentities.xml"));
            try {
                XmlFile config = new XmlFile(peersConfig);
                config.write(getDocument(peers));
            } finally {
                IOUtils.closeQuietly(peersConfig);
            }

            if (!enabled) {
                continue;
            }
            Writer proxy = new FileWriter(new File(dir, "sipXproxy-config"));
            try {
                write(proxy, settings, location, domain, isCdrOn);
            } finally {
                IOUtils.closeQuietly(proxy);
            }
            //Write proxy resource limits separately to notify proxy that needs to get restarted
            //All resource limits for all services are globbaly agregated and replicated in ResLimitsConfiguration.java
            //The replication is effective on each node that runs
            //at least one of the processes: mwi, registrar, proxy, park, rls, saa
            Writer proxyResLimitsWriter = new FileWriter(new File(dir, "resource-limits-proxy.ini"));
            try {
                m_proxyLimitsConfig.writeResourceLimits(proxyResLimitsWriter, settings);
            } finally {
                IOUtils.closeQuietly(proxyResLimitsWriter);
            }
        }
    }

    void write(Writer wtr, ProxySettings settings, Location location, Domain domain, boolean isCdrOn)
        throws IOException {
        KeyValueConfiguration config = KeyValueConfiguration.colonSeparated(wtr);
        Setting root = settings.getSettings();
        Setting proxyConfigurationSettings = root.getSetting("proxy-configuration");
        config.writeSettings(proxyConfigurationSettings);
        config.writeSettings("SIPX_PROXY.205_subscriptionauth.", root.getSetting("subscriptionauth"));
        config.writeSettings("SIPX_PROXY.350_calleralertinfo.", root.getSetting("alert-info"));
        config.writeSettings("SIPX_PROXY.400_authrules.", root.getSetting("authrules"));
        String xferPlugin = "SIPX_PROXY.200_xfer.";
        config.writeSetting(xferPlugin, root.getSetting("msftxchghack/EXCHANGE_SERVER_FQDN"));
        config.writeSetting(xferPlugin, root.getSetting("msftxchghack/ADDITIONAL_EXCHANGE_SERVER_FQDN"));
        config.writeSetting("SIPX_PROXY.210_msftxchghack.", root.getSetting("msftxchghack/USERAGENT"));
        config.write("SIPX_PROXY_HOST_NAME", location.getFqdn());
        int port = settings.getSipTcpPort();
        String aliases = format("%s:%d %s:%d", location.getAddress(), port, location.getFqdn(), port);
        config.write("SIPX_PROXY_HOST_ALIASES", aliases);
        config.write("SIPX_PROXY_CALL_STATE_DB", isCdrOn ? "ENABLE" : "DISABLE");
        config.write("SIPX_PROXY_HOSTPORT", location.getAddress() + ':' + port);
        config.write("SIPX_PROXY_AUTHENTICATE_REALM", domain.getSipRealm());
        

        // write proxy hooks
        config.write("SIPX_PROXY_HOOK_LIBRARY.200_xfer", getFullLibDir("authplugins/libTransferControl.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.205_subscriptionauth",
                getFullLibDir("authplugins/libSubscriptionAuth.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.210_msftxchghack",
                getFullLibDir("authplugins/libMSFT_ExchangeTransferHack.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.300_calldestination",
                getFullLibDir("authplugins/libCallDestination.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.350_calleralertinfo",
                getFullLibDir("authplugins/libCallerAlertInfo.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.400_authrules",
                getFullLibDir("authplugins/libEnforceAuthRules.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.700_fromalias",
                getFullLibDir("authplugins/libCallerAlias.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.900_ntap",
                getFullLibDir("authplugins/libNatTraversalAgent.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.995_requestlinter",
                getFullLibDir("authplugins/libRequestLinter.so"));
        config.write("SIPX_PROXY_HOOK_LIBRARY.990_emerglineid",
                getFullLibDir("authplugins/libEmergencyLineIdentifier.so"));
        config.write("SIPX_PROXY.400_authrules.RULES", getFullEtcDir("authrules.xml"));
        config.write("SIPX_PROXY.990_emerglineid.EMERGRULES", getFullEtcDir("authrules.xml"));
        config.write("SIPX_PROXY_BIND_IP", location.getAddress());
        config.write("SIPX_PROXY_CALL_STATE_DB_PASSWORD", getPostgresPassword());
        config.write("SIPX_PROXY_CALL_STATE_DB_HOST", "postgres.cdr");
        config.write("SIPX_PROXY_CALL_STATE_DB_NAME", "sipxcdr");
        Setting consultativeTransfer = proxyConfigurationSettings
                .getSetting("SIPX_CONSULTATIVE_TRANSFER_GATEWAY_INITIAL_INVITE");
        boolean isConsultativeTransfer = Boolean.parseBoolean(consultativeTransfer.getValue());
        if (isConsultativeTransfer) {
            config.write("SIPX_TRAN_HOOK_LIBRARY.905_gatewaydest",
                    getFullLibDir("transactionplugins/libGatewayDestPlugin.so"));
        }

        // write plugin proxy hooks
        Map<String, ProxyHookPlugin> beans = m_context.getBeansOfType(ProxyHookPlugin.class);
        if (beans != null) {
            for (ProxyHookPlugin bean : beans.values()) {
                if (bean.isEnabled()) {
                    config.write(bean.getProxyHookName(), bean.getProxyHookValue());
                }
            }
        }
    }

    public Document getDocument(Collection<TlsPeer> peers) {
        Document document = XmlFile.FACTORY.createDocument();
        final Element peerIdentities = document.addElement("peeridentities", NAMESPACE);
        for (TlsPeer peer : peers) {
            Element peerElement = peerIdentities.addElement("peer");
            peerElement.addElement("trusteddomain").setText(peer.getName());
            peerElement.addElement("internaluser").setText(peer.getInternalUser().getUserName());
        }

        return document;
    }

    private String getPostgresPassword() {
        AdminSettings settings = m_adminContext.getSettings();
        String password = settings.getPostgresPassword();
        return password;
    }

    @Required
    public void setTlsPeerManager(TlsPeerManager peerManager) {
        m_tlsPeerManager = peerManager;
    }

    public void setProxyManager(ProxyManager proxyManager) {
        m_proxyManager = proxyManager;
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        m_context = context;
    }

    @Required
    public void setProxyLimitsConfig(AbstractResLimitsConfig proxyLimitsConfig) {
        m_proxyLimitsConfig = proxyLimitsConfig;
    }

    public void setAdminContext(AdminContext adminContext) {
        m_adminContext = adminContext;
    }

    public void setLibDir(String libDir) {
        m_libDir = libDir;
    }

    public void setEtcDir(String etcDir) {
        m_etcDir = etcDir;
    }

    private String getFullLibDir(String lib) {
        return m_libDir + "/" + lib;
    }

    private String getFullEtcDir(String lib) {
        return m_etcDir + "/" + lib;
    }

    private File getLocationDataDirectory(Location location) {
        File d = new File(m_etcDir + "/conf", String.valueOf(location.getId()));
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }
}
