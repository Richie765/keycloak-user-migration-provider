/*
 * Copyright 2015 Smartling, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.smartling.keycloak.provider;

import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;

import java.util.List;

/**
 * Remote user federation provider factory.
 *
 * @author Scott Rossillo
 */
public class RemoteUserFederationProviderFactory implements UserStorageProviderFactory<RemoteUserFederationProvider> {
    private static final Logger LOG = Logger.getLogger(RemoteUserFederationProviderFactory.class);
    
    public static final String PROVIDER_NAME = "User Migration API Provider";

    protected static final List<ProviderConfigProperty> configMetadata;

    static {
        configMetadata = ProviderConfigurationBuilder.create()
                .property().name("base_uri")
                .type(ProviderConfigProperty.STRING_TYPE)
                .label("Base URI")
                .helpText("User Validation Host Base URI")
                .add().build();
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        LOG.warn("Returning configuration options");
        return configMetadata;
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }

    @Override
    public RemoteUserFederationProvider create(KeycloakSession session, ComponentModel model) {
        return new RemoteUserFederationProvider(session, model, model.getConfig().getFirst("base_uri"));
    }

    @Override
    public RemoteUserFederationProvider create(KeycloakSession session) {
        return null;
    }

    @Override
    public String getHelpText() {
        return "help text";
    }

    @Override
    public void init(Scope config) {
        // no-op
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    // Removed in 2.5.0
    // @Override
    // public UserFederationSyncResult syncAllUsers(KeycloakSessionFactory sessionFactory, String realmId, UserFederationProviderModel model)
    // {
    //     throw new UnsupportedOperationException("This federation provider doesn't support syncAllUsers()");
    // }

    // Removed in 2.5.0
    // @Override
    // public UserFederationSyncResult syncChangedUsers(KeycloakSessionFactory sessionFactory, String realmId, UserFederationProviderModel model, Date lastSync)
    // {
    //     throw new UnsupportedOperationException("This federation provider doesn't support syncChangedUsers()");
    // }
}
