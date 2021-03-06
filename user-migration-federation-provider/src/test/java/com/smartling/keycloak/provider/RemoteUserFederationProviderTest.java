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

import com.smartling.keycloak.federation.FederatedUserModel;
import com.smartling.keycloak.federation.FederatedUserService;
import com.smartling.keycloak.federation.UserCredentialsDto;
import org.junit.Before;
import org.junit.Test;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.collections.Sets;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Remote user federation provider test cases.
 */
public class RemoteUserFederationProviderTest {

    private static final String FEDERATED_USER_KNOWN_EMAIL = "wa+user@smartling.com";
    private static final String FEDERATED_USER_KNOWN_USERNAME = FEDERATED_USER_KNOWN_EMAIL;
    private static final String FEDERATED_USER_KNOWN_PASSWORD = UUID.randomUUID().toString();
    private static final UserCredentialsDto FEDERATED_USER_CREDENTIALS_DTO = new UserCredentialsDto(FEDERATED_USER_KNOWN_PASSWORD);
    private static final String FEDERATED_USER_ROLE = "ROLE_FOO";

    private static final String KEYCLOAK_EXISTING_USER_EMAIL = "keycloak+user@smartling.com";
    private static final String KEYCLOAK_EXISTING_USER_USERNAME = KEYCLOAK_EXISTING_USER_EMAIL;

    private static final String USER_ID = UUID.randomUUID().toString();

    private RemoteUserFederationProvider provider;

    @Mock
    private FederatedUserModel federatedUserModel;

    @Mock
    private FederatedUserService federatedUserService;

    @Mock
    private KeycloakSession keycloakSession;

    @Mock
    private RealmModel realmModel;

    @Mock
    private UserStorageProviderModel userStorageProviderModel;

    @Mock
    private UserModel userModel;

    @Mock
    private UserProvider userProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        provider = new RemoteUserFederationProvider(keycloakSession, userStorageProviderModel, federatedUserService);
        when(userModel.getUsername()).thenReturn(FEDERATED_USER_KNOWN_USERNAME);
        when(userModel.getEmail()).thenReturn(FEDERATED_USER_KNOWN_EMAIL);
        when(userModel.getId()).thenReturn(USER_ID);

        when(keycloakSession.userLocalStorage()).thenReturn(userProvider);

        when(userProvider.addUser(eq(realmModel), eq(FEDERATED_USER_KNOWN_USERNAME))).thenReturn(mock(UserModel.class));
        when(userProvider.getUserByUsername(eq(KEYCLOAK_EXISTING_USER_USERNAME), eq(realmModel))).thenReturn(userModel);
        when(userProvider.getUserByEmail(eq(KEYCLOAK_EXISTING_USER_EMAIL), eq(realmModel))).thenReturn(userModel);
        when(userProvider.getUserById(eq(USER_ID), eq(realmModel))).thenReturn(userModel);

        when(federatedUserModel.getEmail()).thenReturn(FEDERATED_USER_KNOWN_EMAIL);
        when(federatedUserModel.getUsername()).thenReturn(FEDERATED_USER_KNOWN_USERNAME);


        when(federatedUserService.getUserDetails(eq(FEDERATED_USER_KNOWN_EMAIL))).thenReturn(federatedUserModel);
        when(federatedUserService.getUserDetails(eq(FEDERATED_USER_KNOWN_USERNAME))).thenReturn(federatedUserModel);
        when(federatedUserService.validateUserExists(eq(FEDERATED_USER_KNOWN_USERNAME))).thenReturn(Response.accepted().build());
        when(federatedUserService.validateLogin(eq(FEDERATED_USER_KNOWN_USERNAME), any(UserCredentialsDto.class))).thenReturn(Response.status(Status.PRECONDITION_FAILED).build());
        when(federatedUserService.validateLogin(eq(FEDERATED_USER_KNOWN_USERNAME), eq(FEDERATED_USER_CREDENTIALS_DTO))).thenReturn(Response.ok().build());

        when(federatedUserService.validateUserExists(anyString())).thenReturn(Response.status(Status.PRECONDITION_FAILED).build());
        when(federatedUserService.validateUserExists(eq(FEDERATED_USER_KNOWN_USERNAME))).thenReturn(Response.status(Status.PRECONDITION_FAILED).build());
    }

    @Test
    public void testGetUserByUsername() throws Exception {
        assertNotNull(provider.getUserByUsername(FEDERATED_USER_KNOWN_USERNAME, realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_EMAIL));
        verify(federatedUserService, never()).getUserDetails(eq(KEYCLOAK_EXISTING_USER_USERNAME));
    }

    @Test
    public void testGetUserByUsernameWithRole() throws Exception {

        when(federatedUserModel.getRoles()).thenReturn(Sets.newSet(FEDERATED_USER_ROLE));
        when(realmModel.getRole(FEDERATED_USER_ROLE)).thenReturn(mock(RoleModel.class));
        when(keycloakSession.userLocalStorage().addUser(eq(realmModel), eq(FEDERATED_USER_KNOWN_USERNAME))).thenReturn(userModel);

        assertNotNull(provider.getUserByUsername(FEDERATED_USER_KNOWN_USERNAME, realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_EMAIL));
        verify(userModel).grantRole(any(RoleModel.class));
    }

    @Test
    public void testGetUserByUsernameWithNullRoles() throws Exception {

        when(federatedUserModel.getRoles()).thenReturn(null);
        when(keycloakSession.userLocalStorage().addUser(eq(realmModel), eq(FEDERATED_USER_KNOWN_USERNAME))).thenReturn(userModel);

        assertNotNull(provider.getUserByUsername(FEDERATED_USER_KNOWN_USERNAME, realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_EMAIL));
        verify(realmModel, never()).getRole(anyString());
        verify(userModel, never()).grantRole(any(RoleModel.class));
    }

    @Test
    public void testGetUserByUsernameMixedCase() throws Exception {
        assertNotNull(provider.getUserByUsername(FEDERATED_USER_KNOWN_USERNAME.toUpperCase(), realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_USERNAME));
    }

    @Test
    public void testGetUserByUsernameWithLeadingSpace() throws Exception {
        assertNotNull(provider.getUserByUsername(" " + FEDERATED_USER_KNOWN_USERNAME, realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_USERNAME));
    }

    @Test
    public void testGetUserByUsernameWithLeadingAndTrailingSpace() throws Exception {
        assertNotNull(provider.getUserByUsername(" " + FEDERATED_USER_KNOWN_USERNAME + " \t", realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_USERNAME));
    }

    @Test
    public void testGetUserByUsernameWithTrailingSpace() throws Exception {
        assertNotNull(provider.getUserByUsername(FEDERATED_USER_KNOWN_USERNAME + "   ", realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_USERNAME));
    }

    @Test
    public void testGetUserByUsernameWithAttributes() throws Exception {
        provider.getUserByUsername(FEDERATED_USER_KNOWN_USERNAME, realmModel);
        verify(federatedUserModel, times(2)).getAttributes();
    }

    @Test
    public void testGetUserByUsernameWithoutAttributes() throws Exception {
        UserModel user = provider.getUserByUsername(FEDERATED_USER_KNOWN_USERNAME, realmModel);
        verify(federatedUserModel, times(2)).getAttributes();
        verify(user, never()).setAttribute(anyString(), anyListOf(String.class));
    }

    @Test
    public void testGetUserByEmail() throws Exception {
        assertNotNull(provider.getUserByUsername(FEDERATED_USER_KNOWN_EMAIL, realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_EMAIL));
    }

    @Test
    public void testGetUserByEmailMixedCase() throws Exception {
        assertNotNull(provider.getUserByUsername(FEDERATED_USER_KNOWN_EMAIL.toUpperCase(), realmModel));
        verify(federatedUserService).getUserDetails(eq(FEDERATED_USER_KNOWN_EMAIL));
    }

    @Test
    public void testValidCredentialsVarArg() throws Exception {
        assertTrue(provider.isValid(realmModel, userModel, UserCredentialModel.password(FEDERATED_USER_KNOWN_PASSWORD)));
    }


    @Test
    public void testValidCredentialsListInvalid() throws Exception {
        assertFalse(provider.isValid(realmModel, userModel, UserCredentialModel.password(UUID.randomUUID().toString())));
    }

    @Test
    public void testClose() throws Exception {
        provider.close();
        verifyZeroInteractions(keycloakSession, realmModel, federatedUserService);
    }
}