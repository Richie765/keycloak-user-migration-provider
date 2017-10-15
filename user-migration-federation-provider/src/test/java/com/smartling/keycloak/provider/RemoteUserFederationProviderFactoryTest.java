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

import org.junit.Before;
import org.junit.Test;
import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderModel;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Remote user federation provider factory tests.
 */
public class RemoteUserFederationProviderFactoryTest {

    private RemoteUserFederationProviderFactory factory;

    @Mock
    private KeycloakSessionFactory keycloakSessionFactory;

    @Mock
    private KeycloakSession keycloakSession;

    @Mock
    private Scope config;

    @Mock
    private UserStorageProviderModel userStorageProviderModel;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        factory = new RemoteUserFederationProviderFactory();
        // when(userFederationProviderModel.getConfigProperties())
        //         .thenReturn(Collections.singletonMap("base_uri", "https://fake.com"));
    }

    @Test
    public void testGetInstance() throws Exception {
        UserStorageProvider provider = factory.create(keycloakSession, userStorageProviderModel);
        assertNotNull(provider);
        assertTrue(provider instanceof RemoteUserFederationProvider);
    }

    @Test
    public void testGetId() throws Exception {
        assertEquals(RemoteUserFederationProviderFactory.PROVIDER_NAME, factory.getId());
    }

    @Test
    public void testCreate() throws Exception {
        assertNull(factory.create(keycloakSession));
    }

    @Test
    public void testInit() throws Exception {
        factory.init(config);
        verifyZeroInteractions(config);
    }

    @Test
    public void testPostInit() throws Exception {
        factory.postInit(keycloakSessionFactory);
        verifyZeroInteractions(keycloakSession, keycloakSessionFactory);
    }

    @Test
    public void testClose() throws Exception {
        factory.close();
        verifyZeroInteractions(keycloakSession, keycloakSessionFactory);
    }
}