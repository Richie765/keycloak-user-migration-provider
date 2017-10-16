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
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.ReadOnlyException;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

/**
 * Remote API based user federation provider.
 *
 * @author Scott Rossillo
 */
public class RemoteUserFederationProvider implements
        UserStorageProvider,
        UserLookupProvider, // Basic login capabilities
        CredentialInputValidator, // validate CredentialInput, i.e. verify a password
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
        LOG.infof("Using validation base URI: " + uri);
    }

    protected RemoteUserFederationProvider(KeycloakSession session, ComponentModel model, FederatedUserService federatedUserService) {
        this.session = session;
        this.model = model;
        this.federatedUserService = federatedUserService;
    }


    // UserStorageProvider

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


    // UserLookupProvider

    // When legacyUser is found, create user in Keycloak
    private UserModel createUserModel(RealmModel realm, String rawUsername) throws NotFoundException {
        String username = rawUsername.toLowerCase().trim();
        LOG.infof("Creating user model for: %s", username);

        FederatedUserModel legacyUser = federatedUserService.getUserDetails(username);
        if (!username.equals(legacyUser.getEmail())) {
            throw new IllegalStateException(String.format("Local and remote users differ: [%s != %s]", username, legacyUser.getUsername()));
        }

        UserModel userModel = session.userLocalStorage().addUser(realm, username);
        LOG.info("User model created");

        userModel.setFederationLink(model.getId());
        userModel.setEnabled(legacyUser.isEnabled());
        userModel.setEmail(username);
        userModel.setEmailVerified(legacyUser.isEmailVerified());
        userModel.setFirstName(legacyUser.getFirstName());
        userModel.setLastName(legacyUser.getLastName());

        if (legacyUser.getAttributes() != null) {
            Map<String, List<String>> attributes = legacyUser.getAttributes();
            for (String attributeName : attributes.keySet())
                userModel.setAttribute(attributeName, attributes.get(attributeName));
        }

        if (legacyUser.getRoles() != null) {
            for (String role : legacyUser.getRoles()) {
                RoleModel roleModel = realm.getRole(role);
                if (roleModel != null) {
                    userModel.grantRole(roleModel);
                    LOG.infof("Granted user %s, role %s", username, role);
                }
            }
        }

        return userModel;
    }

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

    @Override
    public UserModel getUserById(String id, RealmModel realm) {
        LOG.infof("Get by id: %s", id);

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

   
    // CredentialInputValidator

    @Override
    public boolean isConfiguredFor(RealmModel realm,UserModel user,String credentialType) {
        LOG.infof("isConfiguredFor: %s", credentialType);
        return true;
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        LOG.infof("supportsCredentialType: %s", credentialType);
        return credentialType.equals(CredentialModel.PASSWORD);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user,CredentialInput input) {
        LOG.infof("isValid", user.getUsername());
        if (!(input instanceof UserCredentialModel)) return false;

        LOG.info("isValid: Checking if user exists");
        Response response = federatedUserService.validateUserExists(user.getUsername());
        if(HttpStatus.SC_OK != response.getStatus()) return false;
        LOG.info("isValid: User exists");

        // Check password

        LOG.info("isValid: Validating credentials");

        UserCredentialModel credentials = (UserCredentialModel)input;

        response = federatedUserService.validateLogin(user.getUsername(), new UserCredentialsDto(credentials.getValue()));
        boolean valid = HttpStatus.SC_OK == response.getStatus();

        if (valid) {
            LOG.info("isValid: Credentials are valid");
            user.setFederationLink(null);
            this.session.userCredentialManager().updateCredential(realm, user, input);
            // user.updateCredential(credentials);
            LOG.info("isValid: Credentials updated in Keycloak and FederationLink removed");
        }

        return valid;        
    }


    // CredentialInputUpdater

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        LOG.info("updateCredential");
        if (input.getType().equals(CredentialModel.PASSWORD)) throw new ReadOnlyException("user is read only for this update");
        
        return false;
    }    

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
        LOG.infof("disableCredentialType: %s", credentialType);
    }
    
    @Override
    public Set<String> getDisableableCredentialTypes(RealmModel realm, UserModel user) {
        LOG.info("getDisableableCredentialTypes");
        return Collections.EMPTY_SET;
    }
}
