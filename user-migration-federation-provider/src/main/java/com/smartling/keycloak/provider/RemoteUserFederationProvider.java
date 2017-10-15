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
import org.apache.http.HttpStatus;
import org.jboss.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.keycloak.models.CredentialValidationOutput;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.models.UserModel;
import org.keycloak.storage.ReadOnlyException;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.storage.user.UserRegistrationProvider;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remote API based user federation provider.
 *
 * @author Scott Rossillo
 */
public class RemoteUserFederationProvider implements
        UserStorageProvider,
        UserLookupProvider,
        // UserRegistrationProvider,
        CredentialInputValidator,
        CredentialInputUpdater {

    private static final Logger LOG = Logger.getLogger(RemoteUserFederationProvider.class);

    private KeycloakSession session;
    protected ComponentModel model;
    private final FederatedUserService federatedUserService;

    private static FederatedUserService buildClient(String uri) {

        ResteasyClient client = new ResteasyClientBuilder().disableTrustManager().build();
        ResteasyWebTarget target =  client.target(uri);

        return target
                .proxyBuilder(FederatedUserService.class)
                .classloader(FederatedUserService.class.getClassLoader())
                .build();
    }

    // Constructor

    public RemoteUserFederationProvider(KeycloakSession session, ComponentModel model, String uri) {
        this(session, model, buildClient(uri));
        LOG.debugf("Using validation base URI: " + uri);
    }

    protected RemoteUserFederationProvider(KeycloakSession session, ComponentModel model, FederatedUserService federatedUserService) {
        this.session = session;
        this.model = model;
        this.federatedUserService = federatedUserService;
    }



    // UserRegistrationProvider - not implemented

    // // was register before 2.5.0
    // @Override
    // public UserModel addUser(RealmModel realm, String username) {
    //     LOG.warn("User registration not supported.");
    //     return null;
    // }

    // @Override
    // public boolean removeUser(RealmModel realm, UserModel user) {
    //     return true;
    // }



    // What does it do exactly?

    private UserModel createUserModel(RealmModel realm, String rawUsername) throws NotFoundException {

        String username = rawUsername.toLowerCase().trim();
        FederatedUserModel remoteUser = federatedUserService.getUserDetails(username);
        LOG.infof("Creating user model for: %s", username);
        // Was useStorage
        UserModel userModel = session.userLocalStorage().addUser(realm, username);

        if (!username.equals(remoteUser.getEmail())) {
            throw new IllegalStateException(String.format("Local and remote users differ: [%s != %s]", username, remoteUser.getUsername()));
        }

        userModel.setFederationLink(model.getId());
        userModel.setEnabled(remoteUser.isEnabled());
        userModel.setEmail(username);
        userModel.setEmailVerified(remoteUser.isEmailVerified());
        userModel.setFirstName(remoteUser.getFirstName());
        userModel.setLastName(remoteUser.getLastName());

        if (remoteUser.getAttributes() != null) {
            Map<String, List<String>> attributes = remoteUser.getAttributes();
            for (String attributeName : attributes.keySet())
                userModel.setAttribute(attributeName, attributes.get(attributeName));
        }

        if (remoteUser.getRoles() != null) {
            for (String role : remoteUser.getRoles()) {
                RoleModel roleModel = realm.getRole(role);
                if (roleModel != null) {
                    userModel.grantRole(roleModel);
                    LOG.infof("Granted user %s, role %s", username, role);
                }
            }
        }

        return userModel;
    }



    // UserLookupProvider

    @Override
    public UserModel getUserByEmail(String email, RealmModel realm) {
        LOG.infof("Get by email: %s", email);

        try {
            return this.createUserModel(realm, email);
        } catch (NotFoundException ex) {
            LOG.error("Federated user (by email) not found: " + email);
            return null;
        }
    }

    // Added 2.5.0
    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        StorageId storageId = new StorageId(id);
        String username = storageId.getExternalId();
        return getUserByUsername(username, realm);
    }

    @Override
    public UserModel getUserByUsername(String username, RealmModel realm) {
        LOG.infof("Get by username: %s", username);

        try {
            return this.createUserModel(realm, username);
        } catch (NotFoundException ex) {
            LOG.errorf("Federated user not found: %s", username);
            return null;
        }
    }

   



    // UserStorageProvider - needed for the factory

    @Override
    public void preRemove(RealmModel realm) {
        // no-op
    }

    @Override
    public void preRemove(RealmModel realm, RoleModel role) {
        // no-op
    }

    @Override
    public void preRemove(RealmModel realm, GroupModel group)
    {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }



    // CredentialInputValidator

    @Override
    public boolean isConfiguredFor(RealmModel realm,UserModel user,String credentialType) {
        return true;
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return credentialType.equals(CredentialModel.PASSWORD);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user,CredentialInput input) {
        if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) return false;

        LOG.debugf("Checking if user is valid: %s", user.getUsername());
        Response response = federatedUserService.validateUserExists(user.getUsername());
        LOG.infof("Checked if %s is valid: %d", user.getUsername(), response.getStatus());
        if(HttpStatus.SC_OK != response.getStatus()) return false;

        // Check password

        LOG.infof("Validating credentials for %s", user.getUsername());

        UserCredentialModel credentials = (UserCredentialModel)input;

        response = federatedUserService.validateLogin(user.getUsername(), new UserCredentialsDto(credentials.getValue()));
        boolean valid = HttpStatus.SC_OK == response.getStatus();

        if (valid) {
            this.session.userCredentialManager().updateCredential(realm, user, input);
            // user.updateCredential(credentials);
            user.setFederationLink(null);
        }

        return valid;        
    }



    // CredentialInputUpdater - good

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        if (input.getType().equals(CredentialModel.PASSWORD)) throw new ReadOnlyException("user is read only for this update");
        
        return false;
    }    

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
    }
    
    @Override
    public Set<String> getDisableableCredentialTypes(RealmModel realm, UserModel user) {
        return Collections.EMPTY_SET;
    }
}
